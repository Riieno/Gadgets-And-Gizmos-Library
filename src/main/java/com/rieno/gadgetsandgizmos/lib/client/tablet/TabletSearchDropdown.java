package com.rieno.gadgetsandgizmos.lib.client.tablet;

import com.rieno.gadgetsandgizmos.lib.text.LocalizedText;
import com.rieno.gadgetsandgizmos.lib.client.ui.GuiControlPreviews;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

// Browse long option lists in a bounded dropdown with text filtering and scrolling
public final class TabletSearchDropdown{
    private final List<String> options;
    private final Consumer<String> selected;
    private final int x, y, width;
    private List<String> filtered;
    private String filter = "";
    private int offset;
    private boolean open = true;

    public TabletSearchDropdown(List<String> options, String initial, int x, int y, int width, Consumer<String> selected){
        this.options = List.copyOf(options); filtered = this.options; this.selected = selected;
        this.x = x; this.y = Math.min(82, Math.max(16, y)); this.width = width;
        offset = Math.max(0, options.indexOf(initial) - 2);
    }
    public boolean isOpen(){ return open; }
    public void render(GuiGraphics graphics, Font font){
        offset = Math.clamp(offset, 0, Math.max(0, filtered.size() - 6));
        graphics.fill(x - 1, y - 1, x + width + 1, y + 83, 0xFF141414);
        graphics.fill(x, y, x + width, y + 12, 0xFF515151);
        text(graphics, font, filter.isBlank() ? LocalizedText.get("gadgetsngizmos.text.search_timezone") : filter + "_", x + 3, y + 2, width - 6);
        for(int idx = 0; idx < Math.min(6, filtered.size() - offset); idx++){
            graphics.fill(x, y + 13 + idx * 11, x + width, y + 24 + idx * 11, idx % 2 == 0 ? 0xFF303030 : 0xFF393939);
            text(graphics, font, filtered.get(offset + idx), x + 3, y + 15 + idx * 11, width - 6);
        }
    }
    public boolean mouseClicked(double mouseX, double mouseY, int button){
        if(button == 0 && mouseX >= x && mouseX < x + width && mouseY >= y + 13 && mouseY < y + 79){
            int idx = offset + (int)(mouseY - y - 13) / 11;
            if(idx < filtered.size()){ selected.accept(filtered.get(idx)); open = false; }
        }else if(mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + 83) open = false;
        return true;
    }
    public boolean mouseScrolled(double amount){ offset = Math.clamp(offset - (int)Math.signum(amount), 0, Math.max(0, filtered.size() - 6)); return true; }
    public boolean charTyped(char ch){
        if(!Character.isISOControl(ch) && filter.length() < 80){ filter += ch; refresh(); }
        return true;
    }
    public boolean keyPressed(int key){
        if(key == GLFW.GLFW_KEY_ESCAPE) open = false;
        else if(key == GLFW.GLFW_KEY_BACKSPACE && !filter.isEmpty()){ filter = filter.substring(0, filter.length() - 1); refresh(); }
        else if(key == GLFW.GLFW_KEY_ENTER && !filtered.isEmpty()){ selected.accept(filtered.get(offset)); open = false; }
        else if(key == GLFW.GLFW_KEY_DOWN) mouseScrolled(-1);
        else if(key == GLFW.GLFW_KEY_UP) mouseScrolled(1);
        return true;
    }
    private void refresh(){ filtered = options.stream().filter(val -> val.toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT))).toList(); offset = 0; }
    private static void text(GuiGraphics graphics, Font font, String text, int x, int y, int width){
        GuiControlPreviews.drawFittedText(graphics, font, Component.literal(text), x, y, width, 9, .75F, 0xFFFFFFFF, false);
    }
}
