package com.rieno.gadgetsandgizmos.lib.client.scratch;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.graph.GraphModel;
import com.rieno.gadgetsandgizmos.lib.scratch.ScratchBlockDefinition;
import com.rieno.gadgetsandgizmos.lib.scratch.ScratchBlockRegistry;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

// Render a graph's nodes as Scratch-style blocks. Hosts own visibility, graph mutation and persistence.
public final class ScratchBlockSurface {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final int BLOCK_HEIGHT = 32;
    // An empty C block still reserves a complete statement-sized cavity, so
    // it is both visibly Scratch-like and an easy drop target for its first
    // nested block.
    public static final int C_BLOCK_HEIGHT = 78;
    public static final int BLOCK_GAP = 0;
    public static final int BLOCK_INDENT = 12;
    /** A compact logical width. Hosts may freely place several scripts on one canvas. */
    public static final int DEFAULT_BLOCK_WIDTH = 184;
    private static final int DEFAULT_CANVAS_COLOUR = 0xFF10141C;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Prevent construction
    private ScratchBlockSurface() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Lay out blocks in their supplied graph order
    public static Layout layout(Collection<? extends GraphModel.Node> blocks,
                                int left, int top, int width) {
        return layout(blocks, null, left, top, width);
    }

    // Lay out a hierarchy-aware Scratch script. A parent with children is
    // automatically shaped as a C block and its children are nested in the
    // cavity. This remains generic: callers provide only the parent id while
    // retaining their existing graph document and node contracts.
    public static Layout layout(Collection<? extends GraphModel.Node> blocks,
                                ScratchBlockRegistry registry,
                                int left, int top, int width,
                                Function<? super GraphModel.Node, String> parentId) {
        if (parentId == null) return layout(blocks, registry, left, top, width);
        List<GraphModel.Node> ordered = orderedNodes(blocks);
        Map<String, List<GraphModel.Node>> children = new LinkedHashMap<>();
        Map<String, GraphModel.Node> nodesById = new LinkedHashMap<>();
        for (GraphModel.Node node : ordered) {
            nodesById.put(node.id(), node);
        }
        for (GraphModel.Node node : ordered) {
            String parent = parentId.apply(node);
            if (parent != null && !parent.isBlank() && nodesById.containsKey(parent)) {
                children.computeIfAbsent(parent, ignored -> new ArrayList<>()).add(node);
            }
        }
        List<BlockBounds> bounds = new ArrayList<>();
        int y = top;
        for (GraphModel.Node node : ordered) {
            String parent = parentId.apply(node);
            if (parent != null && !parent.isBlank() && nodesById.containsKey(parent)) continue;
            y += layoutBranch(node, children, registry, left + BLOCK_INDENT, y,
                    Math.max(40, width - BLOCK_INDENT * 2), bounds) + BLOCK_GAP;
        }
        return new Layout(List.copyOf(bounds), Math.max(0, y - top));
    }

    // Lay out a palette-aware Scratch stack. Condition-category blocks are
    // visually nested beneath their owning statement without changing graph
    // identity or requiring a different graph model.
    private static Layout layout(Collection<? extends GraphModel.Node> blocks,
                                 ScratchBlockRegistry registry,
                                 int left, int top, int width) {
        List<BlockBounds> bounds = new ArrayList<>();
        int y = top;
        if (blocks != null) {
            for (GraphModel.Node block : blocks) {
                if (block == null) continue;
                ScratchBlockDefinition definition = registry == null ? null : registry.get(block.type());
                int indent = definition != null && "conditions".equals(definition.category())
                        ? BLOCK_INDENT * 3 : BLOCK_INDENT;
                bounds.add(new BlockBounds(block.id(), left + indent, y,
                        Math.max(40, width - indent - BLOCK_INDENT), blockHeight(definition)));
                y += blockHeight(definition) + BLOCK_GAP;
            }
        }
        return new Layout(List.copyOf(bounds), Math.max(0, y - top));
    }

    // Draw the styled block stack. This is intentionally presentation-only so hosts can apply their own graph mutations.
    public static Layout render(GuiGraphics graphics, Font font, ScratchBlockRegistry registry,
                                Collection<? extends GraphModel.Node> blocks,
                                int left, int top, int width, int height,
                                String selectedBlockId) {
        return render(graphics, font, registry, blocks, left, top, width, height,
                selectedBlockId, DEFAULT_CANVAS_COLOUR);
    }

    // Draw the styled block stack against the host canvas colour. Passing the
    // canvas through is what lets the connector notches be real cut-outs,
    // rather than decorative rectangles painted over a different UI surface.
    public static Layout render(GuiGraphics graphics, Font font, ScratchBlockRegistry registry,
                                Collection<? extends GraphModel.Node> blocks,
                                int left, int top, int width, int height,
                                String selectedBlockId, int canvasColour) {
        return render(graphics, font, registry, blocks, left, top, width, height,
                selectedBlockId, canvasColour, null);
    }

    // Render one hierarchy-aware script. The optional parent resolver gives
    // hosts true nested C blocks without forcing a different graph model.
    public static Layout render(GuiGraphics graphics, Font font, ScratchBlockRegistry registry,
                                Collection<? extends GraphModel.Node> blocks,
                                int left, int top, int width, int height,
                                String selectedBlockId, int canvasColour,
                                Function<? super GraphModel.Node, String> parentId) {
        Layout layout = parentId == null ? layout(blocks, registry, left, top, width)
                : layout(blocks, registry, left, top, width, parentId);
        if (graphics == null || font == null || registry == null || blocks == null || width <= 0 || height <= 0) {
            return layout;
        }
        Map<String, GraphModel.Node> nodesById = new LinkedHashMap<>();
        for (GraphModel.Node node : orderedNodes(blocks)) nodesById.put(node.id(), node);
        for (BlockBounds bounds : layout.blocks()) {
            if (bounds.y() + bounds.height() <= top || bounds.y() >= top + height) continue;
            GraphModel.Node node = nodesById.get(bounds.id());
            if (node == null) continue;
            ScratchBlockDefinition definition = registry.get(node.type());
            int colour = definition == null ? 0xFF5B6B7A : definition.colour();
            drawBlock(graphics, font, bounds, definition, node.type(), bounds.id().equals(selectedBlockId),
                    colour, canvasColour, "");
        }
        return layout;
    }

    /**
     * Render independently placed blocks from an existing graph document. This
     * is deliberately an alternate presentation of the same {@link GraphModel}
     * node contract: the host supplies positions, viewport state and optional
     * parent links instead of converting its graph into a second Scratch-only
     * model. It is therefore suitable for editors which need panning, zooming,
     * multiple scripts and ordinary graph persistence.
     */
    public static Layout renderFreeform(GuiGraphics graphics, Font font, ScratchBlockRegistry registry,
                                        Collection<? extends GraphModel.Node> blocks,
                                        int left, int top, int width, int height,
                                        Function<? super GraphModel.Node, Position> position,
                                        double panX, double panY, double zoom,
                                        String selectedBlockId, int canvasColour,
                                        Function<? super GraphModel.Node, String> parentId,
                                        Function<? super GraphModel.Node, String> detail) {
        return renderFreeform(graphics, font, registry, blocks, left, top, width, height,
                position, panX, panY, zoom,
                selectedBlockId == null || selectedBlockId.isBlank() ? Set.of() : Set.of(selectedBlockId),
                canvasColour, parentId, detail);
    }

    /**
     * Render independently placed Scratch blocks with the complete host
     * selection set. Keeping selection as an argument avoids storing editor
     * state in this reusable presentation API while allowing marquee and
     * multi-block editing hosts to display every selected block correctly.
     */
    public static Layout renderFreeform(GuiGraphics graphics, Font font, ScratchBlockRegistry registry,
                                        Collection<? extends GraphModel.Node> blocks,
                                        int left, int top, int width, int height,
                                        Function<? super GraphModel.Node, Position> position,
                                        double panX, double panY, double zoom,
                                        Set<String> selectedBlockIds, int canvasColour,
                                        Function<? super GraphModel.Node, String> parentId,
                                        Function<? super GraphModel.Node, String> detail) {
        Layout layout = layoutFreeform(blocks, registry, left, top, position,
                panX, panY, zoom, parentId);
        if (graphics == null || font == null || registry == null || blocks == null || width <= 0 || height <= 0) {
            return layout;
        }
        Map<String, GraphModel.Node> nodesById = new LinkedHashMap<>();
        for (GraphModel.Node node : orderedNodes(blocks)) nodesById.put(node.id(), node);
        for (BlockBounds bounds : layout.blocks()) {
            if (bounds.y() + bounds.height() <= top || bounds.y() >= top + height
                    || bounds.x() + bounds.width() <= left || bounds.x() >= left + width) continue;
            GraphModel.Node node = nodesById.get(bounds.id());
            if (node == null) continue;
            ScratchBlockDefinition definition = registry.get(node.type());
            int colour = definition == null ? 0xFF5B6B7A : definition.colour();
            drawBlock(graphics, font, bounds, definition, node.type(),
                    selectedBlockIds != null && selectedBlockIds.contains(bounds.id()), colour, canvasColour,
                    detail == null ? "" : detail.apply(node));
        }
        // Each lower tab is painted after the receiving block. The receiver's
        // top socket deliberately cuts to the canvas colour, so drawing the
        // preceding tab here lets a snapped chain physically fill that socket
        // instead of leaving a dark 7px gap between otherwise touching blocks.
        drawFreeformConnections(graphics, registry, nodesById, layout.blocks(), canvasColour);
        return layout;
    }

    private static void drawFreeformConnections(GuiGraphics graphics, ScratchBlockRegistry registry,
                                                Map<String, GraphModel.Node> nodesById,
                                                List<BlockBounds> bounds, int canvasColour) {
        if (graphics == null || registry == null || nodesById == null || bounds == null) return;
        for (BlockBounds upper : bounds) {
            GraphModel.Node upperNode = nodesById.get(upper.id());
            if (upperNode == null) continue;
            for (BlockBounds lower : bounds) {
                if (upper == lower) continue;
                if (Math.abs(lower.x() - upper.x()) > 3 || Math.abs(lower.y() - (upper.y() + upper.height())) > 2) {
                    continue;
                }
                ScratchBlockDefinition definition = registry.get(upperNode.type());
                int colour = definition == null ? 0xFF5B6B7A : definition.colour();
                drawLowerTab(graphics, upper, colour);
                break;
            }
        }
    }

    private static void drawLowerTab(GuiGraphics graphics, BlockBounds bounds, int colour) {
        int bottom = bounds.y() + bounds.height();
        int x = bounds.x();
        graphics.fill(x + 15, bottom - 3, x + 29, bottom + 4, colour);
        graphics.fill(x + 12, bottom - 1, x + 32, bottom + 2, colour);
    }

    /** Lay out independently positioned Scratch blocks in screen coordinates. */
    public static Layout layoutFreeform(Collection<? extends GraphModel.Node> blocks,
                                        ScratchBlockRegistry registry,
                                        int left, int top,
                                        Function<? super GraphModel.Node, Position> position,
                                        double panX, double panY, double zoom,
                                        Function<? super GraphModel.Node, String> parentId) {
        double safeZoom = Math.max(0.20D, Math.min(2.50D, zoom));
        List<GraphModel.Node> ordered = orderedNodes(blocks);
        Map<String, GraphModel.Node> nodesById = new LinkedHashMap<>();
        Map<String, List<GraphModel.Node>> children = new LinkedHashMap<>();
        Map<String, BlockBounds> byId = new LinkedHashMap<>();
        for (GraphModel.Node node : ordered) nodesById.put(node.id(), node);
        for (GraphModel.Node node : ordered) {
            ScratchBlockDefinition definition = registry == null ? null : registry.get(node.type());
            Position point = position == null ? Position.ORIGIN : position.apply(node);
            if (point == null) point = Position.ORIGIN;
            int x = left + (int) Math.round((point.x() + panX) * safeZoom);
            int y = top + (int) Math.round((point.y() + panY) * safeZoom);
            int blockWidth = Math.max(64, (int) Math.round(DEFAULT_BLOCK_WIDTH * safeZoom));
            int blockHeight = Math.max(18, (int) Math.round(blockHeight(definition) * safeZoom));
            boolean cBlock = isCBlock(definition);
            byId.put(node.id(), new BlockBounds(node.id(), x, y, blockWidth, blockHeight, cBlock));
            String parent = parentId == null ? "" : parentId.apply(node);
            if (parent != null && !parent.isBlank() && nodesById.containsKey(parent)) {
                children.computeIfAbsent(parent, ignored -> new ArrayList<>()).add(node);
            }
        }
        // Parent links define a real Scratch script hierarchy, not merely a
        // container-sizing hint. Canonically place each child in its owner's
        // channel before measuring C-block bounds. Persisted freeform child
        // coordinates can be stale after a drag or an older save; using them
        // here previously made an otherwise small C block grow a huge empty
        // cavity between its header and nested statements.
        for (GraphModel.Node node : ordered) {
            positionFreeformChildren(node.id(), children, byId, safeZoom, new LinkedHashSet<>());
        }
        for (GraphModel.Node node : ordered) {
            expandFreeformContainer(node.id(), children, byId, safeZoom, new LinkedHashSet<>());
        }
        List<BlockBounds> bounds = new ArrayList<>();
        appendFreeformBounds(ordered, children, byId, bounds, new LinkedHashSet<>());
        return new Layout(List.copyOf(bounds), 0);
    }

    // Place direct children in the parent C channel, preserving their graph
    // order. A parent can itself be a child, so recurse before its enclosing
    // branch is measured.
    private static void positionFreeformChildren(String id,
                                                 Map<String, List<GraphModel.Node>> children,
                                                 Map<String, BlockBounds> byId,
                                                 double zoom, Set<String> visiting) {
        BlockBounds parent = byId.get(id);
        if (parent == null || !visiting.add(id)) return;
        int childX = parent.x() + (int) Math.round(23 * zoom);
        int nextY = parent.y() + (int) Math.round(29 * zoom);
        for (GraphModel.Node child : children.getOrDefault(id, List.of())) {
            BlockBounds original = byId.get(child.id());
            if (original == null) continue;
            BlockBounds positioned = new BlockBounds(original.id(), childX, nextY,
                    original.width(), original.height(), original.cBlock());
            byId.put(child.id(), positioned);
            positionFreeformChildren(child.id(), children, byId, zoom, visiting);
            // A nested C block must contribute its full expanded height before
            // the next sibling is seated. Using its old one-row height here
            // was the source of overlapping lower blocks and the apparent
            // "random" second snap in nested scripts.
            BlockBounds placed = expandFreeformContainer(child.id(), children, byId, zoom,
                    new LinkedHashSet<>());
            if (placed != null) nextY = placed.y() + placed.height();
        }
        visiting.remove(id);
    }

    // Expand a positioned C block enough to visibly contain its direct children.
    private static BlockBounds expandFreeformContainer(String id,
                                                       Map<String, List<GraphModel.Node>> children,
                                                       Map<String, BlockBounds> byId,
                                                       double zoom, Set<String> visiting) {
        BlockBounds original = byId.get(id);
        if (original == null || !visiting.add(id)) return original;
        List<GraphModel.Node> childNodes = children.getOrDefault(id, List.of());
        int right = original.x() + original.width();
        int bottom = original.y() + original.height();
        for (GraphModel.Node child : childNodes) {
            BlockBounds childBounds = expandFreeformContainer(child.id(), children, byId, zoom, visiting);
            if (childBounds == null) continue;
            right = Math.max(right, childBounds.x() + childBounds.width() + (int) Math.round(14 * zoom));
            bottom = Math.max(bottom, childBounds.y() + childBounds.height() + (int) Math.round(17 * zoom));
        }
        visiting.remove(id);
        if (!childNodes.isEmpty() || original.cBlock()) {
            int minimumHeight = Math.max(original.height(), (int) Math.round(C_BLOCK_HEIGHT * zoom));
            BlockBounds expanded = new BlockBounds(original.id(), original.x(), original.y(),
                    Math.max(original.width(), right - original.x()),
                    Math.max(minimumHeight, bottom - original.y()), true);
            byId.put(id, expanded);
            return expanded;
        }
        return original;
    }

    // Emit each root and its children in paint order without changing graph order semantics.
    private static void appendFreeformBounds(List<GraphModel.Node> ordered,
                                             Map<String, List<GraphModel.Node>> children,
                                             Map<String, BlockBounds> byId,
                                             List<BlockBounds> output, Set<String> visited) {
        Set<String> childIds = new java.util.HashSet<>();
        for (List<GraphModel.Node> childNodes : children.values()) {
            for (GraphModel.Node child : childNodes) childIds.add(child.id());
        }
        for (GraphModel.Node node : ordered) {
            if (!childIds.contains(node.id())) appendFreeformBranch(node, children, byId, output, visited);
        }
        for (GraphModel.Node node : ordered) appendFreeformBranch(node, children, byId, output, visited);
    }

    private static void appendFreeformBranch(GraphModel.Node node,
                                             Map<String, List<GraphModel.Node>> children,
                                             Map<String, BlockBounds> byId,
                                             List<BlockBounds> output, Set<String> visited) {
        if (node == null || !visited.add(node.id())) return;
        BlockBounds bounds = byId.get(node.id());
        if (bounds != null) output.add(bounds);
        for (GraphModel.Node child : children.getOrDefault(node.id(), List.of())) {
            appendFreeformBranch(child, children, byId, output, visited);
        }
    }

    // Retain graph order when materialising hierarchy lookups.
    private static List<GraphModel.Node> orderedNodes(Collection<? extends GraphModel.Node> blocks) {
        List<GraphModel.Node> ordered = new ArrayList<>();
        if (blocks == null) return ordered;
        for (GraphModel.Node block : blocks) {
            if (block != null) ordered.add(block);
        }
        return ordered;
    }

    // Lay out one root or nested branch and return the vertical space it owns.
    private static int layoutBranch(GraphModel.Node node, Map<String, List<GraphModel.Node>> children,
                                    ScratchBlockRegistry registry, int x, int y, int width,
                                    List<BlockBounds> bounds) {
        int branchStart = bounds.size();
        List<GraphModel.Node> childNodes = children.getOrDefault(node.id(), List.of());
        ScratchBlockDefinition definition = registry == null ? null : registry.get(node.type());
        boolean cBlock = !childNodes.isEmpty()
                || definition != null && "c".equals(definition.fields().get("shape"));
        if (!cBlock) {
            int height = blockHeight(definition);
            bounds.add(new BlockBounds(node.id(), x, y, width, height, false));
            return height;
        }
        int contentHeight = 0;
        int childY = y + 25;
        for (GraphModel.Node child : childNodes) {
            int childHeight = layoutBranch(child, children, registry, x + BLOCK_INDENT * 2,
                    childY + contentHeight, Math.max(36, width - BLOCK_INDENT * 2 - 2), bounds);
            contentHeight += childHeight + BLOCK_GAP;
        }
        int height = Math.max(C_BLOCK_HEIGHT, 25 + contentHeight + 15);
        bounds.add(new BlockBounds(node.id(), x, y, width, height, true));
        // Parent needs to render behind its contents. It was appended after
        // them, so move it to the start of this branch's bounds.
        BlockBounds parent = bounds.removeLast();
        bounds.add(branchStart, parent);
        return height;
    }

    // Get the height associated with a registered Scratch block shape.
    private static int blockHeight(ScratchBlockDefinition definition) {
        if (definition == null) return BLOCK_HEIGHT;
        String shape = definition.fields().get("shape");
        if ("c".equals(shape)) return C_BLOCK_HEIGHT;
        return "hat".equals(shape) ? BLOCK_HEIGHT + 5 : BLOCK_HEIGHT;
    }

    private static boolean isCBlock(ScratchBlockDefinition definition) {
        return definition != null && "c".equals(definition.fields().get("shape"));
    }

    // Draw one block shape and its compact inline configuration value.
    private static void drawBlock(GuiGraphics graphics, Font font, BlockBounds bounds,
                                  ScratchBlockDefinition definition, String fallbackTitle,
                                  boolean selected, int colour, int canvasColour, String detail) {
        int border = selected ? 0xFFFFFFFF : darken(colour);
        String shape = definition == null ? "stack" : definition.fields().getOrDefault("shape", "stack");
        boolean cBlock = bounds.cBlock() || "c".equals(shape);
        if (cBlock) {
            drawCBlock(graphics, bounds, border, canvasColour);
            drawCBlock(graphics, inset(bounds, 1), colour, canvasColour);
        } else if ("hat".equals(shape)) {
            drawHatBlock(graphics, bounds, border, canvasColour);
            drawHatBlock(graphics, inset(bounds, 1), colour, canvasColour);
        } else if ("cap".equals(shape)) {
            drawCapBlock(graphics, bounds, border, canvasColour);
            drawCapBlock(graphics, inset(bounds, 1), colour, canvasColour);
        } else {
            drawStackBlock(graphics, bounds, border, canvasColour);
            drawStackBlock(graphics, inset(bounds, 1), colour, canvasColour);
        }
        String title = definition == null ? fallbackTitle : definition.title();
        int textY = bounds.y() + Math.max(6, Math.min(11, bounds.height() / 3));
        graphics.drawString(font, trim(font, title, Math.max(28, bounds.width() - 84)),
                bounds.x() + 12, textY, 0xFFFFFFFF, false);
        // A null detail means the host has no editable primary value (or is
        // drawing its own inline editor). Empty text remains a real editable
        // value and therefore still renders the pale Scratch input pill.
        if (detail != null) {
            int inputWidth = Math.min(76, Math.max(34, bounds.width() - 98));
            int inputX = bounds.x() + bounds.width() - inputWidth - 9;
            drawInputPill(graphics, font, inputX, textY - 4, inputWidth, 15, canvasColour, detail);
        }
        if (cBlock) {
            graphics.drawString(font, "\u21b6", bounds.x() + bounds.width() - 21,
                    bounds.y() + bounds.height() - 17, 0xFFFFFFFF, false);
        }
    }

    // Inset a rendered silhouette while retaining a usable minimum size.
    private static BlockBounds inset(BlockBounds bounds, int amount) {
        return new BlockBounds(bounds.id(), bounds.x() + amount, bounds.y() + amount,
                Math.max(28, bounds.width() - amount * 2),
                Math.max(22, bounds.height() - amount * 2));
    }

    // Draw a stack block with the interlocking notch on top and tab below.
    private static void drawStackBlock(GuiGraphics graphics, BlockBounds bounds,
                                       int colour, int canvasColour) {
        int x = bounds.x();
        int y = bounds.y();
        int right = x + bounds.width();
        int bottom = y + bounds.height();
        // A statement is a real interlocking silhouette: a narrow shoulder
        // around the top slot, a wide body, then the matching lower tab.
        // Keeping the notch and tab at fixed logical positions makes adjacent
        // blocks meet cleanly at every supported zoom level.
        graphics.fill(x + 5, y, right - 5, bottom, colour);
        graphics.fill(x + 2, y + 5, right - 2, bottom - 3, colour);
        graphics.fill(x, y + 8, right, bottom - 6, colour);
        graphics.fill(x + 15, y, x + 29, y + 4, canvasColour);
        graphics.fill(x + 12, y + 3, x + 32, y + 7, canvasColour);
        graphics.fill(x + 15, bottom - 3, x + 29, bottom + 4, colour);
        graphics.fill(x + 12, bottom - 1, x + 32, bottom + 2, colour);
    }

    // Draw the rounded cap used by Scratch event blocks. It has no top notch,
    // so it visibly begins an independent script group.
    private static void drawHatBlock(GuiGraphics graphics, BlockBounds bounds,
                                     int colour, int canvasColour) {
        int x = bounds.x();
        int y = bounds.y();
        int right = x + bounds.width();
        int bottom = y + bounds.height();
        graphics.fill(x + 15, y, right - 15, y + 4, colour);
        graphics.fill(x + 8, y + 3, right - 8, y + 8, colour);
        graphics.fill(x + 4, y + 7, right - 4, bottom, colour);
        graphics.fill(x, y + 12, right, bottom - 4, colour);
        graphics.fill(x + 13, bottom - 2, x + 30, bottom + 4, colour);
    }

    // Draw the rounded bottom cap used by terminal blocks.
    private static void drawCapBlock(GuiGraphics graphics, BlockBounds bounds,
                                     int colour, int canvasColour) {
        int x = bounds.x();
        int y = bounds.y();
        int right = x + bounds.width();
        int bottom = y + bounds.height();
        graphics.fill(x + 5, y, right - 5, bottom - 4, colour);
        graphics.fill(x + 2, y + 5, right - 2, bottom - 7, colour);
        graphics.fill(x, y + 8, right, bottom - 10, colour);
        graphics.fill(x + 5, bottom - 7, right - 5, bottom - 3, colour);
        graphics.fill(x + 10, bottom - 4, right - 10, bottom, colour);
        graphics.fill(x + 15, y, x + 29, y + 4, canvasColour);
        graphics.fill(x + 12, y + 3, x + 32, y + 7, canvasColour);
    }

    // Draw a true C-shaped control block: a cap, left spine and closing foot
    // surround an empty nested script cavity. The host's graph remains flat;
    // this is solely an alternate visual grammar for the same graph contract.
    private static void drawCBlock(GuiGraphics graphics, BlockBounds bounds,
                                   int colour, int canvasColour) {
        int x = bounds.x();
        int y = bounds.y();
        int right = x + bounds.width();
        int bottom = y + bounds.height();
        int capBottom = Math.min(bottom - 15, y + 27);
        int footTop = Math.max(capBottom + 10, bottom - 17);
        drawStackBlock(graphics, new BlockBounds(bounds.id(), x, y, bounds.width(), capBottom - y + 2),
                colour, canvasColour);
        // The left spine and closing statement give the block a genuine
        // Scratch C cavity. Child blocks render after this parent and remain
        // completely selectable inside the uninterrupted cut-out.
        graphics.fill(x, capBottom - 4, x + 15, footTop + 4, colour);
        graphics.fill(x + 4, footTop, right - 4, bottom, colour);
        graphics.fill(x, footTop + 4, right, bottom - 4, colour);
        graphics.fill(x + 15, capBottom, right, footTop, canvasColour);
        graphics.fill(x + 15, footTop, x + 29, bottom, canvasColour);
        graphics.fill(x + 15, bottom - 3, x + 29, bottom + 4, colour);
        graphics.fill(x + 12, bottom - 1, x + 32, bottom + 2, colour);
    }

    // Draw the pale rounded-ish reporter field used by command and control
    // blocks. Minecraft's primitive GUI API is rectangular, so small stepped
    // corners are used to preserve the category colour around the pill.
    private static void drawInputPill(GuiGraphics graphics, Font font, int x, int y, int width, int height,
                                      int canvasColour, String text) {
        if (width < 10 || height < 8) return;
        graphics.fill(x + 3, y, x + width - 3, y + height, 0xFFF7F7F7);
        graphics.fill(x, y + 3, x + width, y + height - 3, 0xFFF7F7F7);
        graphics.fill(x + 5, y + 2, x + width - 5, y + height - 2, 0xFFE9EEF3);
        graphics.fill(x + width - 10, y + height / 2 - 1, x + width - 5,
                y + height / 2 + 2, canvasColour);
        if (text != null && !text.isBlank()) {
            graphics.drawString(font, trim(font, text, Math.max(8, width - 14)), x + 4, y + 4,
                    0xFF263545, false);
        }
    }

    private static String trim(Font font, String value, int width) {
        if (value == null || value.isBlank() || font == null || width <= 0) return "";
        String text = value.replace('\n', ' ').replace('\r', ' ');
        if (font.width(text) <= width) return text;
        String suffix = "…";
        int end = text.length();
        while (end > 0 && font.width(text.substring(0, end) + suffix) > width) end--;
        return end <= 0 ? suffix : text.substring(0, end) + suffix;
    }

    // Find the block under a pointer
    public static String blockAt(Layout layout, double mouseX, double mouseY) {
        if (layout == null) return null;
        // Paint order is parent before nested children. Walk it backwards so a
        // click inside a C block selects the visible child rather than the
        // enclosing control block.
        List<BlockBounds> blocks = layout.blocks();
        for (int idx = blocks.size() - 1; idx >= 0; idx--) {
            BlockBounds bounds = blocks.get(idx);
            if (bounds.contains(mouseX, mouseY)) return bounds.id();
        }
        return null;
    }

    // Darken a block colour for its outline
    private static int darken(int colour) {
        int alpha = colour >>> 24 & 0xFF;
        int red = colour >>> 16 & 0xFF;
        int green = colour >>> 8 & 0xFF;
        int blue = colour & 0xFF;
        return alpha << 24 | (red * 3 / 5) << 16 | (green * 3 / 5) << 8 | blue * 3 / 5;
    }

    // Store one laid-out block
    public record BlockBounds(String id, int x, int y, int width, int height, boolean cBlock) {
        // Preserve the original five-argument construction form for hosts
        // which only need a flat stack.
        public BlockBounds(String id, int x, int y, int width, int height) {
            this(id, x, y, width, height, false);
        }
        // Check if the block contains the pointer
        public boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }

    // Store one host-owned layout result
    public record Layout(List<BlockBounds> blocks, int contentHeight) {
    }

    /** Logical graph position supplied by a host without coupling this API to a graph implementation. */
    public record Position(double x, double y) {
        public static final Position ORIGIN = new Position(0.0D, 0.0D);
    }
}
