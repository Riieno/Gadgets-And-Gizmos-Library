package com.rieno.gadgetsandgizmos.lib.client.ui;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rieno.gadgetsandgizmos.lib.display.HudStyleCatalog.ControlStyle;
import com.rieno.gadgetsandgizmos.lib.display.HudStyleCatalog.ProgressStyle;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;

// Draw the same control geometry on GUI canvases and textured world surfaces.
public final class HudControlRenderer{
    private HudControlRenderer(){}

    @FunctionalInterface
    public interface Canvas{
        void quad(double x1, double y1, double x2, double y2, double x3, double y3,
                  double x4, double y4, int color, int layer);
    }

    public record Rect(double x, double y, double width, double height){
        public double right(){ return x + width; }
        public double bottom(){ return y + height; }
        public Rect inset(double padding){
            double p = Math.max(0, Math.min(padding, Math.min(width, height) / 2));
            return new Rect(x + p, y + p, Math.max(0, width - p * 2), Math.max(0, height - p * 2));
        }
    }

    public record Palette(int background, int accent, int gradient, int track, int border,
                          double borderWidth, double radius, double scale){
        public static final Palette DEFAULT = new Palette(0xFF202B30, 0xFF59C58B, 0xFF62B6E8,
                0xFF34454D, 0xFF52656D, 1, 4, 1);
    }

    public static Canvas gui(GuiGraphics graphics){
        return (x1, y1, x2, y2, x3, y3, x4, y4, color, layer) -> {
            VertexConsumer vertices = graphics.bufferSource().getBuffer(RenderType.gui());
            Matrix4f pose = graphics.pose().last().pose();
            float z = layer * 0.001F;
            vertices.addVertex(pose, (float)x1, (float)y1, z).setColor(color);
            vertices.addVertex(pose, (float)x2, (float)y2, z).setColor(color);
            vertices.addVertex(pose, (float)x3, (float)y3, z).setColor(color);
            vertices.addVertex(pose, (float)x4, (float)y4, z).setColor(color);
        };
    }

    // Supply a solid-color texture's vertices and the surface's existing depth direction.
    public static Canvas textured(VertexConsumer vertices, Matrix4f pose, float depth, float layerStep, int light){
        return (x1, y1, x2, y2, x3, y3, x4, y4, color, layer) -> {
            float z = depth + layer * layerStep;
            vertices.addVertex(pose, (float)x1, (float)y1, z).setColor(color).setUv(0.5F, 0.5F).setLight(light);
            vertices.addVertex(pose, (float)x2, (float)y2, z).setColor(color).setUv(0.5F, 0.5F).setLight(light);
            vertices.addVertex(pose, (float)x3, (float)y3, z).setColor(color).setUv(0.5F, 0.5F).setLight(light);
            vertices.addVertex(pose, (float)x4, (float)y4, z).setColor(color).setUv(0.5F, 0.5F).setLight(light);
        };
    }

    // Return the available label rectangle; the host owns font styling and graph data.
    public static Rect button(Canvas canvas, Rect bounds, Palette palette, ControlStyle style,
                              boolean pressed, boolean hovered){
        if(!valid(bounds)) return bounds;
        surface(canvas, bounds, style == ControlStyle.SKEUOMORPHIC_GLASS
                ? withRadius(palette, Math.min(bounds.width(), bounds.height()) / 2) : palette, style, pressed, hovered, 0);
        return bounds.inset(Math.min(8 * scale(palette), bounds.height() / 4));
    }

    public static Rect toggle(Canvas canvas, Rect bounds, Palette palette, ControlStyle style,
                              boolean active, boolean hovered, boolean checkbox){
        if(!valid(bounds)) return bounds;
        double s = scale(palette);
        double h = Math.max(0, Math.min(Math.max(bounds.height() - 4 * s, bounds.height() * 0.65), 22 * s));
        h = Math.min(h, Math.min(bounds.height(), bounds.width() / (checkbox ? 1 : 1.95)));
        double w = Math.min(bounds.width(), h * (checkbox ? 1 : 1.95));
        Rect indicator = new Rect(bounds.right() - w, bounds.y() + (bounds.height() - h) / 2, w, h);
        int surfaceColor = style == ControlStyle.MATERIAL_OUTLINED ? palette.background()
                : active ? palette.accent() : palette.track();
        Palette p = new Palette(surfaceColor, palette.accent(), palette.gradient(),
                palette.track(), palette.border(), palette.borderWidth(), checkbox ? 3 * s : h / 2, s);
        surface(canvas, indicator, p, style, active && style == ControlStyle.NEUMORPHIC_INSET, hovered, 0);
        double inset = Math.min(h / 4, Math.max(s * 2, h * 0.13));
        if(checkbox){
            if(active){
                double stroke = h * 0.12;
                line(canvas, indicator.x() + w * 0.23, indicator.y() + h * 0.52,
                        indicator.x() + w * 0.43, indicator.y() + h * 0.72, stroke, 0xFFF2FAFC, 20);
                line(canvas, indicator.x() + w * 0.43, indicator.y() + h * 0.72,
                        indicator.x() + w * 0.79, indicator.y() + h * 0.28, stroke, 0xFFF2FAFC, 20);
            }
        }else{
            double knob = Math.max(0, h - inset * 2);
            double knobX = active ? indicator.right() - inset - knob : indicator.x() + inset;
            Rect thumb = new Rect(knobX, indicator.y() + inset, knob, knob);
            if(style == ControlStyle.SKEUOMORPHIC_BEVELED || style == ControlStyle.SKEUOMORPHIC_GLASS){
                gradient(canvas, thumb, knob / 2, 0xFFF5FAFC, 0xFF91A6B2, false, 18);
                rounded(canvas, thumb.inset(knob * 0.28), knob / 4, 0x66FFFFFF, 19);
            }else{
                rounded(canvas, thumb, knob / 2, style == ControlStyle.NEUMORPHIC_INSET
                        ? mix(palette.background(), 0xFFFFFFFF, 0.35) : 0xFFF2FAFC, 18);
            }
        }
        return new Rect(bounds.x(), bounds.y(), Math.max(0, indicator.x() - bounds.x() - 7 * s), bounds.height());
    }

    public static void slider(Canvas canvas, Rect bounds, Palette p, ControlStyle style,
                              double requestedAmount, boolean hovered){
        if(!valid(bounds)) return;
        double amount = clamp(requestedAmount);
        double s = scale(p);
        double knob = Math.min(bounds.width(), Math.min(bounds.height() * 0.72, 18 * s));
        double pad = Math.min(bounds.width() / 2, knob / 2 + 3 * s);
        double railHeight = Math.min(bounds.height(), Math.max(2 * s, knob * 0.25));
        Rect rail = new Rect(bounds.x() + pad, bounds.y() + (bounds.height() - railHeight) / 2,
                Math.max(0, bounds.width() - pad * 2), railHeight);
        double cx = rail.x() + amount * rail.width();
        Rect fill = new Rect(rail.x(), rail.y(), rail.width() * amount, rail.height());
        switch(style){
            case MATERIAL_FILLED -> {
                rounded(canvas, rail, rail.height() / 2, p.track(), 1);
                rounded(canvas, fill, rail.height() / 2, p.accent(), 2);
                double halo = Math.min(Math.min(pad * 2, bounds.height()), knob * 1.3);
                if(hovered) rounded(canvas, new Rect(cx - halo / 2, bounds.y() + (bounds.height() - halo) / 2,
                        halo, halo), knob, alpha(p.accent(), 0.16), 3);
                rounded(canvas, new Rect(cx - knob / 2, bounds.y() + (bounds.height() - knob) / 2, knob, knob), knob / 2, p.accent(), 4);
            }
            case MATERIAL_OUTLINED -> {
                rounded(canvas, rail, rail.height() / 2, p.track(), 1);
                rounded(canvas, fill, rail.height() / 2, p.accent(), 2);
                Rect thumb = new Rect(cx - knob / 2, bounds.y() + (bounds.height() - knob) / 2, knob, knob);
                rounded(canvas, thumb, knob / 2, p.accent(), 4);
                rounded(canvas, thumb.inset(Math.min(knob / 3, Math.max(1, 2 * s))), knob / 2, p.background(), 5);
            }
            case NEUMORPHIC_RAISED, NEUMORPHIC_INSET -> {
                surface(canvas, bounds, p, ControlStyle.NEUMORPHIC_INSET, false, hovered, 0);
                rounded(canvas, rail, rail.height() / 2, mix(p.track(), 0xFF000000, 0.3), 10);
                rounded(canvas, fill, rail.height() / 2, p.accent(), 11);
                Rect thumb = new Rect(cx - knob / 2, bounds.y() + (bounds.height() - knob) / 2, knob, knob);
                Palette thumbPalette = new Palette(p.background(), p.accent(), p.gradient(), p.track(), p.border(), 1, knob / 2, s * 0.5);
                surface(canvas, thumb, thumbPalette, style, false, hovered, 12);
                rounded(canvas, thumb.inset(knob * 0.35), knob, p.accent(), 30);
            }
            case SKEUOMORPHIC_BEVELED, SKEUOMORPHIC_GLASS -> {
                surface(canvas, bounds, style == ControlStyle.SKEUOMORPHIC_GLASS
                        ? withRadius(p, Math.min(bounds.width(), bounds.height()) / 2) : p, style, false, hovered, 0);
                rounded(canvas, rail, 2 * s, 0xEE0D151B, 10);
                gradient(canvas, fill, rail.height() / 2, p.accent(), p.gradient(), true, 11);
                for(int i = 0; i <= 10; i++){
                    double x = rail.x() + rail.width() * i / 10;
                    rect(canvas, new Rect(x, rail.bottom() + 2 * s, Math.min(s, rail.width() / 20), Math.min(2 * s, bounds.bottom() - rail.bottom() - 2 * s)),
                            mix(p.border(), 0xFFFFFFFF, 0.35), 12);
                }
                Rect thumb = new Rect(cx - knob * 0.35, bounds.y() + (bounds.height() - knob) / 2, knob * 0.7, knob);
                gradient(canvas, thumb, style == ControlStyle.SKEUOMORPHIC_GLASS ? knob / 2 : 2 * s,
                        0xFFF1F5F8, 0xFF758F9C, false, 20);
                line(canvas, cx, thumb.y() + knob * 0.25, cx, thumb.bottom() - knob * 0.25, Math.max(1, s), 0xAA263943, 21);
            }
        }
    }

    public static Rect progress(Canvas canvas, Rect bounds, Palette p, ProgressStyle style, double requestedAmount){
        if(!valid(bounds)) return bounds;
        double amount = clamp(requestedAmount);
        double s = scale(p);
        if(style.radial()) return radialProgress(canvas, bounds, p, style, amount);
        double radius = switch(style){
            case SOLID, GRADIENT, SEGMENTED, STRIPED -> 0;
            default -> Math.min(bounds.height() / 2, Math.max(p.radius(), 6 * s));
        };
        if(style == ProgressStyle.NEUMORPHIC) surface(canvas, bounds, p, ControlStyle.NEUMORPHIC_INSET, false, false, 0);
        else rounded(canvas, bounds, radius, style == ProgressStyle.MATERIAL_OUTLINED ? p.accent() : p.track(), 0);
        double inset = style == ProgressStyle.MATERIAL_OUTLINED ? Math.max(s, p.borderWidth())
                : style == ProgressStyle.NEUMORPHIC ? Math.min(bounds.height() / 4, 4 * s) : 0;
        Rect inner = bounds.inset(inset);
        if(inset > 0) rounded(canvas, inner, Math.max(0, radius - inset), p.track(), 10);
        Rect fill = new Rect(inner.x(), inner.y(), inner.width() * amount, inner.height());
        if(style == ProgressStyle.SEGMENTED){
            int count = 12;
            double gap = Math.min(2 * s, inner.width() / (count * 3));
            double cell = (inner.width() - gap * (count - 1)) / count;
            for(int i = 0; i < count; i++){
                Rect segment = new Rect(inner.x() + i * (cell + gap), inner.y(), cell, inner.height());
                rect(canvas, segment, p.background(), 10);
                rect(canvas, new Rect(segment.x(), segment.y(), cell * clamp(amount * count - i), segment.height()), p.accent(), 11);
            }
        }else if(style == ProgressStyle.GRADIENT || style == ProgressStyle.GRADIENT_ROUNDED){
            gradient(canvas, fill, radius, p.accent(), mix(p.accent(), p.gradient(), amount), true, 11);
        }else{
            rounded(canvas, fill, radius, p.accent(), 11);
            if(style == ProgressStyle.STRIPED && valid(fill)){
                double period = Math.max(4, 12 * s);
                int rows = Math.max(1, (int)Math.ceil(fill.height()));
                for(int row = 0; row < rows; row++){
                    double top = fill.y() + row * fill.height() / rows;
                    double bottom = fill.y() + (row + 1) * fill.height() / rows;
                    for(double x = fill.x() - period + row % (int)Math.ceil(period); x < fill.right(); x += period){
                        double left = Math.max(fill.x(), x);
                        double right = Math.min(fill.right(), x + period * 0.42);
                        rect(canvas, new Rect(left, top, right - left, bottom - top), alpha(0xFFFFFFFF, 0.2), 12);
                    }
                }
            }
        }
        return inner.inset(Math.min(3 * s, inner.height() / 4));
    }

    private static Rect radialProgress(Canvas c, Rect b, Palette p, ProgressStyle style, double amount){
        double s = scale(p);
        boolean half = style.halfCircle();
        double pad = Math.min(2 * s, Math.min(b.width(), b.height()) / 8);
        double radius = Math.max(0, Math.min((b.width() - pad * 2) / 2, (b.height() - pad * 2) / (half ? 1 : 2)));
        double cx = b.x() + b.width() / 2;
        double cy = half ? b.y() + (b.height() + radius) / 2 : b.y() + b.height() / 2;
        double thickness = style == ProgressStyle.PIE ? radius : Math.max(s * 2, radius * 0.19);
        thickness = Math.min(radius, thickness);
        double start = half ? Math.PI : -Math.PI / 2;
        double sweep = half ? Math.PI : Math.PI * 2;
        boolean gradient = style == ProgressStyle.RADIAL_GRADIENT || style == ProgressStyle.HALF_GRADIENT;
        if(style == ProgressStyle.RADIAL_SEGMENTED){
            int count = 24;
            for(int i = 0; i < count; i++){
                double cellStart = start + sweep * (i + 0.12) / count;
                double cellSweep = sweep * 0.76 / count;
                arc(c, cx, cy, radius, thickness, cellStart, cellSweep, p.track(), p.track(), 0);
                double part = clamp(amount * count - i);
                if(part > 0) arc(c, cx, cy, radius, thickness, cellStart, cellSweep * part, p.accent(), p.accent(), 1);
            }
        }else{
            arc(c, cx, cy, radius, thickness, start, sweep, p.track(), p.track(), 0);
            if(amount > 0) arc(c, cx, cy, radius, thickness, start, sweep * amount, p.accent(),
                    gradient ? mix(p.accent(), p.gradient(), amount) : p.accent(), 1);
        }
        if(half) return new Rect(cx - radius * 0.65, cy - radius * 0.38, radius * 1.3, radius * 0.34);
        double labelSize = radius * (style == ProgressStyle.PIE ? 1.1 : 1.05);
        return new Rect(cx - labelSize / 2, cy - labelSize / 2, labelSize, labelSize);
    }

    private static void surface(Canvas c, Rect b, Palette p, ControlStyle style, boolean pressed, boolean hovered, int layer){
        double s = scale(p);
        double radius = Math.min(Math.min(b.width(), b.height()) / 2, Math.max(p.radius(), 4 * s));
        int base = hovered ? mix(p.background(), 0xFFFFFFFF, 0.08) : p.background();
        if(pressed) base = mix(base, 0xFF000000, 0.2);
        switch(style){
            case MATERIAL_FILLED -> {
                rounded(c, b, radius, pressed ? mix(p.accent(), 0xFF000000, 0.18) : base, layer);
                Rect inner = b.inset(Math.min(1.0 * s, b.height() / 6));
                rounded(c, inner, radius, pressed ? mix(p.accent(), 0xFF000000, 0.1) : base, layer + 1);
            }
            case MATERIAL_OUTLINED -> {
                rounded(c, b, radius, hovered || pressed ? p.accent() : p.border(), layer);
                rounded(c, b.inset(Math.max(s, p.borderWidth())), radius,
                        pressed ? mix(base, p.accent(), 0.12) : base, layer + 1);
            }
            case NEUMORPHIC_RAISED, NEUMORPHIC_INSET -> {
                boolean inset = style == ControlStyle.NEUMORPHIC_INSET || pressed;
                double edge = Math.min(4 * s, Math.min(b.width(), b.height()) / 5);
                rounded(c, b, radius, mix(base, 0xFFFFFFFF, inset ? 0.18 : 0.07), layer);
                for(int i = 0; i < 3; i++){
                    double shift = edge * (i + 1) / 3;
                    Rect shade = new Rect(b.x() + shift, b.y() + shift, Math.max(0, b.width() - shift), Math.max(0, b.height() - shift));
                    rounded(c, shade, radius, mix(base, 0xFF000000, 0.18 + i * 0.09), layer + i + 1);
                }
                Rect face = b.inset(edge);
                gradient(c, face, Math.max(0, radius - edge),
                        mix(base, inset ? 0xFF000000 : 0xFFFFFFFF, inset ? 0.16 : 0.08), base, false, layer + 4);
            }
            case SKEUOMORPHIC_BEVELED -> {
                rounded(c, b, Math.min(radius, 3 * s), 0xFF0C141A, layer);
                Rect rim = b.inset(Math.min(s, b.height() / 6));
                gradient(c, rim, 2 * s, mix(p.border(), 0xFFFFFFFF, 0.7), mix(p.border(), 0xFF000000, 0.48), false, layer + 1);
                Rect face = b.inset(Math.min(3 * s, b.height() / 4));
                gradient(c, face, s, mix(base, 0xFFFFFFFF, pressed ? 0.02 : 0.22), mix(base, 0xFF000000, 0.24), false, layer + 2);
                double dot = Math.min(2 * s, Math.min(b.width(), b.height()) / 9);
                double edge = Math.min(2 * s, Math.min(b.width(), b.height()) / 5);
                for(double x : new double[]{b.x() + edge, b.right() - edge - dot})
                    rounded(c, new Rect(x, b.y() + edge, dot, dot), dot / 2, 0xFF26353F, layer + 3);
            }
            case SKEUOMORPHIC_GLASS -> {
                rounded(c, b, radius, mix(p.border(), 0xFFFFFFFF, 0.65), layer);
                Rect face = b.inset(Math.min(2 * s, b.height() / 5));
                int glass = pressed ? p.accent() : mix(base, p.accent(), 0.42);
                gradient(c, face, radius, mix(glass, 0xFFFFFFFF, 0.25), mix(glass, 0xFF000000, 0.35), false, layer + 1);
                Rect shine = new Rect(face.x() + s, face.y() + s, Math.max(0, face.width() - 2 * s), Math.max(0, face.height() * 0.4 - s));
                gradient(c, shine, radius, alpha(0xFFFFFFFF, 0.45), alpha(0xFFFFFFFF, 0.04), false, layer + 2);
            }
        }
    }

    private static void arc(Canvas c, double cx, double cy, double radius, double thickness,
                            double start, double sweep, int first, int last, int layer){
        if(radius <= 0 || sweep <= 0) return;
        int segments = Math.max(2, Math.min(128, (int)Math.ceil(sweep * Math.max(8, radius) / 2)));
        double inner = Math.max(0, radius - thickness);
        for(int i = 0; i < segments; i++){
            double a = start + sweep * i / segments;
            double b = start + sweep * (i + 1) / segments;
            c.quad(cx + Math.cos(a) * radius, cy + Math.sin(a) * radius,
                    cx + Math.cos(a) * inner, cy + Math.sin(a) * inner,
                    cx + Math.cos(b) * inner, cy + Math.sin(b) * inner,
                    cx + Math.cos(b) * radius, cy + Math.sin(b) * radius,
                    mix(first, last, (i + 0.5) / segments), layer);
        }
    }

    private static void gradient(Canvas c, Rect b, double radius, int first, int last, boolean horizontal, int layer){
        if(!valid(b)) return;
        int rows = Math.max(1, Math.min(256, (int)Math.ceil(b.height())));
        int columns = horizontal ? Math.max(1, Math.min(64, (int)Math.ceil(b.width()))) : 1;
        double r = Math.min(radius, Math.min(b.width(), b.height()) / 2);
        for(int row = 0; row < rows; row++){
            double top = b.y() + b.height() * row / rows;
            double bottom = b.y() + b.height() * (row + 1) / rows;
            double inset = roundedInset((top + bottom) / 2 - b.y(), b.height(), r);
            for(int col = 0; col < columns; col++){
                double left = Math.max(b.x() + inset, b.x() + b.width() * col / columns);
                double right = Math.min(b.right() - inset, b.x() + b.width() * (col + 1) / columns);
                rect(c, new Rect(left, top, right - left, bottom - top),
                        mix(first, last, horizontal ? (col + 0.5) / columns : (row + 0.5) / rows), layer);
            }
        }
    }

    private static void rounded(Canvas c, Rect b, double radius, int color, int layer){
        if(!valid(b)) return;
        double r = Math.max(0, Math.min(radius, Math.min(b.width(), b.height()) / 2));
        if(r <= 0){ rect(c, b, color, layer); return; }
        int rows = Math.max(1, Math.min(256, (int)Math.ceil(b.height())));
        for(int row = 0; row < rows; row++){
            double top = b.y() + b.height() * row / rows;
            double bottom = b.y() + b.height() * (row + 1) / rows;
            double inset = roundedInset((top + bottom) / 2 - b.y(), b.height(), r);
            rect(c, new Rect(b.x() + inset, top, Math.max(0, b.width() - inset * 2), bottom - top), color, layer);
        }
    }

    private static double roundedInset(double y, double height, double radius){
        double distance = Math.min(y, height - y);
        if(distance >= radius) return 0;
        double dy = radius - distance;
        return radius - Math.sqrt(Math.max(0, radius * radius - dy * dy));
    }

    private static void rect(Canvas c, Rect b, int color, int layer){
        if(!valid(b) || (color >>> 24) == 0) return;
        c.quad(b.x(), b.y(), b.x(), b.bottom(), b.right(), b.bottom(), b.right(), b.y(), color, layer);
    }

    private static void line(Canvas c, double x1, double y1, double x2, double y2, double width, int color, int layer){
        double length = Math.hypot(x2 - x1, y2 - y1);
        if(length <= 0) return;
        double dx = (y2 - y1) / length * width / 2;
        double dy = (x1 - x2) / length * width / 2;
        c.quad(x1 + dx, y1 + dy, x1 - dx, y1 - dy, x2 - dx, y2 - dy, x2 + dx, y2 + dy, color, layer);
    }

    private static boolean valid(Rect b){
        return b != null && Double.isFinite(b.x()) && Double.isFinite(b.y())
                && Double.isFinite(b.width()) && Double.isFinite(b.height()) && b.width() > 0 && b.height() > 0;
    }

    private static double scale(Palette p){ return Double.isFinite(p.scale()) ? Math.max(0.05, p.scale()) : 1; }
    private static Palette withRadius(Palette p, double radius){
        return new Palette(p.background(), p.accent(), p.gradient(), p.track(), p.border(), p.borderWidth(), radius, p.scale());
    }
    private static double clamp(double value){ return Double.isFinite(value) ? Math.max(0, Math.min(1, value)) : 0; }
    private static int alpha(int color, double amount){ return (int)Math.round((color >>> 24) * clamp(amount)) << 24 | color & 0xFFFFFF; }

    private static int mix(int first, int last, double amount){
        double t = clamp(amount);
        int result = 0;
        for(int shift : new int[]{0, 8, 16, 24}){
            int a = first >>> shift & 255, b = last >>> shift & 255;
            result |= (int)Math.round(a + (b - a) * t) << shift;
        }
        return result;
    }
}
