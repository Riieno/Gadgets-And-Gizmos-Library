package com.rieno.gadgetsandgizmos.lib.client.tablet;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.inventory.AbstractContainerMenu;

// Expose a host's native menu without making an app depend on its screen implementation
public record TabletMenuInventory(AbstractContainerMenu menu, int left, int top){
    public void renderBackgrounds(GuiGraphics graphics){
        for(var slot : menu.slots){
            if(!slot.isActive()) continue;
            background(graphics, left + slot.x, top + slot.y);
        }
    }

    public static void background(GuiGraphics graphics, int x, int y){
        graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xFF0C1520);
        graphics.fill(x, y, x + 16, y + 16, 0xFF607185);
    }
}
