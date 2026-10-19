package com.rieno.gadgetsandgizmos.lib.client.render;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.function.BooleanSupplier;

// Query Axiom's public editor state without a mandatory Axiom or Fabric dependency
final class AxiomEditorCompat{
    private static final Logger LOGGER = LoggerFactory.getLogger(AxiomEditorCompat.class);

    private AxiomEditorCompat(){}

    static boolean isActive(){ return Probe.ACTIVE.getAsBoolean(); }

    private static BooleanSupplier resolve(){
        try{
            return probe(Class.forName("com.moulberry.axiom.editor.EditorUI", false,
                    AxiomEditorCompat.class.getClassLoader()));
        }catch(ClassNotFoundException err){
            return () -> false;
        }catch(LinkageError err){
            LOGGER.warn("Unable to query Axiom editor ownership", err);
            return () -> false;
        }
    }

    // Bind once; the value remains live as the editor opens and closes
    static BooleanSupplier probe(Class<?> type){
        try{
            Method enabled = type.getMethod("isEnabled");
            if(enabled.getReturnType() != boolean.class || !Modifier.isStatic(enabled.getModifiers())){
                throw new NoSuchMethodException("Expected public static boolean isEnabled()");
            }
            return new BooleanSupplier(){
                private boolean failed;
                @Override public boolean getAsBoolean(){
                    if(failed) return false;
                    try{
                        return Boolean.TRUE.equals(enabled.invoke(null));
                    }catch(ReflectiveOperationException | LinkageError err){
                        failed = true;
                        LOGGER.warn("Unable to query Axiom editor ownership", err);
                        return false;
                    }
                }
            };
        }catch(ReflectiveOperationException | LinkageError err){
            LOGGER.warn("Axiom editor ownership API is unavailable", err);
            return () -> false;
        }
    }

    private static final class Probe{
        private static final BooleanSupplier ACTIVE = resolve();
    }
}
