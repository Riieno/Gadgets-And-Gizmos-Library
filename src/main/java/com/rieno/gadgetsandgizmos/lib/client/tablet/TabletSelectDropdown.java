package com.rieno.gadgetsandgizmos.lib.client.tablet;

import com.rieno.gadgetsandgizmos.lib.client.ui.GuiDropdownControl;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

// Share a single-choice dropdown between projected and opened tablet apps
public final class TabletSelectDropdown{
    private final List<Component> options;
    private final List<TabletLayout.Rect> rows = new ArrayList<>();
    private TabletLayout.Rect button = new TabletLayout.Rect(0, 0, 0, 0);
    private int selected;
    private boolean open;
    private boolean projected;

    public TabletSelectDropdown(List<? extends Component> options){
        if(options.isEmpty()) throw new IllegalArgumentException("A dropdown needs at least one option");
        this.options = List.copyOf(options);
    }

    public int selected(){ return selected; }

    public void render(TabletAppClientContext ctx, int x, int y, int width){
        projected = ctx.ui().projected();
        button = new TabletLayout.Rect(ctx.left() + x, ctx.top() + y, width, 20);
        rows.clear();
        var graphics = ctx.graphics();
        graphics.pose().pushPose();
        try{
            graphics.pose().translate(0, 0, 400);
            draw(ctx, button, "");
            GuiDropdownControl.drawContents(graphics, ctx.font(), options.get(selected).getString(),
                    button.left(), button.top(), width, 20, 1,
                    new GuiDropdownControl.Colors(0xFF22394D, 0xFF152334, 0xFFF1F6FB, 0xFFF1F6FB));
            if(open) for(int idx = 0; idx < options.size(); idx++){
                var row = new TabletLayout.Rect(button.left(), button.top() + 22 + idx * 20, width, 20);
                rows.add(row);
                draw(ctx, row, options.get(idx).getString());
            }
        }finally{ graphics.pose().popPose(); }
    }

    // Consume the open menu before controls underneath it
    public boolean mouseClicked(double x, double y, int mouseButton){
        if(mouseButton != 0 && (mouseButton != 1 || !projected)) return false;
        if(button.contains(x, y)){ open = !open; return true; }
        if(!open) return false;
        for(int idx = 0; idx < rows.size(); idx++){
            if(!rows.get(idx).contains(x, y)) continue;
            selected = idx;
            open = false;
            return true;
        }
        open = false;
        return true;
    }

    public boolean keyPressed(int key){
        if(key != 256 || !open) return false;
        close();
        return true;
    }

    public void close(){ open = false; rows.clear(); button = new TabletLayout.Rect(0, 0, 0, 0); }

    private static void draw(TabletAppClientContext ctx, TabletLayout.Rect area, String text){
        ctx.graphics().fill(area.left(), area.top(), area.left() + area.width(), area.top() + area.height(),
                area.contains(ctx.mouseX(), ctx.mouseY()) ? ctx.app().accentColor() : 0xFF304D66);
        ctx.graphics().drawString(ctx.font(), ctx.font().plainSubstrByWidth(text, area.width() - 8),
                area.left() + 4, area.top() + 6, 0xFFF1F6FB, false);
    }
}
