package com.rieno.gadgetsandgizmos.lib.util;

import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Cache compatibility lookups without retaining classes beyond their loader lifetime
public final class ReflectionMethods{
    private static final ClassValue<Methods> METHODS = new ClassValue<>(){
        @Override
        protected Methods computeValue(Class<?> type){
            Map<String, Map<Integer, Method>> res = new HashMap<>();
            Map<Signature, Method> signatures = new HashMap<>();
            for(Class<?> current = type; current != null; current = current.getSuperclass()){
                Method[] declared;
                try{
                    declared = current.getDeclaredMethods();
                }catch(LinkageError | SecurityException ignored){
                    continue;
                }
                for(Method method : declared){
                    signatures.putIfAbsent(new Signature(method.getName(), List.of(method.getParameterTypes())), method);
                    Map<Integer, Method> overloads = res.computeIfAbsent(method.getName(), key -> new HashMap<>());
                    overloads.putIfAbsent(method.getParameterCount(), method);
                    try{
                        method.setAccessible(true);
                    }catch(RuntimeException ignored){}
                }
            }
            Map<String, Map<Integer, Method>> frozen = new HashMap<>();
            res.forEach((name, overloads) -> frozen.put(name, Map.copyOf(overloads)));
            return new Methods(Map.copyOf(frozen), Map.copyOf(signatures));
        }
    };

    private ReflectionMethods(){}

    // Prefer declarations on the supplied class before searching its superclasses
    public static @Nullable Method find(Class<?> type, String name, int parameterCount){
        if(type == null || name == null || parameterCount < 0) return null;
        Map<Integer, Method> overloads = METHODS.get(type).byArity().get(name);
        return overloads == null ? null : overloads.get(parameterCount);
    }

    // Match the full signature while skipping optional classes whose method types are unavailable
    public static @Nullable Method find(Class<?> type, String name, Class<?>... parameterTypes){
        if(type == null || name == null || parameterTypes == null) return null;
        return METHODS.get(type).bySignature().get(new Signature(name, List.of(parameterTypes)));
    }

    // Rebuild compatibility metadata after a host changes a class's declarations
    public static void invalidate(Class<?> type){
        if(type != null) METHODS.remove(type);
    }

    private record Signature(String name, List<Class<?>> parameters){}
    private record Methods(Map<String, Map<Integer, Method>> byArity, Map<Signature, Method> bySignature){}
}
