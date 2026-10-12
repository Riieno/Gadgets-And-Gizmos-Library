package com.rieno.gadgetsandgizmos.lib.client.view;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

// Retain Distant Horizons' main-view state while its chunk-layer hooks see a secondary scene
public final class ViewLodCompat{
    private static final List<Field> FIELDS = new ArrayList<>();
    private static Object state;
    private static boolean checked;

    // Prevent construction of the optional integration
    private ViewLodCompat(){}

    // Resolve the shared render-state contract without requiring Distant Horizons to be installed
    private static void resolve(){
        if(checked) return;
        checked = true;
        try{
            Class<?> api = Class.forName("com.seibel.distanthorizons.core.api.internal.ClientApi", false,
                    ViewLodCompat.class.getClassLoader());
            try{ state = api.getField("RENDER_STATE").get(null); }
            catch(NoSuchFieldException err){ return; }
            if(state == null) return;
            for(Class<?> type = state.getClass(); type != Object.class; type = type.getSuperclass()){
                for(Field field : type.getDeclaredFields()){
                    if(Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())) continue;
                    field.setAccessible(true);
                    FIELDS.add(field);
                }
            }
        }catch(ClassNotFoundException err){
            state = null;
        }catch(ReflectiveOperationException err){
            throw new IllegalStateException("Unable to retain Distant Horizons render state", err);
        }
    }

    // Restore the same matrix and level references after optional LOD hooks run
    public static Runnable save(){
        resolve();
        if(state == null || FIELDS.isEmpty()) return () -> {};
        Object[] values = new Object[FIELDS.size()];
        try{
            for(int idx = 0; idx < values.length; idx++) values[idx] = FIELDS.get(idx).get(state);
        }catch(IllegalAccessException err){
            throw new IllegalStateException("Unable to read Distant Horizons render state", err);
        }
        return () -> {
            try{
                for(int idx = 0; idx < values.length; idx++) FIELDS.get(idx).set(state, values[idx]);
            }catch(IllegalAccessException err){
                throw new IllegalStateException("Unable to restore Distant Horizons render state", err);
            }
        };
    }
}
