package com.rieno.gadgetsandgizmos.lib.tablet;

// Share inventory coordinates between a tablet's menu slots and its projected app surface
public record TabletInventoryLayout(Grid storage, Grid inputs, Grid outputs, Grid inventory, Grid hotbar){
    public static TabletInventoryLayout split(int contentWidth){
        int playerLeft = Math.max(187, contentWidth - 170);
        return new TabletInventoryLayout(new Grid(7, 29, 9, 4), new Grid(7, 53, 9, 1),
                new Grid(7, 93, 9, 1), new Grid(playerLeft, 34, 9, 3), new Grid(playerLeft, 92, 9, 1));
    }

    public record Grid(int left, int top, int columns, int rows){
        public int x(int slot){ return left + slot % columns * 18; }
        public int y(int slot){ return top + slot / columns * 18; }
        public int size(){ return columns * rows; }
    }
}
