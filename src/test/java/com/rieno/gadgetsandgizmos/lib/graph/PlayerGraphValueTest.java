package com.rieno.gadgetsandgizmos.lib.graph;

import com.rieno.gadgetsandgizmos.lib.chat.ChatPlayers;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

// Verify player identity, snapshot ownership and command policy without a running world
final class PlayerGraphValueTest{
    // Retain the Player type and nested information through editable JSON
    @Test void snapshotsAreDetachedAndRoundTripAsPlayers(){
        UUID id = UUID.randomUUID();
        var data = new LinkedHashMap<String, Object>();
        data.put("uuid", id.toString()); data.put("health", 17);
        data.put("position", Map.of("x", 2, "y", 4, "z", 6));
        var player = new GraphValue("player", data);
        data.put("health", 0);
        var restored = GraphValueText.parse("player", GraphValueText.format(player));
        assertEquals("player", restored.type());
        assertEquals(id, PlayerGraphValue.id(restored));
        assertEquals(17, restored.member("health").asNumber());
        assertEquals(4, restored.member("position").member("y").asNumber());
        assertNull(PlayerGraphValue.resolve(null, restored));
    }

    // Accept a player or mixed audience list while keeping invalid references restrictive
    @Test void chatAudiencesRetainPlayerReferencesInsideLists(){
        UUID id = UUID.randomUUID();
        var player = PlayerGraphValue.reference(id);
        assertEquals(List.of(id.toString()), ChatPlayers.identifiers(player));
        assertEquals(List.of(id.toString(), "NamedPlayer"), ChatPlayers.identifiers(
                GraphValue.list(List.of(player, GraphValue.string("NamedPlayer")))));
        assertEquals(List.of(""), ChatPlayers.identifiers(PlayerGraphValue.reference(null)));
        assertEquals(List.of(""), ChatPlayers.identifiers(new GraphValue("player", Map.of("uuid", "invalid"))));
    }

    // Reject command execution before a disabled node can request its host or return stale success
    @Test void disabledCommandsDoNotReadTheirHostAndClearResults(){
        var state = new LinkedHashMap<String, GraphValue>();
        state.put("output", GraphValue.string("old output")); state.put("success", GraphValue.bool(true));
        var ctx = new GraphExecutionContext(){
            @Override public long tick(){ return 0; }
            @Override public boolean executionTriggered(){ return true; }
            @Override public GraphValue state(String key){ return state.get(key); }
            @Override public void state(String key, GraphValue value){ state.put(key, value); }
            @Override public <T> Optional<T> service(GraphServiceKey<T> key){
                assertEquals(GraphHostServices.COMMAND_EXECUTION, key);
                CommandExecutionPolicy policy = () -> false;
                return Optional.of(key.type().cast(policy));
            }
        };
        var result = CommandGraphNode.execute(ctx, Map.of("command", GraphValue.string("say must not run")));
        assertFalse(result.get("success").asBoolean());
        assertEquals("", result.get("output").asString());
        assertFalse(state.get("success").asBoolean());
    }
}
