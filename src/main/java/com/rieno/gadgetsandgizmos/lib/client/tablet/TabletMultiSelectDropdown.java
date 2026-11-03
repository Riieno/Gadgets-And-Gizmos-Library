package com.rieno.gadgetsandgizmos.lib.client.tablet;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import com.rieno.gadgetsandgizmos.lib.client.render.GuiTextureRegion;
import com.rieno.gadgetsandgizmos.lib.client.ui.GuiControlPreviews;
import net.minecraft.network.chat.Component;

// Share a compact checklist dropdown between tablet app browsers
public final class TabletMultiSelectDropdown{
    private final List<Option> options;
    private final Set<String> selected = new LinkedHashSet<>();
    private final List<TabletLayout.Rect> rows = new ArrayList<>();
    private TabletLayout.Rect button = new TabletLayout.Rect(0, 0, 0, 0);
    private boolean open;
    private boolean projected;

    // Start with every declared option selected
    public TabletMultiSelectDropdown(List<Option> options){
        this.options = List.copyOf(options);
        this.options.forEach(val -> selected.add(val.id()));
    }


    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Render the checklist above content while keeping each option independently selectable
    public void render(TabletAppClientContext ctx, int x, int y, int width, String label){
        projected = ctx.ui().projected();
        button = new TabletLayout.Rect(ctx.left() + x, ctx.top() + y, width, 20);
        rows.clear();
        var graphics = ctx.graphics();
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 400);
        draw(ctx, button, label + ": " + (selected.size() == options.size() ? "All" : selected.isEmpty() ? "None" : selected.size()) + " ▾", false);
        if(open){
            for(int idx = 0; idx < options.size(); idx++){
                Option option = options.get(idx);
                var row = new TabletLayout.Rect(button.left(), button.top() + 22 + idx * 20, width, 20);
                rows.add(row);
                draw(ctx, row, (selected.contains(option.id()) ? "[x] " : "[ ] ") + option.label(), selected.contains(option.id()));
            }
        }
        graphics.pose().popPose();
    }

    // Reuse checklist behavior with the consuming app's painted controls and row height
    public void renderPainted(TabletAppClientContext ctx, GuiTextureRegion normal, GuiTextureRegion selectedTexture,
                              int x, int y, int width, int height, String label){
        projected = ctx.ui().projected();
        button = new TabletLayout.Rect(ctx.left() + x, ctx.top() + y, width, height);
        rows.clear();
        ctx.graphics().pose().pushPose();
        try{
            ctx.graphics().pose().translate(0, 0, 400);
            drawPainted(ctx, normal, button, label + ": " + (selected.size() == options.size() ? "All" : selected.size()));
            if(open){
                for(int idx = 0; idx < options.size(); idx++){
                    Option option = options.get(idx);
                    var row = new TabletLayout.Rect(button.left(), button.top() + (height + 1) * (idx + 1), width, height);
                    rows.add(row);
                    boolean checked = selected.contains(option.id());
                    drawPainted(ctx, checked ? selectedTexture : normal, row, (checked ? "[x] " : "[ ] ") + option.label());
                }
            }
        }finally{ ctx.graphics().pose().popPose(); }
    }

    // Consume dropdown clicks before the consuming app tests underlying controls
    public boolean mouseClicked(double x, double y, int mouseButton){
        if(mouseButton != 0 && (mouseButton != 1 || !projected)) return false;
        if(button.contains(x, y)){ open = !open; return true; }
        if(open){
            for(int idx = 0; idx < rows.size(); idx++){
                if(!rows.get(idx).contains(x, y)) continue;
                String id = options.get(idx).id();
                if(!selected.remove(id)) selected.add(id);
                return true;
            }
            open = false;
        }
        return false;
    }

    // Close the checklist before Escape closes its host screen
    public boolean keyPressed(int key){
        if(key != 256 || !open) return false;
        close();
        return true;
    }

    // Expose selected IDs without exposing mutable dropdown state
    public Set<String> selectedIds(){ return Set.copyOf(selected); }

    // Clear hit areas when a different app page becomes active
    public void close(){ open = false; rows.clear(); button = new TabletLayout.Rect(0, 0, 0, 0); }


    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw one checklist row using the tablet's normal compact controls
    private static void draw(TabletAppClientContext ctx, TabletLayout.Rect area, String text, boolean selected){
        ctx.graphics().fill(area.left(), area.top(), area.left() + area.width(), area.top() + area.height(),
                area.contains(ctx.mouseX(), ctx.mouseY()) ? ctx.app().accentColor() : selected ? 0xFF365572 : 0xFF304D66);
        ctx.graphics().drawString(ctx.font(), ctx.font().plainSubstrByWidth(text, Math.max(1, area.width() - 8)),
                area.left() + 4, area.top() + 6, 0xFFF1F6FB, false);
    }

    private static void drawPainted(TabletAppClientContext ctx, GuiTextureRegion texture, TabletLayout.Rect area, String text){
        texture.draw(ctx.graphics(), area.left(), area.top(), area.width(), area.height());
        GuiControlPreviews.drawFittedText(ctx.graphics(), ctx.font(), Component.literal(text), area.left() + 3, area.top() + 1,
                Math.max(1, area.width() - 6), Math.max(1, area.height() - 2), .85F, 0xFFF1F1F1, false);
    }

    // Pair a stable filter ID with its visible label
    public record Option(String id, String label){}
}
