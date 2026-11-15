package com.rieno.gadgetsandgizmos.lib.client.ui;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.client.render.GuiPoseScissor;
import com.rieno.gadgetsandgizmos.lib.graph.GraphPortInspection;
import com.rieno.gadgetsandgizmos.lib.graph.GraphPortInspectorPins.Mode;
import com.rieno.gadgetsandgizmos.lib.graph.GraphPortInspectorPins.State;
import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import com.rieno.gadgetsandgizmos.lib.graph.GraphValueOutline;
import com.rieno.gadgetsandgizmos.lib.graph.render.GraphViewport;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

// Share typed hover previews and graph-anchored inspectors with expandable value trees
public final class GraphPortInspector{
    // Keep hover inspection independent of retained graph bubbles
    private GraphPortInspector(){}

    // Preserve callers that supply only a palette
    public static void render(GuiGraphics graphics, Font font, int screenWidth, int screenHeight,
                              int mouseX, int mouseY, GraphPortInspection port, HelpModalStyle style, ImagePreview images){
        render(graphics, font, screenWidth, screenHeight, mouseX, mouseY, port, new Appearance(style, style.text(), null), images);
    }

    // Place transient hover details near the pointer without changing graph state
    public static void render(GuiGraphics graphics, Font font, int screenWidth, int screenHeight,
                              int mouseX, int mouseY, GraphPortInspection port, Appearance appearance, ImagePreview images){
        render(graphics, font, screenWidth, screenHeight, mouseX, mouseY, port, appearance, images, null);
    }

    // Prefer a live GUI for transient inspection and retain Advanced details when no feed is available
    public static void render(GuiGraphics graphics, Font font, int screenWidth, int screenHeight,
                              int mouseX, int mouseY, GraphPortInspection port, Appearance appearance, ImagePreview images, GuiPreview preview){
        var bubble = new Bubble();
        bubble.guiPreview(preview);
        int width = bubble.liveGui(port) ? 340 : 300;
        int x = mouseX + 14;
        if(x + width > screenWidth - 8) x = mouseX - width - 14;
        bubble.render(graphics, font, screenWidth, screenHeight, x, mouseY - 12, port, appearance, images,
                false, mouseX, mouseY, List.of(), () -> {});
    }

    // Retain placement, presentation mode and independent tree expansion for one port
    public static final class Bubble{
        /*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        DEFAULTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

        private final GraphValueOutline outline = new GraphValueOutline();
        // Keep authored graph placement separate from the most recently drawn screen bounds
        private State state;
        private Rect bounds, clip, modeButton, closeButton, treeArea, verticalTrack, horizontalTrack;
        private double originX, originY, scale = 1;
        private float layerDepth = 180, layerScale = .001F;
        private GraphViewport viewport;
        private Consumer<State> changed = val -> {};
        private Runnable closed = () -> {};
        // Capture interaction in local bubble coordinates after applying the graph transform
        private final List<Hit> rows = new ArrayList<>();
        private GraphValue value;
        private GraphPortInspection port;
        private Rect codeArea;
        private int codeLine;
        private int codeHorizontal;
        private CodeEditor codeEditor;
        private Consumer<String> codeChanged;
        private List<String> completions = List.of();
        private List<com.rieno.gadgetsandgizmos.lib.script.ScriptPeripheral> peripherals = List.of();
        private boolean editing;
        private Consumer<com.rieno.gadgetsandgizmos.lib.script.ScriptInput> terminalInput;
        private boolean terminalFocused;
        private Rect terminalArea;
        private com.rieno.gadgetsandgizmos.lib.script.ScriptTerminal terminal;
        private GuiPreview guiPreview;
        private int terminalButton = -1;
        private final java.util.Set<Integer> terminalKeys = new java.util.HashSet<>();
        private double terminalX, terminalY;
        private int scrollY, scrollX, contentHeight, contentWidth;
        private Rect textArea;
        private int textScroll, textHeight;
        private boolean pressed, dragging, moved, draggingVertical, draggingHorizontal;
        private double prevX, prevY;
        private boolean resizing;
        private int drawnWidth, drawnHeight, resizeWidth, resizeHeight;
        private Rect resizeHandle;
        private double anchorX = Double.NaN, anchorY;

        // Create independent tree and placement state for one pinned port
        public Bubble(){}

        /*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        FUNCTIONS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

        // Return the visible screen bounds for connectors and host hit testing
        public Rect bounds(){ return bounds; }

        // Return the current authored position and mode for persistence and verification
        public State state(){ return state; }

        // Follow host node movement in graph coordinates while keeping manual bubble offsets
        public void follow(double x, double y, boolean moving){
            if(state != null && Double.isFinite(anchorX) && (x != anchorX || y != anchorY)){
                state = state.translated(x - anchorX, y - anchorY); moved = true;
            }
            anchorX = x; anchorY = y;
            if(moved && !moving && !pressed){ changed.accept(state); moved = false; }
        }

        // Bind editable code only for hosts that own a persistent script document
        public void codeEditing(Consumer<String> changed, List<String> completions){ this.codeChanged = changed; this.completions = completions; }
        public void peripherals(List<com.rieno.gadgetsandgizmos.lib.script.ScriptPeripheral> vals){ peripherals = List.copyOf(vals); }
        public void guiPreview(GuiPreview preview){ guiPreview = preview; }
        private boolean hasGui(GraphPortInspection port){ return port.preview() == GraphPortInspection.Preview.CODE || port.gui() != null || guiPreview != null; }
        private boolean liveGui(GraphPortInspection port){ return guiPreview != null || com.rieno.gadgetsandgizmos.lib.script.ScriptTerminal.fromValue(port.gui()) != null; }
        private boolean showingGui(GraphPortInspection port){ return state != null && state.mode() == Mode.GUI && hasGui(port); }
        public void terminalInput(Consumer<com.rieno.gadgetsandgizmos.lib.script.ScriptInput> input){ terminalInput = input; }
        public void blur(){
            if(terminalInput != null) for(int key : terminalKeys) terminalInput.accept(new com.rieno.gadgetsandgizmos.lib.script.ScriptInput("key_up", 0, 0, key));
            if(terminalInput != null && terminalButton >= 0) terminalInput.accept(new com.rieno.gadgetsandgizmos.lib.script.ScriptInput("release", terminalX, terminalY, terminalButton));
            terminalButton = -1;
            terminalKeys.clear(); editing = false; terminalFocused = false; if(codeEditor != null) codeEditor.focused(false);
        }
        public boolean editing(){ return editing; }
        public boolean terminalFocused(){ return terminalFocused; }
        public boolean keyPressed(int key){
            if(terminalFocused && terminalInput != null){
                if(key < 0 || key > 512) return true;
                if(key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE){ blur(); return true; }
                if(key == org.lwjgl.glfw.GLFW.GLFW_KEY_V && net.minecraft.client.gui.screens.Screen.hasControlDown()){
                    String text = net.minecraft.client.Minecraft.getInstance().keyboardHandler.getClipboard();
                    terminalInput.accept(new com.rieno.gadgetsandgizmos.lib.script.ScriptInput("paste", 0, 0, 0, text.substring(0, Math.min(16384, text.length()))));
                }
                else{ terminalKeys.add(key); terminalInput.accept(new com.rieno.gadgetsandgizmos.lib.script.ScriptInput("key", 0, 0, key)); }
                return true;
            }
            if(!editing || codeEditor == null) return false;
            if(key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE){ blur(); return true; }
            codeEditor.keyPressed(key); return true;
        }
        public boolean charTyped(char ch){
            if(terminalFocused && terminalInput != null){ if(ch >= 32 && ch <= 255) terminalInput.accept(new com.rieno.gadgetsandgizmos.lib.script.ScriptInput("char", 0, 0, ch)); return true; }
            return editing && codeEditor != null && codeEditor.charTyped(ch);
        }
        public boolean keyReleased(int key){ if(!terminalFocused || terminalInput == null) return false; if(terminalKeys.remove(key)) terminalInput.accept(new com.rieno.gadgetsandgizmos.lib.script.ScriptInput("key_up", 0, 0, key)); return true; }

        // Preserve selected-row access without changing which tree root is displayed
        public List<String> path(){ return outline.selected(); }

        // Exclude clipped portions of a graph bubble from pointer capture
        public boolean contains(double x, double y){ return bounds != null && clip != null && bounds.contains(x, y) && clip.contains(x, y); }

        // Release pointer capture when another graph or modal owns input
        public void hide(){
            blur();
            if(moved && state != null) changed.accept(state);
            bounds = null; pressed = false; dragging = false; moved = false;
            draggingVertical = false; draggingHorizontal = false; resizing = false;
        }

        // Preserve the screen-coordinate overload for callers outside graph canvases
        public void render(GuiGraphics graphics, Font font, int screenWidth, int screenHeight, int anchorX, int anchorY,
                           GraphPortInspection port, Appearance appearance, ImagePreview images, boolean interactive,
                           int mouseX, int mouseY, List<Rect> occupied, Runnable closed){
            viewport = null; scale = 1;
            layerDepth = 600; layerScale = .001F;
            clip = new Rect(8, 8, Math.max(0, screenWidth - 16), Math.max(0, screenHeight - 16));
            this.closed = Objects.requireNonNull(closed);
            if(!interactive || state == null) state = new State(anchorX, anchorY, !interactive && liveGui(port) ? Mode.GUI : Mode.ADVANCED);
            int width = width(font, port, appearance, interactive);
            Layout layout = layout(font, port, interactive, width, Math.max(80, screenHeight - 16));
            originX = Math.clamp(interactive ? state.x() : anchorX, clip.x(), Math.max(clip.x(), clip.right() - width));
            originY = Math.clamp(interactive ? state.y() : anchorY, clip.y(), Math.max(clip.y(), clip.bottom() - layout.height()));
            draw(graphics, font, port, appearance, images, interactive, mouseX, mouseY, width, layout);
        }

        // Apply the exact host graph transform without clamping or following the viewport edges
        public void renderGraph(GuiGraphics graphics, Font font, Rect canvas, GraphViewport viewport, State initial,
                                GraphPortInspection port, Appearance appearance, ImagePreview images, int mouseX, int mouseY,
                                Consumer<State> changed, Runnable closed){
            renderGraph(graphics, font, canvas, viewport, initial, port, appearance, images, mouseX, mouseY, changed, closed, 180, .001F);
        }

        // Let a host reserve ordered depth bands above nodes and below its modals
        public void renderGraph(GuiGraphics graphics, Font font, Rect canvas, GraphViewport viewport, State initial,
                                GraphPortInspection port, Appearance appearance, ImagePreview images, int mouseX, int mouseY,
                                Consumer<State> changed, Runnable closed, float depth, float depthScale){
            layerDepth = depth; layerScale = depthScale;
            this.viewport = Objects.requireNonNull(viewport);
            this.clip = Objects.requireNonNull(canvas);
            this.changed = Objects.requireNonNull(changed); this.closed = Objects.requireNonNull(closed);
            if(state == null) state = Objects.requireNonNull(initial);
            scale = viewport.zoom();
            originX = viewport.screenX(state.x()); originY = viewport.screenY(state.y());
            int width = width(font, port, appearance, true);
            draw(graphics, font, port, appearance, images, true, mouseX, mouseY, width,
                    layout(font, port, true, width, 420));
        }

        // Keep Simple mode small while reserving explicit mode and close controls
        private int width(Font font, GraphPortInspection port, Appearance appearance, boolean interactive){
            if(interactive && state != null && state.width() > 0) return state.width();
            if(showingGui(port)) return 340;
            if(!interactive || state == null || state.mode() != Mode.SIMPLE) return 300;
            return Math.clamp(Math.max(font.width(compact(port)) + 18, Math.min(110, font.width(appearance.title())) + 92), 144, 230);
        }

        // Prepare a bounded outline viewport while leaving nested branches in the tree
        private Layout layout(Font font, GraphPortInspection port, boolean interactive, int width, int maximumHeight){
            this.port = port; value = port.value();
            outline.selectedValue(value);
            boolean simple = interactive && state.mode() == Mode.SIMPLE;
            int requestedHeight = interactive ? state.height() : 0;
            if(simple) return new Layout(List.of(), port, 0, null, 0, requestedHeight > 0 ? requestedHeight : 38, true);
            if(showingGui(port))
                return new Layout(List.of(), port, 0, null, 0, requestedHeight > 0 ? requestedHeight : 222, false);
            List<FormattedCharSequence> lines = new ArrayList<>();
            var label = font.split(Component.literal((port.output() ? "Output: " : "Input: ") + port.label()), width - 20);
            lines.addAll(label.subList(0, Math.min(2, label.size())));
            lines.add(Component.literal("Type: " + typeLabel(port.resolvedType())).getVisualOrderText());
            if(!GraphValueOutline.isCollection(value) && port.preview() != GraphPortInspection.Preview.CODE){
                var summary = font.split(Component.literal("Value: " + port.summary()), width - 20);
                lines.addAll(summary.subList(0, Math.min(interactive ? 4096 : 3, summary.size())));
            }
            GraphPortInspection previewPort = port;
            if(!outline.selected().isEmpty()){
                var selected = selectionInspection(outline.selected(), outline.selectedValue(value));
                if(selected.preview() != GraphPortInspection.Preview.NONE){
                    previewPort = selected;
                    var selectedLabel = font.split(Component.literal("Preview: " + String.join(" / ", outline.selected())), width - 20);
                    lines.addAll(selectedLabel.subList(0, Math.min(2, selectedLabel.size())));
                }
            }
            int preview = switch(previewPort.preview()){
                case COLOR, GRADIENT -> 28;
                case IMAGE -> 90;
                case WIDGET -> 120;
                case CODE -> interactive && codeChanged != null ? 216 : Math.min(216, Math.max(40, port.value().asString().split("\n", -1).length * (font.lineHeight + 3) + 8));
                default -> 0;
            };
            GraphValueOutline.Snapshot snapshot = GraphValueOutline.isCollection(value) ? outline.snapshot(value, 2048) : null;
            int base = 42 + lines.size() * (font.lineHeight + 3) + (preview == 0 ? 0 : preview + 6);
            if(requestedHeight > 0 && preview > 0){
                int available = Math.max(16, requestedHeight - (base - preview) - (snapshot == null ? 0 : 53));
                preview = available; base = 42 + lines.size() * (font.lineHeight + 3) + preview + 6;
            }
            int treeHeight = snapshot == null ? 0 : Math.min(Math.max(18, maximumHeight - base - 20),
                    Math.min(216, snapshot.rows().size() * 18));
            if(requestedHeight > 0 && snapshot != null) treeHeight = Math.max(18, requestedHeight - base - 17);
            int height = base + treeHeight + (snapshot == null ? 0 : 17);
            if(snapshot == null && preview == 0 && interactive) height = requestedHeight > 0 ? requestedHeight : Math.min(maximumHeight, height);
            else height = Math.max(height, requestedHeight);
            return new Layout(List.copyOf(lines), previewPort, preview, snapshot, treeHeight, height, false);
        }

        // Paint local geometry through the same pan, zoom and clipping used for graph nodes
        private void draw(GuiGraphics graphics, Font font, GraphPortInspection port, Appearance appearance, ImagePreview images,
                          boolean interactive, int mouseX, int mouseY, int width, Layout layout){
            drawnWidth = width; drawnHeight = layout.height();
            bounds = new Rect((int)Math.floor(originX), (int)Math.floor(originY), (int)Math.ceil(width * scale), (int)Math.ceil(layout.height() * scale));
            codeArea = null; terminalArea = null; textArea = null;
            resizeHandle = interactive ? new Rect(width - 12, layout.height() - 12, 12, 12) : null;
            rows.clear(); treeArea = null; modeButton = null; closeButton = null; verticalTrack = null; horizontalTrack = null;
            if(!bounds.intersects(clip)){ bounds = null; return; }
            double localX = localX(mouseX), localY = localY(mouseY);
            var style = appearance.style();
            try(var layer = GuiRenderLayer.pushFlattened(graphics, layerDepth, layerScale);
                var canvasClip = GuiPoseScissor.push(graphics, clip.x(), clip.y(), clip.right(), clip.bottom())){
                graphics.pose().pushPose();
                try{
                    graphics.pose().translate(originX, originY, 0); graphics.pose().scale((float)scale, (float)scale, 1);
                    int titleHeight = layout.simple() ? 18 : 24;
                    if(appearance.frame() != null) appearance.frame().draw(graphics, 0, 0, width, layout.height(), titleHeight);
                    else{
                        graphics.fill(0, 0, width, layout.height(), style.background());
                        graphics.renderOutline(0, 0, width, layout.height(), style.border());
                        graphics.fill(1, 1, width - 1, titleHeight, style.panel());
                    }
                    int titleRight = width - 8;
                    if(interactive){
                        closeButton = new Rect(width - 19, 3, 14, titleHeight - 6);
                        String mode = layout.simple() ? "Simple" : state.mode() == Mode.GUI && hasGui(port) ? "GUI" : "Advanced";
                        modeButton = new Rect(width - 25 - font.width(mode) - 8, 3, font.width(mode) + 8, titleHeight - 6);
                        button(graphics, font, modeButton, mode, appearance, localX, localY);
                        button(graphics, font, closeButton, "x", appearance, localX, localY);
                        titleRight = modeButton.x() - 5;
                    }
                    GuiControlPreviews.drawFittedText(graphics, font, appearance.title(), 21, 3,
                            Math.max(1, titleRight - 21), titleHeight - 6, 1, appearance.titleColor(), false);
                    if(layout.simple()){
                        graphics.drawString(font, font.plainSubstrByWidth(compact(port), width - 16), 8, 24, style.text(), false);
                        return;
                    }
                    if(showingGui(port)){
                        terminal = com.rieno.gadgetsandgizmos.lib.script.ScriptTerminal.fromValue(port.gui());
                        terminalArea = new Rect(10, 32, width - 20, Math.max(1, layout.height() - 42));
                        if(terminal != null) TerminalPreview.render(graphics, terminal, terminalArea.x(), terminalArea.y(), terminalArea.width(), terminalArea.height());
                        else if(guiPreview != null) guiPreview.draw(graphics, font, terminalArea);
                        else graphics.drawCenteredString(font, "Computer screen unavailable", width / 2, 112, style.muted());
                        return;
                    }
                    int top = 30;
                    if(layout.previewHeight() > 0){
                        if(layout.previewPort().preview() == GraphPortInspection.Preview.CODE){
                            codeArea = new Rect(10, top, width - 20, layout.previewHeight());
                            if(interactive && codeChanged != null){
                                if(codeEditor == null){ codeEditor = new CodeEditor(font, val -> codeChanged.accept(val)); codeEditor.focused(false); }
                                codeEditor.source(value.asString()); codeEditor.appearance(style); codeEditor.completions(completions);
                                codeEditor.peripherals(peripherals);
                                codeEditor.bounds(codeArea.x(), codeArea.y(), codeArea.width(), codeArea.height());
                                codeEditor.render(graphics);
                            }else{
                                codeLine = Math.clamp(codeLine, 0, Math.max(0, value.asString().split("\n", -1).length - (layout.previewHeight() - 8) / (font.lineHeight + 3)));
                                CodePreview.render(graphics, font, value.asString(), 10, top, width - 20, layout.previewHeight(), codeLine, codeHorizontal, style);
                            }
                        }else drawPreview(graphics, font, layout.previewPort(), images, 10, top, width - 20, layout.previewHeight(), style);
                        top += layout.previewHeight() + 6;
                    }
                    if(layout.outline() == null && layout.previewHeight() == 0){
                        textArea = new Rect(10, top, width - 20, Math.max(1, layout.height() - top - 12));
                        textHeight = layout.lines().size() * (font.lineHeight + 3);
                        textScroll = Math.clamp(textScroll, 0, Math.max(0, textHeight - textArea.height()));
                        try(var textClip = GuiPoseScissor.push(graphics, textArea.x(), textArea.y(), textArea.right(), textArea.bottom())){
                            int lineY = top - textScroll;
                            for(var line : layout.lines()){
                                if(lineY + font.lineHeight >= textArea.y() && lineY < textArea.bottom()) graphics.drawString(font, line, 10, lineY, style.text(), false);
                                lineY += font.lineHeight + 3;
                            }
                        }
                    }else for(var line : layout.lines()){
                        graphics.drawString(font, line, 10, top, style.text(), false); top += font.lineHeight + 3;
                    }
                    if(layout.outline() != null) drawTree(graphics, font, appearance, layout, width, top + 3, localX, localY, interactive);
                }finally{
                    if(resizeHandle != null) for(int idx = 0; idx < 3; idx++) for(int step = 0; step <= 2 + idx * 3; step++){
                        int x = width - 3 - step, y = layout.height() - 3 - (2 + idx * 3 - step);
                        graphics.fill(x, y, x + 1, y + 1, appearance.style().muted());
                    }
                    graphics.pose().popPose();
                }
            }
        }

        // Draw expandable object and array rows with indentation, guide lines and colored scalar values
        private void drawTree(GuiGraphics graphics, Font font, Appearance appearance, Layout layout, int width, int top,
                              double mouseX, double mouseY, boolean interactive){
            var style = appearance.style();
            treeArea = new Rect(8, top, width - 23, layout.treeHeight());
            contentHeight = layout.outline().rows().size() * 18;
            contentWidth = treeArea.width();
            for(var row : layout.outline().rows()){
                int textWidth = row.collection() ? font.width(Component.literal(rowText(row)).withStyle(net.minecraft.ChatFormatting.BOLD))
                        : font.width(Component.literal(row.key() + ": ").withStyle(net.minecraft.ChatFormatting.BOLD))
                        + font.width(rowInspection(row).summary());
                contentWidth = Math.max(contentWidth, row.depth() * 14 + 32 + textWidth);
            }
            scrollY = Math.clamp(scrollY, 0, Math.max(0, contentHeight - treeArea.height()));
            scrollX = Math.clamp(scrollX, 0, Math.max(0, contentWidth - treeArea.width()));
            try(var treeClip = GuiPoseScissor.push(graphics, treeArea.x(), treeArea.y(), treeArea.right(), treeArea.bottom())){
                for(int idx = Math.max(0, scrollY / 18); idx < layout.outline().rows().size(); idx++){
                    int y = top + idx * 18 - scrollY;
                    if(y >= treeArea.bottom()) break;
                    var row = layout.outline().rows().get(idx);
                    int x = treeArea.x() + row.depth() * 14 - scrollX;
                    Rect hit = new Rect(treeArea.x(), y, treeArea.width(), 18);
                    if(row.path().equals(outline.selected()) || interactive && hit.contains(mouseX, mouseY))
                        graphics.fill(hit.x(), hit.y(), hit.right(), hit.bottom(), style.panel());
                    for(int depth = 0; depth < row.depth(); depth++)
                        graphics.vLine(treeArea.x() + depth * 14 + 5 - scrollX, y - 1, y + 18, style.border());
                    if(row.depth() > 0) graphics.hLine(x - 9, x + 1, y + 8, style.border());
                    if(row.collection()) triangle(graphics, x + 2, y + 5, row.expanded(), style.accent());
                    else graphics.fill(x + 4, y + 7, x + 7, y + 10, style.muted());
                    int textX = x + 15;
                    if(row.collection()){
                        String name = (row.key().isEmpty() && row.path().isEmpty() ? "" : row.key() + ": ") + collectionLabel(row.value());
                        graphics.fill(textX - 2, y + 2, textX + font.width(name) + 4, y + 15, style.panel());
                        graphics.drawString(font, Component.literal(name).withStyle(net.minecraft.ChatFormatting.BOLD), textX, y + 4, style.text(), false);
                    }else{
                        String key = row.key() + ": ";
                        graphics.drawString(font, Component.literal(key).withStyle(net.minecraft.ChatFormatting.BOLD), textX, y + 4, style.text(), false);
                        int valueX = textX + font.width(Component.literal(key).withStyle(net.minecraft.ChatFormatting.BOLD));
                        var inspection = rowInspection(row);
                        String text = inspection.summary();
                        if(inspection.preview() == GraphPortInspection.Preview.COLOR){
                            graphics.fill(valueX, y + 4, valueX + 10, y + 14, GraphPortInspection.color(row.value()));
                            valueX += 14; text = String.format(Locale.ROOT, "#%08X", GraphPortInspection.color(row.value()));
                        }
                        graphics.drawString(font, text, valueX, y + 4, scalarColor(row.value(), style), false);
                    }
                    if(interactive) rows.add(new Hit(hit, row.path(), row.collection()));
                }
            }
            verticalTrack = new Rect(width - 12, top, 4, layout.treeHeight());
            horizontalTrack = new Rect(8, treeArea.bottom() + 3, width - 23, 4);
            scrollbar(graphics, verticalTrack, scrollY, contentHeight, treeArea.height(), false, style);
            if(contentWidth > treeArea.width()) scrollbar(graphics, horizontalTrack, scrollX, contentWidth, treeArea.width(), true, style);
            if(layout.outline().truncated()) graphics.drawString(font, "Tree preview limited to 2048 rows", 10, treeArea.bottom() + 7, style.muted(), false);
        }

        // Preserve gradient sample colors while keeping ordinary numeric collections numeric
        private GraphPortInspection rowInspection(GraphValueOutline.Row row){
            return selectionInspection(row.path(), row.value());
        }

        // Resolve a selected entry's rich preview while preserving the root collection view
        private GraphPortInspection selectionInspection(List<String> path, GraphValue val){
            boolean color = "color".equals(val.type());
            if(!path.isEmpty()){
                var parentPath = path.subList(0, path.size() - 1);
                var parent = GraphValueOutline.resolve(value, parentPath);
                color |= parentPath.isEmpty() && port.preview() == GraphPortInspection.Preview.GRADIENT
                        || parent != null && GraphPortInspection.preview(parent.type(), parent) == GraphPortInspection.Preview.GRADIENT;
            }
            return new GraphPortInspection(path.isEmpty() ? port.label() : path.getLast(), color ? "color" : val.type(), port.output(), val, null);
        }

        // Measure the exact displayed key and value before laying out horizontal scrolling
        private String rowText(GraphValueOutline.Row row){
            return row.collection() ? row.key() + ": " + collectionLabel(row.value()) : row.key() + ": " + rowInspection(row).summary();
        }

        // Toggle tree branches and presentation mode before the graph receives the same pointer press
        public boolean mouseClicked(double x, double y, int button){
            if(!contains(x, y)){ blur(); return false; }
            pressed = true;
            if(button == 0 && resizeHandle != null && resizeHandle.contains(localX(x), localY(y))){
                blur(); resizing = true; prevX = x; prevY = y;
                resizeWidth = drawnWidth; resizeHeight = drawnHeight;
                state = state.resized(resizeWidth, resizeHeight); return true;
            }
            if(state.mode() == Mode.GUI && terminal != null && terminalInput != null && terminalArea != null && terminalArea.contains(localX(x), localY(y))){
                var point = terminalPoint(x, y);
                if(point != null && button >= 0 && button <= 2){
                    editing = false; terminalFocused = true; terminalButton = button; terminalX = point[0]; terminalY = point[1];
                    terminalInput.accept(new com.rieno.gadgetsandgizmos.lib.script.ScriptInput("click", terminalX, terminalY, button));
                }
                return true;
            }
            if(button != 0) return true;
            double localX = localX(x), localY = localY(y);
            if(closeButton != null && closeButton.contains(localX, localY)){ closed.run(); hide(); return true; }
            if(modeButton != null && modeButton.contains(localX, localY)){
                blur();
                state = state.withMode(state.mode() == Mode.SIMPLE ? Mode.ADVANCED
                        : state.mode() == Mode.ADVANCED && hasGui(port) ? Mode.GUI : Mode.SIMPLE);
                changed.accept(state); return true;
            }
            if(codeArea != null && codeEditor != null && codeChanged != null && codeArea.contains(localX, localY)){
                editing = true; return codeEditor.mouseClicked(localX, localY, button);
            }
            blur();
            if(treeArea != null && treeArea.contains(localX, localY)) for(Hit row : rows){
                if(!row.bounds().contains(localX, localY)) continue;
                outline.select(value, row.path());
                if(row.collection()) outline.toggle(value, row.path());
                return true;
            }
            if(verticalTrack != null && verticalTrack.contains(localX, localY) && contentHeight > treeArea.height()){
                draggingVertical = true; scrollAt(localX, localY); return true;
            }
            if(horizontalTrack != null && horizontalTrack.contains(localX, localY) && contentWidth > treeArea.width()){
                draggingHorizontal = true; scrollAt(localX, localY); return true;
            }
            if(localY < (state.mode() == Mode.SIMPLE ? 18 : 24)){ dragging = true; prevX = x; prevY = y; }
            return true;
        }

        // Convert pointer movement into authored graph distances at every zoom level
        public boolean mouseDragged(double x, double y, int button){
            if(!pressed) return false;
            if(button == 0 && resizing){
                state = state.resized(resizeWidth + (int)Math.round((x - prevX) / scale), resizeHeight + (int)Math.round((y - prevY) / scale));
                moved = true; return true;
            }
            if(terminalButton >= 0 && terminalInput != null){
                var point = terminalPoint(x, y);
                if(point != null){ terminalX = point[0]; terminalY = point[1]; terminalInput.accept(new com.rieno.gadgetsandgizmos.lib.script.ScriptInput("drag", terminalX, terminalY, terminalButton)); }
                return true;
            }
            if(editing && codeEditor != null && codeEditor.mouseDragged(localX(x), localY(y))) return true;
            if(button == 0 && dragging){
                state = state.translated((x - prevX) / scale, (y - prevY) / scale);
                prevX = x; prevY = y; moved = true;
            }else if(button == 0 && (draggingVertical || draggingHorizontal)) scrollAt(localX(x), localY(y));
            return true;
        }

        // Save a completed move once instead of writing preferences for every drag event
        public boolean mouseReleased(){
            if(terminalButton >= 0 && terminalInput != null) terminalInput.accept(new com.rieno.gadgetsandgizmos.lib.script.ScriptInput("release", terminalX, terminalY, terminalButton));
            terminalButton = -1;
            if(codeEditor != null) codeEditor.mouseReleased();
            boolean handled = pressed;
            if(moved) changed.accept(state);
            pressed = false; dragging = false; moved = false; draggingVertical = false; draggingHorizontal = false; resizing = false;
            return handled;
        }

        // Preserve vertical-wheel callers while allowing the graph to zoom over bubble headers
        public boolean mouseScrolled(double x, double y, double amount){ return mouseScrolled(x, y, 0, amount, false); }

        // Scroll the tree horizontally with Shift and vertically with the ordinary wheel
        public boolean mouseScrolled(double x, double y, double horizontal, double vertical, boolean shift){
            if(contains(x, y) && textArea != null && textArea.contains(localX(x), localY(y))){
                textScroll = Math.clamp(textScroll - (int)Math.round(vertical * 24), 0, Math.max(0, textHeight - textArea.height())); return true;
            }
            if(contains(x, y) && state.mode() == Mode.GUI && terminal != null && terminalInput != null){
                var point = terminalPoint(x, y);
                if(point != null){ terminalInput.accept(new com.rieno.gadgetsandgizmos.lib.script.ScriptInput("scroll", point[0], point[1], vertical > 0 ? -1 : 1)); return true; }
            }
            if(contains(x, y) && codeArea != null && codeArea.contains(localX(x), localY(y))){
                if(codeChanged != null && codeEditor != null) return codeEditor.mouseScrolled(horizontal, vertical, shift);
                if(shift || horizontal != 0) codeHorizontal = Math.max(0, codeHorizontal - (int)Math.round((horizontal != 0 ? horizontal : vertical) * 24));
                else codeLine = Math.max(0, codeLine - (int)Math.round(vertical * 3));
                return true;
            }
            if(!contains(x, y) || treeArea == null || !treeArea.contains(localX(x), localY(y))) return false;
            if(shift || horizontal != 0) scrollX = Math.clamp(scrollX - (int)Math.round((horizontal != 0 ? horizontal : vertical) * 24),
                    0, Math.max(0, contentWidth - treeArea.width()));
            else scrollY = Math.clamp(scrollY - (int)Math.round(vertical * 36), 0, Math.max(0, contentHeight - treeArea.height()));
            return true;
        }

        // Position a scrollbar thumb from the same local coordinates used to draw it
        private double[] terminalPoint(double x, double y){
            return terminalArea == null || terminal == null ? null : com.rieno.gadgetsandgizmos.lib.script.ScriptInput.point(
                    localX(x) - terminalArea.x(), localY(y) - terminalArea.y(), terminalArea.width(), terminalArea.height(), terminal.width(), terminal.height());
        }

        private void scrollAt(double x, double y){
            if(draggingVertical) scrollY = (int)Math.clamp((y - treeArea.y()) / treeArea.height() * contentHeight - treeArea.height() / 2.0,
                    0, Math.max(0, contentHeight - treeArea.height()));
            if(draggingHorizontal) scrollX = (int)Math.clamp((x - treeArea.x()) / treeArea.width() * contentWidth - treeArea.width() / 2.0,
                    0, Math.max(0, contentWidth - treeArea.width()));
        }

        // Invert the exact transform used for rendering rather than rounded screen bounds
        private double localX(double x){ return (x - originX) / scale; }

        // Keep vertical hit testing aligned with graph scaling and panning
        private double localY(double y){ return (y - originY) / scale; }
    }

    // Summarize one port compactly without showing type, navigation or media controls
    private static String compact(GraphPortInspection port){
        if(port.value() == null) return "No value";
        if(port.preview() == GraphPortInspection.Preview.COLOR) return String.format(Locale.ROOT, "#%08X", GraphPortInspection.color(port.value()));
        return port.summary();
    }

    // Match the JSON-style collection badges without exposing the storage implementation
    private static String collectionLabel(GraphValue val){
        int size = val.value() instanceof java.util.Map<?, ?> map ? map.size() : ((List<?>)val.value()).size();
        return (val.value() instanceof List<?> ? "ARRAY" : "OBJECT") + " (" + size + ")";
    }

    // Distinguish JSON strings, numbers and booleans with the host palette
    private static int scalarColor(GraphValue val, HelpModalStyle style){
        return switch(val.type()){
            case "string", "direction", "image" -> style.accent();
            case "boolean" -> 0xFF8DD3A0;
            case "number", "color" -> 0xFFE5BA64;
            default -> style.text();
        };
    }

    // Draw a familiar disclosure triangle only on expandable rows
    private static void triangle(GuiGraphics graphics, int x, int y, boolean expanded, int color){
        for(int idx = 0; idx < 3; idx++){
            if(expanded) graphics.fill(x + idx, y + idx, x + 7 - idx, y + idx + 1, color);
            else graphics.fill(x + idx, y + idx, x + idx + 1, y + 7 - idx, color);
        }
    }

    // Draw a proportional scroll thumb in the same graph-scaled coordinate space as the tree
    private static void scrollbar(GuiGraphics graphics, Rect track, int offset, int content, int visible, boolean horizontal, HelpModalStyle style){
        graphics.fill(track.x(), track.y(), track.right(), track.bottom(), style.panel());
        if(content <= visible) return;
        int length = horizontal ? track.width() : track.height();
        int size = Math.max(6, length * visible / content);
        int start = (length - size) * offset / Math.max(1, content - visible);
        if(horizontal) graphics.fill(track.x() + start, track.y(), track.x() + start + size, track.bottom(), style.muted());
        else graphics.fill(track.x(), track.y() + start, track.right(), track.y() + start + size, style.muted());
    }

    // Use explicit action labels in controls painted by the owning controller
    private static void button(GuiGraphics graphics, Font font, Rect rect, String text, Appearance appearance, double mouseX, double mouseY){
        var style = appearance.style();
        if(appearance.control() != null) appearance.control().draw(graphics, rect, rect.contains(mouseX, mouseY));
        else graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), rect.contains(mouseX, mouseY) ? style.border() : style.panel());
        GuiControlPreviews.drawFittedText(graphics, font, Component.literal(text), rect.x() + 2, rect.y(), rect.width() - 4, rect.height(),
                1, style.text(), true);
    }

    // Keep type labels readable without exposing raw storage aliases
    private static String typeLabel(String type){
        if(type == null || type.isBlank()) return "Any";
        return type.substring(0, 1).toUpperCase(Locale.ROOT) + type.substring(1).replace('_', ' ');
    }

    // Draw rich content while leaving image ownership with the host
    private static void drawPreview(GuiGraphics graphics, Font font, GraphPortInspection port, ImagePreview images,
                                    int x, int y, int width, int height, HelpModalStyle style){
        if(port.preview() == GraphPortInspection.Preview.COLOR || port.preview() == GraphPortInspection.Preview.GRADIENT){
            double fit = Math.min(width / 280.0, height / 28.0);
            int w = Math.max(1, (int)Math.round(280 * fit)), h = Math.max(1, (int)Math.round(28 * fit));
            x += (width - w) / 2; y += (height - h) / 2; width = w; height = h;
        }
        if(port.preview() == GraphPortInspection.Preview.WIDGET) graphics.fill(x, y, x + width, y + height, style.panel());
        else for(int row = 0; row < height; row += 6) for(int col = 0; col < width; col += 6)
                graphics.fill(x + col, y + row, x + Math.min(width, col + 6), y + Math.min(height, row + 6),
                        ((row / 6 + col / 6) & 1) == 0 ? 0xFF666666 : 0xFF999999);
        if(port.preview() == GraphPortInspection.Preview.COLOR) graphics.fill(x, y, x + width, y + height, GraphPortInspection.color(port.value()));
        else if(port.preview() == GraphPortInspection.Preview.GRADIENT){
            var colors = port.colors();
            if(!colors.isEmpty()) for(int col = 0; col < width; col++)
                graphics.fill(x + col, y, x + col + 1, y + height, colors.get(Math.min(colors.size() - 1, col * colors.size() / width)));
        }else if(port.preview() == GraphPortInspection.Preview.WIDGET){
            if(images == null || !images.drawWidget(graphics, port.value(), x, y, width, height))
                graphics.drawCenteredString(font, "Widget preview unavailable", x + width / 2, y + (height - font.lineHeight) / 2, style.text());
        }else if(images == null || !images.draw(graphics, port.value(), x, y, width, height))
            graphics.drawCenteredString(font, "Image unavailable or loading", x + width / 2, y + (height - font.lineHeight) / 2, style.text());
        graphics.renderOutline(x - 1, y - 1, width + 2, height + 2, style.border());
    }

    @FunctionalInterface public interface GuiPreview{
        void draw(GuiGraphics graphics, Font font, Rect bounds);
    }

    // Let the host supply its node title, current theme and existing control painters
    public record Appearance(HelpModalStyle style, int titleColor, FramePainter frame, ControlPainter control, Component title){
        // Preserve palette and frame-only consumers
        public Appearance(HelpModalStyle style, int titleColor, FramePainter frame){ this(style, titleColor, frame, null); }

        // Preserve consumers that supplied their own navigation control painter
        public Appearance(HelpModalStyle style, int titleColor, FramePainter frame, ControlPainter control){
            this(style, titleColor, frame, control, Component.literal("Port"));
        }

        // Require text colors and a real host-supplied title
        public Appearance{ Objects.requireNonNull(style); title = Objects.requireNonNull(title).copy(); }

        // Name the inspector after its owning node without changing its shared chrome
        public Appearance withTitle(Component title){ return new Appearance(style, titleColor, frame, control, title); }
    }

    // Paint a frame using the same graph scale as its owning nodes
    @FunctionalInterface public interface FramePainter{
        void draw(GuiGraphics graphics, int x, int y, int width, int height, int titleHeight);
    }

    // Paint Simple, Advanced and close controls with the host's own button treatment
    @FunctionalInterface public interface ControlPainter{
        void draw(GuiGraphics graphics, Rect bounds, boolean hovered);
    }

    // Resolve addon media without depending on its asset store
    @FunctionalInterface public interface ImagePreview{
        boolean draw(GuiGraphics graphics, GraphValue value, int x, int y, int width, int height);

        // Extend existing image painters with host-owned widget styles and assets
        default boolean drawWidget(GuiGraphics graphics, GraphValue value, int x, int y, int width, int height){ return false; }
    }

    // Share bounds between graph clipping, content layout and pointer handling
    public record Rect(int x, int y, int width, int height){
        public int right(){ return x + width; }
        public int bottom(){ return y + height; }
        public boolean contains(double x, double y){ return x >= this.x && x < right() && y >= this.y && y < bottom(); }
        public boolean intersects(Rect other){ return x < other.right() && right() > other.x() && y < other.bottom() && bottom() > other.y(); }
    }

    private record Hit(Rect bounds, List<String> path, boolean collection){}
    private record Layout(List<FormattedCharSequence> lines, GraphPortInspection previewPort, int previewHeight, GraphValueOutline.Snapshot outline,
                          int treeHeight, int height, boolean simple){}
}
