package com.rieno.gadgetsandgizmos.lib.physics;

import net.minecraft.world.level.chunk.LevelChunkSection;
import java.util.Map;
import java.util.WeakHashMap;

// Retain an exact local occupancy map until the underlying section changes
public final class CollisionBlockIndex{
    private static final ThreadLocal<Map<LevelChunkSection, Columns>> SECTIONS = ThreadLocal.withInitial(WeakHashMap::new);
    private CollisionBlockIndex(){}

    public interface Revision{
        long collisionBlockRevision();
    }

    // Return the next non-air local Y, or 16 when the rest of this column is empty
    public static int nextOccupiedY(LevelChunkSection section, int x, int y, int z){
        if(section.hasOnlyAir()) return 16;
        int column = (x & 15) | ((z & 15) << 4);
        long revision = section instanceof Revision tracked ? tracked.collisionBlockRevision() : Long.MIN_VALUE;
        Map<LevelChunkSection, Columns> sections = SECTIONS.get();
        Columns columns = sections.get(section);
        if(columns == null || columns.revision != revision || revision == Long.MIN_VALUE){
            if(sections.size() >= 4096) sections.clear();
            columns = new Columns(revision);
            sections.put(section, columns);
        }
        if(!columns.known[column]){
            int mask = 0;
            for(int idx = 0; idx < 16; idx++) if(!section.getBlockState(x & 15, idx, z & 15).isAir()) mask |= 1 << idx;
            columns.masks[column] = mask;
            columns.known[column] = true;
        }
        int mask = columns.masks[column] & (0xffff << (y & 15));
        return mask == 0 ? 16 : Integer.numberOfTrailingZeros(mask);
    }

    private static final class Columns{
        private final long revision;
        private final int[] masks = new int[256];
        private final boolean[] known = new boolean[256];
        private Columns(long revision){ this.revision = revision; }
    }
}
