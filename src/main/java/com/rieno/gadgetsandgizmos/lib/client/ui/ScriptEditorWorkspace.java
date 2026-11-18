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
import com.rieno.gadgetsandgizmos.lib.script.ScriptInput;
import com.rieno.gadgetsandgizmos.lib.script.ScriptTerminal;
import com.rieno.gadgetsandgizmos.lib.script.ScriptWorkspaceState;
import com.rieno.gadgetsandgizmos.lib.script.ScriptEditorTabs;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
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
                           Map<String, GraphValue> results, String status, String error, Map<String, GraphValue> inputValues, ScriptTerminal terminal){
        public Document(String id, String name, String source, List<String> inputs, List<String> outputs,
                        Map<String, GraphValue> results, String status, String error, Map<String, GraphValue> inputValues){
            this(id, name, source, inputs, outputs, results, status, error, inputValues, null);
        }
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
    private final ScriptTerminalView terminalView = new ScriptTerminalView();
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
    private int preferredRightWidth = -1, preferredTerminalHeight = -1, tabHeight, footerHeight;
    private int defaultRightWidth;
    private Rect resultsResize = bounds, terminalResize = bounds, terminalArea = bounds;
    private boolean resizingResults, resizingTerminal;
    private int resultsTop;
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
    private ScriptFileWorkspace files;
    private String scriptsTitle = "Scripts", filesTitle = "Storage";
    private boolean scriptsCollapsed, filesCollapsed, resizingBrowser;
    private int preferredScriptHeight = -1;
    private Rect scriptsHeader = bounds, filesHeader = bounds, browserResize = bounds, scriptArea = bounds;
    private String editorId = "";
    private BiConsumer<String, ScriptInput> terminalInput;
    private final ScriptEditorTabs tabs = new ScriptEditorTabs();
    private final Map<String, CodeEditor.State> editorStates = new LinkedHashMap<>();
    private boolean initializedTabs, revealTab;
    private int tabScroll;
    private Rect tabStrip = bounds, fileNameArea = bounds, saveButton = bounds, autosaveButton = bounds;
    private Runnable saveDocument;
    private Runnable pendingClose;
    private List<String> closingFiles = List.of();
    private boolean waitingForSave;
    private String closeError = "";
    private Rect closePrompt = bounds;

    public ScriptEditorWorkspace(Font font, Consumer<String> select, BiConsumer<String, String> changed){
        this.font = font;
        this.select = select;
        editor = new CodeEditor(font, val -> { if(fileActive()) files.changed(val); else changed.accept(selected, val); });
    }

    public void browserTitles(String scripts, String files){ scriptsTitle = scripts; filesTitle = files; }
    public void files(ScriptFileWorkspace files){
        this.files = files;
        files.selection(() -> {
            if(files.active()) tabs.open(new ScriptEditorTabs.Tab(files.selected(), files.name(files.selected()), true));
            revealTab = true; refresh(documents, selected);
        });
    }
    public ScriptFileWorkspace files(){ return files; }
    public void tick(){ tick(true); }
    public void tick(boolean active){
        if(files != null) files.tick(active, active && fileActive());
        if(pendingClose == null || !waitingForSave) return;
        closeError = closingFiles.stream().map(files::error).filter(val -> !val.isEmpty()).findFirst().orElse("");
        if(!closeError.isEmpty()){ waitingForSave = false; return; }
        if(closingFiles.stream().noneMatch(id -> files.dirty(id) || files.saving(id))) finishClose();
    }
    public void saveFiles(){ if(files != null) files.save(true); }
    public ScriptEditorTabs tabs(){ return tabs; }
    public ScriptWorkspaceState state(){
        return new ScriptWorkspaceState(tabs.tabs(),tabs.active() == null ? "" : tabs.active().key(),
                files == null ? java.util.Set.of() : files.expanded(),scriptsCollapsed,filesCollapsed,
                preferredScriptHeight,preferredRightWidth,preferredTerminalHeight);
    }

    // Restore metadata before asynchronous file reads, including an intentionally empty tab strip
    public void restore(ScriptWorkspaceState state){
        if(state == null) return;
        blur(); initializedTabs = true;
        var saved = state.tabs().stream().filter(tab -> !tab.file() || files != null).toList();
        if(files != null) files.restore(saved.stream().filter(ScriptEditorTabs.Tab::file).map(ScriptEditorTabs.Tab::id).toList(),state.expanded());
        tabs.restore(saved,state.active());
        scriptsCollapsed = state.scriptsCollapsed(); filesCollapsed = state.filesCollapsed();
        preferredScriptHeight = state.scriptHeight(); preferredRightWidth = state.resultsWidth(); preferredTerminalHeight = state.terminalHeight();
        activateCurrent(); layout();
    }
    public boolean storageActive(){ return fileActive(); }
    public boolean modalOpen(){ return pendingClose != null || files != null && files.modalOpen(); }
    public void saveDocument(Runnable action){ saveDocument = action; }
    public void saveCurrent(){ if(fileActive()) files.save(files.selected()); else if(saveDocument != null) saveDocument.run(); }
    private boolean fileActive(){ return tabs.active() != null && tabs.active().file() && files != null && files.active(); }
    public void openDocument(String id){
        var doc = documents.stream().filter(val -> val.id().equals(id)).findFirst().orElse(null); if(doc == null) return;
        tabs.open(new ScriptEditorTabs.Tab(id, doc.name(), false)); initializedTabs = true; revealTab = true;
        if(files != null) files.clearSelection(); selected = id; select.accept(id); refresh(documents, id);
    }
    public boolean requestClose(Runnable action){
        var dirty = files == null ? List.<String>of() : files.openFiles().stream().filter(files::dirty).toList();
        if(dirty.isEmpty()) return false;
        promptClose(dirty, action); return true;
    }
    public void closeTab(String key){
        var tab = tabs.tabs().stream().filter(val -> val.key().equals(key)).findFirst().orElse(null); if(tab == null) return;
        Runnable close = () -> {
            if(tab.file() && files != null) files.closeFile(tab.id());
            tabs.close(key); editorStates.remove(key); activateCurrent();
        };
        if(tab.file() && files.dirty(tab.id())) promptClose(List.of(tab.id()), close); else close.run();
    }
    private void activateCurrent(){
        var tab = tabs.active();
        if(tab != null && !tab.file()){ selected = tab.id(); if(files != null) files.clearSelection(); select.accept(selected); }
        refresh(documents, selected); revealTab = true;
    }
    private boolean editable(){ return !fileActive() || !files.readOnly(); }

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
    public void terminalInput(BiConsumer<String, ScriptInput> input){
        terminalInput = input; blur(); terminalView.input(event -> {
            if(fileActive()) files.terminalInput(event); else if(terminalInput != null) terminalInput.accept(selected, event);
        });
    }
    public boolean terminalFocused(){ return terminalView.focused(); }
    public int resultsWidth(){ return rightWidth; }
    public void footerHeight(int height){ footerHeight = Math.max(0, height); }
    public Rect footerBounds(){ return new Rect(code.x(), bounds.bottom() - footerHeight, code.width(), footerHeight); }
    public void blur(){ terminalView.blur(); editor.focused(false); mouseReleased(); }

    public void refresh(List<Document> vals, String id){
        documents = List.copyOf(vals);
        for(var tab : tabs.tabs()){
            if(tab.file() && files != null){
                String next = files.canonicalId(tab.id());
                if(files.isOpen(next)){
                    var renamed = new ScriptEditorTabs.Tab(next, files.name(next), true);
                    if(!tab.key().equals(renamed.key())){
                        var state = editorStates.remove(tab.key()); if(state != null) editorStates.put(renamed.key(), state);
                        if(editorId.equals(tab.key())) editorId = renamed.key();
                    }
                    tabs.replace(tab.key(), renamed);
                }
            }else documents.stream().filter(doc -> doc.id().equals(tab.id())).findFirst()
                    .ifPresent(doc -> tabs.replace(tab.key(), new ScriptEditorTabs.Tab(doc.id(), doc.name(), false)));
        }
        tabs.retain(tab -> tab.file() ? files != null && files.isOpen(tab.id()) : documents.stream().anyMatch(doc -> doc.id().equals(tab.id())));
        if(!initializedTabs && tabs.active() == null && !documents.isEmpty()){
            var doc = documents.stream().filter(val -> val.id().equals(id)).findFirst().orElse(documents.getFirst());
            tabs.open(new ScriptEditorTabs.Tab(doc.id(), doc.name(), false));
        }
        if(tabs.active() != null) initializedTabs = true;
        var active = tabs.active(); String nextId = active == null ? "" : active.key();
        boolean changed = !editorId.equals(nextId);
        if(changed){
            if(!editorId.isEmpty() && tabs.tabs().stream().anyMatch(tab -> tab.key().equals(editorId))) editorStates.put(editorId, editor.state());
            else editorStates.remove(editorId);
            blur(); resultsScroll = 0; dropdown = 0; revealTab = true;
        }
        editorId = nextId;
        if(active != null && active.file()){
            if(!active.id().equals(files.selected())) files.activate(active.id());
        }else{
            if(files != null) files.clearSelection(); selected = active == null ? "" : active.id();
        }
        Document doc = document();
        terminalView.terminal(doc == null ? null : doc.terminal());
        if(doc == null){ editor.source("", changed); return; }
        editor.source(doc.source(), changed);
        if(changed) editor.restore(editorStates.get(nextId));
        List<String> completions = new ArrayList<>(LuaCompletions.standard()); completions.add("node.persistentOutputs"); completions.addAll(api);
        doc.inputs().forEach(port -> completions.add(LuaScripts.access("inputs", port)));
        doc.outputs().forEach(port -> completions.add(LuaScripts.access("outputs", port)));
        editor.completions(completions);
    }

    private Document document(){
        if(tabs.active() == null) return null;
        if(fileActive()) return files.document();
        return documents.stream().filter(doc -> doc.id().equals(selected)).findFirst().orElse(null);
    }

    public void bounds(int x, int y, int width, int height){
        bounds(x, y, width, height, Math.min(180, Math.max(100, width / 5)), Math.min(230, Math.max(130, width / 4)), 0);
    }

    // Align the workspace with its host's existing sidebars and tab strip
    public void bounds(int x, int y, int width, int height, int left, int right, int tabHeight){
        bounds = new Rect(x, y, width, height);
        leftWidth = Math.max(0, left);
        defaultRightWidth = Math.max(0, right); this.tabHeight = tabHeight;
        layout();
    }

    private void layout(){
        int x = bounds.x(), y = bounds.y(), width = bounds.width(), height = bounds.height();
        int maximum = Math.max(96, width - leftWidth - 156);
        rightWidth = Math.clamp(preferredRightWidth < 0 ? defaultRightWidth : preferredRightWidth, Math.min(144, maximum), maximum);
        int codeX = x + leftWidth + 8, codeWidth = Math.max(30, width - leftWidth - rightWidth - 16);
        tabStrip = new Rect(codeX, y + tabHeight + 4, codeWidth, 22);
        int toolbarY = tabStrip.bottom() + 5;
        input = new Rect(codeX, toolbarY, 88, 20); output = new Rect(codeX + 94, toolbarY, 94, 20);
        boolean wrapRow = wrapPeripheral != null && codeWidth < 300 && !fileActive();
        wrapButton = new Rect(codeX + (wrapRow ? 0 : 194), toolbarY + (wrapRow ? 24 : 0), 106, 20);
        fileNameArea = new Rect(codeX, toolbarY, codeWidth, 20);
        boolean narrow = fileActive() && codeWidth < 240;
        saveButton = new Rect(narrow ? codeX : codeX + codeWidth - 164, toolbarY + (narrow ? 23 : 0), 50, 20);
        autosaveButton = new Rect(saveButton.right() + 6, saveButton.y(), 108, 20);
        int codeY = toolbarY + 27 + (wrapRow ? 24 : 0) + (narrow ? 23 : 0);
        code = new Rect(codeX, codeY, codeWidth, Math.max(1, bounds.bottom() - codeY - 8 - footerHeight));
        createButton = new Rect(x + 8, y + 32, Math.max(30, leftWidth - 16), 20);
        placeButton = new Rect(x + 8, y + 56, Math.max(30, leftWidth - 16), 20);
        browserTop = create == null ? y + 30 : y + 82;
        layoutBrowser();
        editor.bounds(code.x(), code.y(), code.width(), code.height());
        var footer = footerBounds();
        editor.statusBounds(footerHeight > 0 ? ViewportHelpButtons.contentBounds(footer.x(), footer.right(), footer.bottom(), true) : null);
        int maximumHeight = Math.max(1, height - 112);
        var doc = document();
        double aspect = doc != null && doc.terminal() != null ? doc.terminal().height() * 9.0 / (doc.terminal().width() * 6.0) : 19 * 9.0 / (51 * 6.0);
        int fittedHeight = Math.max(1, (int)Math.round(Math.max(1, rightWidth - 16) * aspect));
        int cap = preferredTerminalHeight < 0 ? maximumHeight : Math.clamp(preferredTerminalHeight, 1, maximumHeight);
        int terminalHeight = Math.min(fittedHeight, cap);
        terminalArea = new Rect(bounds.right() - rightWidth + 8, y + 33, Math.max(1, rightWidth - 16), terminalHeight);
        terminalResize = new Rect(terminalArea.x(), terminalArea.bottom() + 2, terminalArea.width(), 6);
        resultsResize = new Rect(bounds.right() - rightWidth - 4, y + tabHeight, 8, Math.max(1, height - tabHeight));
        resultsTop = terminalResize.bottom() + 8;
        terminalView.bounds(terminalArea.x(), terminalArea.y(), terminalArea.width(), terminalArea.height());
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
        paint(graphics, Surface.HEADER, scriptsHeader, scriptsHeader.contains(mouseX, mouseY), false);
        graphics.drawString(font, (scriptsCollapsed ? "> " : "v ") + scriptsTitle, scriptsHeader.x() + 8, scriptsHeader.y() + 8, appearance.style().muted(), false);
        if(!scriptsCollapsed && create != null){
            button(graphics, createButton, "New Lua Function", mouseX, mouseY);
            if(!fileActive() && document() != null && place != null) button(graphics, placeButton, "Add to Graph", mouseX, mouseY);
        }
        Rect browser = scriptArea;
        if(browser.height() > 0){
            graphics.enableScissor(browser.x(), browser.y(), browser.right(), browser.bottom());
            for(int idx = 0; idx < documents.size(); idx++){
                Document doc = documents.get(idx); int top = browser.y() + idx * 31 - browserScroll;
                if(top + 30 < browser.y() || top >= browser.bottom()) continue;
                paint(graphics, Surface.ROW, new Rect(browser.x() + 6, top, browser.width() - 12, 29),
                        browser.contains(mouseX, mouseY) && mouseY >= top && mouseY < top + 29, !fileActive() && doc.id().equals(selected));
                graphics.drawString(font, font.plainSubstrByWidth(doc.name(), Math.max(8, leftWidth - (files != null && files.canCopyToStorage() ? 86 : 68))), browser.x() + 22, top + 4, appearance.style().text(), false);
                graphics.drawString(font, doc.id().substring(0, Math.min(8, doc.id().length())), browser.x() + 22, top + 17, appearance.style().muted(), false);
                Rect pen = new Rect(browser.right() - 43, top + 5, 16, 19), trash = new Rect(browser.right() - 25, top + 5, 16, 19);
                if(files != null && files.canCopyToStorage()){
                    var disk = new Rect(browser.right() - 61, top + 5, 16, 19);
                    paint(graphics, Surface.BUTTON, disk, disk.contains(mouseX, mouseY), false);
                    FileActionIcon.TO_STORAGE.draw(graphics, disk.x(), disk.y() + 2, appearance.style().text());
                }
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
        }
        if(document() != null){
            if(!fileActive()){
                button(graphics, input, "Insert Input", mouseX, mouseY);
                button(graphics, output, "Insert Output", mouseX, mouseY);
                if(wrapPeripheral != null) button(graphics, wrapButton, "Wrap peripheral", mouseX, mouseY);
            }else{
                int labelWidth = saveButton.y() == fileNameArea.y() ? Math.max(1, saveButton.x() - fileNameArea.x() - 8) : fileNameArea.width();
                graphics.drawString(font, font.plainSubstrByWidth(document().name() + (files.dirty(files.selected()) ? " *" : ""), labelWidth), fileNameArea.x(), fileNameArea.y() + 6, appearance.style().text(), false);
                button(graphics, saveButton, files.readOnly() ? "Read only" : "Save", mouseX, mouseY);
                button(graphics, autosaveButton, "Autosave: " + (files.autosave() ? "On" : "Off"), mouseX, mouseY);
            }
            editor.render(graphics, mouseX, mouseY);
        }else graphics.drawString(font, files == null ? "Create a Lua function to begin." : "Select a script or create a Lua function.", code.x() + 4, code.y() + 8, appearance.style().muted(), false);
        if(files != null){
            paint(graphics, Surface.HEADER, filesHeader, filesHeader.contains(mouseX, mouseY), false);
            graphics.drawString(font, (filesCollapsed ? "> " : "v ") + filesTitle, filesHeader.x() + 8, filesHeader.y() + 8, appearance.style().muted(), false);
            graphics.fill(browserResize.x(), browserResize.y() + 2, browserResize.right(), browserResize.y() + 4,
                    resizingBrowser || browserResize.contains(mouseX, mouseY) ? appearance.style().accent() : appearance.style().border());
            if(!filesCollapsed) files.render(graphics, appearance.style(), mouseX, mouseY);
        }
        renderResults(graphics);
        int divider = resizingResults || resultsResize.contains(mouseX, mouseY) ? appearance.style().accent() : appearance.style().border();
        graphics.fill(resultsResize.x() + 3, resultsResize.y(), resultsResize.x() + 5, resultsResize.bottom(), divider);
        if(document() != null){
            divider = resizingTerminal || terminalResize.contains(mouseX, mouseY) ? appearance.style().accent() : appearance.style().border();
            graphics.fill(terminalResize.x(), terminalResize.y() + 2, terminalResize.right(), terminalResize.y() + 4, divider);
        }
        renderTabs(graphics, mouseX, mouseY);
        if(dropdown != 0) renderDropdown(graphics, mouseX, mouseY);
        if(files != null && files.canCopyToStorage() && scriptArea.contains(mouseX, mouseY)){
            int idx = (mouseY - browserTop + browserScroll) / 31;
            if(idx >= 0 && idx < documents.size() && new Rect(scriptArea.right() - 61, browserTop + idx * 31 - browserScroll + 5, 16, 19).contains(mouseX, mouseY))
                graphics.renderTooltip(font, Component.literal("Copy to disk"), mouseX, mouseY);
        }
        if(files != null){ files.renderToolbarTooltip(graphics, mouseX, mouseY); files.renderOverlay(graphics, appearance.style(), mouseX, mouseY); }
        if(pendingClose != null) renderClosePrompt(graphics, mouseX, mouseY);
    }

    private void renderResults(GuiGraphics graphics){
        Document doc = document();
        int x = bounds.right() - rightWidth + 8;
        paint(graphics, Surface.HEADER, new Rect(x - 4, bounds.y() + 2, rightWidth - 8, 25), false, false);
        graphics.drawString(font, fileActive() ? "PREVIEW" : "RESULTS", x + 4, bounds.y() + 10, appearance.style().muted(), false);
        if(doc == null) return;
        graphics.fill(terminalArea.x(), terminalArea.y(), terminalArea.right(), terminalArea.bottom(), 0xFF080808);
        if(doc.terminal() != null) terminalView.render(graphics);
        else graphics.drawWordWrap(font, Component.literal("Terminal unavailible, use \"term.attach()\" to enable it"),
                terminalArea.x() + 6, terminalArea.y() + 8, Math.max(1, terminalArea.width() - 12), appearance.style().muted());
        graphics.renderOutline(terminalArea.x(), terminalArea.y(), terminalArea.width(), terminalArea.height(), terminalView.focused() ? appearance.style().accent() : appearance.style().border());
        int top = resultsTop - resultsScroll;
        int start = top;
        graphics.enableScissor(x - 2, resultsTop, bounds.right() - 4, bounds.bottom());
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
        resultsScroll = Math.clamp(resultsScroll, 0, Math.max(0, resultsHeight - bounds.bottom() + resultsTop));
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
        if(pendingClose != null) return clickClosePrompt(mx, my, button);
        if(files != null && files.overlayClick(mx, my, button)) return true;
        if(document() != null && editable() && editor.clickSuggestion(mx, my, button)) return true;
        if(tabStrip.contains(mx, my)) return clickTabs(mx, my, button);
        if(fileActive() && button == 0 && saveButton.contains(mx, my)){ if(!files.readOnly()) saveCurrent(); return true; }
        if(fileActive() && button == 0 && autosaveButton.contains(mx, my)){ files.autosave(!files.autosave()); return true; }
        if(button == 0 && scriptsHeader.contains(mx, my)){ scriptsCollapsed = !scriptsCollapsed; layout(); return true; }
        if(files != null){
            if(button == 0 && filesHeader.contains(mx, my)){ filesCollapsed = !filesCollapsed; layout(); return true; }
            if(button == 0 && browserResize.contains(mx, my)){ blur(); resizingBrowser = true; return true; }
            if(!filesCollapsed && files.mouseClicked(mx, my, button)) return true;
        }
        if(button == 0 && resultsResize.contains(mx, my)){ blur(); dropdown = 0; resizingResults = true; return true; }
        if(button == 0 && document() != null && terminalResize.contains(mx, my)){ blur(); dropdown = 0; resizingTerminal = true; return true; }
        if(document() != null && terminalView.mouseClicked(mx, my, button)){ editor.focused(false); dropdown = 0; return true; }
        terminalView.blur();
        if(!code.contains(mx, my)) editor.focused(false);
        if(!fileActive() && button == 0 && document() != null && wrapPeripheral != null && wrapButton.contains(mx, my)){ dropdown = 0; wrapPeripheral.run(); return true; }
        if(!scriptsCollapsed && button == 0 && create != null && createButton.contains(mx, my)){ create.run(); return true; }
        if(!scriptsCollapsed && !fileActive() && button == 0 && place != null && document() != null && placeButton.contains(mx, my)){ place.accept(selected); return true; }
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
        if(!fileActive() && button == 0 && (input.contains(mx, my) || output.contains(mx, my))){
            dropdown = input.contains(mx, my) ? 1 : 2; dropdownScroll = 0; return true;
        }
        if(scriptArea.contains(mx, my)){
            int idx = (int)((my - browserTop + browserScroll) / 31);
            if(button == 0 && idx >= 0 && idx < documents.size()){
                String id = documents.get(idx).id();
                int top = browserTop + idx * 31 - browserScroll;
                if(files != null && files.canCopyToStorage() && new Rect(bounds.x() + leftWidth - 61, top + 5, 16, 19).contains(mx, my)){
                    var doc = documents.get(idx); files.copyToStorage(doc.name(), doc.source(), (int)mx, (int)my);
                }else if(rename != null && new Rect(bounds.x() + leftWidth - 43, top + 5, 16, 19).contains(mx, my)) rename.accept(id);
                else if(delete != null && new Rect(bounds.x() + leftWidth - 25, top + 5, 16, 19).contains(mx, my)) delete.accept(id);
                else openDocument(id);
            }
            return true;
        }
        if(document() != null) editor.mouseClicked(mx, my, button);
        return bounds.contains(mx, my);
    }

    public boolean mouseScrolled(double mx, double my, double amount){
        if(pendingClose != null) return true;
        if(document() != null && editor.scrollSuggestions(mx, my, amount)) return true;
        if(tabStrip.contains(mx, my)){ tabScroll = Math.clamp(tabScroll - (int)(amount * 60), 0, Math.max(0, tabsWidth() - tabStrip.width())); return true; }
        if(files != null && !filesCollapsed && files.mouseScrolled(mx, my, amount)) return true;
        if(dropdown != 0 && dropdownBounds().contains(mx, my)){
            dropdownScroll = Math.max(0, Math.min(Math.max(0, dropdownPorts().size() - (dropdownBounds().height() - 4) / 16), dropdownScroll - (int)amount));
        }else if(terminalArea.contains(mx, my) && document() != null){
            terminalView.mouseScrolled(mx, my, amount);
        }else if(mx < bounds.x() + leftWidth){
            browserScroll = Math.max(0, Math.min(Math.max(0, documents.size() * 31 - scriptArea.height()), browserScroll - (int)(amount * 20)));
        }else if(mx >= bounds.right() - rightWidth){
            resultsScroll = Math.clamp(resultsScroll - (int)(amount * 20), 0, Math.max(0, resultsHeight - bounds.bottom() + resultsTop));
        }else editor.mouseScrolled(amount);
        return true;
    }

    public boolean mouseDragged(double mx, double my){
        if(pendingClose != null) return true;
        if(resizingBrowser){ preferredScriptHeight = (int)my - bounds.y(); layout(); return true; }
        if(resizingResults){ preferredRightWidth = (int)Math.round(bounds.right() - mx); layout(); preferredRightWidth = rightWidth; return true; }
        if(resizingTerminal){ preferredTerminalHeight = (int)Math.round(my - terminalArea.y()); layout(); preferredTerminalHeight = terminalArea.height(); return true; }
        return terminalView.mouseDragged(mx, my) || editor.mouseDragged(mx, my);
    }
    public void mouseReleased(){ terminalView.mouseReleased(); resizingResults = false; resizingTerminal = false; resizingBrowser = false; editor.mouseReleased(); }
    public boolean charTyped(char ch){ return charTyped(ch, 0); }
    public boolean charTyped(char ch, int modifiers){ return pendingClose != null || files != null && files.charTyped(ch) || terminalView.charTyped(ch) || editable() && document() != null && editor.charTyped(ch, modifiers); }
    public boolean keyReleased(int key){ return terminalView.keyReleased(key); }
    public boolean keyPressed(int key){ return keyPressed(key, 0); }
    public boolean keyPressed(int key, int modifiers){
        if(pendingClose != null){
            if(key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) cancelClose();
            else if(key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER && !waitingForSave) saveBeforeClose();
            return true;
        }
        if(files != null && files.keyPressed(key)) return true;
        if(document() == null) return false;
        if(terminalView.keyPressed(key)) return true;
        if(CodeEditor.controlDown(modifiers) && key == org.lwjgl.glfw.GLFW.GLFW_KEY_S){ saveCurrent(); return true; }
        if(CodeEditor.controlDown(modifiers) && key == org.lwjgl.glfw.GLFW.GLFW_KEY_W && tabs.active() != null){ closeTab(tabs.active().key()); return true; }
        if(dropdown != 0 && key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE){ dropdown = 0; return true; }
        if(key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) return editor.keyPressed(key, modifiers);
        if(!editable()){
            boolean copy = CodeEditor.controlDown(modifiers) && (key == org.lwjgl.glfw.GLFW.GLFW_KEY_C || key == org.lwjgl.glfw.GLFW.GLFW_KEY_A);
            if(!copy && key != org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT && key != org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT && key != org.lwjgl.glfw.GLFW.GLFW_KEY_UP
                    && key != org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN && key != org.lwjgl.glfw.GLFW.GLFW_KEY_HOME && key != org.lwjgl.glfw.GLFW.GLFW_KEY_END) return true;
        }
        return editor.keyPressed(key, modifiers);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private int tabWidth(ScriptEditorTabs.Tab tab){
        return Math.clamp(font.width((tab.file() ? "" : "Script: ") + tab.name()) + 43, 72, 180);
    }
    private int tabsWidth(){ return tabs.tabs().stream().mapToInt(tab -> tabWidth(tab) + 3).sum(); }
    private Rect tabsViewport(){
        boolean overflow = tabsWidth() > tabStrip.width();
        return new Rect(tabStrip.x() + (overflow ? 16 : 0), tabStrip.y(), Math.max(1, tabStrip.width() - (overflow ? 32 : 0)), tabStrip.height());
    }
    private Rect tabBounds(String key){
        int x = tabsViewport().x() - tabScroll;
        for(var tab : tabs.tabs()){
            int width = tabWidth(tab);
            if(tab.key().equals(key)) return new Rect(x, tabStrip.y(), width, tabStrip.height());
            x += width + 3;
        }
        return new Rect(0, 0, 0, 0);
    }
    private void renderTabs(GuiGraphics graphics, int mx, int my){
        Rect viewport = tabsViewport();
        tabScroll = Math.clamp(tabScroll, 0, Math.max(0, tabsWidth() - viewport.width()));
        if(revealTab && tabs.active() != null){
            Rect rect = tabBounds(tabs.active().key());
            if(rect.x() < viewport.x()) tabScroll = Math.max(0, tabScroll - viewport.x() + rect.x());
            else if(rect.right() > viewport.right()) tabScroll += rect.right() - viewport.right();
            revealTab = false;
        }
        graphics.enableScissor(viewport.x(), viewport.y(), viewport.right(), viewport.bottom());
        for(var tab : tabs.tabs()){
            Rect rect = tabBounds(tab.key()); boolean active = tabs.active() != null && tabs.active().key().equals(tab.key());
            paint(graphics, Surface.BUTTON, rect, rect.contains(mx, my), active);
            boolean dirty = tab.file() && files.dirty(tab.id());
            String label = (tab.file() ? "" : "Script: ") + tab.name();
            graphics.drawString(font, font.plainSubstrByWidth(label, rect.width() - (dirty ? 37 : 26)), rect.x() + 6, rect.y() + 7, appearance.style().text(), false);
            if(dirty) graphics.drawString(font, "*", rect.right() - 25, rect.y() + 7, appearance.style().accent(), false);
            graphics.drawString(font, "x", rect.right() - 13, rect.y() + 7, appearance.style().muted(), false);
            if(active) graphics.fill(rect.x(), rect.bottom() - 2, rect.right(), rect.bottom(), appearance.style().accent());
        }
        graphics.disableScissor();
        if(tabsWidth() > tabStrip.width()){
            button(graphics, new Rect(tabStrip.x(), tabStrip.y(), 14, 22), "<", mx, my);
            button(graphics, new Rect(tabStrip.right() - 14, tabStrip.y(), 14, 22), ">", mx, my);
        }
        if(viewport.contains(mx, my)) for(var tab : tabs.tabs()) if(tabBounds(tab.key()).contains(mx, my)){
            graphics.renderTooltip(font, Component.literal(tab.file() ? files.locationLabel(tab.id()) : "Script: " + tab.name()), mx, my); break;
        }
    }
    private boolean clickTabs(double mx, double my, int button){
        Rect viewport = tabsViewport();
        if(!viewport.contains(mx, my)){
            if(button == 0) tabScroll = Math.clamp(tabScroll + (mx < viewport.x() ? -120 : 120), 0, Math.max(0, tabsWidth() - viewport.width()));
            return true;
        }
        for(var tab : tabs.tabs()){
            Rect rect = tabBounds(tab.key()); if(!rect.contains(mx, my)) continue;
            if(button == 2 || button == 0 && mx >= rect.right() - 18) closeTab(tab.key());
            else if(button == 0){ tabs.activate(tab.key()); activateCurrent(); }
            return true;
        }
        return true;
    }

    private void promptClose(List<String> ids, Runnable action){
        blur(); closingFiles = List.copyOf(ids); pendingClose = action; waitingForSave = false; closeError = "";
    }
    private void cancelClose(){ pendingClose = null; closingFiles = List.of(); waitingForSave = false; closeError = ""; }
    private void finishClose(){ Runnable action = pendingClose; cancelClose(); if(action != null) action.run(); }
    private void saveBeforeClose(){
        if(waitingForSave) return;
        if(closingFiles.stream().anyMatch(id -> files.buffers().readOnly(id))){ closeError = "A file is read only. Keep the tab open or discard its changes."; return; }
        closeError = ""; waitingForSave = true; closingFiles.forEach(files::save);
    }
    private Rect closeButton(int idx){
        int width = Math.max(1, (closePrompt.width() - 32) / 3);
        return new Rect(closePrompt.x() + 8 + idx * (width + 8), closePrompt.bottom() - 30, width, 20);
    }
    private void renderClosePrompt(GuiGraphics graphics, int mx, int my){
        int width = Math.min(380, graphics.guiWidth() - 20);
        closePrompt = new Rect((graphics.guiWidth() - width) / 2, (graphics.guiHeight() - 142) / 2, width, 142);
        graphics.pose().pushPose(); graphics.pose().translate(0, 0, 500);
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), 0xA0000000);
        paint(graphics, Surface.DROPDOWN, closePrompt, false, false);
        graphics.drawString(font, "Unsaved file changes", closePrompt.x() + 12, closePrompt.y() + 12, appearance.style().text(), false);
        String message = waitingForSave ? "Saving files..." : !closeError.isEmpty() ? closeError : "Save changes before closing?\n"
                + closingFiles.stream().map(files::name).limit(3).collect(java.util.stream.Collectors.joining(", "));
        graphics.drawWordWrap(font, Component.literal(message), closePrompt.x() + 12, closePrompt.y() + 36, width - 24, appearance.style().muted());
        button(graphics, closeButton(0), "Save", mx, my); button(graphics, closeButton(1), "Discard", mx, my); button(graphics, closeButton(2), "Cancel", mx, my);
        graphics.pose().popPose();
    }
    private boolean clickClosePrompt(double mx, double my, int button){
        if(button != 0) return true;
        if(closeButton(2).contains(mx, my)) cancelClose();
        else if(!waitingForSave && closeButton(0).contains(mx, my)) saveBeforeClose();
        else if(!waitingForSave && closeButton(1).contains(mx, my)){ closingFiles.forEach(files::discard); finishClose(); }
        return true;
    }

    private void layoutBrowser(){
        int y = bounds.y(), height = bounds.height();
        int split = files == null ? height : scriptsCollapsed ? 25 : filesCollapsed ? height - 31
                : Math.clamp(preferredScriptHeight < 0 ? height / 2 : preferredScriptHeight, Math.min(110, height / 2), Math.max(110, height - 75));
        scriptsHeader = new Rect(bounds.x() + 4, y + 2, Math.max(1, leftWidth - 8), 25);
        browserTop = create == null ? y + 30 : y + 82;
        scriptArea = new Rect(bounds.x(), browserTop, leftWidth, scriptsCollapsed ? 0 : Math.max(0, split - (browserTop - y)));
        browserResize = new Rect(bounds.x() + 4, y + split, Math.max(1, leftWidth - 8), 6);
        filesHeader = new Rect(bounds.x() + 4, browserResize.bottom(), Math.max(1, leftWidth - 8), 25);
        if(files != null){
            files.toolbarBounds(new Rect(bounds.x() + 8, filesHeader.bottom() + 2, Math.max(0, leftWidth - 16), filesCollapsed ? 0 : 20));
            files.bounds(new Rect(bounds.x(), filesHeader.bottom() + 25, leftWidth, filesCollapsed ? 0 : Math.max(0, bounds.bottom() - filesHeader.bottom() - 25)));
        }
        browserScroll = Math.clamp(browserScroll, 0, Math.max(0, documents.size() * 31 - scriptArea.height()));
    }

    public record Rect(int x, int y, int width, int height){
        public int right(){ return x + width; }
        public int bottom(){ return y + height; }
        public boolean contains(double mx, double my){ return mx >= x && mx < right() && my >= y && my < bottom(); }
    }
}
