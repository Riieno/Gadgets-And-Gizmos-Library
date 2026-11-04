package com.rieno.gadgetsandgizmos.lib.client.ui;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.client.render.GuiPoseScissor;
import com.rieno.gadgetsandgizmos.lib.documentation.MarkdownDocument;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

// Lay out the parsed document as Minecraft text, images, tables and collapsible sections.
final class MarkdownView implements AutoCloseable{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private final ListScrollState scroll = new ListScrollState();
    private final Set<MarkdownDocument.Node> collapsed = new HashSet<>();
    private final Map<String, Integer> anchors = new HashMap<>();
    private final List<Draw> draws = new ArrayList<>();
    private final Function<String, URI> asset;
    private final Function<URI, java.util.concurrent.CompletableFuture<byte[]>> loadAsset;
    private DocumentationImages images;
    private MarkdownDocument document = new MarkdownDocument(List.of());
    private Font font;
    private HelpModalStyle style;
    private int layoutWidth = -1;
    private int contentWidth;
    private int contentHeight;
    private int horizontal;
    private int y;
    private boolean dragging;

    MarkdownView(Function<String, URI> asset){
        this(asset, uri -> com.rieno.gadgetsandgizmos.lib.documentation.RemoteResources.shared()
                .fetch(uri, 8 * 1024 * 1024).thenApply(com.rieno.gadgetsandgizmos.lib.documentation.RemoteResources.Download::bytes));
    }

    MarkdownView(Function<String, URI> asset, Function<URI, java.util.concurrent.CompletableFuture<byte[]>> loadAsset){
        this.asset = asset;
        this.loadAsset = loadAsset;
        images = new DocumentationImages(() -> layoutWidth = -1, loadAsset);
    }

    void document(MarkdownDocument document){
        this.document = document;
        collapsed.clear();
        collapseDetails(document.nodes());
        images.close();
        images = new DocumentationImages(() -> layoutWidth = -1, loadAsset);
        layoutWidth = -1;
        horizontal = 0;
        scroll.scrollToFraction(0);
    }

    private void collapseDetails(List<MarkdownDocument.Node> nodes){
        for(var node : nodes){
            if(node.tag().equals("details") && !node.attributes().containsKey("open")) collapsed.add(node);
            collapseDetails(node.children());
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        RENDERING
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    void render(GuiGraphics graphics, Font font, HelpModalFrame.Rect bounds, HelpModalStyle style, int mouseX, int mouseY){
        if(layoutWidth != bounds.width() || !style.equals(this.style)){
            this.font = font;
            this.style = style;
            layoutWidth = bounds.width();
            contentWidth = Math.max(1, layoutWidth - 14);
            draws.clear();
            anchors.clear();
            y = 0;
            blocks(document.nodes(), 0, contentWidth);
            contentHeight = y + 8;
        }
        scroll.configure(contentHeight, 1, bounds.height() - 8);
        horizontal = Math.clamp(horizontal, 0, Math.max(0, contentWidth - bounds.width() + 10));
        try(var clip = GuiPoseScissor.push(graphics, bounds.x(), bounds.y(), bounds.right() - 7, bounds.bottom() - 7)){
            graphics.pose().pushPose();
            graphics.pose().translate(bounds.x() - horizontal, bounds.y() - scroll.offset(), 0);
            for(var draw : draws){
                if(draw.bottom() < scroll.offset() || draw.top() > scroll.offset() + bounds.height()) continue;
                if(draw instanceof Text text){
                    graphics.pose().pushPose();
                    graphics.pose().translate(text.x(), text.y(), 0);
                    graphics.pose().scale(text.scale(), text.scale(), 1);
                    graphics.drawString(font, text.text(), 0, 0, style.text(), false);
                    graphics.pose().popPose();
                }else if(draw instanceof Box box){
                    graphics.fill(box.x(), box.y(), box.x() + box.width(), box.bottom(), box.color());
                }else if(draw instanceof Picture picture){
                    var image = picture.image();
                    if(image.texture() != null) graphics.blit(image.texture(), picture.x(), picture.y(),
                            picture.width(), picture.height(), 0, 0, image.width(), image.height(), image.width(), image.height());
                    else{
                        graphics.fill(picture.x(), picture.y(), picture.x() + picture.width(), picture.bottom(), style.panel());
                        String label = picture.alt().isBlank() ? (image.failed() ? "Image unavailable" : "Loading image...") : picture.alt();
                        graphics.drawWordWrap(font, Component.literal(label), picture.x() + 5, picture.y() + 5, Math.max(1, picture.width() - 10), style.muted());
                    }
                }
            }
            graphics.pose().popPose();
        }
        HelpModalFrame.scrollbar(graphics, bounds, scroll, style);
        if(contentWidth > bounds.width() - 10){
            int max = Math.max(1, contentWidth - bounds.width() + 10);
            int thumb = Math.max(20, bounds.width() * bounds.width() / contentWidth);
            int x = bounds.x() + (bounds.width() - thumb) * horizontal / max;
            graphics.fill(bounds.x(), bounds.bottom() - 4, bounds.right(), bounds.bottom() - 1, style.panel());
            graphics.fill(x, bounds.bottom() - 4, x + thumb, bounds.bottom() - 1, style.accent());
        }
        if(bounds.contains(mouseX, mouseY)){
            Style hovered = linkAt(mouseX - bounds.x() + horizontal, mouseY - bounds.y() + scroll.offset());
            if(hovered != null && hovered.getClickEvent() != null) graphics.renderTooltip(font,
                    Component.literal(hovered.getClickEvent().getValue()), mouseX, mouseY);
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                          LAYOUT
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private void blocks(List<MarkdownDocument.Node> nodes, int x, int width){
        var inline = new ArrayList<MarkdownDocument.Node>();
        for(var node : nodes){
            if(Set.of("#text", "a", "strong", "b", "em", "i", "del", "s", "strike", "code", "kbd", "samp", "input", "span", "sup", "sub", "u", "ins").contains(node.tag())){
                inline.add(node);
                continue;
            }
            if(!inline.isEmpty()){
                paragraph(inline, x, width, Style.EMPTY, 1);
                inline.clear();
            }
            if(!node.attribute("id").isEmpty()) anchors.put(node.attribute("id"), y);
            switch(node.tag()){
                case "#text" -> { if(!node.text().isBlank()) paragraph(List.of(node), x, width, Style.EMPTY, 1); }
                case "p" -> { paragraph(node.children(), x, width, Style.EMPTY, 1); y += 6; }
                case "h1", "h2", "h3", "h4", "h5", "h6" -> {
                    y += 7;
                    float scale = switch(node.tag()){ case "h1" -> 1.6F; case "h2" -> 1.35F; case "h3" -> 1.15F; default -> 1; };
                    paragraph(node.children(), x, width, Style.EMPTY.withBold(true).withColor(style.accent()), scale);
                    y += 8;
                }
                case "ul", "ol" -> list(node, x, width);
                case "blockquote" -> {
                    int top = y;
                    blocks(node.children(), x + 12, Math.max(1, width - 12));
                    draws.add(new Box(x + 2, top, 2, Math.max(10, y - top), style.border()));
                    y += 6;
                }
                case "pre" -> {
                    int top = y;
                    int idx = draws.size();
                    y += 6;
                    String code = node.plainText().replaceFirst("\\n$", "");
                    for(String line : code.split("\n", -1)) text(Component.literal(line).withStyle(Style.EMPTY.withFont(ResourceLocation.withDefaultNamespace("uniform"))), x + 6, Math.max(1, width - 12), 1);
                    y += 6;
                    draws.add(idx, new Box(x, top, width, y - top, style.panel()));
                    y += 8;
                }
                case "hr" -> { y += 5; draws.add(new Box(x, y, width, 1, style.border())); y += 10; }
                case "table" -> table(node, x, width);
                case "details" -> {
                    var summary = node.children().stream().filter(child -> child.tag().equals("summary")).findFirst().orElse(null);
                    int top = y;
                    String label = (collapsed.contains(node) ? "+ " : "- ") + (summary == null ? "Details" : summary.plainText());
                    text(Component.literal(label).withStyle(Style.EMPTY.withBold(true).withColor(style.accent())), x + 5, Math.max(1, width - 10), 1);
                    draws.add(new Toggle(node, x, top, width, y - top + 4));
                    y += 5;
                    if(!collapsed.contains(node)) blocks(node.children().stream().filter(child -> !child.tag().equals("summary")).toList(), x + 6, Math.max(1, width - 6));
                    y += 5;
                }
                case "img" -> image(node, x, width);
                case "br" -> y += font.lineHeight + 2;
                case "script", "style" -> {}
                default -> blocks(node.children(), x, width);
            }
        }
        if(!inline.isEmpty()) paragraph(inline, x, width, Style.EMPTY, 1);
    }

    private void list(MarkdownDocument.Node node, int x, int width){
        int idx = 1;
        try{ if(!node.attribute("start").isEmpty()) idx = Integer.parseInt(node.attribute("start")); }
        catch(NumberFormatException ignored){}
        for(var item : node.children()){
            if(!item.tag().equals("li")) continue;
            if(!item.attribute("id").isEmpty()) anchors.put(item.attribute("id"), y);
            String bullet = node.tag().equals("ol") ? idx++ + "." : "-";
            int indent = Math.max(14, font.width(bullet) + 6);
            draws.add(new Text(FormattedCharSequence.forward(bullet, Style.EMPTY.withColor(style.muted())), x, y, 1, font.lineHeight + 2));
            blocks(item.children(), x + indent, Math.max(1, width - indent));
            y += 3;
        }
        y += 4;
    }

    private void paragraph(List<MarkdownDocument.Node> nodes, int x, int width, Style base, float scale){
        var text = Component.empty();
        for(var node : nodes){
            if(node.tag().equals("img")){
                if(!text.getString().isBlank()){ text(text, x, width, scale); text = Component.empty(); }
                image(node, x, width);
            }else text.append(inline(node, base));
        }
        if(!text.getString().isBlank()) text(text, x, width, scale);
    }

    private MutableComponent inline(MarkdownDocument.Node node, Style inherited){
        Style next = switch(node.tag()){
            case "strong", "b" -> inherited.withBold(true);
            case "em", "i" -> inherited.withItalic(true);
            case "del", "s", "strike" -> inherited.withStrikethrough(true);
            case "u", "ins" -> inherited.withUnderlined(true);
            case "code", "kbd", "samp" -> inherited.withFont(ResourceLocation.withDefaultNamespace("uniform")).withColor(style.accent());
            case "a" -> inherited.withColor(style.accent()).withUnderlined(true)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, node.attribute("href")));
            default -> inherited;
        };
        if(node.tag().equals("#text")) return Component.literal(node.text().replaceAll("\\s+", " ")).withStyle(next);
        if(node.tag().equals("br")) return Component.literal("\n").withStyle(next);
        if(node.tag().equals("img")) return Component.literal("[" + node.attribute("alt") + "]").withStyle(next);
        if(node.tag().equals("input")) return Component.literal(node.attributes().containsKey("checked") ? "[x] " : "[ ] ").withStyle(next);
        var result = Component.empty();
        for(var child : node.children()) result.append(inline(child, next));
        return result;
    }

    private void text(Component component, int x, int width, float scale){
        var lines = font.split(component, Math.max(1, (int)(width / scale)));
        if(lines.isEmpty()){ y += Math.ceil((font.lineHeight + 2) * scale); return; }
        for(var line : lines){
            int height = (int)Math.ceil((font.lineHeight + 2) * scale);
            draws.add(new Text(line, x, y, scale, height));
            y += height;
        }
    }

    private void image(MarkdownDocument.Node node, int x, int width){
        DocumentationImages.Image image;
        try{ image = images.get(asset.apply(node.attribute("src"))); }
        catch(IllegalArgumentException ex){ image = DocumentationImages.Image.FAILED; }
        int w = Math.max(1, Math.min(width, image.width()));
        int h = Math.max(16, w * image.height() / Math.max(1, image.width()));
        draws.add(new Picture(image, node.attribute("alt"), x, y, w, h));
        y += h + 8;
    }

    private void table(MarkdownDocument.Node node, int x, int width){
        var rows = new ArrayList<MarkdownDocument.Node>();
        tableRows(node, rows);
        int columns = rows.stream().mapToInt(row -> (int)row.children().stream().filter(cell -> cell.tag().equals("td") || cell.tag().equals("th")).count()).max().orElse(0);
        if(columns == 0) return;
        int colWidth = Math.max(64, width / columns);
        contentWidth = Math.max(contentWidth, x + colWidth * columns);
        for(var row : rows){
            int top = y;
            int bottom = top + font.lineHeight + 10;
            int idx = 0;
            int insert = draws.size();
            for(var cell : row.children()){
                if(!cell.tag().equals("td") && !cell.tag().equals("th")) continue;
                y = top + 5;
                paragraph(cell.children(), x + idx * colWidth + 5, colWidth - 10, Style.EMPTY.withBold(cell.tag().equals("th")), 1);
                bottom = Math.max(bottom, y + 5);
                idx++;
            }
            draws.add(insert, new Box(x, top, colWidth * columns, bottom - top, row.children().stream().anyMatch(cell -> cell.tag().equals("th")) ? style.border() : style.panel()));
            for(int col = 0; col <= columns; col++) draws.add(new Box(x + col * colWidth, top, 1, bottom - top, style.border()));
            draws.add(new Box(x, bottom, colWidth * columns, 1, style.border()));
            y = bottom + 1;
        }
        y += 8;
    }

    private static void tableRows(MarkdownDocument.Node node, List<MarkdownDocument.Node> rows){
        if(node.tag().equals("tr")) rows.add(node);
        else for(var child : node.children()) tableRows(child, rows);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            INPUT
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    void click(HelpModalFrame.Rect bounds, double mouseX, double mouseY, Consumer<String> link){
        if(!bounds.contains(mouseX, mouseY)) return;
        if(mouseX >= bounds.right() - 7){ dragging = true; drag(bounds, mouseY); return; }
        if(mouseY >= bounds.bottom() - 7 && contentWidth > bounds.width()){
            horizontal = (int)Math.round((mouseX - bounds.x()) / bounds.width() * Math.max(0, contentWidth - bounds.width() + 10));
            return;
        }
        double x = mouseX - bounds.x() + horizontal;
        double y = mouseY - bounds.y() + scroll.offset();
        for(var draw : draws){
            if(draw instanceof Toggle toggle && x >= toggle.x() && x < toggle.x() + toggle.width() && y >= toggle.y() && y < toggle.bottom()){
                if(!collapsed.remove(toggle.node())) collapsed.add(toggle.node());
                layoutWidth = -1;
                return;
            }
        }
        Style style = linkAt(x, y);
        if(style != null && style.getClickEvent() != null) link.accept(style.getClickEvent().getValue());
    }

    private Style linkAt(double x, double y){
        if(font == null) return null;
        for(var draw : draws){
            if(draw instanceof Text text && x >= text.x() && x < text.x() + font.width(text.text()) * text.scale()
                    && y >= text.y() && y < text.bottom()){
                return font.getSplitter().componentStyleAtWidth(text.text(), (int)((x - text.x()) / text.scale()));
            }
        }
        return null;
    }

    void drag(HelpModalFrame.Rect bounds, double mouseY){
        if(dragging) scroll.scrollToFraction((mouseY - bounds.y() - 8) / Math.max(1, bounds.height() - 16));
    }

    void release(){ dragging = false; }
    void scroll(double amount, boolean horizontal){
        if(horizontal) this.horizontal = Math.clamp(this.horizontal - (int)Math.round(amount * 32), 0, Math.max(0, contentWidth - layoutWidth + 10));
        else scroll.scrollBy((int)Math.round(-amount * 32));
    }

    void jump(String anchor){
        String decoded = java.net.URLDecoder.decode(anchor.replace("+", "%2B"), java.nio.charset.StandardCharsets.UTF_8);
        Integer target = anchors.get(decoded);
        if(target == null) target = anchors.get("user-content-" + decoded);
        if(target != null) scroll.scrollBy(target - scroll.offset());
    }

    void end(boolean end){ scroll.scrollToFraction(end ? 1 : 0); }

    @Override
    public void close(){ images.close(); }

    private sealed interface Draw{
        int top();
        int bottom();
    }
    private record Text(FormattedCharSequence text, int x, int y, float scale, int height) implements Draw{
        public int top(){ return y; }
        public int bottom(){ return y + height; }
    }
    private record Box(int x, int y, int width, int height, int color) implements Draw{
        public int top(){ return y; }
        public int bottom(){ return y + height; }
    }
    private record Picture(DocumentationImages.Image image, String alt, int x, int y, int width, int height) implements Draw{
        public int top(){ return y; }
        public int bottom(){ return y + height; }
    }
    private record Toggle(MarkdownDocument.Node node, int x, int y, int width, int height) implements Draw{
        public int top(){ return y; }
        public int bottom(){ return y + height; }
    }
}
