package com.rieno.gadgetsandgizmos.lib.script;

import com.rieno.gadgetsandgizmos.lib.graph.GraphPortOrder;
import com.rieno.gadgetsandgizmos.lib.graph.LuaGraphNode;
import net.minecraft.nbt.CompoundTag;
import java.util.List;
import java.util.Set;

// Keep authored ports separate from ports owned by declarations in the current script
public final class ScriptPorts{
    public static final String EXEC_INPUTS = "LuaExecInputs", EXEC_OUTPUTS = "LuaExecOutputs";
    public static final String PORT = "__lua_ports";
    public static final String OWNED = "LuaDeclaredPorts";
    public static final String TRACKED = "LuaPortOwnershipTracked";
    public static final List<String> KEYS = List.of("DynamicInputs", "DynamicOutputs", EXEC_INPUTS, EXEC_OUTPUTS,
            GraphPortOrder.INPUT_ORDER, GraphPortOrder.OUTPUT_ORDER, OWNED, TRACKED);
    private ScriptPorts(){}

    public static boolean programmatic(CompoundTag data, String name, boolean output, boolean execution, boolean remove, String declaration){
        boolean changed = change(data, name, output, execution, remove);
        String key = key(output, execution); var owned = data.getCompound(OWNED); var ports = owned.getCompound(key);
        if(remove) ports.remove(name);
        else if(changed || ports.contains(name)) ports.putString(name, declaration);
        if(ports.isEmpty()) owned.remove(key); else owned.put(key, ports);
        if(owned.isEmpty()) data.remove(OWNED); else data.put(OWNED, owned);
        return changed;
    }

    // Remove only script-owned ports whose declaration expression no longer exists
    public static boolean reconcile(CompoundTag data, String previous, String source){
        CompoundTag before = snapshot(data);
        var owned = data.getCompound(OWNED); var old = ScriptPortDeclarations.read(previous); var next = ScriptPortDeclarations.read(source);
        boolean changed = false;
        for(boolean output : List.of(false, true)) for(boolean execution : List.of(false, true)){
            String key = key(output, execution); var ports = data.getCompound(key); var names = owned.getCompound(key);
            for(var declaration : old) if(!data.getBoolean(TRACKED) && declaration.output() == output && declaration.execution() == execution
                    && !declaration.name().isBlank() && ports.contains(declaration.name()) && !names.contains(declaration.name()))
                names.putString(declaration.name(), declaration.key());
            if(execution) for(String name : ports.getAllKeys()) if(!names.contains(name)) names.putString(name, "");
            for(String name : List.copyOf(names.getAllKeys())){
                String declaration = names.getString(name);
                boolean retain = next.stream().anyMatch(val -> val.output() == output && val.execution() == execution
                        && (val.key().equals(declaration) || val.name().equals(name)));
                if(!retain){ changed |= change(data, name, output, execution, true); names.remove(name); }
            }
            if(names.isEmpty()) owned.remove(key); else owned.put(key, names);
        }
        if(owned.isEmpty()) data.remove(OWNED); else data.put(OWNED, owned);
        data.putBoolean(TRACKED, true);
        return changed || !before.equals(snapshot(data));
    }

    private static String key(boolean output, boolean execution){ return execution ? output ? EXEC_OUTPUTS : EXEC_INPUTS : output ? "DynamicOutputs" : "DynamicInputs"; }

    public static boolean change(CompoundTag data, String name, boolean output, boolean execution, boolean remove){
        if(name == null || name.isBlank() || name.length() > 64 || name.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("Invalid script port name");
        if((output ? Set.of("valid", "error", "busy", LuaGraphNode.INSTANCE) : Set.of("name")).contains(name)) throw new IllegalArgumentException("Reserved script port name");
        String key = execution ? output ? EXEC_OUTPUTS : EXEC_INPUTS : output ? "DynamicOutputs" : "DynamicInputs";
        CompoundTag ports = data.getCompound(key);
        if(remove){
            if(!ports.contains(name)) return false;
            ports.remove(name); data.put(key, ports);
            String orderKey = output ? GraphPortOrder.OUTPUT_ORDER : GraphPortOrder.INPUT_ORDER;
            GraphPortOrder.write(data, orderKey, GraphPortOrder.read(data, orderKey).stream().filter(port -> !name.equals(port)).toList());
            return true;
        }
        var existing = LuaGraphNode.ports(data, output).get(name);
        String type = execution ? "exec" : "any";
        if(existing != null){
            if(!existing.equals(type)) throw new IllegalArgumentException("Script port name conflicts with another port");
            return false;
        }
        if(LuaGraphNode.ports(data, output).size() >= 64) throw new IllegalArgumentException("Scripts support at most 64 ports per side");
        ports.putString(name, type); data.put(key, ports);
        String orderKey = output ? GraphPortOrder.OUTPUT_ORDER : GraphPortOrder.INPUT_ORDER;
        var order = new java.util.ArrayList<>(GraphPortOrder.read(data, orderKey)); order.add(name); GraphPortOrder.write(data, orderKey, order);
        return true;
    }

    public static CompoundTag snapshot(CompoundTag data){
        CompoundTag tag = new CompoundTag();
        for(String key : KEYS) if(data.contains(key)) tag.put(key, data.get(key).copy());
        return tag;
    }

    public static void apply(CompoundTag target, CompoundTag schema){
        for(String key : KEYS){
            if(schema.contains(key)) target.put(key, schema.get(key).copy()); else target.remove(key);
        }
    }
}
