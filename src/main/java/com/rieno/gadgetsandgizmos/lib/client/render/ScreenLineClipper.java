package com.rieno.gadgetsandgizmos.lib.client.render;

import org.jetbrains.annotations.Nullable;

/** Clips a line to pixel bounds while retaining the part leading to an offscreen endpoint. */
public final class ScreenLineClipper {
    private static final int LEFT = 1;
    private static final int RIGHT = 2;
    private static final int TOP = 4;
    private static final int BOTTOM = 8;

    private ScreenLineClipper() {
    }

    public record Segment(double x1, double y1, double x2, double y2) {
    }

    public static @Nullable Segment clip(double x1, double y1, double x2, double y2,
                                         int left, int top, int right, int bottom) {
        if (left >= right || top >= bottom || !Double.isFinite(x1) || !Double.isFinite(y1)
                || !Double.isFinite(x2) || !Double.isFinite(y2)) return null;
        int code1 = code(x1, y1, left, top, right - 1, bottom - 1);
        int code2 = code(x2, y2, left, top, right - 1, bottom - 1);
        while (true) {
            if ((code1 | code2) == 0) return new Segment(x1, y1, x2, y2);
            if ((code1 & code2) != 0) return null;
            int outside = code1 != 0 ? code1 : code2;
            double x;
            double y;
            if ((outside & TOP) != 0) {
                x = x1 + (x2 - x1) * (top - y1) / (y2 - y1);
                y = top;
            } else if ((outside & BOTTOM) != 0) {
                x = x1 + (x2 - x1) * (bottom - 1 - y1) / (y2 - y1);
                y = bottom - 1;
            } else if ((outside & RIGHT) != 0) {
                y = y1 + (y2 - y1) * (right - 1 - x1) / (x2 - x1);
                x = right - 1;
            } else {
                y = y1 + (y2 - y1) * (left - x1) / (x2 - x1);
                x = left;
            }
            if (outside == code1) {
                x1 = x;
                y1 = y;
                code1 = code(x1, y1, left, top, right - 1, bottom - 1);
            } else {
                x2 = x;
                y2 = y;
                code2 = code(x2, y2, left, top, right - 1, bottom - 1);
            }
        }
    }

    private static int code(double x, double y, int left, int top, int right, int bottom) {
        int result = 0;
        if (x < left) result |= LEFT;
        else if (x > right) result |= RIGHT;
        if (y < top) result |= TOP;
        else if (y > bottom) result |= BOTTOM;
        return result;
    }
}
