package com.rieno.gadgetsandgizmos.lib.client.render;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.rieno.gadgetsandgizmos.lib.GadgetsNGizmosLibrary;
import com.rieno.gadgetsandgizmos.lib.display.SurfaceFit;
import com.rieno.gadgetsandgizmos.lib.script.ScriptTerminal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;

// Share cached terminal textures between GUI previews and world surfaces
public final class ScriptTerminalRenderer{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final ResourceLocation WHITE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    private static final ResourceLocation CC_FONT = ResourceLocation.fromNamespaceAndPath("computercraft", "textures/gui/term_font.png");
    private static final Map<Content, Texture> TEXTURES = new LinkedHashMap<>(16, .75f, true);
    private static final ArrayDeque<ResourceLocation> FREE_TEXTURES = new ArrayDeque<>();
    private static final int MAX_TEXTURES = 64, MAX_PIXELS = 8 * 1024 * 1024;
    private static int[][] glyphs;
    private static int pixels;
    private static long sequence;
    private ScriptTerminalRenderer(){}

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static void render(GuiGraphics graphics, ScriptTerminal terminal, int x, int y, int width, int height){
        if(width <= 0 || height <= 0) return;
        Texture texture = texture(terminal);
        graphics.fill(x, y, x + width, y + height, 0xFF080808);
        var fit = SurfaceFit.fit(terminal.width() * 6, terminal.height() * 9, width, height, true);
        try(var clip = GuiPoseScissor.push(graphics, x, y, x + width, y + height)){
            var pose = graphics.pose(); pose.pushPose();
            try{
                pose.translate(x + fit.x(), y + fit.y(), 0); pose.scale((float)fit.scaleX(), (float)fit.scaleY(), 1);
                graphics.blit(texture.id(), 0, 0, 0, 0, texture.width(), texture.height(), texture.width(), texture.height());
                if(cursor(terminal)){
                    int x0 = terminal.cursorX() * 6, y0 = terminal.cursorY() * 9 + 7;
                    graphics.fill(x0, y0, x0 + 6, y0 + 2, terminal.cursorArgb());
                }
            }finally{ pose.popPose(); }
        }
    }

    public static void render(ScriptTerminal terminal, PoseStack pose, MultiBufferSource buffers,
                              int width, int height, float z, boolean preserveAspect){
        if(width <= 0 || height <= 0) return;
        Texture texture = texture(terminal);
        var fit = SurfaceFit.fit(terminal.width() * 6, terminal.height() * 9, width, height, preserveAspect);
        pose.pushPose();
        try{
            pose.translate(fit.x(), fit.y(), z); pose.scale((float)fit.scaleX(), (float)fit.scaleY(), 1);
            quad(texture.id(), 0, 0, texture.width(), texture.height(), 0, 0xFFFFFFFF, pose, buffers);
            if(cursor(terminal)){
                int x = terminal.cursorX() * 6, y = terminal.cursorY() * 9 + 7;
                quad(WHITE, x, y, x + 6, y + 2, -.004f, terminal.cursorArgb(), pose, buffers);
            }
        }finally{ pose.popPose(); }
    }

    // Release cached GPU and CPU storage when resources or the connected world change
    public static void clearCache(){
        if(!RenderSystem.isOnRenderThread()){ RenderSystem.recordRenderCall(ScriptTerminalRenderer::clearCache); return; }
        TEXTURES.values().forEach(texture -> {
            Minecraft.getInstance().getTextureManager().release(texture.id()); FREE_TEXTURES.addLast(texture.id());
        });
        TEXTURES.clear(); pixels = 0; glyphs = null;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static Texture texture(ScriptTerminal terminal){
        Content content = new Content(terminal.width(), terminal.height(), terminal.text(), terminal.foreground(), terminal.background(), terminal.palette(), terminal.foregroundHex(), terminal.backgroundHex());
        Texture cached = TEXTURES.get(content); if(cached != null) return cached;
        if(glyphs == null) glyphs = glyphs();
        int width = terminal.width() * 6, height = terminal.height() * 9;
        while(!TEXTURES.isEmpty() && (TEXTURES.size() >= MAX_TEXTURES || pixels + width * height > MAX_PIXELS)){
            var oldest = TEXTURES.entrySet().iterator(); var entry = oldest.next(); oldest.remove();
            pixels -= entry.getValue().width() * entry.getValue().height();
            Minecraft.getInstance().getTextureManager().release(entry.getValue().id());
            FREE_TEXTURES.addLast(entry.getValue().id());
        }
        int[] raster = TerminalRaster.pixels(terminal, glyphs);
        var image = new NativeImage(width, height, false);
        for(int y = 0, idx = 0; y < height; y++) for(int x = 0; x < width; x++, idx++) image.setPixelRGBA(x, y, raster[idx]);
        var dynamic = new DynamicTexture(image); dynamic.setFilter(false, false);
        ResourceLocation id = FREE_TEXTURES.isEmpty() ? ResourceLocation.fromNamespaceAndPath(GadgetsNGizmosLibrary.MOD_ID, "terminal/cache_" + sequence++) : FREE_TEXTURES.removeFirst();
        Minecraft.getInstance().getTextureManager().register(id, dynamic);
        Texture texture = new Texture(id, width, height); TEXTURES.put(content, texture); pixels += width * height;
        return texture;
    }

    private static int[][] glyphs(){
        int[][] masks = new int[256][54];
        if(ModList.get().isLoaded("computercraft")){
            try(var stream = Minecraft.getInstance().getResourceManager().open(CC_FONT); var image = NativeImage.read(stream)){
                for(int ch = 0; ch < 256; ch++) for(int y = 0; y < 9; y++) for(int x = 0; x < 6; x++)
                    masks[ch][y * 6 + x] = image.getPixelRGBA(1 + ch % 16 * 8 + x, 1 + ch / 16 * 11 + y) >>> 24;
                return masks;
            }catch(IOException ex){ throw new IllegalStateException("Cannot read the native terminal font", ex); }
        }
        // Capture the active resource-pack font once, then reuse its glyph coverage for every terminal
        var atlas = ResourceLocation.fromNamespaceAndPath(GadgetsNGizmosLibrary.MOD_ID, "terminal/glyph_atlas");
        try(var target = new FramebufferGuiRenderer(atlas)){
            target.render(96, 144, 96, 144, graphics -> {
                graphics.fill(0, 0, 96, 144, 0xFF000000);
                var font = Minecraft.getInstance().font;
                for(int ch = 1; ch < 256; ch++){
                    if(ch == 32 || ch >= 128 && ch <= 159) continue;
                    String text = String.valueOf((char)ch); int x = ch % 16 * 6, y = ch / 16 * 9;
                    graphics.enableScissor(x, y, x + 6, y + 9); graphics.pose().pushPose();
                    graphics.pose().translate(x, y, 0); graphics.pose().scale(Math.min(1, 6f / Math.max(1, font.width(text))), 1, 1);
                    graphics.drawString(font, text, 0, 0, 0xFFFFFFFF, false);
                    graphics.pose().popPose(); graphics.disableScissor();
                }
            });
            RenderSystem.bindTexture(Minecraft.getInstance().getTextureManager().getTexture(atlas).getId());
            try(var image = new NativeImage(96, 144, false)){
                image.downloadTexture(0, false); image.flipY();
                for(int ch = 1; ch < 256; ch++) for(int y = 0; y < 9; y++) for(int x = 0; x < 6; x++)
                    masks[ch][y * 6 + x] = image.getPixelRGBA(ch % 16 * 6 + x, ch / 16 * 9 + y) & 255;
            }
        }
        for(int ch = 128; ch <= 159; ch++) for(int y = 0; y < 9; y++) for(int x = 0; x < 6; x++){
            int bit = y / 3 * 2 + x / 3; masks[ch][y * 6 + x] = bit < 5 && ((ch - 128) & 1 << bit) != 0 ? 255 : 0;
        }
        return masks;
    }

    private static boolean cursor(ScriptTerminal terminal){
        return terminal.blink() && System.currentTimeMillis() / 400 % 2 == 0 && terminal.cursorX() >= 0 && terminal.cursorX() < terminal.width()
                && terminal.cursorY() >= 0 && terminal.cursorY() < terminal.height();
    }
    private static void quad(ResourceLocation texture, float left, float top, float right, float bottom, float z, int color,
                             PoseStack pose, MultiBufferSource buffers){
        var vertices = buffers.getBuffer(RenderType.text(texture)); var matrix = pose.last().pose();
        vertices.addVertex(matrix, left, top, z).setColor(color).setUv(0, 0).setLight(LightTexture.FULL_BRIGHT);
        vertices.addVertex(matrix, left, bottom, z).setColor(color).setUv(0, 1).setLight(LightTexture.FULL_BRIGHT);
        vertices.addVertex(matrix, right, bottom, z).setColor(color).setUv(1, 1).setLight(LightTexture.FULL_BRIGHT);
        vertices.addVertex(matrix, right, top, z).setColor(color).setUv(1, 0).setLight(LightTexture.FULL_BRIGHT);
    }
    private record Content(int width, int height, List<String> text, List<String> foreground, List<String> background, List<Integer> palette, List<String> foregroundHex, List<String> backgroundHex){}
    private record Texture(ResourceLocation id, int width, int height){}

    @EventBusSubscriber(modid = GadgetsNGizmosLibrary.MOD_ID, value = Dist.CLIENT)
    public static final class Reload{
        private Reload(){}
        @SubscribeEvent public static void register(RegisterClientReloadListenersEvent evt){ evt.registerReloadListener((ResourceManagerReloadListener) resources -> clearCache()); }
    }
    @EventBusSubscriber(modid = GadgetsNGizmosLibrary.MOD_ID, value = Dist.CLIENT)
    public static final class Lifecycle{
        private Lifecycle(){}
        @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut evt){ clearCache(); }
    }
}
