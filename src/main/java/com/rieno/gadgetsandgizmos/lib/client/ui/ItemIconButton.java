package com.rieno.gadgetsandgizmos.lib.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

// Retain vanilla button styling and accessibility while displaying an item instead of text
public final class ItemIconButton extends Button{
    private final ItemStack icon;

    public ItemIconButton(int x, int y, int width, int height, Component message,
                          ItemStack icon, OnPress onPress){
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        this.icon = icon.copy();
        setTooltip(Tooltip.create(message));
    }

    @Override public void renderString(GuiGraphics graphics, Font font, int color){
        graphics.renderItem(icon, getX() + (getWidth() - 16) / 2, getY() + (getHeight() - 16) / 2);
    }
}
