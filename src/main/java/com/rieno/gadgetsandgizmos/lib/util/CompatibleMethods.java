package com.rieno.gadgetsandgizmos.lib.util;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

// Retain compatible method lookups without keeping an owning class loader alive
public final class CompatibleMethods{
    private static final int MAX_SIGNATURES = 256;
    private static final ClassValue<ConcurrentHashMap<Key, Optional<Method>>> METHODS = new ClassValue<>(){
        @Override protected ConcurrentHashMap<Key, Optional<Method>> computeValue(Class<?> type){
            return new ConcurrentHashMap<>();
        }
    };

    private CompatibleMethods(){}

    // Include null arguments and their exact runtime types when caching overload selection
    public static Method find(Class<?> type, String name, Object... arguments){
        List<Class<?>> types = new ArrayList<>(arguments.length);
        for(Object arg : arguments) types.add(arg == null ? Void.class : arg.getClass());
        Key key = new Key(name, List.copyOf(types));
        var cache = METHODS.get(type);
        if(cache.size() >= MAX_SIGNATURES) cache.clear();
        return cache.computeIfAbsent(key, ignored -> Optional.ofNullable(resolve(type, name, arguments)))
                .orElse(null);
    }

    private static Method resolve(Class<?> owner, String name, Object[] arguments){
        for(Class<?> type = owner; type != null; type = type.getSuperclass()){
            for(Method method : type.getDeclaredMethods()){
                if(!method.getName().equals(name) || method.getParameterCount() != arguments.length) continue;
                Class<?>[] types = method.getParameterTypes();
                boolean compatible = true;
                for(int idx = 0; idx < arguments.length; idx++){
                    if(arguments[idx] != null && !accepts(types[idx], arguments[idx])){
                        compatible = false;
                        break;
                    }
                }
                if(compatible) return method;
            }
        }
        return null;
    }

    private static boolean accepts(Class<?> type, Object arg){
        if(!type.isPrimitive()) return type.isInstance(arg);
        return type == boolean.class && arg instanceof Boolean
                || type == byte.class && arg instanceof Byte
                || type == char.class && arg instanceof Character
                || type == short.class && arg instanceof Short
                || type == int.class && arg instanceof Integer
                || type == long.class && arg instanceof Long
                || type == float.class && arg instanceof Float
                || type == double.class && arg instanceof Double;
    }

    private record Key(String name, List<Class<?>> arguments){}
}
