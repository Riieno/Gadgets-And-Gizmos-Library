package com.rieno.gadgetsandgizmos.lib.client.recipe;

import com.rieno.gadgetsandgizmos.lib.create.ProcessingTransformationRecipes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

// Keep the two processing stages aligned across recipe viewers.
public final class ProcessingTransformationLayout{
    public static final int WIDTH = 190;
    public static final int HEIGHT = 104;
    public static final int INTERMEDIATE_X = 112;
    public static final int RESULT_X = 164;
    public static final int OUTPUT_Y = 28;

    private ProcessingTransformationLayout(){}

    public static int inputX(int idx){ return 8 + idx % 4 * 20; }
    public static int inputY(int idx){ return 18 + idx / 4 * 20; }

    public static void render(GuiGraphics graphics, int x, int y, ProcessingTransformationRecipes.View view){
        var font = Minecraft.getInstance().font;
        graphics.drawString(font, view.process(), x + 8, y + 4, 0x555555, false);
        graphics.drawString(font, Component.literal(">"), x + 92, y + OUTPUT_Y + 4, 0x555555, false);
        graphics.drawString(font, Component.literal(">"), x + 140, y + OUTPUT_Y + 4, 0x555555, false);
        int lineY = y + 80;
        for(var line : font.split(view.completion(), WIDTH - 16)){
            graphics.drawString(font, line, x + 8, lineY, 0x555555, false);
            lineY += font.lineHeight;
        }
    }
}
