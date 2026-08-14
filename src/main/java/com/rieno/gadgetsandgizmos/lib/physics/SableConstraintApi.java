package com.rieno.gadgetsandgizmos.lib.physics;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3dc;

import java.lang.reflect.Method;
import java.util.Set;

// Bridge current and legacy Sable constraint packages in one library surface
public final class SableConstraintApi {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final String CONSTRAINT_PACKAGE = "dev.ryanhcode.sable.api.physics.constraint.";

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the Sable constraint API
    private SableConstraintApi() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Resolve one current or legacy constraint configuration
    public static Class<?> configurationClass(String simpleName, String legacyCategory)
            throws ClassNotFoundException {
        try {
            return Class.forName(CONSTRAINT_PACKAGE + simpleName);
        } catch (ClassNotFoundException modernApiMissing) {
            return Class.forName(CONSTRAINT_PACKAGE + legacyCategory + "." + simpleName);
        }
    }

    // Create one compatible fixed constraint configuration
    public static Object fixedConfiguration(Vector3dc posA, Vector3dc posB, Quaterniondc orientation)
            throws ReflectiveOperationException {
        return anchoredConfiguration("FixedConstraintConfiguration", "fixed", posA, posB, orientation);
    }

    // Create one compatible free constraint configuration
    public static Object freeConfiguration(Vector3dc posA, Vector3dc posB, Quaterniondc orientation)
            throws ReflectiveOperationException {
        return anchoredConfiguration("FreeConstraintConfiguration", "free", posA, posB, orientation);
    }

    // Create one compatible generic constraint configuration
    public static Object genericConfiguration(Vector3dc posA, Vector3dc posB,
                                              Quaterniondc orientationA, Quaterniondc orientationB,
                                              Set<?> lockedAxes)
            throws ReflectiveOperationException {
        return configurationClass("GenericConstraintConfiguration", "generic")
                .getConstructor(Vector3dc.class, Vector3dc.class, Quaterniondc.class, Quaterniondc.class, Set.class)
                .newInstance(posA, posB, orientationA, orientationB, lockedAxes);
    }

    // Add one compatible constraint
    public static Object addConstraint(Object pipeline, Object bodyA, Object bodyB, Object config)
            throws ReflectiveOperationException {
        if (pipeline == null || config == null) {
            throw new IllegalArgumentException("Pipeline and constraint configuration are required");
        }
        if (bodyA == bodyB) {
            return null;
        }
        for (Method method : pipeline.getClass().getMethods()) {
            if (!"addConstraint".equals(method.getName()) || method.getParameterCount() != 3) {
                continue;
            }
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (parameterTypes[2].isInstance(config)
                    && acceptsBody(parameterTypes[0], bodyA)
                    && acceptsBody(parameterTypes[1], bodyB)) {
                return method.invoke(pipeline, bodyA, bodyB, config);
            }
        }
        throw new NoSuchMethodException("No compatible addConstraint method on "
                + pipeline.getClass().getName());
    }

    // Wake one compatible physics body
    public static void wakeUp(Object pipeline, Object body) throws ReflectiveOperationException {
        if (pipeline == null) {
            throw new IllegalArgumentException("Pipeline is required");
        }
        if (body == null) {
            return;
        }
        for (Method method : pipeline.getClass().getMethods()) {
            if ("wakeUp".equals(method.getName()) && method.getParameterCount() == 1
                    && acceptsBody(method.getParameterTypes()[0], body)) {
                method.invoke(pipeline, body);
                return;
            }
        }
        throw new NoSuchMethodException("No compatible wakeUp method on "
                + pipeline.getClass().getName());
    }

    // Update one compatible constraint frame
    public static void setFrame(PhysicsConstraintHandle handle, int frame,
                                Vector3dc pos, Quaterniondc orientation)
            throws ReflectiveOperationException {
        if (handle == null || frame < 1 || frame > 2) {
            throw new IllegalArgumentException("A valid constraint handle and frame are required");
        }
        handle.getClass().getMethod("setFrame" + frame, Vector3dc.class, Quaterniondc.class)
                .invoke(handle, pos, orientation);
    }

    // Remove one compatible constraint handle
    public static boolean remove(PhysicsConstraintHandle handle) {
        if (handle == null) {
            return false;
        }
        for (String methodName : new String[]{"remove", "destroy", "invalidate"}) {
            try {
                handle.getClass().getMethod(methodName).invoke(handle);
                return true;
            } catch (ReflectiveOperationException | LinkageError ignored) {
            }
        }
        return false;
    }

    // Create one compatible anchored constraint configuration
    private static Object anchoredConfiguration(String simpleName, String legacyCategory,
                                                Vector3dc posA, Vector3dc posB,
                                                Quaterniondc orientation)
            throws ReflectiveOperationException {
        return configurationClass(simpleName, legacyCategory)
                .getConstructor(Vector3dc.class, Vector3dc.class, Quaterniondc.class)
                .newInstance(posA, posB, orientation == null ? new Quaterniond() : orientation);
    }

    // Check whether one parameter accepts a body
    private static boolean acceptsBody(Class<?> parameterType, Object body) {
        return body == null || parameterType.isInstance(body);
    }
}
