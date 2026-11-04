package com.rieno.gadgetsandgizmos.lib.client.ui;

import com.rieno.gadgetsandgizmos.lib.client.render.GuiPoseScissor;
import com.rieno.gadgetsandgizmos.lib.client.render.GuiTextureRegion;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

// A contextual shortcut grid with action labels and separate keycaps.
public final class ShortcutsModal{
    private final Component title;
    private final List<Shortcut> contextual;
    private final List<Shortcut> all;
    private final ListScrollState scroll = new ListScrollState();
    private List<Cell> cells = List.of();
    private int layoutWidth = -1;
    private int contentHeight;
    private boolean allSelected;
    private boolean dragging;
    private TextureAppearance appearance;

    public ShortcutsModal(Component title, List<Shortcut> shortcuts){
        this(title, shortcuts, shortcuts);
    }

    public ShortcutsModal(Component title, List<Shortcut> contextual, List<Shortcut> all){
        this.title = title;
        this.contextual = List.copyOf(contextual);
        this.all = List.copyOf(all);
    }

    // Scale complete painted frames and entries without changing their proportions
    public ShortcutsModal withAppearance(TextureAppearance appearance){
        this.appearance = appearance;
        layoutWidth = -1;
        return this;
    }

    public void render(GuiGraphics graphics, Font font, int width, int height, int mouseX, int mouseY, HelpModalStyle style){
        var bounds = HelpModalFrame.bounds(width, height, 700, frame());
        var body = body(bounds);
        if(layoutWidth != body.width()) layout(font, body.width());
        scroll.configure(contentHeight, 1, body.height());
        HelpModalFrame.render(graphics, font, width, height, bounds, title, style, mouseX, mouseY, frame());
        if(tabs()){
            for(int idx = 0; idx < 2; idx++){
                var tab = tab(bounds, idx);
                boolean selected = allSelected == (idx == 1);
                String label = idx == 0 ? "CONTEXTUAL SHORTCUTS" : "ALL SHORTCUTS";
                float scale = Math.min(0.85F, (tab.width() - 8F) / Math.max(1, font.width(label)));
                graphics.pose().pushPose();
                graphics.pose().translate(tab.x() + 4, tab.y() + 7, 0);
                graphics.pose().scale(scale, scale, 1);
                graphics.drawString(font, label, 0, 0, selected ? style.accent() : style.muted(), false);
                graphics.pose().popPose();
                if(selected) graphics.fill(tab.x(), tab.bottom() - 2, tab.right(), tab.bottom(), style.accent());
            }
        }
        try(var clip = GuiPoseScissor.push(graphics, body.x(), body.y(), body.right() - 7, body.bottom())){
            for(var cell : cells){
                int x = body.x() + cell.x();
                int y = body.y() + cell.y() - scroll.offset();
                if(y + cell.height() < body.y() || y >= body.bottom()) continue;
                if(appearance != null){
                    drawPaintedCell(graphics, font, cell, x, y, style);
                    continue;
                }
                int keyX = x + cell.width() - cell.keyWidth() - 9;
                int keyHeight = cell.keys().size() * (font.lineHeight + 2) + 8;
                int keyY = y + (cell.height() - keyHeight) / 2;
                graphics.fill(x, y, x + cell.width(), y + cell.height(), style.panel());
                graphics.renderOutline(x, y, cell.width(), cell.height(), style.border());
                graphics.fill(keyX, keyY, keyX + cell.keyWidth(), keyY + keyHeight, style.background());
                graphics.renderOutline(keyX, keyY, cell.keyWidth(), keyHeight, style.border());
                for(int idx = 0; idx < cell.keys().size(); idx++){
                    var line = cell.keys().get(idx);
                    graphics.drawString(font, line, keyX + (cell.keyWidth() - font.width(line)) / 2,
                            keyY + 4 + idx * (font.lineHeight + 2), style.text(), false);
                }
                int textY = y + (cell.height() - cell.text().size() * (font.lineHeight + 2)) / 2;
                for(int idx = 0; idx < cell.text().size(); idx++) graphics.drawString(font, cell.text().get(idx), x + 10,
                        textY + idx * (font.lineHeight + 2), style.muted(), false);
            }
        }
        HelpModalFrame.scrollbar(graphics, body, scroll, style);
    }

    private void drawPaintedCell(GuiGraphics graphics, Font font, Cell cell, int x, int y, HelpModalStyle style){
        HelpModalFrame.texture(graphics, appearance.entry(), new HelpModalFrame.Rect(x, y, cell.width(), cell.height()));
        int divider = Math.round(cell.width() * appearance.dividerX() / (float)appearance.entry().width());
        HelpModalFrame.text(graphics, font, cell.shortcut().description(),
                new HelpModalFrame.Rect(x + 5, y + 3, Math.max(1, divider - 10), Math.max(1, cell.height() - 6)), style.muted(), false);
        HelpModalFrame.text(graphics, font, Component.literal(cell.shortcut().keys()),
                new HelpModalFrame.Rect(x + divider + 3, y + 3, Math.max(1, cell.width() - divider - 6), Math.max(1, cell.height() - 6)), style.text(), true);
    }

    private void layout(Font font, int width){
        layoutWidth = width;
        List<Shortcut> entries = allSelected ? all : contextual;
        int columns = width >= (appearance == null ? 360 : 300) ? 2 : 1;
        int gap = 12;
        int cardWidth = Math.max(1, (width - 9 - gap * (columns - 1)) / columns);
        var cells = new ArrayList<Cell>();
        int y = 0;
        if(appearance != null){
            int rowHeight = Math.max(1, Math.round(cardWidth * appearance.entry().height() / (float)appearance.entry().width()));
            for(int idx = 0; idx < entries.size(); idx++){
                cells.add(new Cell(idx % columns * (cardWidth + gap), idx / columns * (rowHeight + gap),
                        cardWidth, rowHeight, 0, List.of(), List.of(), entries.get(idx)));
            }
            this.cells = List.copyOf(cells);
            contentHeight = entries.isEmpty() ? 0 : (entries.size() + columns - 1) / columns * (rowHeight + gap) - gap;
            return;
        }
        for(int start = 0; start < entries.size(); start += columns){
            var row = new ArrayList<Cell>();
            int rowHeight = 42;
            for(int col = 0; col < columns && start + col < entries.size(); col++){
                var entry = entries.get(start + col);
                int keyWidth = Math.min(Math.max(34, font.width(entry.keys()) + 12), Math.max(34, cardWidth * 45 / 100));
                var keys = font.split(Component.literal(entry.keys()), Math.max(1, keyWidth - 10));
                var text = font.split(Component.literal(entry.description().getString().toUpperCase(Locale.ROOT)), Math.max(1, cardWidth - keyWidth - 30));
                int needed = Math.max(keys.size(), text.size()) * (font.lineHeight + 2) + 18;
                rowHeight = Math.max(rowHeight, needed);
                row.add(new Cell(col * (cardWidth + gap), y, cardWidth, 0, keyWidth, keys, text, entry));
            }
            for(var cell : row) cells.add(new Cell(cell.x(), y, cardWidth, rowHeight, cell.keyWidth(), cell.keys(), cell.text(), cell.shortcut()));
            y += rowHeight + gap;
        }
        this.cells = List.copyOf(cells);
        contentHeight = Math.max(0, y - gap);
    }

    public boolean mouseClicked(int width, int height, double mouseX, double mouseY, int button, Runnable close){
        var bounds = HelpModalFrame.bounds(width, height, 700, frame());
        if(button == 0 && HelpModalFrame.close(bounds, frame()).contains(mouseX, mouseY)) close.run();
        if(button == 0 && tabs()){
            for(int idx = 0; idx < 2; idx++) if(tab(bounds, idx).contains(mouseX, mouseY)){
                allSelected = idx == 1;
                layoutWidth = -1;
                scroll.scrollToFraction(0);
                return true;
            }
        }
        var body = body(bounds);
        if(button == 0 && body.contains(mouseX, mouseY) && mouseX >= body.right() - 7){
            dragging = true;
            drag(width, height, mouseY);
        }
        return true;
    }

    public boolean mouseDragged(int width, int height, double mouseY){
        if(dragging) drag(width, height, mouseY);
        return true;
    }

    private void drag(int width, int height, double mouseY){
        var body = body(HelpModalFrame.bounds(width, height, 700, frame()));
        scroll.scrollToFraction((mouseY - body.y() - 8) / Math.max(1, body.height() - 16));
    }

    public boolean mouseReleased(){ dragging = false; return true; }
    public boolean mouseScrolled(double amount){ scroll.scrollBy((int)Math.round(-amount * 30)); return true; }

    public boolean keyPressed(int key, Runnable close){
        if(key == GLFW.GLFW_KEY_ESCAPE) close.run();
        else if(key == GLFW.GLFW_KEY_TAB && tabs()){
            allSelected = !allSelected;
            layoutWidth = -1;
            scroll.scrollToFraction(0);
        }else if(key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_PAGE_DOWN) scroll.scrollBy(key == GLFW.GLFW_KEY_DOWN ? 22 : 140);
        else if(key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_PAGE_UP) scroll.scrollBy(key == GLFW.GLFW_KEY_UP ? -22 : -140);
        else if(key == GLFW.GLFW_KEY_HOME) scroll.scrollToFraction(0);
        else if(key == GLFW.GLFW_KEY_END) scroll.scrollToFraction(1);
        return true;
    }

    private boolean tabs(){ return !contextual.equals(all); }

    private HelpModalFrame.Rect tab(HelpModalFrame.Rect bounds, int idx){
        int width = Math.min(190, Math.max(1, (bounds.width() - 20) / 2));
        int top = appearance == null ? 32 : HelpModalFrame.close(bounds, frame()).height() + 7;
        return new HelpModalFrame.Rect(bounds.x() + 10 + idx * width, bounds.y() + top, width, 24);
    }

    private HelpModalFrame.Rect body(HelpModalFrame.Rect bounds){
        if(appearance != null){
            var body = HelpModalFrame.region(bounds, frame(), appearance.content());
            if(!tabs()) return body;
            return new HelpModalFrame.Rect(body.x(), body.y() + 28, body.width(), Math.max(1, body.height() - 28));
        }
        int top = 38;
        if(tabs()) top += 28;
        int bottom = 14;
        return new HelpModalFrame.Rect(bounds.x() + 14, bounds.y() + top, Math.max(1, bounds.width() - 28), Math.max(1, bounds.height() - top - bottom));
    }

    private HelpModalAppearance frame(){ return appearance == null ? null : appearance.frame(); }

    public record Shortcut(String keys, Component description){}
    public record TextureAppearance(HelpModalAppearance frame, GuiTextureRegion entry, int dividerX, HelpModalAppearance.Region content){
        public TextureAppearance{
            Objects.requireNonNull(frame, "frame");
            Objects.requireNonNull(entry, "entry");
            Objects.requireNonNull(content, "content");
            if(dividerX <= 0 || dividerX >= entry.width()) throw new IllegalArgumentException("Divider must be inside the entry");
        }
    }
    private record Cell(int x, int y, int width, int height, int keyWidth, List<FormattedCharSequence> keys, List<FormattedCharSequence> text, Shortcut shortcut){}
}
