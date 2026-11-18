package com.rieno.gadgetsandgizmos.lib.client.ui;

import net.minecraft.client.gui.GuiGraphics;

// Draw compact file controls without depending on a mod's texture atlas
enum FileActionIcon{
    NEW_FILE("New File"), DELETE("Delete"), COPY("Copy"), PASTE("Paste"), CUT("Cut"), ADD_GRAPH("Add to Graph"), TO_STORAGE("Copy to disk");
    static FileActionIcon[] toolbar(){ return new FileActionIcon[]{NEW_FILE, DELETE, COPY, PASTE, CUT, ADD_GRAPH}; }
    final String label;
    FileActionIcon(String label){ this.label = label; }
    void draw(GuiGraphics graphics, int x, int y, int color){
        switch(this){
            case NEW_FILE -> {
                graphics.renderOutline(x + 2, y + 1, 8, 11, color);
                graphics.hLine(x + 7, x + 13, y + 9, color); graphics.vLine(x + 10, y + 5, y + 13, color);
            }
            case DELETE -> {
                graphics.hLine(x + 2, x + 11, y + 3, color); graphics.hLine(x + 5, x + 8, y + 1, color);
                graphics.renderOutline(x + 3, y + 5, 8, 8, color); graphics.vLine(x + 6, y + 6, y + 11, color); graphics.vLine(x + 8, y + 6, y + 11, color);
            }
            case COPY -> { graphics.renderOutline(x + 2, y + 1, 8, 10, color); graphics.renderOutline(x + 5, y + 4, 8, 10, color); }
            case PASTE -> {
                graphics.renderOutline(x + 2, y + 3, 10, 11, color); graphics.fill(x + 5, y + 1, x + 10, y + 5, color);
                graphics.hLine(x + 5, x + 9, y + 8, color); graphics.hLine(x + 5, x + 9, y + 10, color);
            }
            case ADD_GRAPH -> {
                graphics.renderOutline(x + 1, y + 4, 6, 7, color); graphics.renderOutline(x + 10, y + 1, 5, 5, color);
                graphics.renderOutline(x + 10, y + 10, 5, 5, color); graphics.hLine(x + 7, x + 9, y + 7, color);
                graphics.vLine(x + 9, y + 2, y + 13, color);
            }
            case TO_STORAGE -> {
                graphics.renderOutline(x + 1, y + 1, 13, 13, color); graphics.renderOutline(x + 4, y + 2, 7, 4, color);
                graphics.renderOutline(x + 4, y + 9, 7, 5, color);
            }
            case CUT -> {
                graphics.renderOutline(x + 1, y + 9, 5, 5, color); graphics.renderOutline(x + 9, y + 9, 5, 5, color);
                for(int idx = 0; idx < 9; idx++){
                    graphics.fill(x + 3 + idx, y + 2 + idx, x + 4 + idx, y + 3 + idx, color);
                    graphics.fill(x + 11 - idx, y + 2 + idx, x + 12 - idx, y + 3 + idx, color);
                }
            }
        }
    }
}
