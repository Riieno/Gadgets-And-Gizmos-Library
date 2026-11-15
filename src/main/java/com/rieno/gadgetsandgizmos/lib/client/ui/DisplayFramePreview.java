package com.rieno.gadgetsandgizmos.lib.client.ui;

import com.rieno.gadgetsandgizmos.lib.client.view.ViewFeedRenderer;
import com.rieno.gadgetsandgizmos.lib.script.ScriptTerminal;
import com.rieno.gadgetsandgizmos.lib.view.ViewReference;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

// Draw target media through the same fitted terminal and live camera renderers as other hosts
public final class DisplayFramePreview{
    private DisplayFramePreview(){}

    public static boolean supports(CompoundTag frame){
        return switch(frame.getString("Format")){ case "terminal", "camera", "text" -> true; default -> false; };
    }

    public static void draw(GuiGraphics graphics, Font font, CompoundTag frame, int x, int y, int width, int height){
        if("camera".equals(frame.getString("Format"))){
            ViewFeedRenderer.draw(graphics, font, ViewReference.fromTag(frame.getCompound("ViewSource")), x, y, width, height); return;
        }
        var terminal = ScriptTerminal.fromDisplayFrame(frame);
        if(terminal != null){ TerminalPreview.render(graphics, terminal, x, y, width, height); return; }
        graphics.fill(x, y, x + width, y + height, 0xFF080808);
        int row = y + 4;
        for(var line : frame.getList("Lines", Tag.TAG_STRING)){
            if(row + font.lineHeight > y + height) break;
            graphics.drawString(font, font.plainSubstrByWidth(line.getAsString(), Math.max(1, width - 8)), x + 4, row, 0xFFFFFFFF, false);
            row += font.lineHeight + 2;
        }
    }
}
