package com.rieno.gadgetsandgizmos.lib.graph;

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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

// Persist per-port visibility independently of graph saves and runtime state
public final class GraphPortInspectorPins{
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    // Let the owning mod choose its namespace and player or world scope
    private final Path file;
    private List<Pin> pins;

    // Load one host-owned preference file without depending on Minecraft client classes
    public GraphPortInspectorPins(Path file) throws IOException{
        this.file = Objects.requireNonNull(file).toAbsolutePath().normalize();
        pins = read();
    }

    // Return visibility for one graph scope without exposing the mutable store
    public Set<Port> ports(String scope){
        Set<Port> res = new LinkedHashSet<>();
        for(Pin pin : pins) if(pin.scope().equals(scope)) res.add(pin.port());
        return java.util.Collections.unmodifiableSet(res);
    }

    // Save each toggle immediately while preserving changes made to other graph scopes
    public synchronized void setPinned(String scope, Port port, boolean pinned) throws IOException{
        List<Pin> next = read();
        State state = next.stream().filter(pin -> pin.scope().equals(scope) && pin.port().equals(port))
                .map(Pin::state).filter(Objects::nonNull).findFirst().orElse(null);
        next.removeIf(pin -> pin.scope().equals(scope) && pin.port().equals(port));
        if(pinned) next.add(new Pin(scope, port, state));
        write(next);
    }

    // Return saved graph coordinates and mode while treating legacy visibility-only pins as unplaced
    public State state(String scope, Port port){
        return pins.stream().filter(pin -> pin.scope().equals(scope) && pin.port().equals(port))
                .map(Pin::state).filter(Objects::nonNull).findFirst().orElse(null);
    }

    // Persist a moved bubble or changed mode without recreating a pin that was already closed
    public synchronized void setState(String scope, Port port, State state) throws IOException{
        List<Pin> next = read();
        boolean found = false;
        for(int idx = 0; idx < next.size(); idx++){
            Pin pin = next.get(idx);
            if(!pin.scope().equals(scope) || !pin.port().equals(port)) continue;
            next.set(idx, new Pin(scope, port, Objects.requireNonNull(state))); found = true; break;
        }
        if(found) write(next);
    }

    // Replace the preference file atomically after all state has been prepared
    private void write(List<Pin> next) throws IOException{
        Files.createDirectories(file.getParent());
        Path temp = Files.createTempFile(file.getParent(), file.getFileName().toString(), ".tmp");
        try{
            Files.writeString(temp, GSON.toJson(next), StandardCharsets.UTF_8);
            try{ Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch(AtomicMoveNotSupportedException ignored){ Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
            pins = next;
        }finally{ Files.deleteIfExists(temp); }
    }

    // Reject malformed entries without treating an unreadable file as a successful save
    private List<Pin> read() throws IOException{
        if(!Files.isRegularFile(file)) return new ArrayList<>();
        try{
            List<Pin> saved = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), new TypeToken<List<Pin>>(){}.getType());
            List<Pin> res = new ArrayList<>();
            if(saved != null) for(Pin pin : saved){
                if(pin != null && pin.scope() != null && pin.port() != null && pin.port().nodeId() != null
                        && pin.port().name() != null) res.add(pin);
            }
            return res;
        }catch(RuntimeException err){ throw new IOException("Could not read port inspector preferences", err); }
    }

    // Distinguish input and output ports even when their serialized names match
    public record Port(String nodeId, String name, boolean output){}

    // Choose between a compact live value and the full typed media and tree preview
    public enum Mode{ SIMPLE, ADVANCED }

    // Store authored graph coordinates independently of viewport pan and zoom
    public record State(double x, double y, Mode mode){
        // Reject invalid placement while preserving visibility files written before modes existed
        public State{
            if(!Double.isFinite(x) || !Double.isFinite(y)) throw new IllegalArgumentException("Inspector coordinates must be finite");
            mode = mode == null ? Mode.ADVANCED : mode;
        }
    }

    // Keep graph identity separate from a node's stable port identity
    private record Pin(String scope, Port port, State state){}
}
