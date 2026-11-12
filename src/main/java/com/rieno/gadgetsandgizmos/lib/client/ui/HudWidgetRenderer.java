package com.rieno.gadgetsandgizmos.lib.client.ui;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.platform.GlStateManager;
import com.rieno.gadgetsandgizmos.lib.client.render.GuiNineSlice;
import com.rieno.gadgetsandgizmos.lib.client.render.GuiTextureRegion;
import com.rieno.gadgetsandgizmos.lib.client.render.StyledText;
import com.rieno.gadgetsandgizmos.lib.display.HudStyleCatalog;
import com.rieno.gadgetsandgizmos.lib.display.WidgetElementLayout;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

// Paint one widget in authored coordinates with identical GUI and world content layout
public final class HudWidgetRenderer{
    private HudWidgetRenderer(){}

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                       FUNCTIONS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    public interface Canvas extends HudControlRenderer.Canvas, GuiNineSlice.Painter{
        void text(Component text, double x, double y, float scale, int color);
    }

    public record Style(HudControlRenderer.Palette palette, double fontSize, String fontStyle, int textColor,
                        HudStyleCatalog.ControlStyle control, HudStyleCatalog.ProgressStyle progress,
                        boolean checkbox, GuiNineSlice textInput){
        public Style scaled(double scale){
            var p = palette;
            return new Style(new HudControlRenderer.Palette(p.background(), p.accent(), p.gradient(), p.track(), p.border(),
                    p.borderWidth() * scale, p.radius() * scale, p.scale() * scale), fontSize * scale,
                    fontStyle, textColor, control, progress, checkbox, textInput);
        }
    }

    // Apply the authored element transform after the caller establishes its canvas transform
    public static void transform(PoseStack poses, WidgetElementLayout.Element element){
        var bounds = element.bounds();
        poses.translate(bounds.x() + bounds.width() * 0.5, bounds.y() + bounds.height() * 0.5, 0);
        poses.mulPose(com.mojang.math.Axis.ZP.rotationDegrees((float)element.rotation()));
        poses.scale((float)element.scale(), (float)element.scale(), 1);
        poses.translate(-bounds.width() * 0.5, -bounds.height() * 0.5, 0);
    }

    // Keep all sizing and fitting independent of preview zoom and display aspect ratio
    public static void draw(Canvas canvas, Font font, String type, Style style, int width, int height,
                            String text, double amount, boolean active, boolean hovered){
        if(width <= 0 || height <= 0) return;
        String label = text == null ? "" : text;
        var bounds = new HudControlRenderer.Rect(0, 0, width, height);
        var palette = style.palette();
        switch(type){
            case "box" -> HudControlRenderer.panel(canvas, bounds, palette);
            case "button" -> label(canvas, font, style, HudControlRenderer.button(canvas, bounds, palette,
                    style.control(), active, hovered), label.isEmpty() ? "Button" : label, true);
            case "toggle" -> label(canvas, font, style, HudControlRenderer.toggle(canvas, bounds, palette,
                    style.control(), active, hovered, style.checkbox()), label.isEmpty() ? "Toggle" : label, false);
            case "slider" -> HudControlRenderer.slider(canvas, bounds, palette, style.control(), amount, hovered);
            case "progress" -> label(canvas, font, style, HudControlRenderer.progress(canvas, bounds, palette,
                    style.progress(), amount), label.isEmpty() ? Math.round(amount * 100) + "%" : label, true);
            case "text_input" -> {
                if(style.textInput() != null) style.textInput().paint(canvas, 0, 0, width, height, 1);
                else HudControlRenderer.panel(canvas, bounds, palette);
                int innerHeight = height - 9;
                int textHeight = Math.max(1, (int)Math.round(style.fontSize()));
                if(textHeight <= innerHeight) label(canvas, font, style,
                        new HudControlRenderer.Rect(4, 5 + (innerHeight - textHeight) / 2, Math.max(0, width - 16), innerHeight),
                        label.isEmpty() ? "Enter text" : label, false, false);
            }
            default -> label(canvas, font, style, bounds, label, false, false);
        }
    }

    public static void dropdown(Canvas canvas, Font font, Style style, int width, int height, String text,
            HudStyleCatalog.DropdownStyle preset, boolean open, boolean hovered){
        if(width <= 0 || height <= 0) return;
        label(canvas, font, style.scaled(Math.min(1, height / Math.max(0.05, style.fontSize()))), HudDropdownRenderer.control(canvas, new HudControlRenderer.Rect(0, 0, width, height),
                style.palette(), preset, open, hovered), text, false);
    }

    // Stop before the first line that would extend outside the element
    public static void wrappedText(Canvas canvas, Font font, Style style, HudControlRenderer.Rect bounds, String text){
        if(text == null || text.isBlank() || bounds.width() <= 0 || bounds.height() <= 0) return;
        float scale = (float)Math.max(0.05, style.fontSize() / font.lineHeight);
        double y = bounds.y(), step = font.lineHeight * scale;
        for(Component line : StyledText.wrap(font, text, style.fontStyle(), Math.max(1, (int)Math.floor(bounds.width() / scale)))){
            if(y + step > bounds.bottom() + 0.01) break;
            canvas.text(line, bounds.x(), y, scale, style.textColor());
            y += step;
        }
    }

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        HELPERS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    private static void label(Canvas canvas, Font font, Style style, HudControlRenderer.Rect bounds,
                              String text, boolean centered){
        label(canvas, font, style, bounds, text, centered, true);
    }

    // Fit using styled glyph widths and place both centered and leading labels identically
    private static void label(Canvas canvas, Font font, Style style, HudControlRenderer.Rect bounds,
                              String text, boolean centered, boolean middle){
        if(text.isBlank() || bounds.width() <= 0 || bounds.height() <= 0) return;
        float scale = (float)Math.max(0.05, style.fontSize() / font.lineHeight);
        if(centered) scale = (float)Math.min(scale, bounds.height() / font.lineHeight);
        Component fitted = StyledText.fit(font, text, style.fontStyle(), Math.max(1, (int)Math.floor(bounds.width() / scale)));
        if(fitted.getString().isEmpty()) return;
        double x = bounds.x() + (centered ? (bounds.width() - font.width(fitted) * scale) / 2 : 0);
        double y = bounds.y() + (middle ? Math.max(0, (bounds.height() - font.lineHeight * scale) / 2) : 0);
        canvas.text(fitted, x, y, scale, style.textColor());
    }

    // Keep missing image placeholders identical in either render target
    public static void placeholder(Canvas canvas, Font font, String text, int width, int height){
        canvas.quad(0, 0, 0, height, width, height, width, 0, 0x66374756, 0);
        if(width > 8 && height > 8) canvas.text(StyledText.fit(font, text, "regular", width - 8), 4, 4, 1, -1);
    }

    public static Canvas gui(GuiGraphics graphics, Font font){
        HudControlRenderer.Canvas shapes = HudControlRenderer.gui(graphics);
        return new Canvas(){
            @Override public void quad(double x1, double y1, double x2, double y2, double x3, double y3,
                                       double x4, double y4, int color, int layer){
                shapes.quad(x1, y1, x2, y2, x3, y3, x4, y4, color, layer);
            }
            @Override public void draw(GuiTextureRegion region, int x, int y, int width, int height){
                graphics.flush();
                RenderSystem.enableBlend();
                RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                        GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
                try{ region.draw(graphics, x, y, width, height); }
                finally{
                    RenderSystem.defaultBlendFunc();
                    RenderSystem.disableBlend();
                }
            }
            @Override public void text(Component text, double x, double y, float scale, int color){
                graphics.pose().pushPose();
                try{
                    graphics.pose().translate(x, y, 1);
                    graphics.pose().scale(scale, scale, 1);
                    graphics.drawString(font, text, 0, 0, color, false);
                }finally{ graphics.pose().popPose(); }
            }
        };
    }

    // Use the surface's existing depth direction while preserving the GUI's label and texture coordinates
    public static Canvas surface(PoseStack poses, MultiBufferSource buffers, Font font,
                                 float depth, float layerStep, float textDepth, int light){
        return new Canvas(){
            @Override public void quad(double x1, double y1, double x2, double y2, double x3, double y3,
                                       double x4, double y4, int color, int layer){
                var vertices = buffers.getBuffer(SurfaceTypes.COLOR);
                var matrix = poses.last().pose();
                float z = depth + layer * layerStep;
                vertices.addVertex(matrix, (float)x1, (float)y1, z).setColor(color);
                vertices.addVertex(matrix, (float)x2, (float)y2, z).setColor(color);
                vertices.addVertex(matrix, (float)x3, (float)y3, z).setColor(color);
                vertices.addVertex(matrix, (float)x4, (float)y4, z).setColor(color);
            }
            @Override public void draw(GuiTextureRegion region, int x, int y, int width, int height){
                if(width <= 0 || height <= 0) return;
                var vertices = buffers.getBuffer(SurfaceTypes.texture(region.texture()));
                var matrix = poses.last().pose();
                float u0 = region.u() / (float)region.textureWidth(), v0 = region.v() / (float)region.textureHeight();
                float u1 = (region.u() + region.width()) / (float)region.textureWidth();
                float v1 = (region.v() + region.height()) / (float)region.textureHeight();
                vertices.addVertex(matrix, x, y, depth).setUv(u0, v0);
                vertices.addVertex(matrix, x, y + height, depth).setUv(u0, v1);
                vertices.addVertex(matrix, x + width, y + height, depth).setUv(u1, v1);
                vertices.addVertex(matrix, x + width, y, depth).setUv(u1, v0);
            }
            @Override public void text(Component text, double x, double y, float scale, int color){
                Matrix4f matrix = new Matrix4f(poses.last().pose()).translate((float)x, (float)y, textDepth)
                        .scale(scale, scale, 1);
                font.drawInBatch(text, 0, 0, color, false, matrix, buffers, Font.DisplayMode.NORMAL, 0, light);
            }
        };
    }

    // Use world shader hooks while retaining GUI blending and faint translucent highlights
    private static final class SurfaceTypes extends RenderStateShard{
        private static final RenderType COLOR = RenderType.create("gadgetsngizmos_widget_color",
                DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 1536, false, false,
                RenderType.CompositeState.builder().setShaderState(POSITION_COLOR_SHADER)
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(NO_CULL)
                        .setDepthTestState(LEQUAL_DEPTH_TEST).createCompositeState(false));
        private static final Map<ResourceLocation, RenderType> TEXTURES = new HashMap<>();

        private SurfaceTypes(){ super("gadgetsngizmos_widget", () -> {}, () -> {}); }

        private static RenderType texture(ResourceLocation texture){
            return TEXTURES.computeIfAbsent(texture, key -> RenderType.create("gadgetsngizmos_widget_texture",
                    DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.QUADS, 1536, false, false,
                    RenderType.CompositeState.builder().setShaderState(POSITION_TEX_SHADER)
                            .setTextureState(new TextureStateShard(key, false, false))
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(NO_CULL)
                            .setDepthTestState(LEQUAL_DEPTH_TEST).createCompositeState(false)));
        }
    }
}
