package com.rieno.gadgetsandgizmos.lib.client.ui;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.client.render.GuiPoseScissor;
import com.rieno.gadgetsandgizmos.lib.documentation.MarkdownDocument;
import com.rieno.gadgetsandgizmos.lib.graph.render.GraphCalloutLayout;
import com.rieno.gadgetsandgizmos.lib.graph.render.GraphViewport;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

// Anchor readable callouts above graph nodes without changing their saved positions or contents
public final class GraphCalloutBubbles{
    /*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        DEFAULTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    private final Component closeLabel;
    // Retain plain text and current screen bounds separately from graph state
    private List<Text> entries = List.of();
    private List<Placed> placed = List.of();
    private Rect viewport;
    // Use the host graph transform for both rendering and pointer input
    private GraphViewport graphViewport;
    private Rect menu;
    private int menuWidth;

    // Let the host translate the shared close-bubbles action
    public GraphCalloutBubbles(Component closeLabel){
        this.closeLabel = Objects.requireNonNull(closeLabel);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        FUNCTIONS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    // Replace a snapshot of annotations while treating configured Markdown as local text
    public void setEntries(List<Entry> entries){
        this.entries = entries.stream().map(entry -> new Text(entry.nodeId(), Component.literal(
                MarkdownDocument.parse(entry.markdown()).nodes().stream().map(MarkdownDocument.Node::plainText)
                        .collect(java.util.stream.Collectors.joining("\n"))))).toList();
        placed = List.of();
        menu = null;
    }

    // Remove the annotations and their context menu together
    public void clear(){ entries = List.of(); placed = List.of(); menu = null; }

    // Check whether this layer still owns any annotations
    public boolean isEmpty(){ return entries.isEmpty(); }

    // Preserve screen-coordinate callers at unit zoom
    public void render(GuiGraphics graphics, Font font, Rect viewport, Function<String, Anchor> anchors,
            HelpModalStyle style, int mouseX, int mouseY){
        render(graphics, font, viewport, new GraphViewport(0, 0, 0, 0, 1), id -> {
            Anchor anchor = anchors.apply(id);
            return anchor == null ? null : new GraphAnchor(anchor.x(), anchor.y(), anchor.width());
        }, style, mouseX, mouseY);
    }

    // Draw authored callouts through the same pan and zoom transform as their graph nodes
    public void render(GuiGraphics graphics, Font font, Rect viewport, GraphViewport graphViewport,
            Function<String, GraphAnchor> anchors, HelpModalStyle style, int mouseX, int mouseY){
        this.viewport = Objects.requireNonNull(viewport);
        if(!Objects.equals(this.graphViewport, graphViewport)) menu = null;
        this.graphViewport = Objects.requireNonNull(graphViewport);
        if(viewport.width() < 24 || viewport.height() < 24){ placed = List.of(); menu = null; return; }
        menuWidth = Math.min(viewport.width(), font.width(closeLabel) + 20);
        List<Placed> next = new ArrayList<>();
        int width = 250;
        for(Text entry : entries){
            GraphAnchor anchor = anchors.apply(entry.nodeId());
            if(anchor == null) continue;
            var lines = font.split(entry.text(), width - 16);
            int height = lines.size() * (font.lineHeight + 2) + 12;
            var bounds = GraphCalloutLayout.above(anchor.x(), anchor.y(), anchor.width(), width, height);
            if(bounds.intersects(graphViewport, viewport.x(), viewport.y(), viewport.right(), viewport.bottom()))
                next.add(new Placed(bounds, lines));
        }
        placed = List.copyOf(next);
        try(var clip = GuiPoseScissor.push(graphics, viewport.x(), viewport.y(), viewport.right(), viewport.bottom())){
            for(Placed bubble : placed){
                var bounds = bubble.bounds();
                graphics.pose().pushPose();
                try{
                    graphics.pose().translate(graphViewport.screenX(bounds.x()), graphViewport.screenY(bounds.y()), 0);
                    graphics.pose().scale((float)graphViewport.zoom(), (float)graphViewport.zoom(), 1);
                    graphics.vLine(bounds.width() / 2, bounds.height(), bounds.height() + 8, style.accent());
                    graphics.fill(2, 2, bounds.width() + 2, bounds.height() + 2, 0x66000000);
                    graphics.fill(0, 0, bounds.width(), bounds.height(), style.background());
                    graphics.renderOutline(0, 0, bounds.width(), bounds.height(),
                            bounds.contains(graphViewport, mouseX, mouseY) ? style.accent() : style.border());
                    for(int idx = 0; idx < bubble.lines().size(); idx++){
                        graphics.drawString(font, bubble.lines().get(idx), 8,
                                6 + idx * (font.lineHeight + 2), style.text(), false);
                    }
                }finally{ graphics.pose().popPose(); }
            }
            if(menu != null){
                graphics.fill(menu.x(), menu.y(), menu.right(), menu.bottom(),
                        menu.contains(mouseX, mouseY) ? style.border() : style.panel());
                graphics.renderOutline(menu.x(), menu.y(), menu.width(), menu.height(), style.accent());
                graphics.drawString(font, closeLabel, menu.x() + 10, menu.y() + 6, style.text(), false);
            }
        }
    }

    // Open a close action on right-click and consume bubble clicks before the graph sees them
    public boolean mouseClicked(double x, double y, int button){
        if(entries.isEmpty() || viewport == null || !viewport.contains(x, y)) return false;
        if(menu != null){
            if(button == GLFW.GLFW_MOUSE_BUTTON_LEFT){
                if(menu.contains(x, y)) clear();
                else menu = null;
                return true;
            }
            menu = null;
        }
        for(int idx = placed.size() - 1; idx >= 0; idx--){
            if(!placed.get(idx).bounds().contains(graphViewport, x, y)) continue;
            if(button == GLFW.GLFW_MOUSE_BUTTON_RIGHT){
                menu = new Rect(Math.clamp((int)x, viewport.x(), viewport.right() - menuWidth),
                        Math.clamp((int)y, viewport.y(), Math.max(viewport.y(), viewport.bottom() - 22)), menuWidth, 22);
            }
            return true;
        }
        return false;
    }

    // Associate a local explanation with a stable node identity
    public record Entry(String nodeId, String markdown){
        public Entry{ Objects.requireNonNull(nodeId); Objects.requireNonNull(markdown); }
    }

    // Supply the current screen position of the top edge of a graph node
    public record Anchor(int x, int y, int width){}

    // Supply the node's authored coordinates and width before applying pan or zoom
    public record GraphAnchor(double x, double y, double width){}

    // Share one viewport rectangle between rendering and hit testing
    public record Rect(int x, int y, int width, int height){
        public int right(){ return x + width; }
        public int bottom(){ return y + height; }
        public boolean contains(double x, double y){ return x >= this.x && x < right() && y >= this.y && y < bottom(); }
    }

    private record Text(String nodeId, Component text){}
    private record Placed(GraphCalloutLayout bounds, List<FormattedCharSequence> lines){}
}
