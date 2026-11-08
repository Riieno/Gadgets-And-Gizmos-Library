package com.rieno.gadgetsandgizmos.lib.geometry;

import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// Keep complete-chain placement and per-cell interaction geometry consistent
class ScissorLinkageLayoutTest{
    private static final ScissorLinkageLayout.Profile PROFILE =
            new ScissorLinkageLayout.Profile(.25, .0625, .09375, .9, 1);

    // Every section must retain equal spacing across supported extension lengths
    @Test void sectionsCoverTheCompleteSpanBeforeAndAfterShortening(){
        for(int range = 1; range <= 32; range++){
            for(double extension : new double[]{0, .25, range * .5, range}){
                var layout = new ScissorLinkageLayout(range + 1, extension);
                double step = layout.sectionLength();
                assertEquals(extension, step * layout.sectionCount(), 1E-12);
                assertEquals(1, step * step + layout.sideSpan() * layout.sideSpan(), 1E-12);
                assertEquals(extension - step * .5, layout.distanceFromHead(0), 1E-12);
                assertEquals(step * .5, layout.distanceFromHead(range), 1E-12);
                for(int idx = 1; idx <= range; idx++){
                    assertEquals(step, layout.distanceFromHead(idx - 1) - layout.distanceFromHead(idx), 1E-12);
                }
            }
        }
    }

    // Network pose delay must not leave a gap when the section count changes first
    @Test void temporaryOverextensionKeepsFiniteConnectedRods(){
        var layout = new ScissorLinkageLayout(2, 5);
        assertEquals(2.5, layout.sectionLength());
        assertEquals(0, layout.sideSpan());
        assertEquals(2.5, layout.modelScale(PROFILE));
        assertEquals(3.75, layout.distanceFromHead(0));
        assertEquals(1.25, layout.distanceFromHead(1));
    }

    // Hitboxes must follow the rods and remain inside their owning cells in every facing
    @Test void hitboxesRemainAvailableAfterAnInteriorCellIsRecreated(){
        for(Direction facing : Direction.values()){
            for(double extension : new double[]{5.75, 5, 4.75}){
                var layout = new ScissorLinkageLayout(extension > 5 ? 7 : 6, extension);
                for(int cell = 1; cell <= 4; cell++){
                    var shape = layout.cellShape(facing, cell, PROFILE);
                    assertFalse(shape.isEmpty(), facing + " cell " + cell + " span " + extension);
                    for(var box : shape.toAabbs()){
                        assertTrue(box.minX >= -1E-10 && box.minY >= -1E-10 && box.minZ >= -1E-10);
                        assertTrue(box.maxX <= 1 + 1E-10 && box.maxY <= 1 + 1E-10 && box.maxZ <= 1 + 1E-10);
                    }
                }
            }
        }
    }

    // Folded cells and invalid layouts must never create invisible interaction blockers
    @Test void foldedCellsAreEmptyAndInvalidDimensionsAreRejected(){
        assertTrue(new ScissorLinkageLayout(6, 0).cellShape(Direction.UP, 1, PROFILE).isEmpty());
        assertTrue(new ScissorLinkageLayout(6, 5).cellShape(Direction.UP, 5, PROFILE).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new ScissorLinkageLayout(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new ScissorLinkageLayout(2, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new ScissorLinkageLayout(2, -1));
    }
}
