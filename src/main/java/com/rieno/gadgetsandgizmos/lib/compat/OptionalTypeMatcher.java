package com.rieno.gadgetsandgizmos.lib.compat;

import java.util.Objects;
import java.util.function.Predicate;

// Match optional runtime types once per concrete class without loading the optional mod
public final class OptionalTypeMatcher implements Predicate<Object>{
    private final String className;
    private final ClassValue<Boolean> matches = new ClassValue<>(){
        @Override
        protected Boolean computeValue(Class<?> type){
            for(Class<?> current = type; current != null; current = current.getSuperclass()){
                if(className.equals(current.getName())) return true;
                for(Class<?> iface : current.getInterfaces()){
                    if(className.equals(iface.getName())) return true;
                }
            }
            return false;
        }
    };

    // Select the class or directly implemented interface to match
    public OptionalTypeMatcher(String className){
        this.className = Objects.requireNonNull(className, "className");
    }

    // Reuse positive and negative matches without retaining runtime instances
    @Override
    public boolean test(Object val){
        return val != null && matches.get(val.getClass());
    }
}
