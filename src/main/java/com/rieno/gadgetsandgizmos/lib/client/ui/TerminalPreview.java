package com.rieno.gadgetsandgizmos.lib.client.ui;

import com.rieno.gadgetsandgizmos.lib.client.render.ScriptTerminalRenderer;
import com.rieno.gadgetsandgizmos.lib.script.ScriptTerminal;
import net.minecraft.client.gui.GuiGraphics;

// Reuse terminal textures and the same fitted cells across every GUI host
public final class TerminalPreview{
    private TerminalPreview(){}
    public static void render(GuiGraphics graphics, ScriptTerminal terminal, int x, int y, int width, int height){
        ScriptTerminalRenderer.render(graphics, terminal, x, y, width, height);
    }
}
