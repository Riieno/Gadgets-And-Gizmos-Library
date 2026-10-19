package com.rieno.gadgetsandgizmos.lib.physics;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintHandle;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.RotaryConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.RotaryConstraintHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.util.Set;

// Bridge current and legacy Sable constraint packages in one library surface
public final class SableConstraintApi {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final String CONSTRAINT_PACKAGE = "dev.ryanhcode.sable.api.physics.constraint.";
    private static final String RAPIER_PACKAGE = "dev.ryanhcode.sable.physics.impl.rapier.";
    private static final ClassValue<FrameAccess> FIXED_FRAMES = new ClassValue<>(){
        @Override
        protected FrameAccess computeValue(Class<?> type){
            try{
                Class<?> base = type;
                while(base != null && !base.getName().equals(RAPIER_PACKAGE + "constraint.RapierConstraintHandle")){
                    base = base.getSuperclass();
                }
                if(base == null) throw new NoSuchMethodException("Unsupported constraint frame API: " + type.getName());
                Field scene;
                try{
                    scene = base.getDeclaredField("sceneHandle");
                }catch(NoSuchFieldException legacy){
                    scene = base.getDeclaredField("sceneID");
                }
                Field joint = base.getDeclaredField("handle");
                scene.setAccessible(true);
                joint.setAccessible(true);
                Class<?> rapier = Class.forName(RAPIER_PACKAGE + "Rapier3D", false, type.getClassLoader());
                Method setter = rapier.getMethod("setConstraintFrame", scene.getType(), long.class, int.class,
                        double.class, double.class, double.class, double.class, double.class, double.class, double.class);
                return new FrameAccess(scene, joint, setter, null);
            }catch(ReflectiveOperationException error){
                return new FrameAccess(null, null, null, error);
            }
        }
    };

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

    // Lock every axis through Sable's solver while keeping both attachment frames adjustable
    public static @Nullable GenericConstraintHandle fixedFrameConstraint(PhysicsPipeline pipeline,
            @Nullable PhysicsPipelineBody bodyA, @Nullable PhysicsPipelineBody bodyB,
            Vector3dc posA, Vector3dc posB, Quaterniondc orientation){
        if(bodyA == bodyB) return null;
        GenericConstraintHandle handle = pipeline.addConstraint(bodyA, bodyB,
                new GenericConstraintConfiguration(new Vector3d(posA), new Vector3d(posB),
                        new Quaterniond(orientation).normalize(), new Quaterniond(), Set.of(ConstraintJointAxis.ALL)));
        if(handle != null) handle.setContactsEnabled(false);
        return handle;
    }

    // Attach a solver-driven hinge using anchors and axes in each body's local frame
    public static @Nullable RotaryConstraintHandle rotaryConstraint(PhysicsPipeline pipeline,
            @Nullable PhysicsPipelineBody bodyA, @Nullable PhysicsPipelineBody bodyB,
            Vector3dc posA, Vector3dc posB, Vector3dc axisA, Vector3dc axisB){
        if(bodyA == bodyB) return null;
        RotaryConstraintHandle handle = pipeline.addConstraint(bodyA, bodyB,
                new RotaryConstraintConfiguration(new Vector3d(posA), new Vector3d(posB),
                        new Vector3d(axisA).normalize(), new Vector3d(axisB).normalize()));
        if(handle != null) handle.setContactsEnabled(false);
        return handle;
    }

    // Scale a rotary servo by the bodies' local inertia without moving either body's pose
    public static void setRotaryServo(RotaryConstraintHandle handle,
            @Nullable ServerSubLevel bodyA, @Nullable ServerSubLevel bodyB,
            Vector3dc axisA, Vector3dc axisB, double targetRadians, double stiffness, double damping){
        Vector3d temp = new Vector3d();
        double inertiaA = bodyA == null ? Double.MAX_VALUE
                : bodyA.getMassTracker().getInertiaTensor().transform(axisA, temp).dot(axisA);
        double inertiaB = bodyB == null ? Double.MAX_VALUE
                : bodyB.getMassTracker().getInertiaTensor().transform(axisB, temp).dot(axisB);
        double inertia = Math.max(10.0D, bodyA != null && bodyB != null
                ? Math.max(inertiaA, inertiaB) : Math.min(inertiaA, inertiaB));
        handle.setMotor(RotaryConstraintHandle.DEFAULT_AXIS, targetRadians,
                stiffness * inertia, damping * inertia, false, 0.0D);
        handle.setContactsEnabled(false);
    }

    // Create a native fixed attachment with exact locked frames after every physics substep
    public static @Nullable SableRigidConstraint rigidFixedConstraint(ServerLevel level,
            @Nullable ServerSubLevel bodyA, @Nullable ServerSubLevel bodyB,
            Vector3dc posA, Vector3dc posB, Quaterniondc orientation) throws ReflectiveOperationException{
        return SableRigidConstraint.attach(level, bodyA, bodyB, posA, posB, orientation);
    }

    // Rotate a mounted frame around the base frame's local Y axis
    public static Quaterniond rotaryOrientation(Quaterniondc baseFrame, Quaterniondc mountedFrame, double angleRadians){
        return new Quaterniond(baseFrame).rotateY(angleRadians)
                .mul(new Quaterniond(mountedFrame).conjugate()).normalize();
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

    // Create one compatible rotary constraint configuration
    public static Object rotaryConfiguration(Vector3dc posA, Vector3dc posB,
                                             Vector3dc axisA, Vector3dc axisB)
            throws ReflectiveOperationException {
        return configurationClass("RotaryConstraintConfiguration", "rotary")
                .getConstructor(Vector3dc.class, Vector3dc.class, Vector3dc.class, Vector3dc.class)
                .newInstance(posA, posB, axisA, axisB);
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
        if(handle instanceof SableRigidConstraint rigid){
            rigid.setFrame(frame, pos, orientation);
            return;
        }
        if(handle instanceof GenericConstraintHandle generic){
            if(frame == 1) generic.setFrame1(pos, orientation);
            else generic.setFrame2(pos, orientation);
            return;
        }
        try{
            handle.getClass().getMethod("setFrame" + frame, Vector3dc.class, Quaterniondc.class)
                    .invoke(handle, pos, orientation);
        }catch(NoSuchMethodException missing){
            if(!handle.isValid()) throw new IllegalStateException("Constraint is unavailable");
            FrameAccess access = FIXED_FRAMES.get(handle.getClass());
            if(access.error != null) throw access.error;
            access.setter.invoke(null, access.scene.get(handle), access.joint.getLong(handle), frame - 1,
                    pos.x(), pos.y(), pos.z(), orientation.x(), orientation.y(), orientation.z(), orientation.w());
        }
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

    // Cache the native frame setter used by Rapier fixed constraints
    private record FrameAccess(Field scene, Field joint, Method setter, ReflectiveOperationException error){}
}
