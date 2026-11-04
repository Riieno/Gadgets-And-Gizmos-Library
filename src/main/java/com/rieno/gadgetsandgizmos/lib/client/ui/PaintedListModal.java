package com.rieno.gadgetsandgizmos.lib.client.ui;

import com.rieno.gadgetsandgizmos.lib.client.render.GuiTextureRegion;
import com.rieno.gadgetsandgizmos.lib.client.render.GuiPoseScissor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

// A scrollable painted list whose owning screen supplies entries and selection actions.
public final class PaintedListModal<T>{
    private static final int THUMB_HEIGHT = 9;
    private final Appearance appearance;
    private final Function<T, Component> label;
    private final Function<T, List<Component>> tooltip;
    private final ListScrollState scroll = new ListScrollState();
    private List<T> entries = List.of();
    private boolean dragging;
    private double dragOffset;

    public PaintedListModal(Appearance appearance, Function<T, Component> label, Function<T, List<Component>> tooltip){
        this.appearance = Objects.requireNonNull(appearance);
        this.label = Objects.requireNonNull(label);
        this.tooltip = Objects.requireNonNull(tooltip);
        configureScroll();
    }

    public void setEntries(List<T> entries){
        List<T> next = entries == null ? List.of() : entries;
        if(!this.entries.equals(next)) this.entries = List.copyOf(next);
        configureScroll();
    }

    public void resetScroll(){
        dragging = false;
        scroll.scrollToFraction(0);
    }

    public Region screenBounds(int width, int height){
        var transform = transform(width, height);
        return new Region(transform.x(), transform.y(), (int)Math.ceil(appearance.panel().width() * transform.scale()),
                (int)Math.ceil(appearance.panel().height() * transform.scale()));
    }

    public void render(GuiGraphics graphics, Font font, int width, int height, int mouseX, int mouseY,
                       Component title, Component empty, Component footer, Component closeHint){
        var transform = transform(width, height);
        double localX = transform.localX(mouseX);
        double localY = transform.localY(mouseY);
        Layout layout = appearance.layout();
        Colors colors = appearance.colors();
        graphics.fill(0, 0, width, height, colors.backdrop());
        graphics.pose().pushPose();
        graphics.pose().translate(transform.x(), transform.y(), 0);
        graphics.pose().scale(transform.scale(), transform.scale(), 1);
        try{
            appearance.panel().draw(graphics, 0, 0);
            drawText(graphics, font, title, layout.title(), 1, colors.primary());
            if(layout.close().contains(localX, localY)) highlight(graphics, layout.close());
            drawText(graphics, font, Component.literal("\u00d7"), layout.close(), 1, colors.primary());
            drawText(graphics, font, footer, layout.footer(), 0.7F, colors.footer());
            Region list = layout.entries();
            try(var clip = GuiPoseScissor.push(graphics, list.x(), list.y(), list.right(), list.bottom())){
                for(int idx = scroll.firstVisibleIndex(); idx < entries.size(); idx++){
                    Region row = row(idx);
                    if(row.y() >= list.bottom()) break;
                    appearance.row().draw(graphics, row.x(), row.y());
                    if(row.contains(localX, localY)) highlight(graphics, row);
                    drawText(graphics, font, label.apply(entries.get(idx)),
                            new Region(row.x() + 4, row.y() + 2, row.width() - 8, row.height() - 4), 1, colors.primary());
                }
                if(entries.isEmpty()) drawText(graphics, font, empty, list, 0.9F, colors.muted());
            }
            if(scroll.maximumOffset() > 0){
                Region track = layout.scrollbar();
                graphics.fill(track.x() + 1, track.y(), track.right() - 1, track.bottom(), colors.track());
                int top = thumbTop();
                graphics.fill(track.x(), top, track.right(), top + THUMB_HEIGHT, colors.thumb());
            }
        }finally{
            graphics.pose().popPose();
        }
        int hovered = entryAt(localX, localY);
        if(hovered >= 0){
            List<Component> lines = tooltip.apply(entries.get(hovered));
            if(lines != null && !lines.isEmpty()) graphics.renderTooltip(font, lines, java.util.Optional.empty(), mouseX, mouseY);
        }else if(layout.close().contains(localX, localY)){
            graphics.renderTooltip(font, closeHint, mouseX, mouseY);
        }
    }

    public boolean mouseClicked(int width, int height, double mouseX, double mouseY, int button,
                                Consumer<T> selected, Runnable closed){
        if(button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true;
        var transform = transform(width, height);
        double x = transform.localX(mouseX);
        double y = transform.localY(mouseY);
        if(appearance.layout().close().contains(x, y) || x < 0 || y < 0
                || x >= appearance.panel().width() || y >= appearance.panel().height()){
            dragging = false;
            closed.run();
            return true;
        }
        Region track = appearance.layout().scrollbar();
        if(scroll.maximumOffset() > 0 && track.contains(x, y)){
            int thumb = thumbTop();
            dragOffset = y >= thumb && y < thumb + THUMB_HEIGHT ? y - thumb : THUMB_HEIGHT / 2.0D;
            dragging = true;
            dragTo(y);
            return true;
        }
        int index = entryAt(x, y);
        if(index >= 0) selected.accept(entries.get(index));
        return true;
    }

    public boolean mouseDragged(int width, int height, double mouseY, int button){
        if(dragging && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) dragTo(transform(width, height).localY(mouseY));
        return true;
    }

    public boolean mouseReleased(){
        dragging = false;
        return true;
    }

    public boolean mouseScrolled(int width, int height, double mouseX, double mouseY, double amount){
        var transform = transform(width, height);
        double x = transform.localX(mouseX);
        double y = transform.localY(mouseY);
        if(appearance.layout().entries().contains(x, y) || appearance.layout().scrollbar().contains(x, y)){
            scroll.scrollBy((int)Math.round(-amount * rowStride()));
        }
        return true;
    }

    public boolean keyPressed(int keyCode, Runnable closed){
        if(keyCode == GLFW.GLFW_KEY_ESCAPE) closed.run();
        else if(keyCode == GLFW.GLFW_KEY_HOME) scroll.scrollToFraction(0);
        else if(keyCode == GLFW.GLFW_KEY_END) scroll.scrollToFraction(1);
        else if(keyCode == GLFW.GLFW_KEY_PAGE_DOWN) scroll.scrollBy(appearance.layout().entries().height());
        else if(keyCode == GLFW.GLFW_KEY_PAGE_UP) scroll.scrollBy(-appearance.layout().entries().height());
        return true;
    }

    private void configureScroll(){
        scroll.configure(entries.size(), rowStride(), appearance.layout().entries().height());
    }

    private int rowStride(){ return appearance.row().height() + appearance.layout().rowGap(); }

    private GuiCanvasTransform transform(int width, int height){
        return GuiCanvasTransform.fit(appearance.panel().width(), appearance.panel().height(), width, height, 8);
    }

    private Region row(int index){
        Region list = appearance.layout().entries();
        return new Region(list.x() + (list.width() - appearance.row().width()) / 2, list.y() + scroll.rowOffset(index),
                appearance.row().width(), appearance.row().height());
    }

    private int entryAt(double x, double y){
        Region list = appearance.layout().entries();
        if(!list.contains(x, y)) return -1;
        int index = (int)((y - list.y() + scroll.offset()) / rowStride());
        return index >= 0 && index < entries.size() && row(index).contains(x, y) ? index : -1;
    }

    private int thumbTop(){
        Region track = appearance.layout().scrollbar();
        return track.y() + (int)Math.round(scroll.fraction() * (track.height() - THUMB_HEIGHT));
    }

    private void dragTo(double y){
        Region track = appearance.layout().scrollbar();
        scroll.scrollToFraction((y - track.y() - dragOffset) / (track.height() - THUMB_HEIGHT));
    }

    private static void highlight(GuiGraphics graphics, Region bounds){
        graphics.fill(bounds.x() + 1, bounds.y() + 1, bounds.right() - 1, bounds.bottom() - 1, 0x22FFFFFF);
    }

    private static void drawText(GuiGraphics graphics, Font font, Component text, Region bounds, float maximumScale, int color){
        float scale = Math.min(maximumScale, Math.min(bounds.width() / (float)Math.max(1, font.width(text)),
                bounds.height() / (float)font.lineHeight));
        graphics.pose().pushPose();
        graphics.pose().translate(bounds.x() + bounds.width() / 2.0F, bounds.y() + bounds.height() / 2.0F, 1);
        graphics.pose().scale(scale, scale, 1);
        graphics.drawString(font, text, -font.width(text) / 2, -font.lineHeight / 2, color, true);
        graphics.pose().popPose();
    }

    public record Region(int x, int y, int width, int height){
        public Region{
            if(width <= 0 || height <= 0) throw new IllegalArgumentException("GUI regions must have positive dimensions");
        }
        public int right(){ return x + width; }
        public int bottom(){ return y + height; }
        public boolean contains(double x, double y){ return x >= this.x && x < right() && y >= this.y && y < bottom(); }
    }

    public record Layout(Region title, Region entries, Region close, Region footer, Region scrollbar, int rowGap){
        public Layout{
            Objects.requireNonNull(title); Objects.requireNonNull(entries); Objects.requireNonNull(close);
            Objects.requireNonNull(footer); Objects.requireNonNull(scrollbar);
            if(rowGap < 0 || scrollbar.height() <= THUMB_HEIGHT || scrollbar.width() < 3){
                throw new IllegalArgumentException("List gaps and scrollbar dimensions must be usable");
            }
        }
    }

    public record Colors(int backdrop, int primary, int muted, int footer, int track, int thumb){}

    public record Appearance(GuiTextureRegion panel, GuiTextureRegion row, Layout layout, Colors colors){
        public Appearance{
            Objects.requireNonNull(panel); Objects.requireNonNull(row); Objects.requireNonNull(layout); Objects.requireNonNull(colors);
            for(Region region : List.of(layout.title(), layout.entries(), layout.close(), layout.footer(), layout.scrollbar())){
                if(region.x() < 0 || region.y() < 0 || region.right() > panel.width() || region.bottom() > panel.height()){
                    throw new IllegalArgumentException("List controls must fit inside the painted panel");
                }
            }
            if(row.width() < 9 || row.height() < 5 || row.width() > layout.entries().width()){
                throw new IllegalArgumentException("Entry backgrounds must fit inside the list");
            }
        }
    }
}
