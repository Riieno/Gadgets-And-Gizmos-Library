package com.rieno.gadgetsandgizmos.lib.util;

import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

// Cache compatibility lookups without retaining classes beyond their loader lifetime
public final class ReflectionMethods{
    private static final ClassValue<Map<String, Map<Integer, Method>>> METHODS = new ClassValue<>(){
        @Override
        protected Map<String, Map<Integer, Method>> computeValue(Class<?> type){
            Map<String, Map<Integer, Method>> res = new HashMap<>();
            for(Class<?> current = type; current != null; current = current.getSuperclass()){
                Method[] declared;
                try{
                    declared = current.getDeclaredMethods();
                }catch(LinkageError | SecurityException ignored){
                    continue;
                }
                for(Method method : declared){
                    Map<Integer, Method> overloads = res.computeIfAbsent(method.getName(), key -> new HashMap<>());
                    if(overloads.putIfAbsent(method.getParameterCount(), method) != null) continue;
                    try{
                        method.setAccessible(true);
                    }catch(RuntimeException ignored){}
                }
            }
            Map<String, Map<Integer, Method>> frozen = new HashMap<>();
            res.forEach((name, overloads) -> frozen.put(name, Map.copyOf(overloads)));
            return Map.copyOf(frozen);
        }
    };

    private ReflectionMethods(){}

    // Prefer declarations on the supplied class before searching its superclasses
    public static @Nullable Method find(Class<?> type, String name, int parameterCount){
        if(type == null || name == null || parameterCount < 0) return null;
        Map<Integer, Method> overloads = METHODS.get(type).get(name);
        return overloads == null ? null : overloads.get(parameterCount);
    }

    // Rebuild compatibility metadata after a host changes a class's declarations
    public static void invalidate(Class<?> type){
        if(type != null) METHODS.remove(type);
    }
}
