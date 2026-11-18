package com.rieno.gadgetsandgizmos.lib.script;

import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;

import java.util.UUID;

// Keep the public session independent of whichever LuaJ version supplies the interpreter
public final class LuaJScriptSession implements ScriptSession{
    private final ScriptSession delegate;

    public LuaJScriptSession(UUID id){ this(id, new ScriptHost(){}); }
    public LuaJScriptSession(UUID id, ScriptHost host){ delegate = LuaJRuntimes.create(id, host); }

    @Override public void register(ScriptRequest req){ delegate.register(req); }
    @Override public void submit(ScriptRequest req){ delegate.submit(req); }
    @Override public void submit(ScriptRequest req, String port){ delegate.submit(req, port); }
    @Override public boolean acceptsPulse(String port){ return delegate.acceptsPulse(port); }
    @Override public boolean running(){ return delegate.running(); }
    @Override public ScriptResult poll(){ return delegate.poll(); }
    @Override public ScriptTerminal terminal(){ return delegate.terminal(); }
    @Override public GraphValue reference(){ return delegate.reference(); }
    @Override public boolean input(ScriptInput input){ return delegate.input(input); }
    @Override public boolean monitorInput(String name, ScriptInput input){ return delegate.monitorInput(name, input); }
    @Override public ScriptExecPulse pollExec(){ return delegate.pollExec(); }
    @Override public ScriptPortChange pollPort(){ return delegate.pollPort(); }
    @Override public GraphValue output(String name){ return delegate.output(name); }
    @Override public ScriptOutputChange pollOutput(){ return delegate.pollOutput(); }
    @Override public void namedEvent(String name, GraphValue data){ delegate.namedEvent(name, data); }
    @Override public void rednetMessage(int sender, String protocol, GraphValue data){ delegate.rednetMessage(sender, protocol, data); }
    @Override public void tick(){ delegate.tick(); }
    @Override public void close(){ delegate.close(); }
}
