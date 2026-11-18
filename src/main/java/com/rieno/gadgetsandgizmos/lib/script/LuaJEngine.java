package com.rieno.gadgetsandgizmos.lib.script;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import net.minecraft.nbt.CompoundTag;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LoadState;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaClosure;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaThread;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.compiler.LuaC;
import org.luaj.vm2.lib.BaseLib;
import org.luaj.vm2.lib.Bit32Lib;
import org.luaj.vm2.lib.CoroutineLib;
import org.luaj.vm2.lib.DebugLib;
import org.luaj.vm2.lib.MathLib;
import org.luaj.vm2.lib.PackageLib;
import org.luaj.vm2.lib.StringLib;
import org.luaj.vm2.lib.TableLib;
import org.luaj.vm2.lib.VarArgFunction;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

// Keep LuaJ implementation types inside the selected private runtime loader
public final class LuaJEngine implements ScriptSession{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final int LIMIT = 1_000_000, MAX_QUEUE = 128;
    private final UUID id;
    private final ScriptHost host;
    private final CompoundTag portData = new CompoundTag();
    private final Map<String, GraphValue> values = new LinkedHashMap<>(), changed = new LinkedHashMap<>();
    private final Set<String> removed = new LinkedHashSet<>();
    private final ArrayDeque<ScriptResult> results = new ArrayDeque<>();
    private final ArrayDeque<ScriptExecPulse> pulses = new ArrayDeque<>();
    private final ArrayDeque<ScriptPortChange> ports = new ArrayDeque<>();
    private final ArrayDeque<Object[]> events = new ArrayDeque<>();
    private final ArrayDeque<Object[]> messages = new ArrayDeque<>();
    private final ArrayDeque<Call> calls = new ArrayDeque<>();
    private final Map<Integer, Long> timers = new LinkedHashMap<>();
    private final Map<Long, java.util.concurrent.CompletableFuture<ScriptHttp.Response>> requests = new LinkedHashMap<>();
    private final Map<Long, String> asynchronousRequests = new HashMap<>();
    private final Set<LuaThread> workers = new LinkedHashSet<>();
    private final AtomicInteger budget = new AtomicInteger();
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private List<ScriptDevice> devices = List.of();
    private ScriptRequest registered, synchronizedRequest, pending;
    private String pendingPort;
    private Globals globals;
    private LuaValue terminalCapture;
    private ScriptTerminal terminal;
    private final Map<String, ScriptTerminal> displayFrames = new LinkedHashMap<>();
    private final Map<String, ScriptTerminal> changedDisplays = new LinkedHashMap<>();
    private final Map<String, int[]> displaySizes = new LinkedHashMap<>();
    private LuaThread thread;
    private long tick, sequence, callId, dayTicks, epoch;
    private double[] position = {0, 0, 0};
    private int timerId;
    private boolean closed, started, initialized, mainRunning, handlerRunning, persistent;

    public LuaJEngine(UUID id){ this(id, new ScriptHost(){}); }
    public LuaJEngine(UUID id, ScriptHost host){
        this.id = Objects.requireNonNull(id); this.host = Objects.requireNonNull(host);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Check the selected interpreter before accepting it as the sandbox backend
    public static void verifyRuntime(){
        var globals = new Globals();
        globals.load(new BaseLib()); globals.load(new PackageLib()); globals.load(new MathLib());
        globals.load(new StringLib()); globals.load(new TableLib()); globals.load(new Bit32Lib()); globals.load(new CoroutineLib());
        globals.load(new DebugLib()); LoadState.install(globals); LuaC.install(globals);
        if(globals.load("return 1 + 1", "@runtime-check").call().checkint() != 2) throw new IllegalStateException("LuaJ verification failed");
    }

    @Override public void register(ScriptRequest req){
        if(closed) return;
        if(!id.equals(req.id())) throw new IllegalArgumentException("Script request belongs to another session");
        ScriptRequest prev = registered;
        boolean restart = prev == null || !prev.source().equals(req.source()) || prev.autoStart() != req.autoStart();
        if(restart){
            stop();
            if(!persistent) values.clear();
            var before = portData.copy();
            portData.getAllKeys().stream().toList().forEach(portData::remove);
            schema(req, null);
            if(prev != null){
                portData.put(ScriptPorts.OWNED, before.getCompound(ScriptPorts.OWNED).copy());
                ScriptPorts.reconcile(portData, prev.source(), req.source());
            }
            portData.putBoolean(ScriptPorts.TRACKED, true);
        }else schema(req, prev);
        registered = req;
        if(restart) start();
        else event("__gng_script");
    }

    @Override public void submit(ScriptRequest req){ register(req); }
    @Override public void submit(ScriptRequest req, String port){
        if(!acceptsPulse(port)) return;
        register(req); pending = req; pendingPort = port; event("__gng_script");
    }
    @Override public boolean acceptsPulse(String port){
        return !closed && thread != null && initialized && !handlerRunning && pending == null
                && portData.getCompound(ScriptPorts.EXEC_INPUTS).contains(port);
    }
    @Override public boolean running(){ return mainRunning || handlerRunning || pending != null || thread != null && !started; }
    @Override public ScriptResult poll(){ return results.poll(); }
    @Override public ScriptTerminal terminal(){ return terminal; }
    @Override public GraphValue reference(){
        return com.rieno.gadgetsandgizmos.lib.graph.TargetGraphNodes.reference(com.rieno.gadgetsandgizmos.lib.display.VirtualDisplaySources.reference(id));
    }
    // Accept validated input on the owning game thread only after the script attaches its terminal
    public boolean input(ScriptInput input){
        if(closed || thread == null || terminal == null) return false;
        Object[] args = input.arguments(terminal.width(), terminal.height());
        Object[] event = new Object[args.length + 1]; event[0] = input.event();
        System.arraycopy(args, 0, event, 1, args.length); event(event); return true;
    }
    public boolean monitorInput(String name, ScriptInput input){
        var frame = displayFrames.get(name);
        if(closed || thread == null || frame == null || !Set.of("click", "drag", "release").contains(input.action())) return false;
        Object[] args = input.arguments(frame.width(), frame.height());
        if("click".equals(input.action())) event("monitor_touch", name, args[1], args[2]);
        event(switch(input.action()){ case "click" -> "monitor_click"; case "drag" -> "monitor_drag"; default -> "monitor_up"; }, name, args[0], args[1], args[2]);
        return true;
    }
    @Override public ScriptExecPulse pollExec(){ return pulses.poll(); }
    @Override public ScriptPortChange pollPort(){ return ports.poll(); }
    @Override public GraphValue output(String name){ return values.get(name); }
    @Override public ScriptOutputChange pollOutput(){
        if(registered == null || changed.isEmpty() && removed.isEmpty()) return null;
        var res = new ScriptOutputChange(id, registered.revision(), changed, removed);
        changed.clear(); removed.clear(); return res;
    }
    @Override public void namedEvent(String name, GraphValue data){ event("gng_named_event", name, ScriptValues.encode(data)); }
    @Override public void rednetMessage(int sender, String protocol, GraphValue data){
        if(closed || registered == null || !registered.autoStart()) return;
        if(messages.size() >= MAX_QUEUE) messages.removeFirst();
        messages.add(new Object[]{"rednet_message", sender, ScriptValues.encode(data), protocol});
        event("__gng_script");
    }

    // Only this method invokes host operations; Lua callbacks exchange bounded data
    @Override public void tick(){
        if(closed || thread == null) return;
        tick++; dayTicks = host.dayTicks(); epoch = System.currentTimeMillis(); position = host.position().clone();
        devices = List.copyOf(host.devices());
        for(var device : devices) if("monitor".equals(device.type())){
            int[] size = host.terminalSize(device.name());
            if(size == null || size.length != 2 || size[0] <= 0 || size[1] <= 0) size = new int[]{51, 19};
            var configured = com.rieno.gadgetsandgizmos.lib.config.LuaRuntimeConfig.monitorSize(size[0], size[1]);
            size = new int[]{configured.width(), configured.height()};
            int[] prev = displaySizes.put(device.name(), size.clone());
            if(prev != null && !java.util.Arrays.equals(prev, size)) event("monitor_resize", device.name());
        }
        var names = devices.stream().map(ScriptDevice::name).collect(java.util.stream.Collectors.toSet());
        displaySizes.keySet().removeIf(name -> !names.contains(name));
        for(int idx = calls.size(); idx > 0; idx--){
            Call call = calls.removeFirst();
            try{
                Object[] res;
                if("__http".equals(call.name) || "__http_async".equals(call.name)){
                    if(requests.size() >= MAX_QUEUE) throw new ScriptException("Too many pending HTTP requests");
                    Map<String, String> headers = new LinkedHashMap<>();
                    if(call.args.size() > 3 && call.args.get(3) instanceof Map<?, ?> map) map.forEach((key, val) -> headers.put(key.toString(), val.toString()));
                    requests.put(call.id, host.request(new ScriptHttp.Request((String)call.args.get(0), (String)call.args.get(1),
                            call.args.get(2) == null ? null : call.args.get(2).toString(), headers)).toCompletableFuture());
                    if("__http_async".equals(call.name)) asynchronousRequests.put(call.id, (String)call.args.getFirst());
                    continue;
                }else if("__rednet".equals(call.name)){
                    host.transmit(id, id.hashCode() & Integer.MAX_VALUE, call.args.get(0) instanceof Number num ? num.intValue() : null,
                            (String)call.args.get(2), call.args.get(1));
                    res = new Object[]{true};
                }else if("__print".equals(call.name)){ host.output((String)call.args.getFirst()); continue; }
                else res = host.call(call.name, call.method, call.args);
                event("__lua_call", call.id, true, res == null ? new Object[0] : res);
            }catch(Exception ex){
                if("__http_async".equals(call.name)) event("http_failure", call.args.getFirst(), Objects.toString(ex.getMessage(), "HTTP request failed"));
                else event("__lua_call", call.id, false, Objects.toString(ex.getMessage(), "Device call failed"));
            }
        }
        requests.entrySet().removeIf(entry -> {
            if(!entry.getValue().isDone()) return false;
            String url = asynchronousRequests.remove(entry.getKey());
            try{
                var res = entry.getValue().join();
                if(url == null) event("__lua_call", entry.getKey(), true, new Object[]{res.code(), res.body(), res.headers()});
                else{
                    Map<String, Object> response = Map.of("__response", true, "code", res.code(), "body", res.body(), "headers", res.headers());
                    if(res.code() >= 200 && res.code() < 400) event("http_success", url, response);
                    else event("http_failure", url, "HTTP request failed with status " + res.code(), response);
                }
            }catch(RuntimeException ex){
                Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                if(url == null) event("__lua_call", entry.getKey(), false, Objects.toString(cause.getMessage(), "HTTP request failed"));
                else event("http_failure", url, Objects.toString(cause.getMessage(), "HTTP request failed"));
            }
            return true;
        });
        timers.entrySet().removeIf(entry -> {
            if(entry.getValue() > tick) return false;
            event("timer", entry.getKey()); return true;
        });
        budget.set(LIMIT);
        if(!started){ started = true; resume(new Object[0]); }
        for(int idx = Math.min(events.size(), 32); idx > 0 && thread != null; idx--) resume(events.removeFirst());
        changedDisplays.forEach(host::terminal);
        changedDisplays.clear();
    }

    @Override public void close(){ if(closed) return; closed = true; stop(); values.clear(); host.close(); }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private void schema(ScriptRequest req, ScriptRequest prev){
        schema("DynamicInputs", "any", req.inputs().keySet(), prev == null ? Set.of() : prev.inputs().keySet());
        schema("DynamicOutputs", "any", req.outputs().keySet(), prev == null ? Set.of() : prev.outputs().keySet());
        schema(ScriptPorts.EXEC_INPUTS, "exec", req.execInputs(), prev == null ? Set.of() : prev.execInputs());
        schema(ScriptPorts.EXEC_OUTPUTS, "exec", req.execOutputs(), prev == null ? Set.of() : prev.execOutputs());
    }
    private void schema(String key, String type, Set<String> names, Set<String> prev){
        var data = portData.getCompound(key);
        for(String name : prev) if(!names.contains(name)) data.remove(name);
        for(String name : names) if(!prev.contains(name)) data.putString(name, type);
        portData.put(key, data);
    }
    private void start(){
        cancelled.set(false); budget.set(LIMIT);
        globals = new Globals();
        globals.load(new BaseLib()); globals.load(new PackageLib()); globals.load(new MathLib());
        globals.load(new StringLib()); globals.load(new TableLib()); globals.load(new Bit32Lib()); globals.load(new CoroutineLib());
        globals.set("string", protectedStrings(globals.get("string")));
        globals.load(new DebugLib(){
            @Override public void onInstruction(int pc, Varargs args, int top){
                if(cancelled.get() || budget.decrementAndGet() < 0) throw new ExecutionStopped();
                super.onInstruction(pc, args, top);
            }
        });
        LoadState.install(globals); LuaC.install(globals);
        globals.get("coroutine").set("create", fn(args -> {
            workers.removeIf(worker -> "dead".equals(worker.getStatus()));
            if(workers.size() >= 64) throw new LuaError("Script exceeds its coroutine limit");
            LuaThread worker = new LuaThread(globals, args.arg1().checkfunction());
            workers.add(worker); return worker;
        }));
        // Retain the metered Globals even when a chunk supplies its own _ENV table
        globals.set("load", fn(args -> {
            String source = args.arg1().checkjstring();
            if(source.length() > LuaScripts.MAX_SOURCE_LENGTH + 128) throw new LuaError("Script source exceeds its limit");
            if(!args.arg(3).optjstring("t").equals("t")) throw new LuaError("Only text chunks are supported");
            try{
                var prototype = globals.compilePrototype(new java.io.StringReader(source), args.arg(2).optjstring("@Lua"));
                LuaClosure closure = new LuaClosure(prototype, globals);
                if(closure.upValues.length > 0 && !args.arg(4).isnil()) closure.upValues[0].setValue(args.arg(4));
                return closure;
            }catch(java.io.IOException | LuaError ex){ return LuaValue.varargsOf(new LuaValue[]{LuaValue.NIL, LuaValue.valueOf(ex.getMessage())}); }
        }));
        LuaTable bridge = new LuaTable();
        bridge.set("next", fn(args -> next()));
        bridge.set("validated", fn(args -> { complete(args.arg(1).checklong(), args.arg(2).checkjstring(), false, "startup"); return LuaValue.NIL; }));
        bridge.set("complete", fn(args -> {
            String port = args.arg(3).checkjstring();
            if("startup".equals(port)) mainRunning = false; else handlerRunning = false;
            complete(registered.revision(), args.arg(2).checkjstring(), true, port); return LuaValue.NIL;
        }));
        bridge.set("ready", fn(args -> { mainRunning = args.arg1().toboolean(); handlerRunning = args.arg(2).toboolean(); initialized = args.arg(3).toboolean(); return LuaValue.NIL; }));
        bridge.set("waiting", fn(args -> {
            if(!"__lua_call".equals(args.arg1().tojstring()) && !"__lua_call".equals(args.arg(2).tojstring())) captureTerminal();
            if(Set.of("*", "rednet_message").contains(args.arg1().tojstring()) || Set.of("*", "rednet_message").contains(args.arg(2).tojstring())){
                while(!messages.isEmpty()) event(messages.removeFirst());
            }
            return LuaValue.NIL;
        }));
        bridge.set("persistence", fn(args -> { persistent = args.arg1().checkboolean(); return LuaValue.NIL; }));
        bridge.set("outputs", fn(args -> {
            Map<?, ?> data = (Map<?, ?>)java(args.arg(2));
            Map<String, String> types = new LinkedHashMap<>();
            for(Object key : data.keySet()) if(key instanceof String name && portData.getCompound("DynamicOutputs").contains(name)) types.put(name, "any");
            ScriptValues.outputs(data, types).forEach((name, val) -> {
                if(!val.equals(values.put(name, val))){ changed.put(name, val); removed.remove(name); }
            });
            for(Object val : ((Map<?, ?>)java(args.arg(3))).values()) if(val instanceof String name && values.remove(name) != null){ changed.remove(name); removed.add(name); }
            return LuaValue.NIL;
        }));
        bridge.set("pulse", fn(args -> {
            String name = args.arg1().checkjstring();
            if(!portData.getCompound(ScriptPorts.EXEC_OUTPUTS).contains(name)) throw new LuaError("Unknown execution output");
            if(pulses.size() >= MAX_QUEUE) throw new LuaError("Too many execution pulses");
            pulses.add(new ScriptExecPulse(id, registered.revision(), name, values)); return LuaValue.NIL;
        }));
        bridge.set("port", fn(args -> {
            String name = args.arg1().checkjstring(); boolean out = args.arg(2).checkboolean(), exec = args.arg(3).checkboolean(), remove = args.arg(4).checkboolean();
            String declaration = ScriptPortDeclarations.at(registered.source(), args.arg(5).optint(0), out, exec, name);
            var before = ScriptPorts.snapshot(portData);
            ScriptPorts.programmatic(portData, name, out, exec, remove, declaration);
            if(!before.equals(ScriptPorts.snapshot(portData))){
                if(ports.size() >= MAX_QUEUE) throw new LuaError("Too many port changes");
                ports.add(new ScriptPortChange(id, registered.revision(), name, out, exec, remove, declaration));
            }
            if(out && !exec && remove){ values.remove(name); changed.remove(name); removed.add(name); }
            return LuaValue.NIL;
        }));
        globals.set("__gng_script", bridge);
        LuaTable api = new LuaTable();
        var terminalSize = com.rieno.gadgetsandgizmos.lib.config.LuaRuntimeConfig.terminalSize();
        api.set("terminalWidth", terminalSize.width()); api.set("terminalHeight", terminalSize.height());
        api.set("capture", fn(args -> { terminalCapture = args.arg1().checkfunction(); return LuaValue.NIL; }));
        api.set("terminalSize", fn(args -> {
            int[] size = displaySizes.get(args.arg1().checkjstring()); if(size == null) return LuaValue.NIL;
            return LuaValue.varargsOf(new LuaValue[]{LuaValue.valueOf(size[0]), LuaValue.valueOf(size[1])});
        }));
        api.set("call", fn(args -> {
            if(calls.size() >= MAX_QUEUE) throw new LuaError("Too many device calls");
            List<Object> vals = new ArrayList<>();
            int[] count = {0}; var active = new IdentityHashMap<LuaValue, Boolean>();
            for(int idx = 3; idx <= args.narg(); idx++) vals.add(java(args.arg(idx), 0, count, active));
            long key = ++callId; calls.add(new Call(key, args.arg1().checkjstring(), args.arg(2).checkjstring(), vals));
            return LuaValue.valueOf(key);
        }));
        api.set("devices", fn(args -> lua(devices.stream().map(device -> Map.of("name", device.name(), "type", device.type(),
                "types", device.types().stream().sorted().toList(), "aliases", device.aliases().stream().sorted().toList(),
                "references", device.references().stream().sorted().toList(), "methods", device.methods().stream().sorted().toList())).toList())));
        api.set("clock", fn(args -> LuaValue.valueOf(tick / 20.0)));
        api.set("dayTicks", fn(args -> LuaValue.valueOf(dayTicks)));
        api.set("epoch", fn(args -> LuaValue.valueOf(epoch)));
        api.set("position", fn(args -> LuaValue.varargsOf(new LuaValue[]{LuaValue.valueOf(position[0]), LuaValue.valueOf(position[1]), LuaValue.valueOf(position[2])})));
        api.set("id", LuaValue.valueOf(id.hashCode() & Integer.MAX_VALUE));
        api.set("resource", fn(args -> lua(host.resource(args.arg1().checkjstring()))));
        api.set("timer", fn(args -> {
            double seconds = args.arg1().checkdouble();
            if(!Double.isFinite(seconds) || timers.size() >= MAX_QUEUE) throw new LuaError("Invalid timer or too many timers");
            int key = ++timerId; timers.put(key, tick + Math.max(1, (long)Math.ceil(seconds * 20))); return LuaValue.valueOf(key);
        }));
        api.set("cancelTimer", fn(args -> { timers.remove(args.arg1().checkint()); return LuaValue.NIL; }));
        api.set("parseJSON", fn(args -> {
            String source = args.arg1().checkjstring();
            if(source.length() > 32768) throw new LuaError("JSON exceeds its size limit");
            try{ return lua(json(com.google.gson.JsonParser.parseString(source), 0)); }
            catch(RuntimeException ex){ return LuaValue.varargsOf(new LuaValue[]{LuaValue.NIL, LuaValue.valueOf("Invalid JSON: " + ex.getMessage())}); }
        }));
        globals.set("__lua_host", api);
        try{
            globals.set("__lua_terminal", globals.load(resource("terminal.lua"), "@terminal").call());
            globals.load(resource("fallback.lua"), "@fallback").call();
            thread = new LuaThread(globals, globals.load(resource("runtime.lua"), "@runtime"));
        }catch(RuntimeException ex){ complete(registered.revision(), ex.getMessage(), true, "startup"); thread = null; }
    }

    private void captureTerminal(){
        if(terminalCapture == null) return;
        Varargs frames = terminalCapture.invoke();
        terminal = snapshot(frames.arg1());
        LuaValue displays = frames.arg(2), key = LuaValue.NIL;
        Set<String> names = new LinkedHashSet<>();
        while(true){
            Varargs next = displays.next(key); key = next.arg1(); if(key.isnil()) break;
            String name = key.checkjstring(); ScriptTerminal frame = snapshot(next.arg(2));
            names.add(name);
            if(frame != null && !frame.equals(displayFrames.put(name, frame))) changedDisplays.put(name, frame);
        }
        displayFrames.keySet().removeIf(name -> {
            if(names.contains(name)) return false;
            changedDisplays.put(name, null); return true;
        });
    }
    private static ScriptTerminal snapshot(LuaValue data){
        if(data.isnil()) return null;
        List<String> text = new ArrayList<>(), fg = new ArrayList<>(), bg = new ArrayList<>();
        List<Integer> palette = new ArrayList<>();
        int width = data.get("width").checkint(), height = data.get("height").checkint();
        for(int idx = 1; idx <= height; idx++){
            var line = data.get("text").get(idx).checkstring();
            text.add(new String(line.m_bytes, line.m_offset, line.m_length, StandardCharsets.ISO_8859_1));
            fg.add(data.get("foreground").get(idx).checkjstring());
            bg.add(data.get("background").get(idx).checkjstring());
        }
        for(int idx = 1; idx <= 16; idx++) palette.add(data.get("palette").get(idx).checkint());
        return new ScriptTerminal(width, height, text, fg, bg, palette, data.get("cursorX").checkint(), data.get("cursorY").checkint(),
                data.get("blink").checkboolean(), data.get("cursorColor").checkint(), rows(data.get("foregroundHex"), height), rows(data.get("backgroundHex"), height), data.get("cursorHex").optjstring(""));
    }
    private static List<String> rows(LuaValue data, int height){
        if(data.isnil()) return List.of();
        List<String> rows = new ArrayList<>(); for(int idx = 1; idx <= height; idx++) rows.add(data.get(idx).checkjstring()); return rows;
    }

    private LuaValue next(){
        ScriptRequest req = pending == null ? registered : pending;
        if(req == null || synchronizedRequest == req && pending == null) return LuaValue.NIL;
        Map<String, Object> update = new LinkedHashMap<>(); update.put("revision", req.revision());
        if(pending != null){ pending = null; handlerRunning = true; update.put("run", true); update.put("exec", pendingPort); }
        var delta = ScriptUpdate.between(synchronizedRequest, req);
        if(synchronizedRequest == null || delta.source() != null){
            update.put("source", LuaScripts.function(req.source())); update.put("autoStart", req.autoStart());
            Map<String, Object> retained = new LinkedHashMap<>();
            if(persistent) values.forEach((name, val) -> retained.put(name, ScriptValues.encode(val)));
            update.put("retained", retained); persistent = false; mainRunning = req.autoStart();
        }
        if(delta.outputs() != null){
            Map<String, Object> defaults = new LinkedHashMap<>(); delta.outputs().forEach((name, type) -> defaults.put(name, ScriptValues.defaultValue(type)));
            update.put("defaults", defaults);
        }
        Map<String, Object> inputs = new LinkedHashMap<>(); delta.inputs().forEach((name, val) -> inputs.put(name, ScriptValues.encode(val)));
        update.put("inputs", inputs); update.put("removed", delta.removed().stream().toList());
        update.put("execInputs", flags(req.execInputs())); update.put("execOutputs", flags(req.execOutputs()));
        synchronizedRequest = req; return lua(update);
    }
    private static Map<String, Boolean> flags(Set<String> names){ var res = new LinkedHashMap<String, Boolean>(); names.forEach(name -> res.put(name, true)); return res; }
    private void resume(Object[] event){
        try{
            LuaValue[] args = new LuaValue[event.length]; for(int idx = 0; idx < args.length; idx++) args[idx] = lua(event[idx]);
            if(event.length > 1 && Set.of("char", "paste").contains(Objects.toString(event[0])) && event[1] instanceof String text){
                args[1] = LuaValue.valueOf(text.getBytes(StandardCharsets.ISO_8859_1));
            }
            Varargs res = thread.resume(LuaValue.varargsOf(args));
            if(!res.arg1().toboolean() || "dead".equals(thread.getStatus())){
                String error = res.arg1().toboolean() ? "Script scheduler stopped" : res.arg(2).tojstring();
                complete(registered.revision(), error, true, "startup"); thread = null; mainRunning = false; handlerRunning = false; initialized = true;
            }
        }catch(RuntimeException | StackOverflowError ex){
            complete(registered.revision(), Objects.toString(ex.getMessage(), "Script exceeded its recursion limit"), true, "startup");
            thread = null; mainRunning = false; handlerRunning = false; initialized = true;
        }
    }
    private void complete(long revision, String error, boolean executed, String port){
        if(results.size() >= MAX_QUEUE) results.removeFirst();
        results.add(new ScriptResult(id, revision, values, error == null ? "" : error, executed, ++sequence, port));
    }
    private void event(Object... args){
        if(closed) return;
        if(events.size() >= MAX_QUEUE) events.removeFirst();
        events.add(args);
    }
    private void stop(){
        cancelled.set(true);
        if(thread != null && "suspended".equals(thread.getStatus())) thread.resume(LuaValue.valueOf("terminate"));
        for(LuaThread worker : workers) if("suspended".equals(worker.getStatus())) worker.resume(LuaValue.valueOf("terminate"));
        workers.clear();
        thread = null; globals = null; synchronizedRequest = null; pending = null;
        terminalCapture = null; terminal = null;
        displayFrames.keySet().forEach(name -> changedDisplays.put(name, null)); displayFrames.clear();
        displaySizes.clear();
        started = false; initialized = false; mainRunning = false; handlerRunning = false;
        events.clear(); messages.clear(); calls.clear(); timers.clear(); results.clear(); pulses.clear(); ports.clear(); changed.clear(); removed.clear();
        requests.values().forEach(request -> request.cancel(true)); requests.clear();
        asynchronousRequests.clear();
    }
    private static String resource(String name){
        try(InputStream stream = ScriptSession.class.getResourceAsStream(name)){
            if(stream == null) throw new IllegalStateException("Missing Lua resource " + name);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }catch(java.io.IOException ex){ throw new IllegalStateException("Cannot load Lua resource " + name, ex); }
    }
    private static LuaValue protectedStrings(LuaValue library){
        // LuaJ shares its string metatable across users, so retain a separate API table
        LuaTable copy = new LuaTable(); LuaValue key = LuaValue.NIL;
        while(true){ Varargs next = library.next(key); key = next.arg1(); if(key.isnil()) break; copy.set(key, next.arg(2)); }
        boundRepeat(copy); return copy;
    }
    private static void boundRepeat(LuaValue library){
        LuaValue original = library.get("rep");
        if(original instanceof BoundedRepeat) return;
        library.set("rep", new BoundedRepeat(original));
    }
    private static final class BoundedRepeat extends VarArgFunction{
        private final LuaValue original;
        private BoundedRepeat(LuaValue original){ this.original = original; }
        @Override public Varargs invoke(Varargs args){
            long count = Math.max(0, args.arg(2).checklong()), length = args.arg1().checkstring().rawlen();
            long separator = args.arg(3).isnil() ? 0 : args.arg(3).checkstring().rawlen();
            if(count > 32768 || length * count + separator * Math.max(0, count - 1) > 32768) throw new LuaError("Script text exceeds 32768 characters");
            return original.invoke(args);
        }
    }
    // Resource limits must unwind protected calls as well as ordinary Lua code
    private static final class ExecutionStopped extends Error{
        private ExecutionStopped(){ super("Script exceeded its execution budget"); }
    }
    private static VarArgFunction fn(Function<Varargs, Varargs> action){
        return new VarArgFunction(){ @Override public Varargs invoke(Varargs args){
            try{ return action.apply(args); }catch(IllegalArgumentException ex){ throw new LuaError(ex.getMessage()); }
        }};
    }
    private record Call(long id, String name, String method, List<Object> args){}

    private static Object json(com.google.gson.JsonElement val, int depth){
        if(depth > 16) throw new IllegalArgumentException("JSON exceeds the table limit");
        if(val.isJsonNull()) return null;
        if(val.isJsonArray()){
            List<Object> list = new ArrayList<>(); val.getAsJsonArray().forEach(entry -> list.add(json(entry, depth + 1))); return list;
        }
        if(val.isJsonObject()){
            Map<String, Object> map = new LinkedHashMap<>(); val.getAsJsonObject().entrySet().forEach(entry -> map.put(entry.getKey(), json(entry.getValue(), depth + 1))); return map;
        }
        var primitive = val.getAsJsonPrimitive();
        return primitive.isBoolean() ? primitive.getAsBoolean() : primitive.isNumber() ? primitive.getAsDouble() : primitive.getAsString();
    }

    // Copy primitives and bounded tables without exposing Java objects to Lua
    private static LuaValue lua(Object val){ return lua(val, 0, new int[]{0}, new IdentityHashMap<>()); }
    private static LuaValue lua(Object val, int depth, int[] count, IdentityHashMap<Object, Boolean> active){
        if(depth > 16 || ++count[0] > 4096) throw new IllegalArgumentException("Host values exceed the table limit");
        if(val == null) return LuaValue.NIL;
        if(val instanceof Boolean bool) return LuaValue.valueOf(bool);
        if(val instanceof Number num){
            if(!Double.isFinite(num.doubleValue())) throw new IllegalArgumentException("Host numbers must be finite");
            return LuaValue.valueOf(num.doubleValue());
        }
        if(val instanceof String text){
            if(text.length() > 32768) throw new IllegalArgumentException("Host text exceeds 32768 characters");
            return LuaValue.valueOf(text);
        }
        if(active.put(val, true) != null) throw new IllegalArgumentException("Host values cannot contain cycles");
        try{
            LuaTable table = new LuaTable();
            if(val instanceof Object[] array){ for(int idx = 0; idx < array.length; idx++) table.set(idx + 1, lua(array[idx], depth + 1, count, active)); table.set("n", array.length); }
            else if(val instanceof List<?> list){ for(int idx = 0; idx < list.size(); idx++) table.set(idx + 1, lua(list.get(idx), depth + 1, count, active)); }
            else if(val instanceof Map<?, ?> map) for(var entry : map.entrySet()) table.set(lua(entry.getKey(), depth + 1, count, active), lua(entry.getValue(), depth + 1, count, active));
            else throw new IllegalArgumentException("Unsupported host value");
            return table;
        }finally{ active.remove(val); }
    }
    private static Object java(LuaValue val){ return java(val, 0, new int[]{0}, new IdentityHashMap<>()); }
    private static Object java(LuaValue val, int depth, int[] count, IdentityHashMap<LuaValue, Boolean> active){
        if(depth > 16 || ++count[0] > 4096) throw new LuaError("Script values exceed the table limit");
        if(val.isnil()) return null;
        if(val.isboolean()) return val.toboolean();
        if(val.type() == LuaValue.TNUMBER){ if(!Double.isFinite(val.todouble())) throw new LuaError("Script numbers must be finite"); return val.todouble(); }
        if(val.type() == LuaValue.TSTRING){
            if(val.rawlen() > 32768) throw new LuaError("Script text exceeds 32768 characters");
            var text = val.checkstring();
            return text.isValidUtf8() ? text.tojstring() : new String(text.m_bytes, text.m_offset, text.m_length, StandardCharsets.ISO_8859_1);
        }
        if(!val.istable() || active.put(val, true) != null) throw new LuaError("Unsupported or recursive script value");
        try{
            Map<Object, Object> res = new LinkedHashMap<>(); LuaValue key = LuaValue.NIL;
            while(true){ Varargs next = val.next(key); key = next.arg1(); if(key.isnil()) break;
                if(key.type() != LuaValue.TSTRING && key.type() != LuaValue.TNUMBER) throw new LuaError("Invalid table key");
                res.put(java(key, depth + 1, count, active), java(next.arg(2), depth + 1, count, active));
            }
            return res;
        }finally{ active.remove(val); }
    }
}
