package com.rieno.gadgetsandgizmos.lib.physics;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import dev.ryanhcode.sable.neoforge.event.ForgeSablePostPhysicsTickEvent;
import dev.ryanhcode.sable.neoforge.event.ForgeSablePrePhysicsTickEvent;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Correct retained fixed joints before their poses reach other physics consumers
@EventBusSubscriber(modid = "gadgetsngizmos")
public final class SableRigidConstraintEvents{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Map<SubLevelPhysicsSystem, Set<SableRigidConstraint>> RETAINED = new IdentityHashMap<>();

    private SableRigidConstraintEvents(){}

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Keep controlled attachments acyclic with one parent per moving body
    static boolean canAttach(SubLevelPhysicsSystem system, @Nullable ServerSubLevel parent, ServerSubLevel child){
        List<SableRigidConstraint> joints = retained(system);
        if(joints.stream().anyMatch(joint -> joint.child() == child)) return false;
        Set<ServerSubLevel> visited = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        while(parent != null && visited.add(parent)){
            if(parent == child) return false;
            ServerSubLevel current = parent;
            parent = joints.stream().filter(joint -> joint.child() == current).findFirst().map(SableRigidConstraint::parent).orElse(null);
        }
        return parent == null;
    }

    // Retain an attachment in its owning physics system
    static synchronized void retain(SableRigidConstraint joint){
        RETAINED.computeIfAbsent(joint.system, ignored -> new LinkedHashSet<>()).add(joint);
    }

    // Forget a released attachment
    static synchronized void release(SableRigidConstraint joint){
        Set<SableRigidConstraint> joints = RETAINED.get(joint.system);
        if(joints == null) return;
        joints.remove(joint);
        if(joints.isEmpty()) RETAINED.remove(joint.system);
    }

    // Apply final controlled frames after block actors have updated their targets
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void prePhysicsTick(ForgeSablePrePhysicsTickEvent evt){
        if(!Double.isFinite(evt.getTimeStep()) || evt.getTimeStep() <= 0.0D) return;
        for(SableRigidConstraint joint : retained(evt.getPhysicsSystem())){
            if(!joint.isValid()){
                joint.remove();
                continue;
            }
            try{
                joint.prepare(evt.getTimeStep());
            }catch(ReflectiveOperationException error){
                joint.remove();
                throw new IllegalStateException("Failed to update a rigid fixed constraint", error);
            }
        }
    }

    // Remove native spring error from each complete parent and child attachment tree
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void postPhysicsTick(ForgeSablePostPhysicsTickEvent evt){
        List<SableRigidConstraint> joints = retained(evt.getPhysicsSystem());
        joints.removeIf(joint -> {
            if(joint.isValid()) return false;
            joint.remove();
            return true;
        });
        SableRigidConstraintProjection.project(evt.getPhysicsSystem(), joints);
    }

    // Release attachments before their level and native scene unload
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void levelUnloaded(LevelEvent.Unload evt){
        if(!(evt.getLevel() instanceof ServerLevel level)) return;
        for(SableRigidConstraint joint : retained()){
            if(joint.system.getLevel() == level) joint.remove();
        }
    }

    // Release attachments before the native backend shuts down
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void serverStopping(ServerStoppingEvent evt){
        for(SableRigidConstraint joint : retained()){
            if(joint.system.getLevel().getServer() == evt.getServer()) joint.remove();
        }
    }

    // Copy a system's attachments before callbacks can release them
    private static synchronized List<SableRigidConstraint> retained(SubLevelPhysicsSystem system){
        Set<SableRigidConstraint> joints = RETAINED.get(system);
        return joints == null ? new ArrayList<>() : new ArrayList<>(joints);
    }

    // Copy every attachment for shutdown
    private static synchronized List<SableRigidConstraint> retained(){
        List<SableRigidConstraint> res = new ArrayList<>();
        RETAINED.values().forEach(res::addAll);
        return res;
    }
}
