package com.rieno.gadgetsandgizmos.lib.client.ui;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.client.render.GuiPoseScissor;
import com.rieno.gadgetsandgizmos.lib.client.render.GuiTextureRegion;
import com.rieno.gadgetsandgizmos.lib.documentation.DocumentationSource;
import com.rieno.gadgetsandgizmos.lib.documentation.MarkdownDocument;
import com.rieno.gadgetsandgizmos.lib.documentation.RemoteResources;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

// A live Markdown reader with page discovery, navigation, refresh and cached offline content.
public final class DocumentationModal implements AutoCloseable{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private final DocumentationSource source;
    private final Consumer<URI> external;
    private final Labels labels;
    private final MarkdownView view;
    private final ListScrollState pagesScroll = new ListScrollState();
    private final ArrayDeque<DocumentationSource.Page> history = new ArrayDeque<>();
    private List<DocumentationSource.Page> pages = List.of();
    private DocumentationSource.Page selected;
    private String error = "";
    private String anchor = "";
    private boolean loadingIndex;
    private boolean loadingPage;
    private boolean stale;
    private boolean indexStale;
    private boolean draggingPages;
    private boolean closed;
    private int generation;
    private int indexGeneration;
    private TextureAppearance appearance;

    public DocumentationModal(DocumentationSource source, Consumer<URI> external){
        this(source, external, Labels.DEFAULT);
    }

    public DocumentationModal(DocumentationSource source, Consumer<URI> external, Labels labels){
        this.source = Objects.requireNonNull(source);
        this.external = Objects.requireNonNull(external);
        this.labels = Objects.requireNonNull(labels);
        view = new MarkdownView(target -> source.assetUri(selected, target), source::loadAsset);
        refresh();
    }

    // Use painted chrome and page buttons while retaining the Markdown reader
    public DocumentationModal withAppearance(TextureAppearance appearance){
        this.appearance = appearance;
        return this;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        LIVE CONTENT
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public void refresh(){
        if(closed) return;
        int request = ++indexGeneration;
        loadingIndex = true;
        error = "";
        source.pages().whenComplete((index, failure) -> Minecraft.getInstance().execute(() -> {
            if(closed || request != indexGeneration) return;
            loadingIndex = false;
            if(failure != null){
                error = labels.failed().getString();
                return;
            }
            pages = index.pages();
            stale = index.stale();
            indexStale = index.stale();
            var next = selected == null ? pages.stream().filter(page -> page.id().equals("Home")).findFirst().orElse(pages.isEmpty() ? null : pages.getFirst())
                    : pages.stream().filter(page -> page.id().equals(selected.id())).findFirst().orElse(pages.isEmpty() ? null : pages.getFirst());
            if(next != null) load(next, false, "");
            else error = labels.failed().getString();
        }));
    }

    private void load(DocumentationSource.Page page, boolean remember, String anchor){
        if(closed) return;
        if(remember && selected != null && !selected.id().equals(page.id())) history.addLast(selected);
        while(history.size() > 64) history.removeFirst();
        boolean switching = selected == null || !selected.id().equals(page.id());
        selected = page;
        this.anchor = anchor;
        error = "";
        loadingPage = true;
        if(switching) view.document(new MarkdownDocument(List.of()));
        int request = ++generation;
        source.load(page).whenComplete((content, failure) -> Minecraft.getInstance().execute(() -> {
            if(closed || request != generation) return;
            loadingPage = false;
            if(failure != null){ error = labels.failed().getString(); return; }
            stale = indexStale || content.stale();
            view.document(content.document());
            int idx = pages.indexOf(page);
            if(idx >= 0) pagesScroll.ensureVisible(idx);
        }));
    }

    private void follow(String target){
        if(selected == null || target.isBlank()) return;
        URI uri;
        try{ uri = source.linkUri(selected, target); }
        catch(IllegalArgumentException ex){ return; }
        for(var page : pages){
            if(Objects.equals(page.uri().getScheme(), uri.getScheme()) && Objects.equals(page.uri().getHost(), uri.getHost())
                    && page.uri().getPath().equals(uri.getPath())){
                String fragment = uri.getRawFragment() == null ? "" : uri.getRawFragment();
                if(page.id().equals(selected.id())) view.jump(fragment);
                else load(page, true, fragment);
                return;
            }
        }
        if(RemoteResources.isWebUri(uri)) external.accept(uri);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        RENDERING
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public void render(GuiGraphics graphics, Font font, int width, int height, int mouseX, int mouseY, HelpModalStyle style){
        var bounds = HelpModalFrame.bounds(width, height, 1050, frame());
        var sidebar = sidebar(bounds);
        var body = body(bounds);
        int stride = pageStride(sidebar);
        pagesScroll.configure(pages.size(), stride, sidebar.height());
        HelpModalFrame.render(graphics, font, width, height, bounds, labels.title(), style, mouseX, mouseY, frame());
        button(graphics, font, back(bounds), labels.back(), !history.isEmpty(), mouseX, mouseY, style);
        button(graphics, font, refresh(bounds), labels.refresh(), !loadingIndex && !loadingPage, mouseX, mouseY, style);
        if(appearance == null) graphics.drawString(font, labels.pages(), bounds.x() + 10, bounds.y() + 37, style.accent(), false);
        else HelpModalFrame.text(graphics, font, labels.pages(), paintedRegion(bounds, appearance.layout().pagesTitle()), style.accent(), false);
        if(appearance == null) graphics.fill(sidebar.right() + 3, sidebar.y() - 4, sidebar.right() + 4, sidebar.bottom(), style.border());
        try(var clip = GuiPoseScissor.push(graphics, sidebar.x(), sidebar.y(), sidebar.right() - 6, sidebar.bottom())){
            for(int idx = pagesScroll.firstVisibleIndex(); idx < pages.size(); idx++){
                int y = sidebar.y() + pagesScroll.rowOffset(idx);
                if(y >= sidebar.bottom()) break;
                var page = pages.get(idx);
                boolean current = selected != null && selected.id().equals(page.id());
                boolean hover = sidebar.contains(mouseX, mouseY) && mouseY >= y && mouseY < y + stride - 1;
                if(appearance != null){
                    HelpModalFrame.texture(graphics, current ? appearance.selectedButton() : appearance.button(),
                            new HelpModalFrame.Rect(sidebar.x(), y, sidebar.width() - 7, stride - 3));
                    if(hover) graphics.fill(sidebar.x() + 2, y + 2, sidebar.right() - 9, y + stride - 5, 0x18FFFFFF);
                }else if(current || hover){
                    graphics.fill(sidebar.x(), y, sidebar.right() - 7, y + 23, current ? style.border() : style.panel());
                }
                int color = current ? style.accent() : style.text();
                if(appearance == null) graphics.drawString(font, font.plainSubstrByWidth(page.title(), Math.max(1, sidebar.width() - 15)),
                        sidebar.x() + 4, y + 7, color, false);
                else HelpModalFrame.text(graphics, font, Component.literal(page.title()),
                        new HelpModalFrame.Rect(sidebar.x() + 4, y + 3, Math.max(1, sidebar.width() - 20), Math.max(1, stride - 9)), color, false);
            }
        }
        HelpModalFrame.scrollbar(graphics, sidebar, pagesScroll, style);
        view.render(graphics, font, body, style, mouseX, mouseY);
        if(!loadingPage && !anchor.isEmpty()){ view.jump(anchor); anchor = ""; }
        Component status = !error.isEmpty() ? Component.literal(error) : loadingPage || loadingIndex ? labels.loading() : stale ? labels.offline() : selected == null ? Component.empty() : Component.literal(selected.title());
        var statusBounds = appearance == null
                ? new HelpModalFrame.Rect(body.x(), bounds.y() + 31, Math.max(1, back(bounds).x() - body.x() - 8), 20)
                : paintedRegion(bounds, appearance.layout().status());
        if(statusBounds.width() > 25){
            if(appearance == null) graphics.drawString(font, font.plainSubstrByWidth(status.getString(), statusBounds.width()), statusBounds.x(), statusBounds.y() + 6, style.muted(), false);
            else HelpModalFrame.text(graphics, font, status, statusBounds, style.muted(), false);
        }
        if(!error.isEmpty() && selected == null) graphics.drawWordWrap(font, labels.failed(), body.x() + 6, body.y() + 6, Math.max(1, body.width() - 12), style.text());
        if(appearance == null) graphics.drawString(font, font.plainSubstrByWidth(labels.footer().getString(), Math.max(1, bounds.width() - 20)),
                bounds.x() + 10, bounds.bottom() - 15, style.muted(), false);
        else HelpModalFrame.text(graphics, font, labels.footer(), paintedRegion(bounds, appearance.layout().footer()), frame().footerColor(), false);
        if(sidebar.contains(mouseX, mouseY)){
            int idx = (mouseY - sidebar.y() + pagesScroll.offset()) / stride;
            if(idx >= 0 && idx < pages.size()) graphics.renderTooltip(font, Component.literal(pages.get(idx).title()), mouseX, mouseY);
        }
    }

    private void button(GuiGraphics graphics, Font font, HelpModalFrame.Rect rect, Component label, boolean enabled,
                               double mouseX, double mouseY, HelpModalStyle style){
        boolean hovered = enabled && rect.contains(mouseX, mouseY);
        if(appearance == null) graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), hovered ? style.border() : style.panel());
        else{
            HelpModalFrame.texture(graphics, appearance.actionButton(), rect);
            if(hovered) graphics.fill(rect.x() + 1, rect.y() + 1, rect.right() - 1, rect.bottom() - 1, 0x22FFFFFF);
            if(!enabled) graphics.fill(rect.x() + 2, rect.y() + 2, rect.right() - 2, rect.bottom() - 2, 0x55000000);
        }
        if(appearance == null) graphics.drawCenteredString(font, label, rect.x() + rect.width() / 2, rect.y() + 5, enabled ? style.text() : style.muted());
        else HelpModalFrame.text(graphics, font, label, new HelpModalFrame.Rect(rect.x() + 3, rect.y() + 2,
                Math.max(1, rect.width() - 9), Math.max(1, rect.height() - 4)), enabled ? style.text() : style.muted(), true);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            INPUT
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public boolean mouseClicked(int width, int height, double mouseX, double mouseY, int button, Runnable close){
        var bounds = HelpModalFrame.bounds(width, height, 1050, frame());
        if(button == 0){
            if(HelpModalFrame.close(bounds, frame()).contains(mouseX, mouseY)){ close.run(); return true; }
            if(refresh(bounds).contains(mouseX, mouseY) && !loadingIndex && !loadingPage){ refresh(); return true; }
            if(back(bounds).contains(mouseX, mouseY) && !history.isEmpty()){ load(history.removeLast(), false, ""); return true; }
            var sidebar = sidebar(bounds);
            if(sidebar.contains(mouseX, mouseY)){
                if(mouseX >= sidebar.right() - 7){ draggingPages = true; dragPages(sidebar, mouseY); }
                else{
                    int idx = ((int)mouseY - sidebar.y() + pagesScroll.offset()) / pageStride(sidebar);
                    if(idx >= 0 && idx < pages.size()) load(pages.get(idx), true, "");
                }
            }else view.click(body(bounds), mouseX, mouseY, this::follow);
        }
        return true;
    }

    public boolean mouseScrolled(int width, int height, double mouseX, double mouseY, double amount, boolean horizontal){
        var bounds = HelpModalFrame.bounds(width, height, 1050, frame());
        if(sidebar(bounds).contains(mouseX, mouseY)) pagesScroll.scrollBy((int)Math.round(-amount * 32));
        else if(body(bounds).contains(mouseX, mouseY)) view.scroll(amount, horizontal);
        return true;
    }

    public boolean mouseDragged(int width, int height, double mouseY){
        var bounds = HelpModalFrame.bounds(width, height, 1050, frame());
        if(draggingPages) dragPages(sidebar(bounds), mouseY);
        else view.drag(body(bounds), mouseY);
        return true;
    }

    private void dragPages(HelpModalFrame.Rect sidebar, double mouseY){
        pagesScroll.scrollToFraction((mouseY - sidebar.y() - 8) / Math.max(1, sidebar.height() - 16));
    }

    public boolean mouseReleased(){ draggingPages = false; view.release(); return true; }

    public boolean keyPressed(int key, Runnable close){
        if(key == GLFW.GLFW_KEY_ESCAPE) close.run();
        else if(key == GLFW.GLFW_KEY_F5) refresh();
        else if(key == GLFW.GLFW_KEY_BACKSPACE && !history.isEmpty()) load(history.removeLast(), false, "");
        else if(key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_PAGE_UP) view.scroll(key == GLFW.GLFW_KEY_UP ? 1 : 5, false);
        else if(key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_PAGE_DOWN) view.scroll(key == GLFW.GLFW_KEY_DOWN ? -1 : -5, false);
        else if(key == GLFW.GLFW_KEY_HOME || key == GLFW.GLFW_KEY_END) view.end(key == GLFW.GLFW_KEY_END);
        return true;
    }

    @Override
    public void close(){ closed = true; view.close(); }

    private HelpModalFrame.Rect sidebar(HelpModalFrame.Rect bounds){
        if(appearance != null) return paintedRegion(bounds, appearance.layout().pages());
        return new HelpModalFrame.Rect(bounds.x() + 10, bounds.y() + 57, Math.min(190, Math.max(60, bounds.width() / 4)), Math.max(1, bounds.height() - 83));
    }

    private HelpModalFrame.Rect body(HelpModalFrame.Rect bounds){
        if(appearance != null) return paintedRegion(bounds, appearance.layout().body());
        var sidebar = sidebar(bounds);
        return new HelpModalFrame.Rect(sidebar.right() + 12, sidebar.y(), Math.max(1, bounds.right() - sidebar.right() - 22), sidebar.height());
    }

    private HelpModalAppearance frame(){ return appearance == null ? null : appearance.frame(); }
    private int pageStride(HelpModalFrame.Rect sidebar){
        return appearance == null ? 24 : Math.max(1, Math.round((sidebar.width() - 7) * appearance.button().height() / (float)appearance.button().width())) + 3;
    }

    private HelpModalFrame.Rect paintedRegion(HelpModalFrame.Rect bounds, HelpModalAppearance.Region region){
        return HelpModalFrame.region(bounds, frame(), region);
    }

    private HelpModalFrame.Rect refresh(HelpModalFrame.Rect bounds){
        return appearance == null ? new HelpModalFrame.Rect(bounds.right() - 75, bounds.y() + 31, 65, 20)
                : paintedRegion(bounds, appearance.layout().refresh());
    }

    private HelpModalFrame.Rect back(HelpModalFrame.Rect bounds){
        return appearance == null ? new HelpModalFrame.Rect(bounds.right() - 128, bounds.y() + 31, 48, 20)
                : paintedRegion(bounds, appearance.layout().back());
    }

    public record TextureAppearance(HelpModalAppearance frame, GuiTextureRegion button, GuiTextureRegion selectedButton,
                                    GuiTextureRegion actionButton, Layout layout){
        public TextureAppearance(HelpModalAppearance frame, GuiTextureRegion button, GuiTextureRegion selectedButton, Layout layout){
            this(frame, button, selectedButton, button, layout);
        }

        public TextureAppearance{
            Objects.requireNonNull(frame, "frame");
            Objects.requireNonNull(button, "button");
            Objects.requireNonNull(selectedButton, "selectedButton");
            Objects.requireNonNull(actionButton, "actionButton");
            Objects.requireNonNull(layout, "layout");
            if(button.width() != selectedButton.width() || button.height() != selectedButton.height()){
                throw new IllegalArgumentException("Page button textures must share their dimensions");
            }
        }
    }

    public record Layout(HelpModalAppearance.Region pages, HelpModalAppearance.Region body, HelpModalAppearance.Region pagesTitle,
                          HelpModalAppearance.Region status, HelpModalAppearance.Region back, HelpModalAppearance.Region refresh,
                          HelpModalAppearance.Region footer){
        public Layout{
            for(var region : List.of(pages, body, pagesTitle, status, back, refresh, footer)) Objects.requireNonNull(region, "region");
        }
    }

    public record Labels(Component title, Component pages, Component refresh, Component back, Component loading,
                         Component failed, Component offline, Component footer){
        public static final Labels DEFAULT = new Labels(Component.literal("Documentation"), Component.literal("Pages"),
                Component.literal("Refresh"), Component.literal("Back"), Component.literal("Loading..."),
                Component.literal("Documentation unavailable. Check your connection and click Refresh to retry."),
                Component.literal("Offline: showing cached documentation"), Component.literal("Esc: close  |  F5: refresh  |  Shift+Wheel: scroll wide tables"));
    }
}
