package com.rieno.gadgetsandgizmos.lib.script;

import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;

// Scope engines to their host and discard completions from superseded code or schemas
public final class ScriptSessions implements AutoCloseable{
    private final Function<UUID, ScriptSession> factory;
    private final Map<String, Entry> entries = new HashMap<>();

    public ScriptSessions(Function<UUID, ScriptSession> factory){ this.factory = factory; }

    // Restart source changes once while retaining live inputs and schema updates
    public boolean register(String key, String source, Map<String, GraphValue> inputs, Map<String, String> outputs){
        var ports = ScriptExecPorts.read(source);
        return register(key, source, inputs, outputs, true, Set.copyOf(ports.inputs()), Set.copyOf(ports.outputs()));
    }

    public boolean register(String key, String source, Map<String, GraphValue> inputs, Map<String, String> outputs, boolean autoStart){
        var ports = ScriptExecPorts.read(source);
        return register(key, source, inputs, outputs, autoStart, Set.copyOf(ports.inputs()), Set.copyOf(ports.outputs()));
    }

    public boolean register(String key, String source, Map<String, GraphValue> inputs, Map<String, String> outputs,
                            boolean autoStart, Set<String> execInputs, Set<String> execOutputs){
        Entry entry = entries.computeIfAbsent(key, ignored -> new Entry());
        ScriptRequest prev = entry.req;
        if(prev != null && prev.source().equals(source) && prev.inputs().equals(inputs) && prev.outputs().equals(outputs)
                && prev.autoStart() == autoStart && prev.execInputs().equals(execInputs) && prev.execOutputs().equals(execOutputs)) return false;
        boolean changed = prev == null || !prev.source().equals(source) || prev.autoStart() != autoStart;
        entry.req = new ScriptRequest(entry.id, ++entry.revision, source, inputs, outputs, autoStart, execInputs, execOutputs);
        entry.pulsed = true;
        if(changed){ entry.sourceRevision = entry.revision; entry.runningRevision = entry.revision; entry.busy = autoStart; }
        if(entry.session == null) entry.session = factory.apply(entry.id);
        entry.session.register(entry.req);
        return true;
    }

    // Computers expose only named execution inputs supplied by their script
    public boolean pulse(String key){ return pulse(key, "exec"); }
    public boolean pulse(String key, String port){
        Entry entry = entries.get(key);
        if(entry == null || entry.req == null || !entry.session.acceptsPulse(port)) return false;
        ScriptRequest prev = entry.req;
        entry.req = new ScriptRequest(entry.id, ++entry.revision, prev.source(), prev.inputs(), prev.outputs(), prev.autoStart(), prev.execInputs(), prev.execOutputs());
        entry.runningRevision = entry.revision;
        entry.busy = true;
        entry.session.submit(entry.req, port);
        return true;
    }

    // Submit changed inputs automatically and allow execution pulses to repeat an invocation
    public boolean submit(String key, String source, Map<String, GraphValue> inputs, Map<String, String> outputs, boolean force){
        Entry entry = entries.computeIfAbsent(key, ignored -> new Entry());
        ScriptRequest prev = entry.req;
        boolean same = prev != null && prev.source().equals(source) && prev.inputs().equals(inputs) && prev.outputs().equals(outputs);
        if(same && (!force || entry.busy)) return false;
        boolean changed = prev == null || !prev.source().equals(source) || !prev.outputs().equals(outputs);
        entry.req = new ScriptRequest(entry.id, ++entry.revision, source, inputs, outputs);
        if(changed) entry.sourceRevision = entry.revision;
        entry.busy = true;
        if(entry.session == null) entry.session = factory.apply(entry.id);
        entry.session.submit(entry.req);
        return true;
    }

    // Publish completions only on the owning thread
    public void tick(BiConsumer<String, ScriptResult> completed){
        tick(completed, (key, pulse) -> {});
    }

    // Keep custom pulses separate from completion so they can fire while code is yielding
    public void tick(BiConsumer<String, ScriptResult> completed, BiConsumer<String, ScriptExecPulse> pulsed){
        tick(completed, pulsed, (key, port) -> {}, (key, output) -> {});
    }

    public void tick(BiConsumer<String, ScriptResult> completed, BiConsumer<String, ScriptExecPulse> pulsed,
                     BiConsumer<String, ScriptPortChange> ports, BiConsumer<String, ScriptOutputChange> outputs){
        entries.forEach((key, entry) -> {
            if(entry.session == null) return;
            entry.session.tick();
            ScriptPortChange port;
            while((port = entry.session.pollPort()) != null){
                if(port.id().equals(entry.id) && port.revision() >= entry.sourceRevision) ports.accept(key, port);
            }
            ScriptOutputChange output;
            while((output = entry.session.pollOutput()) != null){
                if(output.id().equals(entry.id) && output.revision() >= entry.sourceRevision) outputs.accept(key, output);
            }
            ScriptExecPulse pulse;
            while((pulse = entry.session.pollExec()) != null){
                if(pulse.id().equals(entry.id) && pulse.revision() >= entry.sourceRevision && pulse.revision() <= entry.revision) pulsed.accept(key, pulse);
            }
            ScriptResult res;
            while((res = entry.session.poll()) != null){
                if(res.id().equals(entry.id) && res.revision() >= entry.sourceRevision
                        && res.revision() <= entry.revision && (res.revision() > entry.completedRevision
                        || res.revision() == entry.completedRevision && res.sequence() > entry.completedSequence)){
                    entry.completedRevision = res.revision();
                    entry.completedSequence = res.sequence();
                    if(res.revision() == entry.revision || entry.pulsed && res.executed() && res.revision() == entry.runningRevision) entry.busy = false;
                    completed.accept(key, res);
                }
            }
        });
    }

    // Include the current invocation and any newer queued inputs
    public boolean busy(String key){
        Entry entry = entries.get(key);
        return entry != null && (entry.busy || entry.session != null && entry.session.running());
    }

    // Inspect the latest immutable snapshot without changing engine state
    public ScriptRequest request(String key){
        Entry entry = entries.get(key);
        return entry == null ? null : entry.req;
    }

    public ScriptTerminal terminal(String key){
        Entry entry = entries.get(key);
        return entry == null || entry.session == null ? null : entry.session.terminal();
    }

    public GraphValue reference(String key){
        Entry entry = entries.get(key);
        return entry == null || entry.session == null ? null : entry.session.reference();
    }

    // Cancel a removed or invalid script before accepting another invocation for its key
    public void remove(String key){
        Entry entry = entries.remove(key);
        if(entry != null && entry.session != null) entry.session.close();
    }

    public void retain(Set<String> keys){
        entries.entrySet().removeIf(entry -> {
            if(keys.contains(entry.getKey())) return false;
            if(entry.getValue().session != null) entry.getValue().session.close();
            return true;
        });
    }

    public void namedEvent(String name, GraphValue data){
        entries.values().forEach(entry -> { if(entry.session != null) entry.session.namedEvent(name, data); });
    }

    public void rednetMessage(int sender, String protocol, GraphValue data){
        entries.values().forEach(entry -> { if(entry.session != null && entry.req != null && entry.req.autoStart()) entry.session.rednetMessage(sender, protocol, data); });
    }

    public GraphValue output(String key, String name){ var entry = entries.get(key); return entry == null || entry.session == null ? null : entry.session.output(name); }

    @Override public void close(){ retain(Set.of()); }

    private static final class Entry{
        private final UUID id = UUID.randomUUID();
        private long revision;
        private long sourceRevision;
        private long completedRevision;
        private long completedSequence;
        private long runningRevision;
        private boolean pulsed;
        private ScriptRequest req;
        private ScriptSession session;
        private boolean busy;
    }
}
