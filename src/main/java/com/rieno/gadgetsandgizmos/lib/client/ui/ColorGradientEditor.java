package com.rieno.gadgetsandgizmos.lib.client.ui;

import com.rieno.gadgetsandgizmos.lib.client.render.GuiTextureRegion;
import com.rieno.gadgetsandgizmos.lib.color.ColorStopEditor;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;

// Share gradient drawing, stop dragging and modifier shortcuts between host screens
public final class ColorGradientEditor{
    private final ColorStopEditor editor;
    private int x, y, width, height;
    private boolean dragging;
    private int lastClick = -1;
    private long lastClickTime;

    public ColorGradientEditor(ColorStopEditor editor){ this.editor = editor; }
    public ColorStopEditor editor(){ return editor; }

    public boolean contains(double mouseX, double mouseY){
        return mouseX >= x - 6 && mouseX < x + width + 6 && mouseY >= y && mouseY < y + height + 20;
    }

    public void bounds(int x, int y, int width, int height){
        this.x = x; this.y = y; this.width = Math.max(2, width); this.height = Math.max(1, height);
    }

    // Accept the owning mod's handle artwork without depending on its textures
    public void render(GuiGraphics graphics, GuiTextureRegion handle, GuiTextureRegion selected){
        graphics.fill(x - 2, y - 2, x + width + 2, y + height + 2, 0xFF454545);
        for(int col = 0; col < width; col++) graphics.fill(x + col, y, x + col + 1, y + height,
                0xFF000000 | editor.preset().samplePosition(col / (float)(width - 1)));
        if(!editor.preset().usesStops()) return;
        for(int idx = 0; idx < editor.preset().stops().size(); idx++){
            var stop = editor.preset().stops().get(idx);
            int left = x + Math.round(stop.position() * (width - 1)) - 6;
            (idx == editor.selected() ? selected : handle).draw(graphics, left, y + height + 4, 12, 14);
            graphics.fill(left + 3, y + height + 12, left + 10, y + height + 19, 0xFF000000 | stop.color());
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button, boolean ctrl, boolean shift, Runnable pickColor){
        if(button != 0) return false;
        if(editor.preset().usesStops()){
            for(int idx = editor.preset().stops().size() - 1; idx >= 0; idx--){
                int tick = x + Math.round(editor.preset().stops().get(idx).position() * (width - 1));
                if(mouseX < tick - 6 || mouseX >= tick + 6 || mouseY < y + height + 4 || mouseY >= y + height + 20) continue;
                editor.select(idx);
                if(shift){ editor.remove(); dragging = false; lastClick = -1; return true; }
                long time = Util.getMillis();
                if(lastClick == idx && time - lastClickTime < 300){ dragging = false; lastClick = -1; pickColor.run(); }
                else{ dragging = true; lastClick = idx; lastClickTime = time; }
                return true;
            }
        }
        if(mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + height) return false;
        if(ctrl && editor.preset().usesStops()) editor.add((float)((mouseX - x) / (width - 1)));
        else if(!editor.preset().usesStops()) pickColor.run();
        return true;
    }

    public boolean mouseDragged(double mouseX){
        return dragging && editor.move((float)((mouseX - x) / (width - 1)));
    }

    public boolean dragging(){ return dragging; }
    public void mouseReleased(){ dragging = false; }
}
