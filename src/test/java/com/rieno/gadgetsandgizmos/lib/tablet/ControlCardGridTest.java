package com.rieno.gadgetsandgizmos.lib.tablet;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class ControlCardGridTest{
    @Test void mixedCardSizesKeepEveryCellSeparateAndInsideTheGrid(){
        var random = new Random(126);
        for(int pass = 0; pass < 100; pass++){
            var sizes = new ArrayList<ControlCardStyle.Size>();
            for(int idx = 0; idx < 24; idx++) sizes.add(ControlCardStyle.Size.values()[random.nextInt(3)]);
            var cells = ControlCardGrid.arrange(sizes, 3);
            var occupied = new HashSet<String>();
            for(int idx = 0; idx < cells.size(); idx++){
                var cell = cells.get(idx);
                assertEquals(sizes.get(idx).columns, cell.columns()); assertEquals(sizes.get(idx).rows, cell.rows());
                assertTrue(cell.column() >= 0 && cell.column() + cell.columns() <= 3);
                for(int row = cell.row(); row < cell.row() + cell.rows(); row++) for(int col = cell.column(); col < cell.column() + cell.columns(); col++)
                    assertTrue(occupied.add(col + ":" + row), "Cards overlapped");
            }
            assertEquals(cells, ControlCardGrid.arrange(sizes, 3));
        }
    }

    @Test void laterSinglesFillHolesLeftByWideCards(){
        var cells = ControlCardGrid.arrange(List.of(ControlCardStyle.Size.WIDE, ControlCardStyle.Size.WIDE, ControlCardStyle.Size.SINGLE), 3);
        assertEquals(new ControlCardGrid.Cell(2, 0, 1, 1), cells.getLast());
        assertThrows(IllegalArgumentException.class, () -> ControlCardGrid.arrange(List.of(), 1));
    }

    @Test void appearanceRoundTripsWithoutRequiringLegacyChannelsToHaveMetadata(){
        var style = new ControlCardStyle(ControlCardStyle.Size.TALL, 0xFF773344, ResourceLocation.parse("minecraft:spyglass"));
        assertEquals(0x773344, style.color());
        assertEquals(style, ControlCardStyle.fromTag(style.toTag()));
        var legacy = ControlCardStyle.fromTag(new CompoundTag());
        assertEquals(ControlCardStyle.Size.SINGLE, legacy.size());
        assertEquals(0x303030, legacy.color());
        assertEquals(ResourceLocation.parse("minecraft:redstone"), legacy.icon());
    }
}
