package com.rieno.gadgetsandgizmos.lib.client.ui;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import com.rieno.gadgetsandgizmos.lib.script.LuaCompletions;
import com.rieno.gadgetsandgizmos.lib.script.LuaScripts;
import com.rieno.gadgetsandgizmos.lib.script.ScriptPeripheral;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

// Share one code editor across named scripts with port insertion and live result panels
public final class ScriptEditorWorkspace{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public record Document(String id, String name, String source, List<String> inputs, List<String> outputs,
                           Map<String, GraphValue> results, String status, String error, Map<String, GraphValue> inputValues){
        public Document(String id, String name, String source, List<String> inputs, List<String> outputs,
                        Map<String, GraphValue> results, String status, String error){
            this(id, name, source, inputs, outputs, results, status, error, Map.of());
        }
    }
    public enum Surface{ CANVAS, SIDEBAR, HEADER, BUTTON, ROW, DROPDOWN }
    @FunctionalInterface public interface Painter{
        void draw(GuiGraphics graphics, Surface surface, int x, int y, int width, int height, boolean hovered, boolean selected);
    }
    public record Appearance(HelpModalStyle style, int canvas, int selected, int error, Painter painter){}
    private Appearance appearance = new Appearance(new HelpModalStyle(0xFF17212D, 0xFF202D3B, 0xFF3B4F69,
            0xFFEAF2FF, 0xFF91A4BC, 0xFFAEBEFF), 0xFF121923, 0xFF3B4F69, 0xFFFF9696, null);
    private final Font font;
    private final CodeEditor editor;
    private final Consumer<String> select;
    private List<Document> documents = List.of();
    private List<String> api = List.of();
    private String selected = "";
    private Rect bounds = new Rect(0, 0, 1, 1);
    private Rect code = bounds;
    private Rect input = bounds;
    private Rect output = bounds;
    private Rect wrapButton = bounds;
    private Runnable wrapPeripheral;
    private int leftWidth;
    private int rightWidth;
    private int browserScroll;
    private int resultsScroll;
    private int resultsHeight;
    private int dropdown;
    private int dropdownScroll;
    private Runnable create;
    private Consumer<String> place;
    private Rect createButton = bounds;
    private Rect placeButton = bounds;
    private int browserTop;
    private Consumer<String> rename, delete;

    public ScriptEditorWorkspace(Font font, Consumer<String> select, BiConsumer<String, String> changed){
        this.font = font;
        this.select = select;
        editor = new CodeEditor(font, val -> changed.accept(selected, val));
    }

    public void appearance(Appearance val){
        appearance = val;
        editor.appearance(new HelpModalStyle(val.canvas(), val.style().panel(), val.selected(), val.style().text(), val.style().muted(), val.style().accent()));
    }

    public void api(List<String> vals){ api = List.copyOf(vals); }

    public void actions(Runnable create, Consumer<String> place){ this.create = create; this.place = place; }
    // Let the host persist library actions without treating them as source edits
    public void documentActions(Consumer<String> rename, Consumer<String> delete){ this.rename = rename; this.delete = delete; }
    public void wrapPeripheral(Runnable action){ wrapPeripheral = action; }
    public void peripherals(List<ScriptPeripheral> vals){ editor.peripherals(vals); }
    public void insert(String source){ editor.insert(source); }

    public void refresh(List<Document> vals, String id){
        documents = List.copyOf(vals);
        boolean changed = !selected.equals(id);
        if(changed){ resultsScroll = 0; dropdown = 0; }
        selected = id;
        Document doc = document();
        if(doc == null){ editor.source("", changed); return; }
        editor.source(doc.source(), changed);
        List<String> completions = new ArrayList<>(LuaCompletions.standard());
        completions.add("node.persistentOutputs");
        completions.addAll(api);
        doc.inputs().forEach(port -> completions.add(LuaScripts.access("ports", port)));
        doc.outputs().forEach(port -> completions.add(LuaScripts.access("output", port)));
        editor.completions(completions);
    }

    private Document document(){ return documents.stream().filter(doc -> doc.id().equals(selected)).findFirst().orElse(null); }

    public void bounds(int x, int y, int width, int height){
        bounds(x, y, width, height, Math.min(180, Math.max(100, width / 5)), Math.min(230, Math.max(130, width / 4)), 0);
    }

    // Align the workspace with its host's existing sidebars and tab strip
    public void bounds(int x, int y, int width, int height, int left, int right, int tabHeight){
        bounds = new Rect(x, y, width, height);
        leftWidth = Math.max(0, left);
        rightWidth = Math.max(0, right);
        code = new Rect(x + leftWidth + 8, y + tabHeight + 33, Math.max(30, width - leftWidth - rightWidth - 16), Math.max(30, height - tabHeight - 41));
        input = new Rect(code.x(), y + tabHeight + 6, 88, 20);
        output = new Rect(code.x() + 94, y + tabHeight + 6, 94, 20);
        boolean wrapRow = wrapPeripheral != null && code.width() < 300;
        wrapButton = new Rect(code.x() + (wrapRow ? 0 : 194), y + tabHeight + (wrapRow ? 30 : 6), 106, 20);
        if(wrapRow) code = new Rect(code.x(), code.y() + 24, code.width(), Math.max(1, code.height() - 24));
        createButton = new Rect(x + 8, y + 32, Math.max(30, leftWidth - 16), 20);
        placeButton = new Rect(x + 8, y + 56, Math.max(30, leftWidth - 16), 20);
        browserTop = create == null ? y + 30 : y + 82;
        editor.bounds(code.x(), code.y(), code.width(), code.height());
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public void render(GuiGraphics graphics, int mouseX, int mouseY){
        paint(graphics, Surface.CANVAS, bounds, false, false);
        paint(graphics, Surface.SIDEBAR, new Rect(bounds.x(), bounds.y(), leftWidth, bounds.height()), false, false);
        paint(graphics, Surface.SIDEBAR, new Rect(bounds.right() - rightWidth, bounds.y(), rightWidth, bounds.height()), false, false);
        paint(graphics, Surface.HEADER, new Rect(bounds.x() + 4, bounds.y() + 2, leftWidth - 8, 25), false, false);
        graphics.drawString(font, "LUA NODES", bounds.x() + 12, bounds.y() + 10, appearance.style().muted(), false);
        if(create != null){
            button(graphics, createButton, "New Lua Function", mouseX, mouseY);
            if(document() != null && place != null) button(graphics, placeButton, "Add to Graph", mouseX, mouseY);
        }
        Rect browser = new Rect(bounds.x(), browserTop, leftWidth, Math.max(1, bounds.bottom() - browserTop));
        graphics.enableScissor(browser.x(), browser.y(), browser.right(), browser.bottom());
        for(int idx = 0; idx < documents.size(); idx++){
            Document doc = documents.get(idx);
            int top = browser.y() + idx * 31 - browserScroll;
            if(top + 30 < browser.y() || top > browser.bottom()) continue;
            paint(graphics, Surface.ROW, new Rect(browser.x() + 6, top, browser.width() - 12, 29), mouseX >= browser.x() && mouseX < browser.right() && mouseY >= top && mouseY < top + 29, doc.id().equals(selected));
            graphics.drawString(font, font.plainSubstrByWidth(doc.name(), Math.max(8, leftWidth - 68)), browser.x() + 22, top + 4, appearance.style().text(), false);
            graphics.drawString(font, doc.id().substring(0, Math.min(8, doc.id().length())), browser.x() + 22, top + 17, appearance.style().muted(), false);
            Rect pen = new Rect(browser.right() - 43, top + 5, 16, 19), trash = new Rect(browser.right() - 25, top + 5, 16, 19);
            if(rename != null){
                paint(graphics, Surface.BUTTON, pen, pen.contains(mouseX, mouseY), false);
                for(int step = 0; step < 7; step++) graphics.fill(pen.x() + 4 + step, pen.y() + 12 - step, pen.x() + 6 + step, pen.y() + 14 - step, appearance.style().text());
            }
            if(delete != null){
                paint(graphics, Surface.BUTTON, trash, trash.contains(mouseX, mouseY), false);
                graphics.hLine(trash.x() + 4, trash.x() + 11, trash.y() + 5, appearance.error());
                graphics.renderOutline(trash.x() + 5, trash.y() + 7, 6, 8, appearance.error());
            }
        }
        graphics.disableScissor();
        if(mouseY >= browserTop && mouseX >= bounds.x() + leftWidth - 43 && mouseX < bounds.x() + leftWidth - 9){
            int idx = (mouseY - browserTop + browserScroll) / 31;
            if(idx >= 0 && idx < documents.size()) graphics.renderTooltip(font, Component.literal(mouseX < bounds.x() + leftWidth - 27 ? "Rename Lua function" : "Delete Lua function and all instances"), mouseX, mouseY);
        }
        if(document() != null){
            button(graphics, input, "Insert Input", mouseX, mouseY);
            button(graphics, output, "Insert Output", mouseX, mouseY);
            if(wrapPeripheral != null) button(graphics, wrapButton, "Wrap peripheral", mouseX, mouseY);
            editor.render(graphics);
        }else graphics.drawString(font, "Create a Lua function to begin.", code.x() + 4, code.y() + 8, appearance.style().muted(), false);
        renderResults(graphics);
        if(dropdown != 0) renderDropdown(graphics, mouseX, mouseY);
    }

    private void renderResults(GuiGraphics graphics){
        Document doc = document();
        int x = bounds.right() - rightWidth + 8;
        paint(graphics, Surface.HEADER, new Rect(x - 4, bounds.y() + 2, rightWidth - 8, 25), false, false);
        graphics.drawString(font, "RESULTS", x + 4, bounds.y() + 10, appearance.style().muted(), false);
        if(doc == null) return;
        int top = bounds.y() + 33 - resultsScroll;
        int start = top;
        graphics.enableScissor(x - 2, bounds.y() + 30, bounds.right() - 4, bounds.bottom());
        top = wrapped(graphics, doc.status(), x, top, doc.error().isEmpty() ? appearance.style().text() : appearance.error());
        if(!doc.error().isEmpty()) top = wrapped(graphics, doc.error(), x, top + 6, appearance.error());
        for(String port : doc.outputs()){
            top += 9;
            top = wrapped(graphics, port, x, top, appearance.style().accent());
            GraphValue val = doc.results().get(port);
            String text = val == null ? "Waiting for result" : valueText(val);
            if(val != null) top = wrapped(graphics, val.type(), x, top, appearance.style().muted());
            if(text.length() > 4096) text = text.substring(0, 4096) + "…";
            top = wrapped(graphics, text, x, top + 2, appearance.style().text());
        }
        graphics.disableScissor();
        resultsHeight = top - start + 20;
    }

    private String valueText(GraphValue val){
        try{ return LuaScripts.value(val); }
        catch(IllegalArgumentException ex){ return "Unavailable: " + ex.getMessage(); }
    }

    private int wrapped(GuiGraphics graphics, String text, int x, int top, int col){
        for(var line : font.split(Component.literal(text), Math.max(20, rightWidth - 20))){
            graphics.drawString(font, line, x, top, col, false);
            top += font.lineHeight + 3;
        }
        return top;
    }

    private void button(GuiGraphics graphics, Rect rect, String label, int mx, int my){
        paint(graphics, Surface.BUTTON, rect, rect.contains(mx, my), false);
        graphics.drawString(font, label, rect.x() + (rect.width() - font.width(label)) / 2, rect.y() + 6, appearance.style().text(), false);
    }

    private void paint(GuiGraphics graphics, Surface surface, Rect rect, boolean hovered, boolean selected){
        if(appearance.painter() != null){ appearance.painter().draw(graphics, surface, rect.x(), rect.y(), rect.width(), rect.height(), hovered, selected); return; }
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), surface == Surface.CANVAS ? appearance.canvas()
                : selected ? appearance.selected() : hovered ? appearance.style().border() : appearance.style().panel());
    }

    private List<String> dropdownPorts(){
        Document doc = document();
        return doc == null ? List.of() : dropdown == 1 ? doc.inputs() : doc.outputs();
    }

    private Rect dropdownBounds(){
        Rect btn = dropdown == 1 ? input : output;
        int rows = Math.max(1, Math.min(12, Math.min(dropdownPorts().size(), (bounds.bottom() - btn.bottom() - 6) / 16)));
        return new Rect(btn.x(), btn.bottom() + 2, Math.min(220, code.width()), rows * 16 + 4);
    }

    private void renderDropdown(GuiGraphics graphics, int mouseX, int mouseY){
        Rect rect = dropdownBounds();
        paint(graphics, Surface.DROPDOWN, rect, false, false);
        graphics.enableScissor(rect.x(), rect.y(), rect.right(), rect.bottom());
        List<String> ports = dropdownPorts();
        if(ports.isEmpty()) graphics.drawString(font, "No ports", rect.x() + 5, rect.y() + 5, appearance.style().muted(), false);
        for(int idx = 0; idx < (rect.height() - 4) / 16 && idx + dropdownScroll < ports.size(); idx++)
            graphics.drawString(font, font.plainSubstrByWidth(ports.get(idx + dropdownScroll), rect.width() - 10), rect.x() + 5, rect.y() + 5 + idx * 16, appearance.style().text(), false);
        graphics.disableScissor();
        int idx = (mouseY - rect.y() - 2) / 16 + dropdownScroll;
        Document doc = document();
        if(dropdown == 1 && rect.contains(mouseX, mouseY) && idx >= 0 && idx < ports.size() && doc != null){
            GraphValue val = doc.inputValues().get(ports.get(idx));
            if(val != null){
                String text = valueText(val);
                if(text.length() > 1024) text = text.substring(0, 1024) + "...";
                graphics.renderTooltip(font, font.split(Component.literal(text), 240), mouseX, mouseY);
            }
        }
    }

    public boolean mouseClicked(double mx, double my, int button){
        if(button == 0 && document() != null && wrapPeripheral != null && wrapButton.contains(mx, my)){ dropdown = 0; wrapPeripheral.run(); return true; }
        if(button == 0 && create != null && createButton.contains(mx, my)){ create.run(); return true; }
        if(button == 0 && place != null && document() != null && placeButton.contains(mx, my)){ place.accept(selected); return true; }
        if(dropdown != 0){
            Rect rect = dropdownBounds();
            if(rect.contains(mx, my)){
                int idx = (int)((my - rect.y() - 2) / 16) + dropdownScroll;
                List<String> ports = dropdownPorts();
                if(button == 0 && idx >= 0 && idx < ports.size()) editor.insert(dropdown == 1
                        ? LuaScripts.inputSnippet(ports.get(idx)) : LuaScripts.outputSnippet(ports.get(idx)));
                dropdown = 0;
                return true;
            }
            dropdown = 0;
        }
        if(button == 0 && (input.contains(mx, my) || output.contains(mx, my))){
            dropdown = input.contains(mx, my) ? 1 : 2; dropdownScroll = 0; return true;
        }
        if(mx < bounds.x() + leftWidth && my >= browserTop){
            int idx = (int)((my - browserTop + browserScroll) / 31);
            if(button == 0 && idx >= 0 && idx < documents.size()){
                String id = documents.get(idx).id();
                int top = browserTop + idx * 31 - browserScroll;
                if(rename != null && new Rect(bounds.x() + leftWidth - 43, top + 5, 16, 19).contains(mx, my)) rename.accept(id);
                else if(delete != null && new Rect(bounds.x() + leftWidth - 25, top + 5, 16, 19).contains(mx, my)) delete.accept(id);
                else select.accept(id);
            }
            return true;
        }
        if(document() != null) editor.mouseClicked(mx, my, button);
        return bounds.contains(mx, my);
    }

    public boolean mouseScrolled(double mx, double my, double amount){
        if(dropdown != 0 && dropdownBounds().contains(mx, my)){
            dropdownScroll = Math.max(0, Math.min(Math.max(0, dropdownPorts().size() - (dropdownBounds().height() - 4) / 16), dropdownScroll - (int)amount));
        }else if(mx < bounds.x() + leftWidth){
            browserScroll = Math.max(0, Math.min(Math.max(0, documents.size() * 31 - bounds.height() + 30), browserScroll - (int)(amount * 20)));
        }else if(mx >= bounds.right() - rightWidth){
            resultsScroll = Math.max(0, Math.min(Math.max(0, resultsHeight - bounds.height() + 30), resultsScroll - (int)(amount * 20)));
        }else editor.mouseScrolled(amount);
        return true;
    }

    public boolean mouseDragged(double mx, double my){ return editor.mouseDragged(mx, my); }
    public void mouseReleased(){ editor.mouseReleased(); }
    public boolean charTyped(char ch){ return document() != null && editor.charTyped(ch); }
    public boolean keyPressed(int key){
        if(document() == null) return false;
        if(dropdown != 0 && key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE){ dropdown = 0; return true; }
        return editor.keyPressed(key);
    }

    private record Rect(int x, int y, int width, int height){
        int right(){ return x + width; }
        int bottom(){ return y + height; }
        boolean contains(double mx, double my){ return mx >= x && mx < right() && my >= y && my < bottom(); }
    }
}
