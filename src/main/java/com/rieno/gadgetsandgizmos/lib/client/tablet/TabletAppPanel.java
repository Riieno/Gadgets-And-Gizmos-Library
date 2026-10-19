package com.rieno.gadgetsandgizmos.lib.client.tablet;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Share compact controls and text input between independently rendered tablet apps
public final class TabletAppPanel{
    private final List<Control> controls = new ArrayList<>();
    private final Map<String, String> inputs = new HashMap<>();
    private String focus = "";
    private boolean projected;

    public void begin(TabletAppClientContext ctx){
        controls.clear();
        projected = ctx.ui().projected();
        ctx.graphics().fill(ctx.left(), ctx.top(), ctx.left() + ctx.width(), ctx.top() + ctx.height(), 0xEF152334);
    }

    // Render errors above app content, after the remaining controls have been drawn
    public void error(TabletAppClientContext ctx){
        if(!ctx.data().contains("Error")) return;
        ctx.graphics().pose().pushPose();
        ctx.graphics().pose().translate(0, 0, 500);
        ctx.graphics().fill(ctx.left(), ctx.top() + ctx.height() - 36, ctx.left() + ctx.width(), ctx.top() + ctx.height() - 23, 0xFF49262D);
        text(ctx, ctx.data().getString("Error"), 4, ctx.height() - 33, ctx.width() - 8, 0xFFFFAAAA);
        ctx.graphics().pose().popPose();
    }

    public void text(TabletAppClientContext ctx, String text, int x, int y, int width, int color){
        ctx.graphics().drawString(ctx.font(), ctx.font().plainSubstrByWidth(text, Math.max(1, width)), ctx.left() + x, ctx.top() + y, color, false);
    }

    public void wrap(TabletAppClientContext ctx, String text, int x, int y, int width, int height){
        int row = 0;
        for(var line : ctx.font().split(Component.literal(text), width)){
            if(row + ctx.font().lineHeight > height) break;
            ctx.graphics().drawString(ctx.font(), line, ctx.left() + x, ctx.top() + y + row, 0xFFCAD7E5, false);
            row += ctx.font().lineHeight;
        }
    }

    // Draw a compact progress bar shared by tablet app task lists
    public void progress(TabletAppClientContext ctx, int x, int y, int width, int height, double fraction){
        int left = ctx.left() + x;
        int top = ctx.top() + y;
        int filled = Mth.clamp((int) Math.round(width * fraction), 0, width);
        ctx.graphics().fill(left, top, left + width, top + height, 0xFF22394D);
        if(filled > 0) ctx.graphics().fill(left, top, left + filled, top + height,
                ctx.app() == null ? 0xFF75BD77 : ctx.app().accentColor());
    }

    public void button(TabletAppClientContext ctx, int x, int y, int width, int height, String label, boolean enabled, Runnable action){
        var area = new TabletLayout.Rect(ctx.left() + x, ctx.top() + y, width, height);
        ctx.graphics().fill(area.left(), area.top(), area.left() + width, area.top() + height,
                enabled ? area.contains(ctx.mouseX(), ctx.mouseY()) ? ctx.app().accentColor() : 0xFF304D66 : 0xFF26313D);
        text(ctx, label, x + 5, y + (height - 8) / 2, width - 10, enabled ? 0xFFF1F6FB : 0xFF687886);
        controls.add(new Control(area, enabled ? action : () -> {}, ""));
    }

    public void input(TabletAppClientContext ctx, String id, int x, int y, int width, String hint){
        var area = new TabletLayout.Rect(ctx.left() + x, ctx.top() + y, width, 20);
        ctx.graphics().fill(area.left(), area.top(), area.left() + width, area.top() + 20, id.equals(focus) ? 0xFF365572 : 0xFF22394D);
        String val = inputs.getOrDefault(id, "");
        String text = val.isEmpty() ? hint : val + (id.equals(focus) ? "_" : "");
        text(ctx, text, x + 5, y + 6, width - 10, val.isEmpty() ? 0xFF8B9BAA : 0xFFEAF3FC);
        controls.add(new Control(area, () -> {
            if(ctx.ui().projected()){
                focus = "";
                ctx.ui().editText(hint, value(id), 128, res -> setValue(id, res));
            }
        }, id));
    }

    public String value(String id){ return inputs.getOrDefault(id, ""); }
    public void setValue(String id, String val){ inputs.put(id, val == null ? "" : val); }

    public boolean click(double x, double y, int button){
        if(button != 0 && (button != 1 || !projected)) return false;
        for(Control control : controls){
            if(!control.area().contains(x, y)) continue;
            focus = control.input();
            control.action().run();
            return true;
        }
        focus = "";
        return false;
    }

    public boolean keyPressed(int keyCode, int modifiers){
        if(focus.isBlank()) return false;
        String val = value(focus);
        if(keyCode == 259 && !val.isEmpty()) inputs.put(focus, val.substring(0, val.offsetByCodePoints(val.length(), -1)));
        else if(keyCode == 261) inputs.put(focus, "");
        else if(keyCode == 86 && (modifiers & 2) != 0){
            String text = Minecraft.getInstance().keyboardHandler.getClipboard().replaceAll("[\\p{Cntrl}]", "");
            inputs.put(focus, (val + text).substring(0, Math.min(128, val.length() + text.length())));
        }else if(keyCode == 256 || keyCode == 257){ focus = ""; }
        return true;
    }

    public boolean charTyped(char codePoint){
        if(focus.isBlank()) return false;
        String val = value(focus);
        if(codePoint >= 32 && codePoint != 127 && val.length() < 128) inputs.put(focus, val + codePoint);
        return true;
    }

    private record Control(TabletLayout.Rect area, Runnable action, String input){}
}
