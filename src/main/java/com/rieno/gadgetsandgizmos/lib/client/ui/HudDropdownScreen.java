package com.rieno.gadgetsandgizmos.lib.client.ui;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.client.render.StyledText;
import com.rieno.gadgetsandgizmos.lib.display.DropdownOptions;
import com.rieno.gadgetsandgizmos.lib.display.HudStyleCatalog;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

// Open a bounded, scrollable option popup over the host's existing screen or world
public final class HudDropdownScreen extends Screen{
/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        FIELDS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    private final Screen parent;
    private final Supplier<List<DropdownOptions.Option>> options;
    private final Consumer<String> selection;
    private final HudWidgetRenderer.Style style;
    private final HudStyleCatalog.DropdownStyle preset;
    private final HudControlRenderer.Rect anchor;
    private final String selected, prompt;
    private int scroll, focused;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        PRELOAD / SETUP
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    public HudDropdownScreen(Screen parent, Supplier<List<DropdownOptions.Option>> options, String selected, String prompt,
            HudWidgetRenderer.Style style, HudStyleCatalog.DropdownStyle preset, HudControlRenderer.Rect anchor, Consumer<String> selection){
        super(Component.literal(prompt));
        this.parent = parent; this.options = options; this.selected = selected; this.prompt = prompt;
        this.style = style; this.preset = preset; this.anchor = anchor; this.selection = selection;
        var entries = options.get();
        focused = 0;
        for(int idx = 0; idx < entries.size(); idx++) if(entries.get(idx).key().equals(selected)){ focused = idx; break; }
    }

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        FUNCTIONS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    private int rowHeight(){ return Math.max(18, (int)Math.ceil(style.fontSize()) + 8); }

    private HudControlRenderer.Rect control(){
        double w = Math.max(1, Math.min(width - 8, Math.max(80, anchor.width())));
        double h = Math.min(height - 8, Math.max(18, anchor.height()));
        return new HudControlRenderer.Rect(Math.clamp(anchor.x(), 4, Math.max(4, width - w - 4)),
                Math.clamp(anchor.y(), 4, Math.max(4, height - h - 4)), w, h);
    }

    private int rows(int count){
        var bounds = control();
        double space = Math.max(bounds.y() - 4, height - bounds.bottom() - 4);
        return Math.max(1, Math.min(Math.min(8, Math.max(1, count)), (int)(space - 6) / rowHeight()));
    }

    private HudControlRenderer.Rect popup(int count){
        var bounds = control();
        int h = rows(count) * rowHeight() + 4;
        double y = bounds.bottom() + 2;
        if(y + h > height - 4) y = Math.max(4, bounds.y() - h - 2);
        return new HudControlRenderer.Rect(bounds.x(), y, bounds.width(), h);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick){
        if(parent != null) parent.render(graphics, mouseX, mouseY, partialTick);
        List<DropdownOptions.Option> entries = options.get();
        scroll = Math.clamp(scroll, 0, Math.max(0, entries.size() - rows(entries.size())));
        var bounds = control();
        String text = prompt;
        var current = DropdownOptions.find(entries, selected);
        if(current != null) text = current.label();
        graphics.pose().pushPose();
        try{
            graphics.pose().translate(0, 0, 500);
            graphics.pose().pushPose();
            graphics.pose().translate(bounds.x(), bounds.y(), 0);
            HudWidgetRenderer.dropdown(HudWidgetRenderer.gui(graphics, font), font, style,
                    (int)bounds.width(), (int)bounds.height(), text, preset, true, false);
            graphics.pose().popPose();
            var popup = popup(entries.size());
            var canvas = HudWidgetRenderer.gui(graphics, font);
            var p = style.palette();
            var menu = new HudControlRenderer.Palette(p.track(), p.accent(), p.gradient(), p.track(), p.border(), p.borderWidth(), p.radius(), p.scale());
            HudDropdownRenderer.panel(canvas, popup, menu, preset, false);
            graphics.pose().translate(0, 0, 2);
            int visible = rows(entries.size());
            for(int row = 0; row < visible; row++){
                int idx = scroll + row;
                double y = popup.y() + 2 + row * rowHeight();
                if(idx >= entries.size()){
                    if(entries.isEmpty()) drawLabel(graphics, "No options", popup.x() + 6, y, popup.width() - 12);
                    break;
                }
                var option = entries.get(idx);
                boolean hover = mouseX >= popup.x() && mouseX < popup.right() && mouseY >= y && mouseY < y + rowHeight();
                if(hover || idx == focused || option.key().equals(selected)){
                    int accent = hover ? style.palette().gradient() : style.palette().accent();
                    HudControlRenderer.panel(canvas, new HudControlRenderer.Rect(popup.x() + 2, y, popup.width() - 4, rowHeight()),
                            (int)((accent >>> 24) * (hover ? 0.55 : 0.3)) << 24 | accent & 0xFFFFFF, 0, 0, style.palette().radius());
                }
                drawLabel(graphics, option.label(), popup.x() + 6, y, popup.width() - 16);
            }
            if(entries.size() > visible){
                graphics.pose().translate(0, 0, 1);
                double h = Math.max(6, (popup.height() - 4) * visible / entries.size());
                double y = popup.y() + 2 + (popup.height() - 4 - h) * scroll / (entries.size() - visible);
                HudControlRenderer.panel(canvas, new HudControlRenderer.Rect(popup.right() - 4, y, 2, h), style.palette().accent(), 0, 0, 1);
            }
        }finally{ graphics.pose().popPose(); }
    }

    private void drawLabel(GuiGraphics graphics, String text, double x, double y, double width){
        float scale = (float)Math.max(0.05, style.fontSize() / font.lineHeight);
        var label = StyledText.fit(font, text, style.fontStyle(), Math.max(1, (int)(width / scale)));
        HudWidgetRenderer.gui(graphics, font).text(label, x, y + (rowHeight() - font.lineHeight * scale) / 2, scale, style.textColor());
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button){
        if(button != GLFW.GLFW_MOUSE_BUTTON_LEFT){ onClose(); return true; }
        var entries = options.get();
        var popup = popup(entries.size());
        if(mouseX >= popup.x() && mouseX < popup.right() && mouseY >= popup.y() + 2 && mouseY < popup.bottom() - 2){
            int idx = scroll + (int)((mouseY - popup.y() - 2) / rowHeight());
            if(idx < entries.size()) select(entries.get(idx).key());
        }else onClose();
        return true;
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical){
        int count = options.get().size();
        scroll = Math.clamp(scroll - (int)Math.signum(vertical), 0, Math.max(0, count - rows(count)));
        return true;
    }

    @Override public boolean keyPressed(int key, int scan, int modifiers){
        var entries = options.get();
        if(key == GLFW.GLFW_KEY_ESCAPE){ onClose(); return true; }
        if(entries.isEmpty()) return super.keyPressed(key, scan, modifiers);
        if(key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_SPACE){
            select(entries.get(Math.clamp(focused, 0, entries.size() - 1)).key());
            return true;
        }
        if(key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_HOME || key == GLFW.GLFW_KEY_END){
            focused = key == GLFW.GLFW_KEY_HOME ? 0 : key == GLFW.GLFW_KEY_END ? entries.size() - 1
                    : Math.clamp(focused + (key == GLFW.GLFW_KEY_DOWN ? 1 : -1), 0, entries.size() - 1);
            scroll = Math.clamp(scroll, Math.max(0, focused - rows(entries.size()) + 1), focused);
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    private void select(String key){
        onClose();
        if(!key.equals(selected)) selection.accept(key);
    }

    @Override public void onClose(){ minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen(){ return false; }
}
