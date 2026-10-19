package com.rieno.gadgetsandgizmos.lib.view;

import com.rieno.gadgetsandgizmos.lib.client.view.ViewControlPanel;
import com.rieno.gadgetsandgizmos.lib.client.view.ViewfinderOverlay;
import net.minecraft.client.gui.Font;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

class CompactViewfinderTest{
    @Test void smallGridFeedsRetainTheirOwnOverlayWithoutDrawingOutsideTheirImage(){
        for(int[] size : new int[][]{{191, 29}, {191, 43}, {240, 60}}){
            for(int cell = 0; cell < 4; cell++){
                int x = 17 + cell % 2 * (size[0] + 4), y = 23 + cell / 2 * (size[1] + 19);
                var marks = new ArrayList<int[]>();
                var canvas = new ViewControlPanel.Canvas(){
                    @Override public void fill(int left, int top, int right, int bottom, int color){ marks.add(new int[]{left, top, right, bottom}); }
                    @Override public void drawString(Font font, String text, int left, int top, int color, boolean shadow){ fail("Compact feeds must not draw overlapping status text"); }
                };
                ViewfinderOverlay.render(canvas, null, x, y, size[0], size[1], true, true, 70, 0);
                assertFalse(marks.isEmpty());
                for(int[] mark : marks){
                    assertTrue(mark[0] >= x && mark[2] <= x + size[0]);
                    assertTrue(mark[1] >= y && mark[3] <= y + size[1]);
                }
                assertTrue(marks.stream().anyMatch(mark -> mark[0] > x && mark[2] - mark[0] == 1 && mark[3] - mark[1] > size[1] / 2), "Composition grid was omitted");
            }
        }
    }
}
