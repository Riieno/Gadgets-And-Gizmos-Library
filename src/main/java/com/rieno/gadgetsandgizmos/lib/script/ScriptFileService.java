package com.rieno.gadgetsandgizmos.lib.script;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.List;

// Execute requests only against the storage roots the host authorizes for this call
public final class ScriptFileService{
    private ScriptFileService(){}
    public record Folder(String name, String path){}
    public record Root(String name, ScriptFileStorage storage, List<Folder> exportFolders){
        public Root(String name, ScriptFileStorage storage){ this(name, storage, List.of()); }
        public Root{ exportFolders = List.copyOf(exportFolders); }
    }

    public static CompoundTag catalog(Map<String, Root> roots){
        var data = new CompoundTag(); data.putString("Action", "catalog"); var list = new ListTag();
        roots.forEach((id, root) -> {
            var tag = new CompoundTag(); tag.putString("Id", id); tag.putString("Name", root.name());
            try{ tag.putBoolean("Startup", root.storage().exists("startup.lua") && !root.storage().directory("startup.lua")); }
            catch(IOException ignored){}
            try{ tag.putBoolean("ReadOnly", root.storage().readOnly("")); }catch(IOException ignored){ tag.putBoolean("ReadOnly", true); }
            var folders = new ListTag();
            for(var folder : root.exportFolders()) try{
                String path = ScriptFiles.path(folder.path());
                if(!root.storage().exists(path) || !root.storage().directory(path) || root.storage().readOnly(path)) continue;
                var dest = new CompoundTag(); dest.putString("Name", folder.name()); dest.putString("Path", path); folders.add(dest);
            }catch(IOException | IllegalArgumentException ignored){}
            tag.put("ExportFolders", folders); list.add(tag);
        });
        data.put("Roots", list); data.putBoolean("Success", true); return data;
    }

    public static String text(ScriptFileStorage storage, String path) throws IOException{
        byte[] bytes = storage.read(ScriptFiles.path(path));
        try{
            String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
            if(text.indexOf('\0') >= 0 || text.length() > LuaScripts.MAX_SOURCE_LENGTH) throw new IOException("The file cannot be opened as Lua text");
            return text;
        }catch(CharacterCodingException ex){ throw new IOException("The file is not UTF-8 text", ex); }
    }

    // The host must allow local filesystem disclosure before calling this entry point
    public static CompoundTag reveal(Map<String, Root> roots, CompoundTag req){
        var res = req.copy();
        try{
            var root = roots.get(req.getString("Root")); if(root == null) throw new IOException("Storage is no longer available");
            var file = root.storage().localPath(ScriptFiles.path(req.getString("Path")))
                    .orElseThrow(() -> new IOException("This virtual volume has no directory in file explorer"));
            var parent = file.getParent(); if(parent == null) throw new IOException("The parent directory is unavailable");
            res.putString("Directory", parent.toString()); res.putBoolean("Success", true);
        }catch(IOException | IllegalArgumentException ex){ res.putBoolean("Success", false); res.putString("Message", ex.getMessage()); }
        return res;
    }

    public static CompoundTag handle(Map<String, Root> roots, CompoundTag req){
        var res = req.copy(); res.remove("Source"); res.remove("Previous");
        try{
            var root = roots.get(req.getString("Root"));
            if(root == null) throw new IOException("Storage is no longer available");
            var storage = root.storage(); String path = ScriptFiles.path(req.getString("Path"));
            switch(req.getString("Action")){
                case "list" -> {
                    var entries = new ListTag();
                    for(var entry : storage.list(path)){
                        var tag = new CompoundTag(); tag.putString("Name", entry.name()); tag.putBoolean("Directory", entry.directory());
                        tag.putBoolean("ReadOnly", entry.readOnly()); tag.putBoolean("Protected", entry.protectedEntry()); entries.add(tag);
                    }
                    res.put("Entries", entries); res.putBoolean("ReadOnly", storage.readOnly(path));
                }
                case "read" -> { res.putString("Source", text(storage, path)); res.putBoolean("ReadOnly", storage.readOnly(path)); }
                case "create", "store" -> storage.access(path, () -> {
                    ScriptFiles.writable(storage, path);
                    if(storage.exists(path)) throw new IOException("The file already exists");
                    String parent = ScriptFiles.parent(path);
                    if(!storage.exists(parent) || !storage.directory(parent)) throw new IOException("The parent directory is unavailable");
                    String source = "store".equals(req.getString("Action")) ? req.getString("Source") : "";
                    if(source.length() > LuaScripts.MAX_SOURCE_LENGTH) throw new IOException("The source exceeds 32768 characters");
                    storage.write(path, source.getBytes(StandardCharsets.UTF_8)); res.putString("Source", source); res.putBoolean("ReadOnly", false); return null;
                });
                case "write" -> {
                    storage.access(path, () -> {
                        ScriptFiles.writable(storage, path); String source = req.getString("Source");
                        if(source.length() > LuaScripts.MAX_SOURCE_LENGTH) throw new IOException("The source exceeds 32768 characters");
                        String prev = text(storage, path);
                        if(!prev.equals(req.getString("Previous")) && !prev.equals(source)){
                            res.putString("Source", prev); throw new IOException("The file changed elsewhere. Save again to retry.");
                        }
                        storage.write(path, source.getBytes(StandardCharsets.UTF_8)); res.putString("Source", source); return null;
                    });
                }
                case "delete" -> { ScriptFiles.writable(storage, path); storage.delete(path); }
                case "rename" -> {
                    String dest = ScriptFiles.path(req.getString("Destination")); ScriptFiles.writable(storage, path); ScriptFiles.writable(storage, dest);
                    if(dest.isEmpty() || storage.exists(dest)) throw new IOException("The destination already exists");
                    storage.move(path, dest);
                }
                case "copy", "cut" -> {
                    var dest = roots.get(req.getString("DestinationRoot"));
                    if(dest == null) throw new IOException("The destination is no longer available");
                    ScriptFiles.copy(storage, path, dest.storage(), req.getString("Destination"), "cut".equals(req.getString("Action")));
                }
                default -> throw new IOException("Unknown file operation");
            }
            res.putBoolean("Success", true); res.putString("Message", "write".equals(req.getString("Action")) ? "File saved" : "");
        }catch(IOException | IllegalArgumentException ex){
            res.putBoolean("Success", false); res.putString("Message", ex.getMessage() == null ? "File operation failed" : ex.getMessage());
        }
        return res;
    }
}
