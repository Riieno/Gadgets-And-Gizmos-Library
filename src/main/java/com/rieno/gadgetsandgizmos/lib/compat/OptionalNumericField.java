package com.rieno.gadgetsandgizmos.lib.compat;

import java.lang.reflect.Field;
import java.util.Optional;

// Read an optional dependency's numeric field without binding to one binary field type
public final class OptionalNumericField{
    private final double fallback;
    private final ClassValue<Optional<Field>> fields;

    public OptionalNumericField(String name, double fallback){
        this.fallback = fallback;
        fields = new ClassValue<>(){
            @Override protected Optional<Field> computeValue(Class<?> type){
                try{ return Optional.of(type.getField(name)); }
                catch(NoSuchFieldException | SecurityException err){ return Optional.empty(); }
            }
        };
    }

    // Accept an instance or a class for static fields and default missing numeric values
    public double get(Object target){
        if(target == null) return fallback;
        var field = fields.get(target instanceof Class<?> type ? type : target.getClass());
        if(field.isEmpty()) return fallback;
        if(target instanceof Class<?> && !java.lang.reflect.Modifier.isStatic(field.get().getModifiers())) return fallback;
        try{
            Object val = field.get().get(target instanceof Class<?> ? null : target);
            return val instanceof Number num && Double.isFinite(num.doubleValue()) ? num.doubleValue() : fallback;
        }catch(IllegalAccessException err){ return fallback; }
    }
}
