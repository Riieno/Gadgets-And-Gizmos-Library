package com.rieno.gadgetsandgizmos.lib.client.render;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.rieno.gadgetsandgizmos.lib.client.view.ViewSceneEnvironment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.neoforge.client.ClientHooks;
import org.joml.Matrix4f;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.function.Consumer;

// Draw projected GUIs without consuming the player's buffers or changing their render state
public final class FramebufferGuiRenderer implements AutoCloseable{
/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        CONSTANTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    private final ResourceLocation texture;
    private final ByteBufferBuilder storage = new ByteBufferBuilder(8192);
    private final MultiBufferSource.BufferSource buffers = MultiBufferSource.immediate(storage);
    private TextureTarget target;
    private boolean ready;
    private boolean closed;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        FUNCTIONS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    // Keep texture registration and framebuffer allocation inside the first render scope
    public FramebufferGuiRenderer(ResourceLocation texture){
        this.texture = Objects.requireNonNull(texture);
    }

    // Return the owning mod's texture identifier for drawing the completed surface
    public ResourceLocation texture(){ return texture; }

    // Wait for a completed frame before sampling the surface
    public boolean isReady(){ return ready; }

    // Scale logical GUI coordinates and nested clipping to this surface's own raster
    public void render(int width, int height, int guiWidth, int guiHeight, Consumer<GuiGraphics> draw){
        if(closed) throw new IllegalStateException("GUI renderer is closed");
        if(width <= 0 || height <= 0 || guiWidth <= 0 || guiHeight <= 0) return;
        Minecraft mc = Minecraft.getInstance();
        Runnable restore = ViewSceneEnvironment.save();
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        var sorting = RenderSystem.getVertexSorting();
        var modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        try{
            if(target == null){
                target = new TextureTarget(width, height, true, Minecraft.ON_OSX);
                mc.getTextureManager().register(texture, new AbstractTexture(){
                    // Leave attachment ownership with the framebuffer
                    @Override public int getId(){ return target.getColorTextureId(); }
                    @Override public void releaseId(){}
                    @Override public void load(ResourceManager manager){}
                    @Override public void close(){}
                });
            }else if(target.width != width || target.height != height){
                target.resize(width, height, Minecraft.ON_OSX);
            }
            RenderSystem.disableScissor();
            RenderSystem.depthMask(true);
            target.setClearColor(0.04F, 0.055F, 0.075F, 1.0F);
            target.clear(Minecraft.ON_OSX);
            target.bindWrite(true);
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, guiWidth, guiHeight, 0,
                    1000, ClientHooks.getGuiFarPlane()), VertexSorting.ORTHOGRAPHIC_Z);
            modelView.translation(0, 0, 10000 - ClientHooks.getGuiFarPlane());
            RenderSystem.applyModelViewMatrix();
            RenderSystem.enableDepthTest();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1, 1, 1, 1);
            FogRenderer.setupNoFog();
            GuiGraphics graphics = new SurfaceGraphics(mc, buffers, guiWidth, guiHeight, width, height);
            try{
                draw.accept(graphics);
            }finally{
                graphics.flush();
            }
            ready = true;
        }finally{
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(projection, sorting);
            restore.run();
        }
    }

    // Release the surface and its private vertex storage without rebinding the player's target
    @Override
    public void close(){
        if(closed) return;
        Runnable restore = ViewSceneEnvironment.save();
        try{
            closed = true;
            ready = false;
            if(target != null){
                Minecraft.getInstance().getTextureManager().release(texture);
                target.destroyBuffers();
                target = null;
            }
            storage.close();
        }finally{
            restore.run();
        }
    }

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        HELPERS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    // Preserve nested GUI clipping without using the player's window dimensions
    private static final class SurfaceGraphics extends GuiGraphics{
        private final Deque<ScreenRectangle> clips = new ArrayDeque<>();
        private final int guiWidth;
        private final int guiHeight;
        private final int width;
        private final int height;

        private SurfaceGraphics(Minecraft mc, MultiBufferSource.BufferSource buffers,
                                int guiWidth, int guiHeight, int width, int height){
            super(mc, buffers);
            this.guiWidth = guiWidth;
            this.guiHeight = guiHeight;
            this.width = width;
            this.height = height;
        }

        // Use this surface for tooltip and widget viewport calculations
        @Override public int guiWidth(){ return guiWidth; }
        @Override public int guiHeight(){ return guiHeight; }

        // Intersect child clips in logical coordinates before scaling to the target
        @Override
        public void enableScissor(int left, int top, int right, int bottom){
            ScreenRectangle clip = new ScreenRectangle(left, top, right - left, bottom - top);
            if(!clips.isEmpty()) clip = clip.intersection(clips.peek());
            clips.push(clip == null ? new ScreenRectangle(0, 0, 0, 0) : clip);
            applyClip();
        }

        // Reinstate the parent clip after the current child finishes drawing
        @Override
        public void disableScissor(){
            clips.pop();
            applyClip();
        }

        // Keep projected widget hit testing consistent with its visible clip
        @Override
        public boolean containsPointInScissor(int x, int y){
            ScreenRectangle clip = clips.peek();
            return clip == null || x >= clip.left() && x < clip.right()
                    && y >= clip.top() && y < clip.bottom();
        }

        // Flush private geometry before changing the effective GPU clip
        private void applyClip(){
            if(clips.isEmpty()){
                GuiFramebufferScissor.disable(this);
            }else{
                ScreenRectangle clip = clips.peek();
                GuiFramebufferScissor.enable(this, clip.left(), clip.top(), clip.right(), clip.bottom(),
                        guiWidth, guiHeight, width, height);
            }
        }
    }
}
