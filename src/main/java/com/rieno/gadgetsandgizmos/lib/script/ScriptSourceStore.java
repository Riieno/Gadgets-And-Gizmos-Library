package com.rieno.gadgetsandgizmos.lib.script;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtAccounter;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.UUID;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

// Move script source out of serialized documents while preserving graph and revision isolation
public final class ScriptSourceStore{
    private final String table;
    private final String sourceKey;
    private final String referenceKey;

    public ScriptSourceStore(String table, String sourceKey, String referenceKey){
        if(!table.matches("[A-Za-z_][A-Za-z0-9_]*")) throw new IllegalArgumentException("Invalid script table name");
        this.table = table; this.sourceKey = sourceKey; this.referenceKey = referenceKey;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public void initialize(Connection connection) throws SQLException{
        try(var statement = connection.createStatement()){
            statement.execute("CREATE TABLE IF NOT EXISTS " + table + "_Deleted (owner_type TEXT NOT NULL, owner_id TEXT NOT NULL, function_id TEXT NOT NULL, PRIMARY KEY(owner_type, owner_id, function_id))");
            statement.execute("CREATE TABLE IF NOT EXISTS " + table + " (owner_type TEXT NOT NULL, owner_id TEXT NOT NULL, graph_role TEXT NOT NULL, "
                    + "source_id TEXT NOT NULL, function_id TEXT NOT NULL, source TEXT NOT NULL, PRIMARY KEY(owner_type, owner_id, graph_role, source_id))");
        }
        boolean metadata = false;
        try(var statement = connection.createStatement(); var rows = statement.executeQuery("PRAGMA table_info(" + table + ")")){
            while(rows.next()) metadata |= "definition_nbt".equals(rows.getString("name"));
        }
        if(!metadata) try(var statement = connection.createStatement()){ statement.execute("ALTER TABLE " + table + " ADD COLUMN definition_nbt BLOB"); }
    }

    // Explicit creation or graph saves may update metadata; source autosave uses updateSource instead
    public boolean saveFunction(Connection connection, String ownerType, String ownerId, ScriptFunction function, boolean createOnly) throws SQLException{
        if(deletedFunctions(connection, ownerType, ownerId).contains(function.id())) return false;
        CompoundTag metadata = function.toTag();
        String source = function.data().getString(sourceKey);
        metadata.getCompound("Data").remove(sourceKey);
        byte[] bytes;
        try(var stream = new ByteArrayOutputStream()){
            NbtIo.writeCompressed(metadata, stream); bytes = stream.toByteArray();
        }catch(IOException ex){ throw new SQLException("Cannot encode script definition", ex); }
        String conflict = createOnly ? "DO NOTHING" : "DO UPDATE SET source=excluded.source, definition_nbt=excluded.definition_nbt";
        try(var statement = connection.prepareStatement("INSERT INTO " + table
                + " (owner_type,owner_id,graph_role,source_id,function_id,source,definition_nbt) VALUES (?,?,'library',?,?,?,?) ON CONFLICT " + conflict)){
            statement.setString(1, ownerType); statement.setString(2, ownerId); statement.setString(3, function.id());
            statement.setString(4, function.id()); statement.setString(5, source); statement.setBytes(6, bytes);
            return statement.executeUpdate() > 0;
        }
    }

    // Persist explicit interface metadata without changing its source or graph references
    public boolean updateDefinition(Connection connection, String ownerType, String ownerId, ScriptFunction function) throws SQLException{
        CompoundTag metadata = function.toTag(); metadata.getCompound("Data").remove(sourceKey);
        try(var bytes = new ByteArrayOutputStream()){
            NbtIo.writeCompressed(metadata, bytes);
            try(var statement = connection.prepareStatement("UPDATE " + table + " SET definition_nbt=? WHERE owner_type=? AND owner_id=? AND graph_role='library' AND source_id=?")){
                statement.setBytes(1, bytes.toByteArray()); statement.setString(2, ownerType); statement.setString(3, ownerId); statement.setString(4, function.id());
                return statement.executeUpdate() == 1;
            }
        }catch(IOException ex){ throw new SQLException("Cannot encode script metadata", ex); }
    }

    // Delete source versions and metadata, retaining only an ID to reject stale clients
    public boolean deleteFunction(Connection connection, String ownerType, String ownerId, String id) throws SQLException{
        UUID.fromString(id);
        try(var statement = connection.prepareStatement("INSERT INTO " + table + "_Deleted (owner_type,owner_id,function_id) VALUES (?,?,?) ON CONFLICT DO NOTHING")){
            statement.setString(1, ownerType); statement.setString(2, ownerId); statement.setString(3, id); statement.executeUpdate();
        }
        try(var statement = connection.prepareStatement("DELETE FROM " + table + " WHERE owner_type=? AND owner_id=? AND function_id=?")){
            statement.setString(1, ownerType); statement.setString(2, ownerId); statement.setString(3, id); return statement.executeUpdate() > 0;
        }
    }

    public Set<String> deletedFunctions(Connection connection, String ownerType, String ownerId) throws SQLException{
        Set<String> ids = new HashSet<>();
        try(var statement = connection.prepareStatement("SELECT function_id FROM " + table + "_Deleted WHERE owner_type=? AND owner_id=?")){
            statement.setString(1, ownerType); statement.setString(2, ownerId);
            try(var rows = statement.executeQuery()){ while(rows.next()) ids.add(rows.getString(1)); }
        }
        return Set.copyOf(ids);
    }

    public enum SourceStatus{ SAVED, UNCHANGED, CONFLICT, MISSING, UNAVAILABLE, INVALID }
    public record SourceWrite(SourceStatus status, String source){
        public boolean saved(){ return status == SourceStatus.SAVED || status == SourceStatus.UNCHANGED; }
    }

    // Keep the original boolean API while supplying current source to conflict-aware editors
    public boolean updateSource(Connection connection, String ownerType, String ownerId, String id, String previous, String source) throws SQLException{
        return writeSource(connection, ownerType, ownerId, id, previous, source).saved();
    }

    public SourceWrite writeSource(Connection connection, String ownerType, String ownerId, String id, String previous, String source) throws SQLException{
        if(source.length() > LuaScripts.MAX_SOURCE_LENGTH) return new SourceWrite(SourceStatus.INVALID, null);
        String stored = source(connection, ownerType, ownerId, id);
        if(stored == null) return new SourceWrite(SourceStatus.MISSING, null);
        if(stored.equals(source)) return new SourceWrite(SourceStatus.UNCHANGED, stored);
        if(!stored.equals(previous)) return new SourceWrite(SourceStatus.CONFLICT, stored);
        try(var statement = connection.prepareStatement("UPDATE " + table + " SET source=? WHERE owner_type=? AND owner_id=? AND graph_role='library' AND source_id=? AND source=?")){
            statement.setString(1, source); statement.setString(2, ownerType); statement.setString(3, ownerId);
            statement.setString(4, id); statement.setString(5, previous);
            if(statement.executeUpdate() == 1) return new SourceWrite(SourceStatus.SAVED, source);
        }
        stored = source(connection, ownerType, ownerId, id);
        return new SourceWrite(stored == null ? SourceStatus.MISSING : SourceStatus.CONFLICT, stored);
    }

    private String source(Connection connection, String ownerType, String ownerId, String id) throws SQLException{
        try(var statement = connection.prepareStatement("SELECT source FROM " + table + " WHERE owner_type=? AND owner_id=? AND graph_role='library' AND source_id=?")){
            statement.setString(1, ownerType); statement.setString(2, ownerId); statement.setString(3, id);
            try(var rows = statement.executeQuery()){ return rows.next() ? rows.getString(1) : null; }
        }
    }

    public List<ScriptFunction> functions(Connection connection, String ownerType, String ownerId) throws SQLException{
        List<ScriptFunction> functions = new ArrayList<>();
        try(var statement = connection.prepareStatement("SELECT definition_nbt,source FROM " + table + " WHERE owner_type=? AND owner_id=? AND graph_role='library' ORDER BY function_id")){
            statement.setString(1, ownerType); statement.setString(2, ownerId);
            try(var rows = statement.executeQuery()){
                while(rows.next()){
                    byte[] bytes = rows.getBytes(1);
                    if(bytes == null) continue;
                    try{
                        var tag = NbtIo.readCompressed(new ByteArrayInputStream(bytes), NbtAccounter.unlimitedHeap());
                        tag.getCompound("Data").putString(sourceKey, rows.getString(2));
                        functions.add(ScriptFunction.fromTag(tag));
                    }catch(IOException | IllegalArgumentException ex){ throw new SQLException("Cannot decode script definition", ex); }
                }
            }
        }
        return List.copyOf(functions);
    }

    // Run inside the caller's transaction so source rows and graph references commit together
    public CompoundTag separate(Connection connection, String ownerType, String ownerId, String role, CompoundTag document) throws SQLException{
        CompoundTag res = document.copy();
        walk(connection, ownerType, ownerId, role, res, "", false);
        Set<String> retained = new HashSet<>();
        references(res, retained);
        try(var statement = connection.prepareStatement("SELECT source_id FROM " + table + " WHERE owner_type=? AND owner_id=? AND graph_role=?")){
            statement.setString(1, ownerType); statement.setString(2, ownerId); statement.setString(3, role);
            var removed = new ArrayList<String>();
            try(var rows = statement.executeQuery()){ while(rows.next()) if(!retained.contains(rows.getString(1))) removed.add(rows.getString(1)); }
            try(var delete = connection.prepareStatement("DELETE FROM " + table + " WHERE owner_type=? AND owner_id=? AND graph_role=? AND source_id=?")){
                for(String id : removed){
                    delete.setString(1, ownerType); delete.setString(2, ownerId); delete.setString(3, role); delete.setString(4, id); delete.addBatch();
                }
                delete.executeBatch();
            }
        }
        return res;
    }

    private void references(Tag tag, Set<String> retained){
        if(tag instanceof ListTag list) list.forEach(entry -> references(entry, retained));
        else if(tag instanceof CompoundTag data){
            if(data.contains(referenceKey, Tag.TAG_STRING)) retained.add(data.getString(referenceKey));
            data.getAllKeys().forEach(key -> references(data.get(key), retained));
        }
    }

    public CompoundTag hydrate(Connection connection, String ownerType, String ownerId, String role, CompoundTag document) throws SQLException{
        CompoundTag res = document.copy();
        walk(connection, ownerType, ownerId, role, res, "", true);
        return res;
    }

    public boolean embedded(Tag tag){
        if(tag instanceof CompoundTag data) return data.contains(sourceKey, Tag.TAG_STRING) || data.getAllKeys().stream().anyMatch(key -> embedded(data.get(key)));
        return tag instanceof ListTag list && list.stream().anyMatch(this::embedded);
    }

    private void walk(Connection connection, String ownerType, String ownerId, String role, Tag tag, String identity, boolean hydrate) throws SQLException{
        if(tag instanceof ListTag list){
            for(Tag entry : list) walk(connection, ownerType, ownerId, role, entry, identity, hydrate);
            return;
        }
        if(!(tag instanceof CompoundTag data)) return;
        if(data.contains("Id", Tag.TAG_STRING)) identity = data.getString("Id");
        if(data.contains(ScriptFunction.REFERENCE, Tag.TAG_STRING)) identity = data.getString(ScriptFunction.REFERENCE);
        if(hydrate && data.contains(referenceKey, Tag.TAG_STRING)){
            try(var statement = connection.prepareStatement("SELECT source FROM " + table + " WHERE owner_type=? AND owner_id=? AND graph_role=? AND source_id=?")){
                statement.setString(1, ownerType); statement.setString(2, ownerId); statement.setString(3, role); statement.setString(4, data.getString(referenceKey));
                try(var rows = statement.executeQuery()){
                    if(!rows.next()) throw new SQLException("Missing stored Lua source " + data.getString(referenceKey));
                    data.putString(sourceKey, rows.getString(1));
                }
            }
            data.remove(referenceKey);
        }else if(!hydrate && data.contains(sourceKey, Tag.TAG_STRING)){
            String source = data.getString(sourceKey);
            String id = UUID.nameUUIDFromBytes((identity + "\0" + source).getBytes(StandardCharsets.UTF_8)).toString();
            try(var statement = connection.prepareStatement("INSERT INTO " + table
                    + " (owner_type, owner_id, graph_role, source_id, function_id, source) VALUES (?,?,?,?,?,?) ON CONFLICT DO NOTHING")){
                statement.setString(1, ownerType); statement.setString(2, ownerId); statement.setString(3, role);
                statement.setString(4, id); statement.setString(5, identity); statement.setString(6, source); statement.executeUpdate();
            }
            data.remove(sourceKey); data.putString(referenceKey, id);
        }
        for(String key : new ArrayList<>(data.getAllKeys())) walk(connection, ownerType, ownerId, role, data.get(key), identity, hydrate);
    }
}
