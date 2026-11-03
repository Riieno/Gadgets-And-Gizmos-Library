package com.rieno.gadgetsandgizmos.lib.tablet;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

// Pack card spans into fixed columns without overlapping earlier cards
public final class ControlCardGrid{
    private ControlCardGrid(){}
    public record Cell(int column, int row, int columns, int rows){}

    public static List<Cell> arrange(List<ControlCardStyle.Size> sizes, int columns){
        if(columns < 2) throw new IllegalArgumentException("Card grids require at least two columns");
        var occupied = new BitSet();
        var res = new ArrayList<Cell>();
        for(var size : sizes){
            int width = Math.min(columns, size.columns);
            for(int idx = 0; ; idx++){
                int col = idx % columns, row = idx / columns;
                if(col + width > columns) continue;
                boolean free = true;
                for(int y = 0; y < size.rows && free; y++) for(int x = 0; x < width; x++){
                    if(occupied.get((row + y) * columns + col + x)){ free = false; break; }
                }
                if(!free) continue;
                for(int y = 0; y < size.rows; y++) occupied.set((row + y) * columns + col, (row + y) * columns + col + width);
                res.add(new Cell(col, row, width, size.rows));
                break;
            }
        }
        return List.copyOf(res);
    }
}
