package com.rieno.gadgetsandgizmos.lib.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;

// Paint a button after its screen has applied its GUI transform
@FunctionalInterface
public interface GuiButtonPainter{
    // The host supplies hover state in the button's coordinate space
    void draw(GuiGraphics graphics, Button button, boolean hovered, float partialTick);
}
