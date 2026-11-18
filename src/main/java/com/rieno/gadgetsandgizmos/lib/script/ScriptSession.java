package com.rieno.gadgetsandgizmos.lib.script;

import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;

// Keep an optional script engine behind a game-thread-owned asynchronous boundary
public interface ScriptSession extends AutoCloseable{
    // Restart changed source according to autoStart and update live inputs separately
    default void register(ScriptRequest req){}
    void submit(ScriptRequest req);
    default void submit(ScriptRequest req, String port){
        if(!"exec".equals(port)) throw new IllegalArgumentException("Named execution inputs are unavailable");
        submit(req);
    }
    void tick();
    ScriptResult poll();
    default ScriptExecPulse pollExec(){ return null; }
    default ScriptTerminal terminal(){ return null; }
    default GraphValue reference(){ return null; }
    // Read a value actually published by this computer, rather than a host's retained graph cache
    default GraphValue output(String name){ return null; }
    default ScriptPortChange pollPort(){ return null; }
    default ScriptOutputChange pollOutput(){ return null; }
    default boolean running(){ return false; }
    // Hosts authorize physical input before forwarding it to an interactive script session
    default boolean input(ScriptInput input){ return false; }
    default boolean monitorInput(String name, ScriptInput input){ return false; }
    // Admit a named callback only while its handler slot is available
    default boolean acceptsPulse(String port){ return false; }
    default void namedEvent(String name, GraphValue data){}
    // Supply a local transport event without broadcasting it outside a preview session
    default void rednetMessage(int sender, String protocol, GraphValue data){}
    @Override void close();
}
