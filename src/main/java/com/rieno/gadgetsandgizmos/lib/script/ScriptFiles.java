package com.rieno.gadgetsandgizmos.lib.script;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

// Validate virtual paths and copy complete trees before removing a cut source
public final class ScriptFiles{
    public static final int MAX_FILE_BYTES = 1024 * 1024;
    public static final int MAX_COPY_BYTES = 8 * 1024 * 1024;
    public static final int MAX_COPY_ENTRIES = 16384;
    private ScriptFiles(){}

    public static String path(String val){
        if(val == null || val.length() > 1024 || val.startsWith("/") || val.contains("\\") || val.indexOf(':') >= 0
                || val.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("Invalid storage path");
        if(val.isEmpty()) return val;
        for(String part : val.split("/", -1)) if(part.isEmpty() || part.equals(".") || part.equals(".."))
            throw new IllegalArgumentException("Invalid storage path");
        return val;
    }

    public static String child(String dir, String name){ return path(dir.isEmpty() ? name : dir + "/" + name); }
    public static String parent(String path){ int idx = path.lastIndexOf('/'); return idx < 0 ? "" : path.substring(0, idx); }
    public static String name(String path){ return path.substring(path.lastIndexOf('/') + 1); }

    public static void writable(ScriptFileStorage storage, String path) throws IOException{
        if(path.isEmpty() || storage.location(path).path().isEmpty() || storage.readOnly(path)) throw new IOException("This location is read only");
    }

    public static void copy(ScriptFileStorage src, String path, ScriptFileStorage dest, String target, boolean cut) throws IOException{
        path(path); path(target); writable(dest, target);
        var from = src.location(path); var to = dest.location(target);
        if(path.isEmpty() || from.volume() == to.volume() && (to.path().equals(from.path()) || to.path().startsWith(from.path() + "/") || from.path().isEmpty()))
            throw new IOException("Cannot copy a directory into itself");
        if(dest.exists(target)) throw new IOException("The destination already exists");
        if(cut) writable(src, path);
        List<CopyEntry> entries = new ArrayList<>();
        collect(src, path, "", entries, new int[1], 0);
        try{
            for(var entry : entries){
                String next = entry.path().isEmpty() ? target : child(target, entry.path());
                if(entry.data() == null) dest.mkdir(next); else dest.write(next, entry.data());
            }
        }catch(IOException ex){
            try{ if(dest.exists(target)) dest.delete(target); }catch(IOException cleanup){ ex.addSuppressed(cleanup); }
            throw ex;
        }
        if(cut) src.delete(path);
    }

    private record CopyEntry(String path, byte[] data){}

    // Capture a bounded workspace for an isolated preview without writing to its source volumes
    public static void snapshot(ScriptFileStorage src, ScriptFileStorage dest, java.util.Set<String> excluded) throws IOException{
        List<CopyEntry> entries = new ArrayList<>(); int[] bytes = new int[1];
        for(var entry : src.list("")) if(!excluded.contains(entry.name())) collect(src, entry.name(), entry.name(), entries, bytes, 0);
        for(var entry : entries) if(entry.data() == null) dest.mkdir(entry.path()); else dest.write(entry.path(), entry.data());
    }
    private static void collect(ScriptFileStorage src, String path, String relative, List<CopyEntry> entries, int[] bytes, int depth) throws IOException{
        if(entries.size() >= MAX_COPY_ENTRIES || depth > 64) throw new IOException("The directory is too large to copy");
        if(src.directory(path)){
            entries.add(new CopyEntry(relative, null));
            for(var entry : src.list(path)) collect(src, child(path, entry.name()), child(relative, entry.name()), entries, bytes, depth + 1);
        }else{
            byte[] data = src.read(path); bytes[0] += data.length;
            if(bytes[0] > MAX_COPY_BYTES) throw new IOException("The files are too large to copy");
            entries.add(new CopyEntry(relative, data));
        }
    }
}
