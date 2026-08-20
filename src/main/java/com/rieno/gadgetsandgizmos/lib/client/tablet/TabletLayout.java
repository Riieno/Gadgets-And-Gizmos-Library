package com.rieno.gadgetsandgizmos.lib.client.tablet;

public record TabletLayout(int logicalWidth, int logicalHeight) {
    public TabletLayout {
        if (logicalWidth <= 0 || logicalHeight <= 0) {
            throw new IllegalArgumentException("[G&G-LIB][Tablet] - Tablet canvas dimensions must be positive");
        }
    }

    public Bounds fit(int left, int top, int availableWidth, int availableHeight) {
        double scale = Math.min(availableWidth / (double) logicalWidth,
                availableHeight / (double) logicalHeight);
        int width = Math.max(1, (int) Math.round(logicalWidth * scale));
        int height = Math.max(1, (int) Math.round(logicalHeight * scale));
        return new Bounds(left + (availableWidth - width) / 2,
                top + (availableHeight - height) / 2, width, height, scale, scale);
    }

    public Bounds stretch(int left, int top, int availableWidth, int availableHeight) {
        return new Bounds(left, top, Math.max(1, availableWidth), Math.max(1, availableHeight),
                availableWidth / (double) logicalWidth, availableHeight / (double) logicalHeight);
    }

    public Rect project(Bounds bounds, Rect logical) {
        return new Rect(bounds.left + (int) Math.round(logical.left * bounds.scaleX),
                bounds.top + (int) Math.round(logical.top * bounds.scaleY),
                Math.max(1, (int) Math.round(logical.width * bounds.scaleX)),
                Math.max(1, (int) Math.round(logical.height * bounds.scaleY)));
    }

    public record Bounds(int left, int top, int width, int height, double scaleX, double scaleY) {
        public Bounds(int left, int top, int width, int height, double scale) {
            this(left, top, width, height, scale, scale);
        }

        public double scale() {
            return Math.min(scaleX, scaleY);
        }
    }

    public record Rect(int left, int top, int width, int height) {
        public boolean contains(double mouseX, double mouseY) {
            return mouseX >= left && mouseY >= top && mouseX < left + width && mouseY < top + height;
        }
    }
}
