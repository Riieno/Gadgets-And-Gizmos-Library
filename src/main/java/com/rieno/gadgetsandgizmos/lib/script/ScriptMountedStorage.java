package com.rieno.gadgetsandgizmos.lib.script;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Combine a writable root and named volumes while protecting mount boundaries
public final class ScriptMountedStorage implements ScriptFileStorage{
    private final ScriptFileStorage root;
    private final Map<String, ScriptFileStorage> mounts = new LinkedHashMap<>();
    public ScriptMountedStorage(ScriptFileStorage root){ this.root = root; }
    public ScriptMountedStorage mount(String name, ScriptFileStorage storage){
        ScriptFiles.path(name);
        if(name.isEmpty() || name.contains("/")) throw new IllegalArgumentException("A volume name is required");
        mounts.put(name, storage); return this;
    }
    private record Resolved(ScriptFileStorage storage, String path){}
    private Resolved resolve(String path){
        ScriptFiles.path(path); int idx = path.indexOf('/'); String name = idx < 0 ? path : path.substring(0, idx);
        var storage = mounts.get(name);
        return storage == null ? new Resolved(root, path) : new Resolved(storage, idx < 0 ? "" : path.substring(idx + 1));
    }
    private Resolved writable(String path) throws IOException{
        var loc = resolve(path); ScriptFiles.writable(loc.storage(), loc.path()); return loc;
    }
    @Override public Location location(String path) throws IOException{ var loc = resolve(path); return loc.storage().location(loc.path()); }
    @Override public java.util.Optional<java.nio.file.Path> localPath(String path) throws IOException{
        var loc = resolve(path); return loc.storage().localPath(loc.path());
    }
    @Override public <T> T access(String path, Operation<T> operation) throws IOException{
        var loc = resolve(path); return loc.storage().access(loc.path(), operation);
    }
    @Override public boolean exists(String path) throws IOException{ var loc = resolve(path); return loc.storage().exists(loc.path()); }
    @Override public boolean directory(String path) throws IOException{ var loc = resolve(path); return loc.storage().directory(loc.path()); }
    @Override public boolean readOnly(String path) throws IOException{ var loc = resolve(path); return loc.storage().readOnly(loc.path()); }
    @Override public List<Entry> list(String path) throws IOException{
        var loc = resolve(path); List<Entry> entries = new ArrayList<>(loc.storage().list(loc.path()));
        if(path.isEmpty()) for(var mount : mounts.entrySet()){
            entries.removeIf(entry -> entry.name().equals(mount.getKey()));
            entries.add(new Entry(mount.getKey(), true, mount.getValue().readOnly(""), true));
        }
        entries.sort(Comparator.comparing(Entry::directory).reversed().thenComparing(Entry::name, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(entries);
    }
    @Override public byte[] read(String path) throws IOException{ var loc = resolve(path); return loc.storage().read(loc.path()); }
    @Override public void write(String path, byte[] data) throws IOException{ var loc = writable(path); loc.storage().write(loc.path(), data); }
    @Override public void mkdir(String path) throws IOException{ var loc = writable(path); loc.storage().mkdir(loc.path()); }
    @Override public void delete(String path) throws IOException{ var loc = writable(path); loc.storage().delete(loc.path()); }
    @Override public void move(String path, String dest) throws IOException{
        var src = writable(path); var target = writable(dest);
        if(src.storage() != target.storage()) throw new IOException("Use cut and paste to move between volumes");
        src.storage().move(src.path(), target.path());
    }
}
