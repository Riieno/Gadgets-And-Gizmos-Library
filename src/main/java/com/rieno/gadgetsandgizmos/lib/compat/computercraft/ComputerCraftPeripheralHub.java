package com.rieno.gadgetsandgizmos.lib.compat.computercraft;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import dan200.computercraft.api.filesystem.Mount;
import dan200.computercraft.api.filesystem.WritableMount;
import dan200.computercraft.api.lua.IArguments;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.MethodResult;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.peripheral.NotAttachedException;
import dan200.computercraft.api.peripheral.WorkMonitor;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

// Forward native peripheral calls through caller-owned target discovery and method bindings
public final class ComputerCraftPeripheralHub{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        API CONTRACTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    @FunctionalInterface
    public interface Method{
        MethodResult call(IPeripheral peripheral, IComputerAccess computer, ILuaContext ctx,
                          IArguments args) throws LuaException;
    }

    // Resolve loaded targets on the server thread and keep their keys independent of labels
    public record Target(String key, IPeripheral peripheral, Map<String, Method> methods){
        public Target{
            if(key == null || key.isBlank()) throw new IllegalArgumentException("A target key is required");
            Objects.requireNonNull(peripheral, "peripheral");
            methods = Map.copyOf(methods);
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        VARIABLES
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private final String namespace;
    private final Supplier<? extends Collection<Target>> source;
    private final Map<String, String> ids = new HashMap<>();
    private final Map<String, Integer> nextIds = new HashMap<>();
    private final Map<IComputerAccess, Map<String, RemoteAccess>> computers = new ConcurrentHashMap<>();
    private volatile Map<String, Target> targets = Map.of();
    private volatile Map<String, String> names = Map.of();

    public ComputerCraftPeripheralHub(String namespace, Supplier<? extends Collection<Target>> source){
        if(namespace == null || namespace.isBlank()) throw new IllegalArgumentException("A hub namespace is required");
        this.namespace = namespace;
        this.source = Objects.requireNonNull(source, "source");
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Track each computer separately; target attachment happens on the next server refresh
    public synchronized void attach(IComputerAccess computer){
        computers.putIfAbsent(computer, new ConcurrentHashMap<>());
    }

    public synchronized void detach(IComputerAccess computer){
        Map<String, RemoteAccess> attached = computers.remove(computer);
        if(attached != null) attached.values().forEach(RemoteAccess::close);
    }

    public boolean hasComputers(){
        return !computers.isEmpty();
    }

    // Call on the owning server thread, including after link or capability changes
    public synchronized void refresh(){
        Map<String, Target> next = new LinkedHashMap<>();
        Map<String, String> nextNames = new LinkedHashMap<>();
        for(Target target : source.get()){
            if(target.methods().isEmpty()) continue;
            String type = target.peripheral().getType();
            String key = type + "/" + target.key();
            String id = ids.computeIfAbsent(key, ignored -> type + "_"
                    + (nextIds.merge(type, 1, Integer::sum) - 1));
            String name = namespace + "/" + id;
            Target prev = targets.get(name);
            if(prev != null && sameTarget(prev, target)) target = prev;
            next.putIfAbsent(name, target);
            nextNames.putIfAbsent(id, name);
        }
        targets = Collections.unmodifiableMap(next);
        names = Collections.unmodifiableMap(nextNames);
        computers.forEach((computer, attached) -> {
            for(var entry : List.copyOf(attached.entrySet())){
                if(next.get(entry.getKey()) == entry.getValue().target) continue;
                attached.remove(entry.getKey());
                entry.getValue().close();
                computer.queueEvent("peripheral_detach", entry.getKey());
            }
            next.forEach((name, target) -> {
                if(attached.containsKey(name)) return;
                RemoteAccess access = new RemoteAccess(computer, name, target);
                attached.put(name, access);
                try{
                    target.peripheral().attach(access);
                }catch(RuntimeException ex){
                    attached.remove(name);
                    access.close();
                    throw ex;
                }
                computer.queueEvent("peripheral", name);
            });
        });
    }

    // Both lookup forms return the same native peripheral name
    public Map<String, String> getPeripherals(){
        refresh();
        return names;
    }

    public String peripheral(String id) throws LuaException{
        String name = getPeripherals().get(id);
        if(name == null) throw new LuaException("unknown or unavailable peripheral '" + id + "'");
        return name;
    }

    public List<String> getNamesRemote(){
        refresh();
        return List.copyOf(targets.keySet());
    }

    public boolean isPresentRemote(String name){
        refresh();
        return targets.containsKey(name);
    }

    public Object[] getTypeRemote(String name){
        refresh();
        Target target = targets.get(name);
        if(target == null) return null;
        Set<String> types = new LinkedHashSet<>();
        types.add(target.peripheral().getType());
        types.addAll(target.peripheral().getAdditionalTypes());
        return types.toArray();
    }

    public Boolean hasTypeRemote(String name, String type){
        Object[] types = getTypeRemote(name);
        return types == null ? null : List.of(types).contains(type);
    }

    public List<String> getMethodsRemote(String name){
        refresh();
        Target target = targets.get(name);
        return target == null ? null : target.methods().keySet().stream().sorted().toList();
    }

    // Resolve on the server thread, then invoke on the Lua thread so yielding methods stay native
    public MethodResult callRemote(IComputerAccess computer, ILuaContext ctx, IArguments args) throws LuaException{
        String name = args.getString(0);
        String method = args.getString(1);
        args.escapes();
        RemoteAccess[] resolved = new RemoteAccess[1];
        MethodResult lookup = ctx.executeMainThreadTask(() -> {
            refresh();
            Map<String, RemoteAccess> attached = computers.get(computer);
            RemoteAccess access = attached == null ? null : attached.get(name);
            if(access == null) throw new LuaException("peripheral '" + name + "' is no longer available");
            resolved[0] = access;
            return null;
        });
        return after(lookup, () -> {
            RemoteAccess access = resolved[0];
            access.checkAttached();
            Method fn = access.target.methods().get(method);
            if(fn == null) throw new LuaException("No such method '" + method + "'");
            ILuaContext guarded = task -> ctx.issueMainThreadTask(() -> {
                refresh();
                if(!access.active) throw new LuaException("peripheral '" + name + "' is no longer available");
                return task.execute();
            });
            return fn.call(access.target.peripheral(), access, guarded, args.drop(2));
        });
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static boolean sameTarget(Target prev, Target next){
        return prev.key().equals(next.key()) && prev.peripheral().equals(next.peripheral())
                && prev.peripheral().getAdditionalTypes().equals(next.peripheral().getAdditionalTypes())
                && prev.methods().keySet().equals(next.methods().keySet());
    }

    @FunctionalInterface
    private interface Continuation{
        MethodResult run() throws LuaException;
    }

    private static MethodResult after(MethodResult res, Continuation next) throws LuaException{
        if(res.getCallback() == null) return next.run();
        return MethodResult.yield(res.getResult(), event -> after(res.getCallback().resume(event), next));
    }

    // Give targets their remote name, shared target lookup, events and owned filesystem mounts
    private final class RemoteAccess implements IComputerAccess{
        private final IComputerAccess computer;
        private final String name;
        private final Target target;
        private final Set<String> mounts = new LinkedHashSet<>();
        private volatile boolean active = true;

        private RemoteAccess(IComputerAccess computer, String name, Target target){
            this.computer = computer;
            this.name = name;
            this.target = target;
        }

        private void checkAttached(){
            if(!active) throw new NotAttachedException();
        }

        private synchronized void close(){
            if(!active) return;
            active = false;
            try{
                target.peripheral().detach(this);
            }finally{
                for(String mount : mounts) computer.unmount(mount);
                mounts.clear();
            }
        }

        @Override
        public synchronized String mount(String path, Mount mount, String drive){
            checkAttached();
            String mounted = computer.mount(path, mount, drive);
            if(mounted != null) mounts.add(mounted);
            return mounted;
        }

        @Override
        public String mount(String path, Mount mount){
            return mount(path, mount, name);
        }

        @Override
        public synchronized String mountWritable(String path, WritableMount mount, String drive){
            checkAttached();
            String mounted = computer.mountWritable(path, mount, drive);
            if(mounted != null) mounts.add(mounted);
            return mounted;
        }

        @Override
        public String mountWritable(String path, WritableMount mount){
            return mountWritable(path, mount, name);
        }

        @Override
        public synchronized void unmount(String path){
            checkAttached();
            if(mounts.remove(path)) computer.unmount(path);
        }

        @Override
        public int getID(){
            checkAttached();
            return computer.getID();
        }

        @Override
        public void queueEvent(String event, Object... args){
            checkAttached();
            computer.queueEvent(event, args);
        }

        @Override
        public String getAttachmentName(){
            checkAttached();
            return name;
        }

        @Override
        public Map<String, IPeripheral> getAvailablePeripherals(){
            checkAttached();
            Map<String, IPeripheral> available = new LinkedHashMap<>(computer.getAvailablePeripherals());
            targets.forEach((targetName, entry) -> available.put(targetName, entry.peripheral()));
            return Map.copyOf(available);
        }

        @Override
        public IPeripheral getAvailablePeripheral(String name){
            checkAttached();
            Target entry = targets.get(name);
            return entry == null ? computer.getAvailablePeripheral(name) : entry.peripheral();
        }

        @Override
        public WorkMonitor getMainThreadMonitor(){
            checkAttached();
            return computer.getMainThreadMonitor();
        }
    }
}
