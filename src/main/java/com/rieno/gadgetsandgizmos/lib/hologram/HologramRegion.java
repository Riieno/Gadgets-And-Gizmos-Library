package com.rieno.gadgetsandgizmos.lib.hologram;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;

// Describe a square of root-world chunks using its total width
public record HologramRegion(ChunkPos center, int width){
    public HologramRegion{
        if(width < 1 || width > 31) throw new IllegalArgumentException("Chunk width must be between 1 and 31");
    }

    // Keep negative coordinates aligned with Minecraft chunk boundaries
    public int minX(){ return center.x - width / 2; }
    public int minZ(){ return center.z - width / 2; }
    public int maxX(){ return minX() + width - 1; }
    public int maxZ(){ return minZ() + width - 1; }
    public boolean contains(ChunkPos pos){
        return pos.x >= minX() && pos.x <= maxX() && pos.z >= minZ() && pos.z <= maxZ();
    }
    public AABB bounds(int minY, int maxY){
        return new AABB(minX() * 16, minY, minZ() * 16, (maxX() + 1) * 16, maxY, (maxZ() + 1) * 16);
    }
}
