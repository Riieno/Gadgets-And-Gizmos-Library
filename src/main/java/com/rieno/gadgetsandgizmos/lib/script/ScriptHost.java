package com.rieno.gadgetsandgizmos.lib.script;

import java.util.List;
import java.util.UUID;

// Resolve devices and perform world operations exclusively on the owning game thread
public interface ScriptHost extends AutoCloseable{
    default List<ScriptDevice> devices(){ return List.of(); }
    default Object[] call(String name, String method, List<Object> args) throws Exception{ throw new ScriptException("Peripheral is unavailable: " + name); }
    default long ticks(){ return 0; }
    default long dayTicks(){ return ticks(); }
    default double[] position(){ return new double[]{0, 0, 0}; }
    default void transmit(UUID session, int sender, Integer recipient, String protocol, Object data){}
    default void output(String text){}
    // Receive complete monitor frames on the owning game thread, or null to release a frame
    default void terminal(String name, ScriptTerminal frame){}
    // Resolve a monitor's unscaled character area on the owning game thread
    default int[] terminalSize(String name){ return new int[]{51, 19}; }
    // Supply registered read-only Lua modules without granting host filesystem access
    default String resource(String path){ return null; }
    default java.util.concurrent.CompletionStage<ScriptHttp.Response> request(ScriptHttp.Request req){ return ScriptHttp.request(req); }
    @Override default void close(){}
}
