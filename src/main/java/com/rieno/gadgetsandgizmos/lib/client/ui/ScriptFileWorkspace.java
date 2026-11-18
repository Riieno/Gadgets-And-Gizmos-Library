package com.rieno.gadgetsandgizmos.lib.client.ui;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.script.ScriptFiles;
import com.rieno.gadgetsandgizmos.lib.script.ScriptFileBuffers;
import com.rieno.gadgetsandgizmos.lib.script.ScriptSourceEdits;
import com.rieno.gadgetsandgizmos.lib.script.ScriptTerminal;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.BiConsumer;

// Share a lazy file browser, clipboard, source saves and preview selection across storage providers
public final class ScriptFileWorkspace{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private record File(String root, String path, String name, boolean directory, boolean readOnly, boolean protectedEntry, int depth){
        String id(){ return root + "\n" + path; }
    }
    private final Font font;
    private final Consumer<CompoundTag> send;
    private final Map<String, String> roots = new LinkedHashMap<>();
    private final Map<String, List<File>> children = new HashMap<>();
    private final Map<String, File> openFiles = new LinkedHashMap<>();
    private final Map<String, String> redirects = new HashMap<>();
    private final ScriptFileBuffers buffers = new ScriptFileBuffers();
    private final Set<String> expanded = new HashSet<>(), requested = new HashSet<>(), startup = new HashSet<>();
    private final Set<String> restoring = new HashSet<>(), restoreRequests = new HashSet<>();
    private final Set<String> readOnlyRoots = new HashSet<>();
    private record Destination(String root, String path, String name){}
    private final List<Destination> destinations = new ArrayList<>();
    private String exportName = "", exportSource = "";
    private boolean choosingDestination;
    private BiConsumer<String, String> addToGraph, importScript;
    private String importLabel = "Copy to Scripts";
    private File selected, focused, clipboard, menuFile;
    private boolean cut;
    private Consumer<java.nio.file.Path> reveal;
    private ScriptEditorWorkspace.Rect toolbar = new ScriptEditorWorkspace.Rect(0, 0, 0, 0);
    private Runnable selection = () -> {};
    private String message = "", previewRoot = "", previewPath = "", previewError = "";
    private ScriptTerminal terminal;
    private boolean previewVisible, previewRunning;
    private String previewId = "";
    private int scroll, ticks, saveTicks, menuX, menuY;
    private long requestId;
    private ScriptEditorWorkspace.Rect bounds = new ScriptEditorWorkspace.Rect(0, 0, 1, 1);
    private List<String> menu = List.of();
    private TextInputModal modal;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public ScriptFileWorkspace(Font font, Consumer<CompoundTag> send){ this.font = font; this.send = send; }
    public void addToGraph(BiConsumer<String, String> action){ addToGraph = action; }
    public void importScript(BiConsumer<String, String> action){ importScript = action; }
    public void importScript(String label, BiConsumer<String, String> action){ importLabel = label; importScript = action; }
    public boolean canCopyToStorage(){ return !destinations.isEmpty(); }

    // The host marks writable export folders without tying the browser to a storage implementation
    public void copyToStorage(String name, String source, int x, int y){
        if(destinations.isEmpty()) return;
        exportName = name; exportSource = source; choosingDestination = true;
        menu = destinations.stream().map(Destination::name).toList(); menuX = x; menuY = y;
        var mc = net.minecraft.client.Minecraft.getInstance();
        menuX = Math.clamp(menuX, 0, Math.max(0, mc.getWindow().getGuiScaledWidth() - 154));
        menuY = Math.clamp(menuY, 0, Math.max(0, mc.getWindow().getGuiScaledHeight() - menu.size() * 18 - 4));
    }
    private void export(Destination dest){
        String name = exportName.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
        if(!name.toLowerCase(java.util.Locale.ROOT).endsWith(".lua")) name += ".lua";
        String source = exportSource;
        modal = new TextInputModal(font, Component.literal("Copy script"), Component.literal(dest.name()), name, 255, val -> {
            try{
                if(val.contains("/")) throw new IllegalArgumentException("Use a file name");
                var req = request("store", dest.root(), ScriptFiles.child(dest.path(), val)); req.putString("Source", source); send.accept(req);
            }catch(IllegalArgumentException ex){ message = ex.getMessage(); }
        }, () -> modal = null);
        modal.confirmLabel(Component.literal("Copy"));
    }
    public void selection(Runnable action){ selection = action; }
    public boolean active(){ return selected != null; }
    public boolean readOnly(){ return selected == null || buffers.readOnly(selected.id()); }
    public String selected(){ return selected == null ? "" : selected.id(); }
    public void bounds(ScriptEditorWorkspace.Rect rect){ bounds = rect; clampScroll(); }
    public void clearSelection(){ selected = null; previewVisible(false); }
    public ScriptFileBuffers buffers(){ return buffers; }
    public boolean autosave(){ return buffers.autosave(); }
    public void autosave(boolean val){ buffers.autosave(val); if(val) saveTicks = 10; }
    public void revealFiles(Consumer<java.nio.file.Path> action){ reveal = action; }
    public void toolbarBounds(ScriptEditorWorkspace.Rect rect){ toolbar = rect; }
    public List<String> openFiles(){ return List.copyOf(openFiles.keySet()); }
    public Set<String> expanded(){ return Set.copyOf(expanded); }

    // Restore identities before requesting storage so late replies cannot reorder the tab strip
    public void restore(List<String> files, Set<String> folders){
        expanded.clear(); expanded.addAll(folders);
        for(String id : files){
            if(!com.rieno.gadgetsandgizmos.lib.script.ScriptWorkspaceState.storageId(id,true) || openFiles.containsKey(id)) continue;
            int idx = id.indexOf('\n'); String root = id.substring(0,idx), path = id.substring(idx + 1);
            openFiles.put(id,new File(root,path,ScriptFiles.name(path),false,true,false,0)); restoring.add(id);
        }
        send.accept(request("catalog","","")); ticks = 1;
    }
    public boolean isOpen(String id){ return openFiles.containsKey(id); }
    public String canonicalId(String id){ return redirects.getOrDefault(id, id); }
    public String name(String id){ var file = openFiles.get(id); return file == null ? "" : file.name(); }
    public String locationLabel(String id){
        var file = openFiles.get(id); return file == null ? "" : roots.getOrDefault(file.root(), "Unavailable storage") + "/" + file.path();
    }
    public boolean dirty(String id){ return buffers.dirty(id); }
    public boolean saving(String id){ return buffers.saving(id); }
    public String error(String id){ return buffers.error(id); }
    public void discard(String id){ buffers.discard(id); }
    public void closeFile(String id){
        openFiles.remove(id); buffers.remove(id);
        restoring.remove(id); restoreRequests.remove(id);
        if(selected != null && selected.id().equals(id)){ selected = null; previewVisible(false); }
    }
    public boolean activate(String id){
        var file = openFiles.get(id); if(file == null) return false;
        activateFile(file); return true;
    }
    public void close(){
        previewVisible(false);
        buffers.automatic(); dispatch();
        for(var save = buffers.flush(++requestId); save != null; save = buffers.flush(++requestId)) sendSave(save);
    }

    // Keep source tabs open while releasing the engine behind a hidden preview
    public void previewVisible(boolean visible){
        visible &= selected != null;
        if(previewVisible == visible) return;
        previewVisible = visible;
        if(!visible) stopPreview(); else if(!previewRoot.isEmpty() && !previewPath.isEmpty()) startPreview();
    }
    private void stopPreview(){
        if(previewRunning) send.accept(previewRequest("preview_close"));
        previewRunning = false; previewId = ""; terminal = null;
    }
    private CompoundTag previewRequest(String action){
        var req = request(action, previewRoot, previewPath); req.putString("PreviewId", previewId); return req;
    }
    private void startPreview(){
        if(!previewVisible || previewRoot.isEmpty() || previewPath.isEmpty()) return;
        previewId = java.util.UUID.randomUUID().toString(); previewRunning = true; terminal = null; previewError = "";
        var req = previewRequest("preview"); String draft = buffers.source(previewRoot + "\n" + previewPath);
        if(draft != null) req.putString("Draft", draft); send.accept(req);
    }

    public ScriptEditorWorkspace.Document document(){
        if(selected == null) return null;
        String source = buffers.source(selected.id()); if(source == null) return null;
        String error = buffers.error(selected.id()); if(error.isEmpty()) error = previewError;
        return new ScriptEditorWorkspace.Document(selected.id(), selected.name(), source, List.of(), List.of(), Map.of(),
                (readOnly() ? "Read only" : buffers.status(selected.id())) + (previewPath.isEmpty() ? " | Choose Load in preview from a file menu" : " | Preview: " + previewPath),
                error, Map.of(), terminal);
    }

    public void changed(String source){ if(selected != null){ buffers.edit(selected.id(), source); if(autosave()) saveTicks = 10; } }
    public void tick(){ tick(true); }
    public void tick(boolean active){ tick(active, active); }
    public void tick(boolean active, boolean visible){
        previewVisible(visible);
        if(active && ticks++ % 40 == 0) send.accept(request("catalog", "", ""));
        if(saveTicks > 0 && --saveTicks == 0){ buffers.automatic(); dispatch(); }
        if(previewVisible && previewRunning && ticks % 4 == 0) send.accept(previewRequest("preview_poll"));
    }
    public void save(boolean retry){ buffers.saveAll(retry); dispatch(); }
    public void save(String id){ buffers.save(id); dispatch(); }
    private void dispatch(){ for(var save = buffers.submit(++requestId); save != null; save = buffers.submit(++requestId)) sendSave(save); }
    private void sendSave(ScriptSourceEdits.Save save){
        int idx = save.id().indexOf('\n'); var req = request("write", save.id().substring(0, idx), save.id().substring(idx + 1));
        req.putLong("Request", save.requestId()); req.putString("Previous", save.previous()); req.putString("Source", save.source()); send.accept(req);
    }
    private void select(File file){
        activateFile(file); selection.run();
        if(!buffers.contains(file.id())) send.accept(request("read", file.root(), file.path()));
    }
    private void activateFile(File file){
        selected = file; focused = file; openFiles.put(file.id(), file);
        if(!previewRoot.equals(file.root())){
            stopPreview();
            previewRoot = file.root(); previewPath = ""; terminal = null; previewError = "";
            if(startup.contains(file.root())) preview(file.root(), "startup.lua");
        }
    }

    public void terminalInput(com.rieno.gadgetsandgizmos.lib.script.ScriptInput input){
        if(!previewVisible || !previewRunning) return;
        var req = previewRequest("preview_input"); req.putString("Input", input.action());
        req.putDouble("X", input.x()); req.putDouble("Y", input.y()); req.putInt("Value", input.value()); req.putString("Text", input.text()); send.accept(req);
    }

    public void accept(CompoundTag res){
        String action = res.getString("Action"), root = res.getString("Root"), path = res.getString("Path"), id = root + "\n" + path;
        if(action.equals("read") && res.getBoolean("Restore") && !restoring.contains(id)) return;
        if(action.startsWith("preview") && (!previewVisible || !previewRunning || !previewId.equals(res.getString("PreviewId")))) return;
        requested.remove(id);
        if(action.equals("catalog")){
            Map<String, String> next = new LinkedHashMap<>(); startup.clear(); readOnlyRoots.clear(); destinations.clear();
            for(var raw : res.getList("Roots", Tag.TAG_COMPOUND)){
                var tag = (CompoundTag)raw; next.put(tag.getString("Id"), tag.getString("Name"));
                if(tag.getBoolean("Startup")) startup.add(tag.getString("Id"));
                if(tag.getBoolean("ReadOnly")) readOnlyRoots.add(tag.getString("Id"));
                for(var rawFolder : tag.getList("ExportFolders", Tag.TAG_COMPOUND)){
                    var folder = (CompoundTag)rawFolder; destinations.add(new Destination(tag.getString("Id"), folder.getString("Path"), folder.getString("Name")));
                }
            }
            if(!next.equals(roots)){
                roots.clear(); roots.putAll(next); children.clear(); requested.clear();
            }
            for(String key : expanded) if(roots.containsKey(key.split("\n", 2)[0])) load(key.split("\n", 2)[0], key.split("\n", 2)[1]);
            for(String key : List.copyOf(restoring)){
                var file = openFiles.get(key);
                if(file != null && roots.containsKey(file.root()) && restoreRequests.add(key)){
                    var req = request("read",file.root(),file.path()); req.putBoolean("Restore",true); send.accept(req);
                }
            }
            if(selected != null && previewPath.isEmpty() && startup.contains(selected.root())) preview(selected.root(),"startup.lua");
            return;
        }
        if(action.equals("write")){
            buffers.acknowledge(res.getLong("Request"), res.getBoolean("Success"), res.contains("Source") ? res.getString("Source") : null, res.getString("Message"));
            dispatch(); if(autosave()) saveTicks = 1;
        }
        if(!res.getBoolean("Success")){
            message = res.getString("Message");
            if(action.equals("read") && res.getBoolean("Restore")){ closeFile(id); selection.run(); }
            if(action.startsWith("preview") && root.equals(previewRoot) && path.equals(previewPath)) previewError = message;
            return;
        }
        if(action.startsWith("preview")){
            if(root.equals(previewRoot) && path.equals(previewPath)){
                terminal = res.contains("Terminal") ? ScriptTerminal.fromDisplayFrame(res.getCompound("Terminal")) : terminal;
                previewError = res.getString("Error");
            }
            return;
        }
        message = res.getString("Message");
        if(action.equals("reveal")){
            if(reveal != null) try{ reveal.accept(java.nio.file.Path.of(res.getString("Directory"))); }
            catch(RuntimeException ex){ message = "Cannot open the file explorer"; }
            return;
        }
        if(action.equals("list")){
            List<File> entries = new ArrayList<>();
            for(var raw : res.getList("Entries", Tag.TAG_COMPOUND)){
                var tag = (CompoundTag)raw; String name = tag.getString("Name");
                entries.add(new File(root, ScriptFiles.child(path, name), name, tag.getBoolean("Directory"), tag.getBoolean("ReadOnly"), tag.getBoolean("Protected"), 0));
            }
            children.put(id, List.copyOf(entries)); clampScroll();
        }else if(action.equals("read")){
            if(res.contains("ScriptAction")){
                var callback = "graph".equals(res.getString("ScriptAction")) ? addToGraph : importScript;
                if(callback != null) callback.accept(ScriptFiles.name(path), res.getString("Source")); return;
            }
            if(!openFiles.containsKey(id)) return;
            restoring.remove(id); restoreRequests.remove(id);
            var prev = openFiles.get(id);
            var file = new File(root,path,prev.name(),false,res.getBoolean("ReadOnly"),prev.protectedEntry(),prev.depth());
            openFiles.put(id,file);
            if(focused != null && focused.id().equals(id)) focused = file;
            if(selected != null && selected.id().equals(id)) selected = file;
            buffers.load(id, res.getString("Source"), res.getBoolean("ReadOnly"));
            if(selected != null && selected.id().equals(id)) selection.run();
        }else if(!action.equals("write")){
            if(action.equals("cut")) clipboard = null;
            children.clear(); requested.clear();
            if(action.equals("create")){
                var file = new File(root, path, ScriptFiles.name(path), false, false, false, 0);
                buffers.load(id, res.getString("Source"), false); expanded.add(root + "\n" + ScriptFiles.parent(path)); select(file);
            }else if(action.equals("rename") || action.equals("cut") || action.equals("delete")){
                String prefix = id + "/";
                if(focused != null && (focused.id().equals(id) || focused.id().startsWith(prefix))){
                    String nextRoot = action.equals("cut") ? res.getString("DestinationRoot") : root;
                    String nextPath = action.equals("delete") ? ScriptFiles.parent(path) : res.getString("Destination") + focused.path().substring(path.length());
                    focused = new File(nextRoot, nextPath, ScriptFiles.name(nextPath), action.equals("delete") || focused.directory(),
                            readOnlyRoots.contains(nextRoot), nextPath.isEmpty(), focused.depth());
                }
                for(var file : List.copyOf(openFiles.values())){
                    if(!file.id().equals(id) && !file.id().startsWith(prefix)) continue;
                    if(action.equals("delete")){ closeFile(file.id()); continue; }
                    String nextRoot = action.equals("cut") ? res.getString("DestinationRoot") : root;
                    String nextPath = res.getString("Destination") + file.path().substring(path.length());
                    var next = new File(nextRoot, nextPath, ScriptFiles.name(nextPath), false, file.readOnly(), false, file.depth());
                    openFiles.remove(file.id()); openFiles.put(next.id(), next); buffers.move(file.id(), next.id());
                    if(restoring.remove(file.id())){ restoring.add(next.id()); restoreRequests.remove(file.id()); }
                    redirects.replaceAll((key, val) -> val.equals(file.id()) ? next.id() : val); redirects.remove(next.id()); redirects.put(file.id(), next.id());
                    if(selected != null && selected.id().equals(file.id())) activateFile(next);
                }
                selection.run();
            }
            for(String key : expanded) if(roots.containsKey(key.split("\n", 2)[0])) load(key.split("\n", 2)[0], key.split("\n", 2)[1]);
        }
    }

    private CompoundTag request(String action, String root, String path){
        var req = new CompoundTag(); req.putString("Action", action); req.putString("Root", root); req.putString("Path", path); return req;
    }
    private void load(String root, String path){
        String id = root + "\n" + path; if(requested.add(id)) send.accept(request("list", root, path));
    }
    private List<File> rows(){
        List<File> rows = new ArrayList<>();
        roots.forEach((id, name) -> append(rows, new File(id, "", name, true, readOnlyRoots.contains(id), true, 0))); return rows;
    }
    private void append(List<File> rows, File file){
        rows.add(file);
        if(!expanded.contains(file.id()) || file.depth() > 64) return;
        for(var child : children.getOrDefault(file.id(), List.of())) append(rows,
                new File(child.root(), child.path(), child.name(), child.directory(), child.readOnly(), child.protectedEntry(), file.depth() + 1));
    }
    private void clampScroll(){ scroll = Math.clamp(scroll, 0, Math.max(0, rows().size() * 18 - bounds.height() + 20)); }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            RENDERING
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public void render(GuiGraphics graphics, HelpModalStyle style, int mx, int my){
        renderToolbar(graphics, style, mx, my);
        if(bounds.height() <= 0) return;
        graphics.enableScissor(bounds.x(), bounds.y(), bounds.right(), bounds.bottom());
        List<File> rows = rows();
        for(int idx = 0; idx < rows.size(); idx++){
            File file = rows.get(idx); int y = bounds.y() + idx * 18 - scroll;
            if(y + 18 < bounds.y() || y >= bounds.bottom()) continue;
            if(file.id().equals(focused == null ? selected() : focused.id())) graphics.fill(bounds.x() + 3, y, bounds.right() - 3, y + 17, style.border());
            String name = file.path().equals("rom") ? "ROM" : file.path().matches("disk[0-9]*") ? "Disk" + file.path().substring(4) : file.name();
            String label = (file.directory() ? expanded.contains(file.id()) ? "v " : "> " : "  ") + name;
            int x = bounds.x() + 7 + file.depth() * 10;
            graphics.drawString(font, font.plainSubstrByWidth(label, Math.max(1, bounds.right() - x - 5)), x, y + 4, file.readOnly() ? style.muted() : style.text(), false);
        }
        if(rows.isEmpty()) graphics.drawWordWrap(font, Component.literal("No connected storage"), bounds.x() + 8, bounds.y() + 5, Math.max(1, bounds.width() - 16), style.muted());
        if(!message.isEmpty()){
            graphics.fill(bounds.x(), bounds.bottom() - 17, bounds.right(), bounds.bottom(), style.panel());
            graphics.drawString(font, font.plainSubstrByWidth(message, Math.max(1, bounds.width() - 12)), bounds.x() + 6, bounds.bottom() - 12, style.muted(), false);
        }
        graphics.disableScissor();
    }

    public void renderOverlay(GuiGraphics graphics, HelpModalStyle style, int mx, int my){
        if(!menu.isEmpty()){
            var rect = menuBounds(); graphics.pose().pushPose(); graphics.pose().translate(0, 0, 350);
            graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), style.panel()); graphics.renderOutline(rect.x(), rect.y(), rect.width(), rect.height(), style.border());
            for(int idx = 0; idx < menu.size(); idx++){
                int y = rect.y() + 2 + idx * 18;
                if(mx >= rect.x() && mx < rect.right() && my >= y && my < y + 18) graphics.fill(rect.x() + 1, y, rect.right() - 1, y + 18, style.border());
                graphics.drawString(font, menu.get(idx), rect.x() + 6, y + 5, style.text(), false);
            }
            graphics.pose().popPose();
        }
        if(modal != null){ modal.appearance(style, null); modal.render(graphics, graphics.guiWidth(), graphics.guiHeight(), mx, my, 0); }
        if(!message.isEmpty() && bounds.contains(mx, my)) graphics.renderTooltip(font, Component.literal(message), mx, my);
    }

    private ScriptEditorWorkspace.Rect menuBounds(){ return new ScriptEditorWorkspace.Rect(menuX, menuY, 152, menu.size() * 18 + 4); }
    public boolean modalOpen(){ return modal != null || !menu.isEmpty(); }
    public boolean overlayClick(double mx, double my, int button){
        if(modal != null) return modal.mouseClicked(mx, my, button);
        if(menu.isEmpty()) return false;
        var rect = menuBounds(); int idx = (int)((my - rect.y() - 2) / 18);
        String action = rect.contains(mx, my) && idx >= 0 && idx < menu.size() ? menu.get(idx) : "";
        menu = List.of();
        if(choosingDestination){
            choosingDestination = false;
            if(button == 0 && rect.contains(mx, my) && idx >= 0 && idx < destinations.size()) export(destinations.get(idx));
        }else if(button == 0) action(action);
        return true;
    }

    public boolean mouseClicked(double mx, double my, int button){
        if(toolbar.contains(mx, my)){
            int idx = (int)((mx - toolbar.x()) / toolbarStep());
            if(button == 0 && idx >= 0 && idx < FileActionIcon.toolbar().length){
                var icon = FileActionIcon.toolbar()[idx]; if(enabled(icon)) perform(icon.label);
            }
            return true;
        }
        if(!bounds.contains(mx, my)) return false;
        int idx = (int)((my - bounds.y() + scroll) / 18); var rows = rows();
        if(idx < 0 || idx >= rows.size()) return true;
        File file = rows.get(idx); focused = file;
        if(button == 1){
            choosingDestination = false; menuFile = file; List<String> actions = new ArrayList<>();
            if(file.directory() && !file.readOnly()) actions.add("New File");
            if(!file.directory()){
                actions.add("Load in preview");
                if(importScript != null) actions.add(importLabel);
            }
            if(!file.path().isEmpty()){
                if(!file.readOnly() && !file.protectedEntry()){ actions.add("Rename"); actions.add("Delete"); actions.add("Cut"); }
                actions.add("Copy");
            }
            if(file.directory() && !file.readOnly() && clipboard != null) actions.add("Paste");
            if(reveal != null) actions.add("Reveal in file explorer");
            menu = List.copyOf(actions); var mc = net.minecraft.client.Minecraft.getInstance();
            menuX = Math.clamp((int)mx, 0, Math.max(0, mc.getWindow().getGuiScaledWidth() - 154));
            menuY = Math.clamp((int)my, 0, Math.max(0, mc.getWindow().getGuiScaledHeight() - menu.size() * 18 - 4));
        }else if(button == 0){
            if(file.directory()){
                if(!expanded.remove(file.id())){ expanded.add(file.id()); load(file.root(), file.path()); }
            }else select(file);
        }
        return true;
    }

    private void preview(String root, String path){
        stopPreview();
        previewRoot = root; previewPath = path; terminal = null; previewError = "";
        startPreview();
    }
    private void action(String action){
        if(menuFile == null) return;
        var file = menuFile;
        if(action.equals(importLabel)){ readScript(file, importScript, "library"); return; }
        switch(action){
            case "New File" -> {
                modal = new TextInputModal(font, Component.literal("New File"), Component.literal("File name"), "untitled.lua", 255, name -> {
                    try{
                        if(name.contains("/")) throw new IllegalArgumentException("Use a file name");
                        send.accept(request("create", file.root(), ScriptFiles.child(file.path(), name)));
                    }catch(IllegalArgumentException ex){ message = ex.getMessage(); }
                }, () -> modal = null);
                modal.confirmLabel(Component.literal("Create"));
            }
            case "Reveal in file explorer" -> { if(reveal != null) send.accept(request("reveal", file.root(), file.path())); }
            case "Copy", "Cut" -> { clipboard = file; cut = action.equals("Cut"); }
            case "Load in preview" -> preview(file.root(), file.path());
            case "Add to Graph" -> readScript(file, addToGraph, "graph");
            case "Delete" -> send.accept(request("delete", file.root(), file.path()));
            case "Paste" -> {
                if(clipboard == null) return;
                var copied = clipboard; boolean moving = cut;
                Consumer<String> paste = name -> {
                    try{
                        if(name.contains("/")) throw new IllegalArgumentException("Use a file name");
                        var req = request(moving ? "cut" : "copy", copied.root(), copied.path());
                        req.putString("DestinationRoot", file.root()); req.putString("Destination", ScriptFiles.child(file.path(), name)); send.accept(req);
                    }catch(IllegalArgumentException ex){ message = ex.getMessage(); }
                };
                if(children.getOrDefault(file.id(), List.of()).stream().anyMatch(child -> child.name().equals(copied.name()))){
                    modal = new TextInputModal(font, Component.literal("Paste as"), Component.literal("Name"), copied.name(), 255, paste, () -> modal = null);
                    modal.confirmLabel(Component.literal("Paste"));
                }else paste.accept(copied.name());
            }
            case "Rename" -> {
                modal = new TextInputModal(font, Component.literal("Rename"), Component.literal("Name"), file.name(), 255, name -> {
                    try{
                        if(name.contains("/")) throw new IllegalArgumentException("Use a file name");
                        var req = request("rename", file.root(), file.path()); req.putString("Destination", ScriptFiles.child(ScriptFiles.parent(file.path()), name)); send.accept(req);
                    }catch(IllegalArgumentException ex){ message = ex.getMessage(); }
                }, () -> modal = null);
                modal.confirmLabel(Component.literal("Rename"));
            }
        }
    }
    private void readScript(File file, BiConsumer<String, String> callback, String mode){
        if(file.directory() || callback == null) return;
        String source = buffers.source(file.id());
        if(source != null) callback.accept(file.name(), source);
        else{ var req = request("read", file.root(), file.path()); req.putString("ScriptAction", mode); send.accept(req); }
    }
    private File target(){
        if(focused != null && roots.containsKey(focused.root())) return focused;
        if(selected != null) return selected;
        return roots.isEmpty() ? null : new File(roots.keySet().iterator().next(), "", roots.values().iterator().next(), true,
                readOnlyRoots.contains(roots.keySet().iterator().next()), true, 0);
    }
    private File directory(File file){
        if(file == null || file.directory()) return file;
        String path = ScriptFiles.parent(file.path());
        return new File(file.root(), path, ScriptFiles.name(path), true, readOnlyRoots.contains(file.root()) || file.readOnly(), path.isEmpty(), 0);
    }
    private boolean enabled(FileActionIcon icon){
        var file = target(); if(file == null) return false;
        return switch(icon){
            case NEW_FILE -> !directory(file).readOnly();
            case DELETE, CUT -> !file.path().isEmpty() && !file.readOnly() && !file.protectedEntry();
            case COPY -> !file.path().isEmpty();
            case PASTE -> clipboard != null && !directory(file).readOnly();
            case ADD_GRAPH -> addToGraph != null && !file.directory();
            case TO_STORAGE -> false;
        };
    }
    public void perform(String action){
        var file = target(); if(file == null) return;
        menuFile = action.equals("New File") || action.equals("Paste") ? directory(file) : file; action(action);
    }
    private int toolbarStep(){ return Math.max(1, Math.min(23, toolbar.width() / FileActionIcon.toolbar().length)); }
    private void renderToolbar(GuiGraphics graphics, HelpModalStyle style, int mx, int my){
        for(int idx = 0; idx < FileActionIcon.toolbar().length; idx++){
            var icon = FileActionIcon.toolbar()[idx]; var rect = new ScriptEditorWorkspace.Rect(toolbar.x() + idx * toolbarStep(), toolbar.y(), toolbarStep() - 2, 20);
            if(rect.right() > toolbar.right()) break;
            boolean enabled = enabled(icon);
            graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), enabled && rect.contains(mx, my) ? style.border() : style.panel());
            icon.draw(graphics, rect.x() + 3, rect.y() + 3, enabled ? style.text() : style.muted());
        }
    }
    public void renderToolbarTooltip(GuiGraphics graphics, int mx, int my){
        if(!toolbar.contains(mx, my)) return;
        int idx = (mx - toolbar.x()) / toolbarStep();
        if(idx >= 0 && idx < FileActionIcon.toolbar().length) graphics.renderTooltip(font, Component.literal(FileActionIcon.toolbar()[idx].label), mx, my);
    }

    public boolean mouseScrolled(double mx, double my, double amount){
        if(!bounds.contains(mx, my)) return false;
        scroll -= (int)(amount * 18); clampScroll(); return true;
    }
    public boolean charTyped(char ch){ return modal != null && modal.charTyped(ch, 0); }
    public boolean keyPressed(int key){
        if(modal != null) return modal.keyPressed(key, 0, 0);
        if(!menu.isEmpty()){ if(key == GLFW.GLFW_KEY_ESCAPE) menu = List.of(); return true; }
        return false;
    }
}
