package com.rieno.gadgetsandgizmos.lib.tablet;

// Share inventory coordinates between a tablet's menu slots and its projected app surface
public record TabletInventoryLayout(Grid storage, Grid inputs, Grid outputs, Grid inventory, Grid hotbar){
    public static TabletInventoryLayout split(int contentWidth){
        int playerLeft = Math.max(187, contentWidth - 170);
        return new TabletInventoryLayout(new Grid(7, 29, 9, 4), new Grid(7, 53, 9, 1),
                new Grid(7, 93, 9, 1), new Grid(playerLeft, 34, 9, 3), new Grid(playerLeft, 92, 9, 1));
    }

    // Keep player slots below a full-width container grid on a stacked tablet surface
    public static TabletInventoryLayout stacked(int width, int storageTop, int playerTop, int columns, int rows){
        if(width < columns * 18 - 2 || columns < 1 || rows < 1) throw new IllegalArgumentException("Invalid tablet inventory grid");
        int storageLeft = (width - (columns * 18 - 2)) / 2;
        int playerLeft = (width - 160) / 2;
        return new TabletInventoryLayout(new Grid(storageLeft, storageTop, columns, rows),
                new Grid((width - 160) / 2, storageTop + 27, 9, 1),
                new Grid((width - 160) / 2, storageTop + 67, 9, 1),
                new Grid(playerLeft, playerTop + 8, 9, 3), new Grid(playerLeft, playerTop + 66, 9, 1));
    }

    public record Grid(int left, int top, int columns, int rows){
        public int x(int slot){ return left + slot % columns * 18; }
        public int y(int slot){ return top + slot / columns * 18; }
        public int size(){ return columns * rows; }
    }
}
