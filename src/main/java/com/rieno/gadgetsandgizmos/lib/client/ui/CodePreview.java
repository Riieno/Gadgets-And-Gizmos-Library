package com.rieno.gadgetsandgizmos.lib.client.ui;

import com.rieno.gadgetsandgizmos.lib.client.render.GuiPoseScissor;
import com.rieno.gadgetsandgizmos.lib.script.LuaSyntax;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

// Render read-only Lua using the same tokens and line numbers as the code editor
public final class CodePreview{
    private CodePreview(){}

    public static int color(LuaSyntax.Kind kind, int text, int muted){
        return switch(kind){
            case KEYWORD -> 0xFFC29BFF;
            case STRING -> 0xFFA3D49C;
            case COMMENT -> muted;
            case NUMBER -> 0xFFF0BF80;
            default -> text;
        };
    }

    public static void render(GuiGraphics graphics, Font font, String source, int x, int y, int width, int height,
                              int firstLine, int horizontal, HelpModalStyle style){
        graphics.fill(x, y, x + width, y + height, style.background());
        String[] lines = source.split("\n", -1);
        var spans = LuaSyntax.spans(source);
        int gutter = Math.max(24, font.width(Integer.toString(lines.length)) + 12);
        int offset = 0;
        try(var clip = GuiPoseScissor.push(graphics, x, y, x + width, y + height)){
            for(int idx = 0; idx < lines.length; idx++){
                String row = lines[idx];
                int top = y + 4 + (idx - firstLine) * (font.lineHeight + 3);
                if(top >= y && top < y + height - font.lineHeight){
                    graphics.drawString(font, Integer.toString(idx + 1), x + gutter - 7 - font.width(Integer.toString(idx + 1)), top, style.muted(), false);
                    try(var rowClip = GuiPoseScissor.push(graphics, x + gutter, y, x + width, y + height)){
                        for(var span : spans){
                            if(span.end() <= offset) continue;
                            if(span.start() >= offset + row.length()) break;
                            int start = Math.max(offset, span.start()), end = Math.min(offset + row.length(), span.end());
                            graphics.drawString(font, source.substring(start, end), x + gutter - horizontal + font.width(row.substring(0, start - offset)),
                                    top, color(span.kind(), style.text(), style.muted()), false);
                        }
                    }
                }
                offset += row.length() + 1;
            }
        }
        graphics.renderOutline(x, y, width, height, style.border());
    }
}
