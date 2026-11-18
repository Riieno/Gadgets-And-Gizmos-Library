package com.rieno.gadgetsandgizmos.lib.script;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

// Let hosts choose the file, namespace and scope for their local script workspaces
public final class ScriptWorkspacePreferences{
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path file;
    private Map<String,ScriptWorkspaceState> states;

    public ScriptWorkspacePreferences(Path file) throws IOException{
        this.file = Objects.requireNonNull(file).toAbsolutePath().normalize(); states = read();
    }
    public ScriptWorkspaceState state(String scope){ return states.get(scope); }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Merge other scopes before replacing the preference file atomically
    public synchronized void save(String scope, ScriptWorkspaceState state) throws IOException{
        Objects.requireNonNull(scope); Objects.requireNonNull(state);
        var next = read();
        if(state.equals(next.put(scope,state))){ states = next; return; }
        Files.createDirectories(file.getParent());
        Path temp = Files.createTempFile(file.getParent(),file.getFileName().toString(),".tmp");
        try{
            Files.writeString(temp,GSON.toJson(next),StandardCharsets.UTF_8);
            try{ Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
            catch(AtomicMoveNotSupportedException ex){ Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING); }
            states = next;
        }finally{ Files.deleteIfExists(temp); }
    }

    private Map<String,ScriptWorkspaceState> read() throws IOException{
        if(!Files.isRegularFile(file)) return new LinkedHashMap<>();
        try{
            Map<String,ScriptWorkspaceState> saved = GSON.fromJson(Files.readString(file,StandardCharsets.UTF_8),
                    new TypeToken<Map<String,ScriptWorkspaceState>>(){}.getType());
            var res = new LinkedHashMap<String,ScriptWorkspaceState>();
            if(saved != null) saved.forEach((scope,state) -> { if(scope != null && state != null) res.put(scope,state); });
            return res;
        }catch(RuntimeException ex){ throw new IOException("Could not read script workspace preferences",ex); }
    }
}
