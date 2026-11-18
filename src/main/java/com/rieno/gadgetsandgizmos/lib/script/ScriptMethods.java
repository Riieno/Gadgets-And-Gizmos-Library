package com.rieno.gadgetsandgizmos.lib.script;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import com.rieno.gadgetsandgizmos.lib.probe.BlockEntityDataAdapterRegistry;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

// Bind explicit host methods and registered control schemas without exposing reflective access to scripts
public final class ScriptMethods{
    @FunctionalInterface public interface Call{ Object[] invoke(List<Object> args) throws Exception; }
    private final Map<String, Call> methods = new LinkedHashMap<>();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public ScriptMethods bind(String name, Call call){ methods.put(name, Objects.requireNonNull(call)); return this; }
    public ScriptMethods bind(ScriptPeripheralBinding binding){
        for(Method method : binding.getClass().getMethods()){
            var annotation = method.getAnnotation(ScriptFunctionBinding.class);
            if(annotation == null || method.isSynthetic() || method.isBridge()) continue;
            String[] names = annotation.value().length == 0 ? new String[]{method.getName()} : annotation.value();
            for(String name : names) bind(name, args -> invoke(binding, method, args));
        }
        binding.bindMethods(this);
        return this;
    }
    private static Object[] invoke(Object target, Method method, List<Object> args) throws Exception{
        var types = method.getGenericParameterTypes(); Object[] vals = new Object[types.length]; int idx = 0;
        for(int param = 0; param < types.length; param++){
            if(types[param] == ScriptArguments.class){ vals[param] = new ScriptArguments(args.subList(idx, args.size())); idx = args.size(); }
            else vals[param] = argument(idx < args.size() ? args.get(idx++) : null, types[param]);
        }
        if(idx < args.size()) throw new ScriptException("Too many arguments for '" + method.getName() + "'");
        try{
            Object res = method.invoke(target, vals);
            return method.getReturnType() == void.class ? new Object[0] : res instanceof Object[] array ? array : new Object[]{res};
        }catch(InvocationTargetException ex){
            if(ex.getCause() instanceof Exception cause) throw cause;
            throw new ScriptException("Device call failed: " + ex.getCause().getMessage());
        }
    }
    public Set<String> names(){ return Set.copyOf(methods.keySet()); }
    public Object[] call(String name, List<Object> args) throws Exception{
        Call call = methods.get(name);
        if(call == null) throw new IllegalArgumentException("No such method '" + name + "'");
        return call.invoke(args);
    }

    // Only named public methods supplied by the host can be invoked
    public ScriptMethods bind(Object target, String exposed, String nativeName){
        List<Method> candidates = Arrays.stream(target.getClass().getMethods())
                .filter(method -> method.getName().equals(nativeName) && !method.isSynthetic()).toList();
        if(candidates.isEmpty()) return this;
        return bind(exposed, args -> {
            for(Method method : candidates){
                var types = method.getParameterTypes();
                if(args.size() > types.length) continue;
                Object[] vals = new Object[types.length]; boolean valid = true;
                for(int idx = 0; idx < types.length; idx++){
                    try{ vals[idx] = argument(idx < args.size() ? args.get(idx) : null, types[idx]); }
                    catch(IllegalArgumentException ex){ valid = false; break; }
                }
                if(!valid) continue;
                try{
                    Object res = method.invoke(target, vals);
                    return method.getReturnType() == void.class ? new Object[0] : new Object[]{res};
                }catch(InvocationTargetException ex){
                    if(ex.getCause() instanceof Exception cause) throw cause;
                    throw new IllegalArgumentException("Device call failed", ex.getCause());
                }
            }
            throw new IllegalArgumentException("Invalid arguments for '" + exposed + "'");
        });
    }

    // Reuse the same typed data controls consumed by graph Get Data and Set Data
    public ScriptMethods data(BlockEntity target){
        var readable = BlockEntityDataAdapterRegistry.readableData(target);
        var writable = BlockEntityDataAdapterRegistry.writableData(target);
        readable.forEach((field, type) -> {
            String suffix = suffix(field);
            Call read = args -> {
                if(!args.isEmpty()) throw new IllegalArgumentException("Expected no arguments");
                GraphValue val = BlockEntityDataAdapterRegistry.read(target, field);
                return new Object[]{val == null ? null : ScriptValues.encode(val)};
            };
            methods.putIfAbsent("get" + suffix, read);
            if("boolean".equals(type)) methods.putIfAbsent("is" + suffix, read);
        });
        writable.forEach((field, type) -> methods.putIfAbsent("set" + suffix(field), args -> {
            if(args.size() != 1) throw new IllegalArgumentException("Expected one value");
            GraphValue val = ScriptValues.outputs(Map.of("value", args.getFirst()), Map.of("value", type)).get("value");
            BlockEntityDataAdapterRegistry.write(target, field, val); return new Object[0];
        }));
        bind("getData", args -> {
            if(args.size() != 1 || !readable.containsKey(args.getFirst())) throw new IllegalArgumentException("Unknown readable field");
            return new Object[]{ScriptValues.encode(BlockEntityDataAdapterRegistry.read(target, (String)args.getFirst()))};
        });
        bind("setData", args -> {
            if(args.size() != 2 || !writable.containsKey(args.getFirst())) throw new IllegalArgumentException("Unknown writable field");
            String field = (String)args.getFirst();
            GraphValue val = ScriptValues.outputs(Map.of("value", args.get(1)), Map.of("value", writable.get(field))).get("value");
            return new Object[]{BlockEntityDataAdapterRegistry.write(target, field, val)};
        });
        return this;
    }
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static String suffix(String field){
        StringBuilder res = new StringBuilder(); boolean upper = true;
        for(char ch : field.toCharArray()){
            if(ch == '_' || ch == '-' || ch == ' '){ upper = true; continue; }
            res.append(upper ? Character.toUpperCase(ch) : ch); upper = false;
        }
        return res.toString();
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object argument(Object val, java.lang.reflect.Type type){
        if(type instanceof java.lang.reflect.ParameterizedType generic){
            if(generic.getRawType() == Optional.class) return val == null ? Optional.empty() : Optional.of(argument(val, generic.getActualTypeArguments()[0]));
            type = generic.getRawType();
        }
        return argument(val, (Class<?>)type);
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object argument(Object val, Class<?> type){
        if(type == Optional.class) return Optional.ofNullable(val);
        if(val == null) throw new IllegalArgumentException("Missing argument");
        if(type == String.class && val instanceof String) return val;
        if((type == boolean.class || type == Boolean.class) && val instanceof Boolean) return val;
        if(val instanceof Number num){
            if(!Double.isFinite(num.doubleValue())) throw new IllegalArgumentException("Expected a finite number");
            if(type == double.class || type == Double.class) return num.doubleValue();
            if(type == float.class || type == Float.class) return num.floatValue();
            if(type == int.class || type == Integer.class) return num.intValue();
            if(type == long.class || type == Long.class) return num.longValue();
        }
        if(type.isEnum() && val instanceof String text) return Enum.valueOf((Class)type, text.toUpperCase(Locale.ROOT));
        if(type.isInstance(val)) return val;
        throw new IllegalArgumentException("Invalid argument type");
    }
}
