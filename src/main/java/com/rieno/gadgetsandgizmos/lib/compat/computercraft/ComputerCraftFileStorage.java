package com.rieno.gadgetsandgizmos.lib.compat.computercraft;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.mixin.ComputerCraftStorageAccess;
import com.rieno.gadgetsandgizmos.lib.script.ScriptFileStorage;
import com.rieno.gadgetsandgizmos.lib.script.ScriptFiles;
import dan200.computercraft.core.filesystem.FileSystem;
import dan200.computercraft.core.filesystem.FileSystemException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

// Preserve the live computer's mount names and filesystem synchronization
public final class ComputerCraftFileStorage implements ScriptFileStorage{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private final FileSystem files;
    private ComputerCraftFileStorage(FileSystem files){ this.files = files; }
    @Override public java.util.Optional<java.nio.file.Path> localPath(String path) throws IOException{
        var loc = location(path);
        return loc.volume() instanceof dan200.computercraft.api.filesystem.Mount mount
                ? ComputerCraftMountStorage.localPath(mount, loc.path()) : java.util.Optional.empty();
    }
    @Override public Location location(String path) throws IOException{
        ScriptFiles.path(path);
        synchronized(files){
            if(files instanceof com.rieno.gadgetsandgizmos.lib.mixin.ComputerCraftFileSystemAccess access){
                com.rieno.gadgetsandgizmos.lib.mixin.ComputerCraftMountAccess found = null;
                for(Object val : access.gadgetsngizmos$mounts().values()){
                    if(!(val instanceof com.rieno.gadgetsandgizmos.lib.mixin.ComputerCraftMountAccess mount)) continue;
                    String prefix = mount.gadgetsngizmos$location();
                    if((prefix.isEmpty() || path.equals(prefix) || path.startsWith(prefix + "/"))
                            && (found == null || prefix.length() > found.gadgetsngizmos$location().length())) found = mount;
                }
                if(found != null){
                    String prefix = found.gadgetsngizmos$location();
                    return new Location(found.gadgetsngizmos$mount(), prefix.isEmpty() ? path : path.equals(prefix) ? "" : path.substring(prefix.length() + 1));
                }
            }
            return new Location(files, path);
        }
    }
    @Override public <T> T access(String path, ScriptFileStorage.Operation<T> operation) throws IOException{
        ScriptFiles.path(path); synchronized(files){ return operation.run(); }
    }
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static ScriptFileStorage of(Object computer){
        if(!(computer instanceof ComputerCraftStorageAccess access)) return null;
        try{
            var files = access.gadgetsngizmos$computer().getAPIEnvironment().getFileSystem();
            return files == null ? null : new ComputerCraftFileStorage(files);
        }catch(IllegalStateException ex){ return null; }
    }
    @FunctionalInterface private interface Operation<T>{ T run() throws FileSystemException, IOException; }
    private <T> T access(Operation<T> operation) throws IOException{
        synchronized(files){
            try{ return operation.run(); }catch(FileSystemException ex){ throw new IOException(ex.getMessage(), ex); }
        }
    }
    @Override public boolean exists(String path) throws IOException{ return access(() -> files.exists(ScriptFiles.path(path))); }
    @Override public boolean directory(String path) throws IOException{ return access(() -> files.isDir(ScriptFiles.path(path))); }
    @Override public boolean readOnly(String path) throws IOException{ return access(() -> files.isReadOnly(ScriptFiles.path(path))); }
    @Override public List<Entry> list(String path) throws IOException{
        return access(() -> {
            List<Entry> entries = new ArrayList<>();
            for(String name : files.list(ScriptFiles.path(path))){
                String next = ScriptFiles.child(path, name);
                boolean mount = location(next).path().isEmpty();
                entries.add(new Entry(name, files.isDir(next), files.isReadOnly(next), mount));
            }
            entries.sort(Comparator.comparing(Entry::directory).reversed().thenComparing(Entry::name, String.CASE_INSENSITIVE_ORDER));
            return List.copyOf(entries);
        });
    }
    @Override public byte[] read(String path) throws IOException{
        return access(() -> {
            ScriptFiles.path(path);
            if(files.getSize(path) > ScriptFiles.MAX_FILE_BYTES) throw new IOException("The file exceeds 1 MiB");
            try(var handle = files.openForRead(path)){
                var stream = Channels.newInputStream(handle.get());
                byte[] data = stream.readNBytes(ScriptFiles.MAX_FILE_BYTES + 1);
                if(data.length > ScriptFiles.MAX_FILE_BYTES) throw new IOException("The file exceeds 1 MiB");
                return data;
            }
        });
    }
    @Override public void write(String path, byte[] data) throws IOException{
        access(() -> {
            ScriptFiles.writable(this, path);
            try(var handle = files.openForWrite(path, Set.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))){
                var buffer = ByteBuffer.wrap(data); while(buffer.hasRemaining()) handle.get().write(buffer);
            }
            return null;
        });
    }
    @Override public void mkdir(String path) throws IOException{ access(() -> { ScriptFiles.writable(this, path); files.makeDir(path); return null; }); }
    @Override public void delete(String path) throws IOException{ access(() -> { ScriptFiles.writable(this, path); files.delete(path); return null; }); }
    @Override public void move(String path, String dest) throws IOException{
        access(() -> { ScriptFiles.writable(this, path); ScriptFiles.writable(this, dest); files.move(path, ScriptFiles.path(dest)); return null; });
    }
}
