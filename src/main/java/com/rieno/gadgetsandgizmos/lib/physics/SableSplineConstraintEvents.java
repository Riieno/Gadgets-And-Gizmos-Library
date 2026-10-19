package com.rieno.gadgetsandgizmos.lib.physics;

import dev.ryanhcode.sable.neoforge.event.ForgeSablePrePhysicsTickEvent;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

// Step only attached route constraints and release them with their owning level
final class SableSplineConstraintEvents{
    private static final Map<SubLevelPhysicsSystem, Set<SableSplineConstraint>> RETAINED =
            new IdentityHashMap<>();
    private static final Map<SableSplineConstraint, SubLevelPhysicsSystem> OWNERS =
            new IdentityHashMap<>();
    private static boolean bootstrapped;

    // Initialize the route constraint events
    private SableSplineConstraintEvents(){}

    // Register the native physics lifecycle before the first attachment is retained
    static synchronized void bootstrap(){
        if(bootstrapped) return;
        NeoForge.EVENT_BUS.addListener(
                ForgeSablePrePhysicsTickEvent.class,
                SableSplineConstraintEvents::prePhysicsTick);
        NeoForge.EVENT_BUS.addListener(
                LevelEvent.Unload.class,
                SableSplineConstraintEvents::levelUnloaded);
        NeoForge.EVENT_BUS.addListener(
                ServerStoppingEvent.class,
                SableSplineConstraintEvents::serverStopping);
        bootstrapped = true;
    }

    // Retain one attached constraint
    static synchronized void retain(SableSplineConstraint constraint, SubLevelPhysicsSystem system){
        SubLevelPhysicsSystem prev = OWNERS.put(constraint, system);
        if(prev == system) return;
        remove(constraint, prev);
        RETAINED.computeIfAbsent(system, ignored -> new HashSet<>()).add(constraint);
    }

    // Forget one released constraint
    static synchronized void release(SableSplineConstraint constraint){
        SubLevelPhysicsSystem system = OWNERS.remove(constraint);
        remove(constraint, system);
    }

    // Remove one retained constraint from an owner
    private static void remove(SableSplineConstraint constraint, SubLevelPhysicsSystem system){
        Set<SableSplineConstraint> constraints = RETAINED.get(system);
        if(constraints == null) return;
        constraints.remove(constraint);
        if(constraints.isEmpty()) RETAINED.remove(system);
    }

    // Retarget attached frames before the solver advances
    public static void prePhysicsTick(ForgeSablePrePhysicsTickEvent evt){
        for(SableSplineConstraint constraint : retained(evt.getPhysicsSystem())){
            constraint.step(evt.getTimeStep());
        }
    }

    // Release constraints before their level unloads
    public static void levelUnloaded(LevelEvent.Unload evt){
        if(!(evt.getLevel() instanceof ServerLevel level)) return;
        for(var entry : retained().entrySet()){
            if(entry.getKey().getLevel() != level) continue;
            for(SableSplineConstraint constraint : entry.getValue()) constraint.closeImmediately();
        }
    }

    // Release every retained solver handle before server shutdown
    public static void serverStopping(ServerStoppingEvent evt){
        for(var entry : retained().entrySet()){
            if(entry.getKey().getLevel().getServer() != evt.getServer()) continue;
            for(SableSplineConstraint constraint : entry.getValue()) constraint.closeImmediately();
        }
    }

    // Copy retained constraints for one physics callback
    private static synchronized Set<SableSplineConstraint> retained(SubLevelPhysicsSystem system){
        Set<SableSplineConstraint> constraints = RETAINED.get(system);
        return constraints == null ? Set.of() : Set.copyOf(constraints);
    }

    // Copy every retained constraint for lifecycle shutdown
    private static synchronized Map<SubLevelPhysicsSystem, Set<SableSplineConstraint>> retained(){
        Map<SubLevelPhysicsSystem, Set<SableSplineConstraint>> res = new IdentityHashMap<>();
        RETAINED.forEach((system, constraints) -> res.put(system, Set.copyOf(constraints)));
        return res;
    }
}
