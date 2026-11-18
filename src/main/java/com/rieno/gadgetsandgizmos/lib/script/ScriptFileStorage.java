package com.rieno.gadgetsandgizmos.lib.script;

import java.io.IOException;
import java.util.List;

// Expose virtual storage without depending on a Lua runtime or a world directory
public interface ScriptFileStorage{
    @FunctionalInterface interface Operation<T>{ T run() throws IOException; }
    record Location(Object volume, String path){}
    default Location location(String path) throws IOException{ return new Location(this, ScriptFiles.path(path)); }
    default java.util.Optional<java.nio.file.Path> localPath(String path) throws IOException{ return java.util.Optional.empty(); }
    record Entry(String name, boolean directory, boolean readOnly, boolean protectedEntry){
        public Entry(String name, boolean directory, boolean readOnly){ this(name, directory, readOnly, false); }
    }
    // Keep a source comparison and its write under the provider's storage lock
    default <T> T access(String path, Operation<T> operation) throws IOException{ synchronized(this){ return operation.run(); } }
    boolean exists(String path) throws IOException;
    boolean directory(String path) throws IOException;
    boolean readOnly(String path) throws IOException;
    List<Entry> list(String path) throws IOException;
    byte[] read(String path) throws IOException;
    void write(String path, byte[] data) throws IOException;
    void mkdir(String path) throws IOException;
    void delete(String path) throws IOException;
    void move(String path, String dest) throws IOException;
}
