package com.rieno.gadgetsandgizmos.lib.geometry;

import java.util.BitSet;

// Keep faces connected to exterior air or transparent cells, including connected interiors behind glass
public final class ExteriorHull{
    private static final int MAX_CELLS = 1_048_576;
    private static final int[] DX = {0, 0, 0, 0, -1, 1}, DY = {-1, 1, 0, 0, 0, 0}, DZ = {0, 0, -1, 1, 0, 0};
    private final int x, y, z, width, height, depth;
    private final BitSet exterior;

    @FunctionalInterface
    public interface Occlusion{ boolean opaque(int x, int y, int z); }

    public ExteriorHull(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, Occlusion occlusion){
        x = minX - 1; y = minY - 1; z = minZ - 1;
        long w = (long)maxX - minX + 3, h = (long)maxY - minY + 3, d = (long)maxZ - minZ + 3;
        if(w < 3 || h < 3 || d < 3 || w > MAX_CELLS || h > MAX_CELLS || d > MAX_CELLS || w * h > MAX_CELLS || w * h * d > MAX_CELLS){
            width = height = depth = 0; exterior = null; return;
        }
        width = (int)w; height = (int)h; depth = (int)d;
        int size = width * height * depth;
        var blocked = new BitSet(size); exterior = new BitSet(size);
        for(int idx = 0; idx < size; idx++){
            int lx = idx % width, ly = idx / width % height, lz = idx / (width * height);
            if(lx > 0 && ly > 0 && lz > 0 && lx < width - 1 && ly < height - 1 && lz < depth - 1
                    && occlusion.opaque(x + lx, y + ly, z + lz)) blocked.set(idx);
        }
        int[] queue = new int[size]; int head = 0, tail = 1;
        exterior.set(0);
        while(head < tail){
            int idx = queue[head++], lx = idx % width, ly = idx / width % height, lz = idx / (width * height);
            for(int face = 0; face < 6; face++){
                int nx = lx + DX[face], ny = ly + DY[face], nz = lz + DZ[face];
                if(nx < 0 || ny < 0 || nz < 0 || nx >= width || ny >= height || nz >= depth) continue;
                int next = nx + width * (ny + height * nz);
                if(blocked.get(next) || exterior.get(next)) continue;
                exterior.set(next); queue[tail++] = next;
            }
        }
    }

    // Oversized or unavailable grids conservatively retain their original faces
    public int visibleFaces(int bx, int by, int bz){
        if(exterior == null) return 63;
        int faces = 0;
        for(int face = 0; face < 6; face++) if(reachable(bx + DX[face], by + DY[face], bz + DZ[face])) faces |= 1 << face;
        return faces;
    }

    private boolean reachable(int bx, int by, int bz){
        int lx = bx - x, ly = by - y, lz = bz - z;
        return lx < 0 || ly < 0 || lz < 0 || lx >= width || ly >= height || lz >= depth
                || exterior.get(lx + width * (ly + height * lz));
    }
}
