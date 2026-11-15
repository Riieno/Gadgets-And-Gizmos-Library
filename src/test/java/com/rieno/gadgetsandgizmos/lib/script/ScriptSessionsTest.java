package com.rieno.gadgetsandgizmos.lib.script;

import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ScriptSessionsTest{
    @Test void sourceStartsOnceWhileInputAndPortUpdatesDoNotRestartIt(){
        var engine = new Engine(); var sessions = new ScriptSessions(id -> engine);
        String source = "node.addExecIn('step')";
        sessions.register("node", source, Map.of("value", GraphValue.number(1)), Map.of("result", "any"));
        assertEquals(1, engine.requests.size());
        var running = engine.requests.getFirst();
        sessions.register("node", source, Map.of("value", GraphValue.number(2)), Map.of("result", "any", "extra", "any"));
        assertEquals(1, engine.requests.size());
        assertFalse(sessions.pulse("node", "step"));
        engine.complete(running); sessions.tick((key, res) -> {});
        assertFalse(sessions.pulse("node"));
        assertTrue(sessions.pulse("node", "step"));
        assertEquals(2, engine.requests.getLast().inputs().get("value").asNumber());
        assertFalse(sessions.pulse("node", "step"));
        var delta = ScriptUpdate.between(running, engine.requests.getLast());
        assertNull(delta.source()); assertEquals(Map.of("value", GraphValue.number(2)), delta.inputs());
        sessions.close();
    }

    @Test void repeatedPulsesCoalesceUntilTheCurrentInvocationCompletes(){
        var engine = new Engine();
        var sessions = new ScriptSessions(id -> engine);
        assertTrue(sessions.submit("node", "code", Map.of(), Map.of(), false));
        assertFalse(sessions.submit("node", "code", Map.of(), Map.of(), false));
        assertFalse(sessions.submit("node", "code", Map.of(), Map.of(), true));
        engine.complete(engine.requests.getFirst());
        var results = new ArrayList<ScriptResult>();
        sessions.tick((key, res) -> results.add(res));
        assertEquals(1, results.size());
        assertTrue(sessions.submit("node", "code", Map.of(), Map.of(), true));
        sessions.close();
        assertTrue(engine.closed);
    }

    @Test void staleResultsCannotReplaceNewCodeAndHostIdentitiesDoNotCollide(){
        var engines = new ArrayList<Engine>();
        var sessions = new ScriptSessions(id -> { var engine = new Engine(); engines.add(engine); return engine; });
        sessions.submit("first", "old", Map.of("name", GraphValue.string("Same name")), Map.of(), false);
        sessions.submit("second", "old", Map.of("name", GraphValue.string("Same name")), Map.of(), false);
        assertNotEquals(engines.getFirst().requests.getFirst().id(), engines.getLast().requests.getFirst().id());
        sessions.submit("first", "new", Map.of(), Map.of(), false);
        var first = engines.getFirst();
        first.complete(first.requests.getFirst());
        first.complete(first.requests.getLast());
        var results = new ArrayList<ScriptResult>();
        sessions.tick((key, res) -> results.add(res));
        assertEquals(1, results.size());
        assertEquals(2, results.getFirst().revision());
        sessions.retain(Set.of("first"));
        assertTrue(engines.getLast().closed);
        assertFalse(first.closed);
        sessions.close();
    }

    @Test void changingInputsPublishCompletedSnapshotsWithoutAcceptingOutOfOrderResults(){
        var engine = new Engine();
        var sessions = new ScriptSessions(id -> engine);
        sessions.submit("node", "code", Map.of("value", GraphValue.number(1)), Map.of(), false);
        sessions.submit("node", "code", Map.of("value", GraphValue.number(2)), Map.of(), false);
        engine.complete(engine.requests.getFirst());
        var results = new ArrayList<ScriptResult>();
        sessions.tick((key, res) -> results.add(res));
        assertEquals(1, results.size());
        assertTrue(sessions.busy("node"));
        engine.complete(engine.requests.getLast());
        engine.complete(engine.requests.getFirst());
        sessions.tick((key, res) -> results.add(res));
        assertEquals(List.of(1L, 2L), results.stream().map(ScriptResult::revision).toList());
        assertFalse(sessions.busy("node"));
        sessions.close();
    }

    private static final class Engine implements ScriptSession{
        private final List<ScriptRequest> requests = new ArrayList<>();
        private final ArrayDeque<ScriptResult> results = new ArrayDeque<>();
        private boolean closed, busy;
        private ScriptRequest registered;
        @Override public void register(ScriptRequest req){
            if(req.autoStart() && (registered == null || !registered.source().equals(req.source()))) submit(req);
            registered = req;
        }
        @Override public boolean acceptsPulse(String port){ return !busy && registered != null && registered.execInputs().contains(port); }
        @Override public void submit(ScriptRequest req, String port){ submit(req); }
        @Override public void submit(ScriptRequest req){ requests.add(req); busy = true; }
        @Override public void tick(){}
        @Override public ScriptResult poll(){ return results.poll(); }
        @Override public void close(){ closed = true; }
        private void complete(ScriptRequest req){ busy = false; results.add(new ScriptResult(req.id(), req.revision(), Map.of(), "")); }
    }
}
