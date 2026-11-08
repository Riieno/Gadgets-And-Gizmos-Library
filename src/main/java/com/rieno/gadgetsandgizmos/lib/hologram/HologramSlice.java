package com.rieno.gadgetsandgizmos.lib.hologram;

// Describe either the exterior surface or a bounded vertical cutaway
public record HologramSlice(int minY, int maxY, boolean surface){
    public static final HologramSlice SURFACE = new HologramSlice(Integer.MIN_VALUE, Integer.MAX_VALUE, true);
    public static final int CUTAWAY_DEPTH = 64;
    public static final int SURFACE_DEPTH = 10;
    public static final int SURFACE_PRE_OFFSET = SURFACE_DEPTH + 1;

    public HologramSlice{
        if(minY >= maxY) throw new IllegalArgumentException("Invalid hologram height range");
    }

    // Zero keeps a shallow exterior band; other offsets move a cutaway through the world
    public static HologramSlice at(double averageY, int offset){
        if(offset == 0) return surface(averageY, Integer.MIN_VALUE, Integer.MAX_VALUE);
        int top = (int)Math.floor(averageY) + offset;
        return new HologramSlice(top - CUTAWAY_DEPTH, top, false);
    }

    // Include ten blocks below the heightmap's top block and every higher mountain or structure
    public static HologramSlice surface(double baseY, int minBuildY, int maxBuildY){
        if(minBuildY >= maxBuildY) throw new IllegalArgumentException("Invalid world height range");
        double base = Double.isFinite(baseY) ? Math.floor(baseY) - SURFACE_PRE_OFFSET : minBuildY;
        int bottom = (int)Math.clamp(base, minBuildY, (double)maxBuildY - 1);
        return new HologramSlice(bottom, maxBuildY, true);
    }

    public boolean contains(double y){ return y >= minY && y < maxY; }

    // Leave room below the zero-offset surface without changing where cutaway offsets are measured
    public static double projectionOriginY(double averageY, int offset){
        return averageY + offset - (offset == 0 ? SURFACE_PRE_OFFSET : 0);
    }
}
