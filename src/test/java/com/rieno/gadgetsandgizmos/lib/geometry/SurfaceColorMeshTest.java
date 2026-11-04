package com.rieno.gadgetsandgizmos.lib.geometry;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Collections;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class SurfaceColorMeshTest{
    @Test void flatWallMergesIntoOneQuad(){
        var cells = new ArrayList<SurfaceColorMesh.Cell>();
        for(int x = 0; x < 16; x++) for(int y = 0; y < 16; y++) cells.add(new SurfaceColorMesh.Cell(2, 4, x, y, 0x445566));
        var mesh = SurfaceColorMesh.build(cells, 1);
        assertEquals(1, mesh.size()); assertEquals(16, mesh.getFirst().width()); assertEquals(16, mesh.getFirst().height());
    }

    @Test void colorTilesAverageWithoutClosingWindows(){
        var cells = new ArrayList<SurfaceColorMesh.Cell>();
        for(int x = -4; x < 4; x++) for(int y = -4; y < 4; y++){
            if(x >= -1 && x <= 1 && y >= -1 && y <= 1) continue;
            cells.add(new SurfaceColorMesh.Cell(5, 0, x, y, (x & 1) == 0 ? 0 : 0xFFFFFF));
        }
        checkCoverage(cells, 4);
        assertTrue(SurfaceColorMesh.build(cells, 4).size() < SurfaceColorMesh.build(cells, 1).size());
    }

    @Test void randomSurfacesKeepExactCoverageAndStableOrder(){
        var random = new Random(49271);
        for(int pass = 0; pass < 100; pass++){
            var cells = new ArrayList<SurfaceColorMesh.Cell>();
            for(int face = 0; face < 6; face++) for(int x = 0; x < 8; x++) for(int y = 0; y < 8; y++){
                if(random.nextBoolean()) cells.add(new SurfaceColorMesh.Cell(face, face - 3, x, y, random.nextBoolean() ? 0 : 0xFFFFFF));
            }
            for(int tile : new int[]{1, 2, 4}){
                checkCoverage(cells, tile);
                var mesh = SurfaceColorMesh.build(cells, tile);
                Collections.shuffle(cells, random);
                assertEquals(mesh, SurfaceColorMesh.build(cells, tile));
            }
        }
    }

    private static void checkCoverage(ArrayList<SurfaceColorMesh.Cell> cells, int tile){
        var expected = new HashSet<String>(); var actual = new HashSet<String>();
        for(var cell : cells) expected.add(cell.face() + ":" + cell.plane() + ":" + cell.u() + ":" + cell.v());
        for(var quad : SurfaceColorMesh.build(cells, tile)) for(int x = 0; x < quad.width(); x++) for(int y = 0; y < quad.height(); y++){
            assertTrue(actual.add(quad.face() + ":" + quad.plane() + ":" + (quad.u() + x) + ":" + (quad.v() + y)), "Overlapping surface");
        }
        assertEquals(expected, actual);
    }
}
