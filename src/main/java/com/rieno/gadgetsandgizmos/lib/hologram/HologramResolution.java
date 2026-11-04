package com.rieno.gadgetsandgizmos.lib.hologram;

// Select surface detail from projected block size without changing the sampled world
public enum HologramResolution{
    FULL(0), HIGH(2), MEDIUM(4), LOW(8);

    private final double pixels;
    HologramResolution(double pixels){ this.pixels = pixels; }

    // Keep a small overlap between levels so camera motion does not repeatedly rebuild meshes
    public int level(double blockPixels, int prev){
        if(this == FULL || Double.isNaN(blockPixels) || blockPixels < 0) return 0;
        int minimum = this == LOW ? 2 : this == MEDIUM ? 1 : 0;
        if(!Double.isFinite(blockPixels)) return minimum;
        if(prev >= minimum && prev <= 3){
            double lower = prev == 3 ? 0 : pixels / (1 << prev);
            double upper = prev == 0 ? Double.POSITIVE_INFINITY : pixels / (1 << (prev - 1));
            if(blockPixels >= lower * .8 && blockPixels <= upper * 1.2) return prev;
        }
        if(blockPixels > pixels) return minimum;
        if(blockPixels > pixels / 2) return Math.max(minimum, 1);
        return Math.max(minimum, blockPixels > pixels / 4 ? 2 : 3);
    }
}
