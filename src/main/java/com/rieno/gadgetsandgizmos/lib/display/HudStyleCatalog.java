package com.rieno.gadgetsandgizmos.lib.display;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

// Stable style identifiers shared by HUD designers and GUI or world renderers.
public final class HudStyleCatalog{
    private HudStyleCatalog(){}

    public record Option(String id, String label){}

    public enum ControlStyle{
        MATERIAL_FILLED("material_filled", "Material Filled"),
        MATERIAL_OUTLINED("material_outlined", "Material Outlined"),
        NEUMORPHIC_RAISED("neumorphic_raised", "Neumorphic Raised"),
        NEUMORPHIC_INSET("neumorphic_inset", "Neumorphic Inset"),
        SKEUOMORPHIC_BEVELED("skeuomorphic_beveled", "Skeuomorphic Beveled"),
        SKEUOMORPHIC_GLASS("skeuomorphic_glass", "Skeuomorphic Glass");

        private final String id;
        private final String label;
        ControlStyle(String id, String label){ this.id = id; this.label = label; }
        public String id(){ return id; }
        public String label(){ return label; }
    }

    public enum ProgressStyle{
        SOLID("solid", "Solid Bar"),
        MATERIAL_ROUNDED("material_rounded", "Material Rounded"),
        MATERIAL_OUTLINED("material_outlined", "Material Outlined"),
        GRADIENT("gradient", "Gradient Bar"),
        GRADIENT_ROUNDED("gradient_rounded", "Gradient Pill"),
        SEGMENTED("segmented", "Segmented Bar"),
        STRIPED("striped", "Striped Bar"),
        NEUMORPHIC("neumorphic", "Neumorphic Track"),
        RADIAL("radial", "Radial Ring"),
        RADIAL_GRADIENT("radial_gradient", "Gradient Ring"),
        RADIAL_SEGMENTED("radial_segmented", "Segmented Ring"),
        PIE("pie", "Circular Fill"),
        HALF_CIRCLE("half_circle", "Half Circle"),
        HALF_GRADIENT("half_gradient", "Gradient Half Circle");

        private final String id;
        private final String label;
        ProgressStyle(String id, String label){ this.id = id; this.label = label; }
        public String id(){ return id; }
        public String label(){ return label; }
        public boolean radial(){ return ordinal() >= RADIAL.ordinal(); }
        public boolean halfCircle(){ return this == HALF_CIRCLE || this == HALF_GRADIENT; }
    }

    public static final List<Option> CONTROL_OPTIONS = Arrays.stream(ControlStyle.values())
            .map(style -> new Option(style.id(), style.label())).toList();
    public static final List<Option> PROGRESS_OPTIONS = Arrays.stream(ProgressStyle.values())
            .map(style -> new Option(style.id(), style.label())).toList();

    public static boolean supports(String type){
        return "button".equals(type) || "toggle".equals(type) || "slider".equals(type) || "progress".equals(type);
    }

    public static List<Option> options(String type){
        return "progress".equals(type) ? PROGRESS_OPTIONS : supports(type) ? CONTROL_OPTIONS : List.of();
    }

    // Migrate saved looks to the closest new family without offering the old styles in the designer.
    public static ControlStyle controlStyle(String requested){
        String id = normalize(requested);
        for(ControlStyle style : ControlStyle.values()) if(style.id().equals(id)) return style;
        return switch(id){
            case "create_icon", "controller_graph", "aileron_range" -> ControlStyle.MATERIAL_OUTLINED;
            case "tnt_brass", "minecraft", "thruster_trim", "gyroscope_range" -> ControlStyle.SKEUOMORPHIC_BEVELED;
            case "powered_zipline" -> ControlStyle.SKEUOMORPHIC_GLASS;
            default -> ControlStyle.MATERIAL_FILLED;
        };
    }

    public static ProgressStyle progressStyle(String requested){
        String id = normalize(requested);
        for(ProgressStyle style : ProgressStyle.values()) if(style.id().equals(id)) return style;
        return ProgressStyle.SOLID;
    }

    public static String style(String type, String requested){
        return "progress".equals(type) ? progressStyle(requested).id() : controlStyle(requested).id();
    }

    public static boolean checkbox(String requested){
        return "checkbox".equals(normalize(requested));
    }

    // Handle unavailable or degenerate ranges consistently in previews and live displays.
    public static double amount(double value, double minimum, double maximum){
        if(!Double.isFinite(value)) return 0;
        if(!Double.isFinite(minimum)) minimum = 0;
        if(!Double.isFinite(maximum) || maximum <= minimum) maximum = minimum + 1;
        double amount = (value - minimum) / (maximum - minimum);
        return Double.isFinite(amount) ? Math.max(0, Math.min(1, amount)) : 0;
    }

    private static String normalize(String requested){
        return requested == null ? "" : requested.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }
}
