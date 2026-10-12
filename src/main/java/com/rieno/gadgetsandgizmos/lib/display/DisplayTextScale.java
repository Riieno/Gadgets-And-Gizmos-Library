package com.rieno.gadgetsandgizmos.lib.display;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

// Configure linked text independently of graph, camera, tablet and terminal presentations
public final class DisplayTextScale{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final String KEY = "LinkedTextScale";
    public static final double DEFAULT = 1.0D;
    public static final double MINIMUM = 0.25D;
    public static final double MAXIMUM = 4.0D;
    public static final double STEP = 0.25D;

    private DisplayTextScale(){}

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Keep missing, invalid and out-of-range settings safe for persistence and network input
    public static double normalize(double val){
        return Double.isFinite(val) ? Math.max(MINIMUM, Math.min(MAXIMUM, val)) : DEFAULT;
    }

    public static double read(CompoundTag frame){
        return frame != null && frame.contains(KEY, Tag.TAG_ANY_NUMERIC)
                ? normalize(frame.getDouble(KEY)) : DEFAULT;
    }

    // Host-owned node content keeps its own font and layout settings
    public static boolean supportsFrame(CompoundTag frame){
        if(frame == null || frame.contains("ContentNode", Tag.TAG_STRING)) return false;
        String state = frame.getString("State");
        if("display_link".equals(state) || "crn".equals(state)) return true;
        if(!"external".equals(state)) return false;
        String format = frame.getCompound("External").getString("Format");
        return "text".equals(format) || "crn".equals(format);
    }

    // Keep a zoomed schedule inside the physical display's content area
    public static int canvasSize(int size, double scale){
        return Math.max(1, (int) Math.floor(Math.max(1, size) / normalize(scale)));
    }

    // Fit the default font and apply the requested density without drawing outside the text area
    public static Layout layout(int width, int height, int longestLine, int lineCount,
                                int fontHeight, double requestedScale){
        int glyphHeight = Math.max(1, fontHeight);
        double availableWidth = Math.max(1.0D, width - 12.0D);
        double inset = Math.min(5.0D, Math.max(0.0D, (height - 1.0D) * 0.5D));
        double availableHeight = Math.max(1.0D, height - inset * 2.0D);
        double fit = Math.max(0.25D, Math.min(6.0D, Math.min(
                availableWidth / Math.max(1, longestLine),
                availableHeight / (Math.max(1, lineCount) * (glyphHeight + 2.0D)))));
        double scale = Math.min(fit * normalize(requestedScale), availableHeight / (glyphHeight + 2.0D));
        double spacing = (glyphHeight + 2.0D) * scale;
        int visibleLines = Math.min(Math.max(0, lineCount), Math.max(1, (int) Math.floor(availableHeight / spacing)));
        double top = Math.max(0.0D, (height - visibleLines * spacing) * 0.5D);
        return new Layout(scale, spacing, top, Math.max(1, (int) Math.floor(availableWidth / scale)), visibleLines);
    }

    public record Layout(double scale, double lineHeight, double top, int maximumLineWidth, int visibleLines){}
}
