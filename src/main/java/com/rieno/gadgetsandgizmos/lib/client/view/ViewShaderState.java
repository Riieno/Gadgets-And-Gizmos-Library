package com.rieno.gadgetsandgizmos.lib.client.view;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

// Retain optional shader uniforms and immediate draw flags across secondary scenes
final class ViewShaderState{
    private static final List<Slot> SLOTS = new ArrayList<>();
    private static final List<Field> LOCKS = new ArrayList<>();
    private static boolean checked;

    // Prevent construction of the optional state helper
    private ViewShaderState(){}

    // Resolve shader state without loading Iris on clients where it is absent
    private static void resolve(){
        if(checked) return;
        checked = true;
        for(String name : List.of("net.irisshaders.iris.uniforms.CapturedRenderingState",
                "net.irisshaders.iris.vertices.ImmediateState", "net.irisshaders.iris.uniforms.IrisTimeUniforms",
                "net.irisshaders.iris.gl.blending.BlendModeStorage", "net.irisshaders.iris.gl.blending.DepthColorStorage")){
            try{
                Class<?> type = Class.forName(name, false, ViewShaderState.class.getClassLoader());
                Object owner = name.endsWith("CapturedRenderingState") ? type.getField("INSTANCE").get(null) : null;
                for(Field field : type.getDeclaredFields()){
                    if(Modifier.isFinal(field.getModifiers())) continue;
                    if(Modifier.isStatic(field.getModifiers()) != (owner == null)) continue;
                    field.setAccessible(true);
                    SLOTS.add(new Slot(field, owner));
                    if(field.getType() == boolean.class && field.getName().endsWith("Locked")) LOCKS.add(field);
                }
            }catch(ClassNotFoundException err){
                // Leave vanilla clients independent of the optional shader mod
            }catch(ReflectiveOperationException err){
                throw new IllegalStateException("Unable to retain Iris scene state", err);
            }
        }
    }

    // Restore actual GPU state without overwriting a shader's deferred blend or depth settings
    static void restoreDrawState(Runnable restore){
        boolean[] locked = new boolean[LOCKS.size()];
        try{
            for(int idx = 0; idx < locked.length; idx++) locked[idx] = LOCKS.get(idx).getBoolean(null);
            try{
                for(Field field : LOCKS) field.setBoolean(null, false);
                restore.run();
            }finally{
                for(int idx = 0; idx < locked.length; idx++) LOCKS.get(idx).setBoolean(null, locked[idx]);
            }
        }catch(IllegalAccessException err){
            throw new IllegalStateException("Unable to restore Iris draw overrides", err);
        }
    }

    // Restore the player's uniform references and flags even if a capture fails
    static Runnable save(){
        resolve();
        Object[] values = new Object[SLOTS.size()];
        try{
            for(int idx = 0; idx < values.length; idx++){
                Slot slot = SLOTS.get(idx);
                values[idx] = slot.field.get(slot.owner);
            }
        }catch(IllegalAccessException err){
            throw new IllegalStateException("Unable to read Iris scene state", err);
        }
        return () -> {
            try{
                for(int idx = 0; idx < values.length; idx++){
                    Slot slot = SLOTS.get(idx);
                    slot.field.set(slot.owner, values[idx]);
                }
            }catch(IllegalAccessException err){
                throw new IllegalStateException("Unable to restore Iris scene state", err);
            }
        };
    }

    private record Slot(Field field, Object owner){}
}
