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
import com.rieno.gadgetsandgizmos.lib.script.ScriptPeripheral;

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
    public record Target(String key, IPeripheral peripheral, Map<String, Method> methods, Set<String> aliases, Set<String> references){
        public Target(String key, IPeripheral peripheral, Map<String, Method> methods){ this(key, peripheral, methods, Set.of(), Set.of()); }
        public Target{
            if(key == null || key.isBlank()) throw new IllegalArgumentException("A target key is required");
            Objects.requireNonNull(peripheral, "peripheral");
            methods = Map.copyOf(methods);
            aliases = Set.copyOf(aliases);
            references = Set.copyOf(references);
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
    private volatile Map<String, String> aliases = Map.of();

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
        Map<String, RemoteAccess> attached = computers.computeIfAbsent(computer, ignored -> new ConcurrentHashMap<>());
        reconcile(computer, attached, targets);
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
        Map<String, String> nextAliases = new LinkedHashMap<>();
        Set<String> ambiguous = new LinkedHashSet<>();
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
            for(String alias : target.aliases()){
                if(alias.isBlank() || Set.of("top", "bottom", "left", "right", "front", "back").contains(alias)) continue;
                String prevName = nextAliases.putIfAbsent(alias, name);
                if(prevName != null && !prevName.equals(name)) ambiguous.add(alias);
            }
        }
        nextAliases.entrySet().removeIf(entry -> ambiguous.contains(entry.getKey()) || next.containsKey(entry.getKey())
                || nextNames.containsKey(entry.getKey()) && !nextNames.get(entry.getKey()).equals(entry.getValue()));
        nextAliases.forEach(nextNames::putIfAbsent);
        targets = Collections.unmodifiableMap(next);
        names = Collections.unmodifiableMap(nextNames);
        aliases = Collections.unmodifiableMap(nextAliases);
        computers.forEach((computer, attached) -> reconcile(computer, attached, next));
    }

    private void reconcile(IComputerAccess computer, Map<String, RemoteAccess> attached, Map<String, Target> next){
        for(var entry : List.copyOf(attached.entrySet())){
            if(next.get(entry.getKey()) == entry.getValue().target) continue;
            attached.remove(entry.getKey()); entry.getValue().close(); computer.queueEvent("peripheral_detach", entry.getKey());
        }
        next.forEach((name, target) -> {
            if(attached.containsKey(name)) return;
            RemoteAccess access = new RemoteAccess(computer, name, target);
            attached.put(name, access);
            try{ target.peripheral().attach(access); }
            catch(RuntimeException ex){ attached.remove(name); access.close(); throw ex; }
            computer.queueEvent("peripheral", name);
        });
    }

    // Both lookup forms return the same native peripheral name
    public Map<String, String> getPeripherals(){
        return names;
    }

    public String peripheral(String id) throws LuaException{
        String name = getPeripherals().get(id);
        if(name == null && targets.containsKey(id)) name = id;
        if(name == null) throw new LuaException("unknown or unavailable peripheral '" + id + "'");
        return name;
    }

    public List<String> getNamesRemote(){
        return List.copyOf(targets.keySet());
    }

    // Supply the same resolved aliases and native methods used by runtime calls
    public List<ScriptPeripheral> catalog(){
        return targets.entrySet().stream().map(entry -> new ScriptPeripheral(entry.getKey(), entry.getValue().peripheral().getType(),
                aliases.entrySet().stream().filter(alias -> alias.getValue().equals(entry.getKey())).map(Map.Entry::getKey).toList(),
                List.copyOf(entry.getValue().methods().keySet()), List.copyOf(entry.getValue().references()))).toList();
    }

    private String resolveName(String name){ return aliases.getOrDefault(name, name); }

    public boolean isPresentRemote(String name){
        return targets.containsKey(resolveName(name));
    }

    public Object[] getTypeRemote(String name){
        Target target = targets.get(resolveName(name));
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
        Target target = targets.get(resolveName(name));
        return target == null ? null : target.methods().keySet().stream().sorted().toList();
    }

    // Use published attachments directly and preserve only the native method's own yields
    public MethodResult callRemote(IComputerAccess computer, ILuaContext ctx, IArguments args) throws LuaException{
        String name = args.getString(0), method = args.getString(1);
        Map<String, RemoteAccess> attached = computers.get(computer);
        RemoteAccess access = attached == null ? null : attached.get(resolveName(name));
        if(access == null) throw new LuaException("peripheral '" + name + "' is no longer available");
        access.checkAttached();
        Method fn = access.target.methods().get(method);
        if(fn == null) throw new LuaException("No such method '" + method + "'");
        args.escapes();
        ILuaContext guarded = task -> ctx.issueMainThreadTask(() -> {
            access.checkAttached(); return task.execute();
        });
        return fn.call(access.target.peripheral(), access, guarded, args.drop(2));
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static boolean sameTarget(Target prev, Target next){
        return prev.key().equals(next.key()) && prev.peripheral().equals(next.peripheral())
                && prev.peripheral().getAdditionalTypes().equals(next.peripheral().getAdditionalTypes())
                && prev.aliases().equals(next.aliases()) && prev.references().equals(next.references())
                && prev.methods().keySet().equals(next.methods().keySet());
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
            Target entry = targets.get(resolveName(name));
            return entry == null ? computer.getAvailablePeripheral(name) : entry.peripheral();
        }

        @Override
        public WorkMonitor getMainThreadMonitor(){
            checkAttached();
            return computer.getMainThreadMonitor();
        }
    }
}
