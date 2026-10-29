package com.rieno.gadgetsandgizmos.lib.display;

import java.util.ArrayList;
import java.util.List;

// Partition a rectangular surface and describe each tile's connected neighbours
public record ConnectedSurfaceLayout(int columns, int rows){
    public static final int LEFT = 1;
    public static final int RIGHT = 2;
    public static final int UP = 4;
    public static final int DOWN = 8;

    public ConnectedSurfaceLayout{
        if(columns <= 0 || rows <= 0) throw new IllegalArgumentException("Surface dimensions must be positive");
    }

    // Round shared boundaries once so neighbouring tiles cannot leave gaps
    public List<Cell> cells(int x, int y, int width, int height){
        if(width <= 0 || height <= 0) return List.of();
        List<Cell> res = new ArrayList<>();
        for(int row = 0; row < rows; row++){
            int top = y + (int)Math.round(row * height / (double)rows);
            int bottom = y + (int)Math.round((row + 1) * height / (double)rows);
            for(int col = 0; col < columns; col++){
                int left = x + (int)Math.round(col * width / (double)columns);
                int right = x + (int)Math.round((col + 1) * width / (double)columns);
                if(right <= left || bottom <= top) continue;
                int connections = (col > 0 ? LEFT : 0) | (col + 1 < columns ? RIGHT : 0)
                        | (row > 0 ? UP : 0) | (row + 1 < rows ? DOWN : 0);
                res.add(new Cell(left, top, right - left, bottom - top, connections));
            }
        }
        return List.copyOf(res);
    }

    public record Cell(int x, int y, int width, int height, int connections){}
}
