package com.rieno.gadgetsandgizmos.lib.compat.computercraft;

import com.rieno.gadgetsandgizmos.lib.script.ScriptFileStorage;
import com.rieno.gadgetsandgizmos.lib.script.ScriptFiles;
import dan200.computercraft.api.filesystem.Mount;
import dan200.computercraft.api.filesystem.WritableMount;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

// Adapt native CC mounts while preserving their write protection and capacity checks
public final class ComputerCraftMountStorage implements ScriptFileStorage{
    private final Mount mount;
    public ComputerCraftMountStorage(Mount mount){ this.mount = java.util.Objects.requireNonNull(mount); }
    @Override public Location location(String path){ return new Location(mount, ScriptFiles.path(path)); }
    @Override public java.util.Optional<java.nio.file.Path> localPath(String path) throws IOException{ return localPath(mount, path); }
    public static java.util.Optional<java.nio.file.Path> localPath(Mount mount, String path) throws IOException{
        ScriptFiles.path(path);
        if(!(mount instanceof com.rieno.gadgetsandgizmos.lib.mixin.ComputerCraftFileMountAccess access)) return java.util.Optional.empty();
        var root = access.gadgetsngizmos$root().toRealPath(); var file = root.resolve(path).normalize().toRealPath();
        return file.startsWith(root) ? java.util.Optional.of(file) : java.util.Optional.empty();
    }
    @Override public boolean exists(String path) throws IOException{ return mount.exists(ScriptFiles.path(path)); }
    @Override public boolean directory(String path) throws IOException{ return mount.isDirectory(ScriptFiles.path(path)); }
    @Override public boolean readOnly(String path) throws IOException{
        return !(mount instanceof WritableMount writable) || writable.isReadOnly(ScriptFiles.path(path));
    }
    @Override public List<Entry> list(String path) throws IOException{
        List<String> names = new ArrayList<>(); mount.list(ScriptFiles.path(path), names);
        List<Entry> entries = new ArrayList<>();
        for(String name : names){ String next = ScriptFiles.child(path, name); entries.add(new Entry(name, directory(next), readOnly(next))); }
        entries.sort(Comparator.comparing(Entry::directory).reversed().thenComparing(Entry::name, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(entries);
    }
    @Override public byte[] read(String path) throws IOException{
        ScriptFiles.path(path);
        if(mount.getSize(path) > ScriptFiles.MAX_FILE_BYTES) throw new IOException("The file exceeds 1 MiB");
        try(var stream = Channels.newInputStream(mount.openForRead(path))){
            byte[] data = stream.readNBytes(ScriptFiles.MAX_FILE_BYTES + 1);
            if(data.length > ScriptFiles.MAX_FILE_BYTES) throw new IOException("The file exceeds 1 MiB");
            return data;
        }
    }
    private WritableMount writable(String path) throws IOException{
        ScriptFiles.writable(this, ScriptFiles.path(path)); return (WritableMount)mount;
    }
    @Override public void write(String path, byte[] data) throws IOException{
        try(var channel = writable(path).openFile(path, Set.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))){
            var buffer = ByteBuffer.wrap(data); while(buffer.hasRemaining()) channel.write(buffer);
        }
    }
    @Override public void mkdir(String path) throws IOException{ writable(path).makeDirectory(path); }
    @Override public void delete(String path) throws IOException{ writable(path).delete(path); }
    @Override public void move(String path, String dest) throws IOException{
        writable(dest); writable(path).rename(path, ScriptFiles.path(dest));
    }
}
