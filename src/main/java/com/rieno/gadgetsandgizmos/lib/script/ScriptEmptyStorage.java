package com.rieno.gadgetsandgizmos.lib.script;

import java.io.IOException;
import java.util.List;

// Represent an available drive with no readable data volume
public final class ScriptEmptyStorage implements ScriptFileStorage{
    @Override public boolean exists(String path){ return ScriptFiles.path(path).isEmpty(); }
    @Override public boolean directory(String path){ return ScriptFiles.path(path).isEmpty(); }
    @Override public boolean readOnly(String path){ return true; }
    @Override public List<Entry> list(String path) throws IOException{ if(!path.isEmpty()) throw missing(); return List.of(); }
    @Override public byte[] read(String path) throws IOException{ throw missing(); }
    @Override public void write(String path, byte[] data) throws IOException{ throw missing(); }
    @Override public void mkdir(String path) throws IOException{ throw missing(); }
    @Override public void delete(String path) throws IOException{ throw missing(); }
    @Override public void move(String path, String dest) throws IOException{ throw missing(); }
    private IOException missing(){ return new IOException("No data disk is present"); }
}
