package com.rieno.gadgetsandgizmos.lib.scm;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.Set;

// Read optional physical control demands while the SCM retains actuator allocation and ownership
public final class ScmWrenchSourceRegistry{
    private static final Map<ResourceLocation, Source> SOURCES = new LinkedHashMap<>();

    private ScmWrenchSourceRegistry(){}

    public static synchronized void register(ResourceLocation id, Source source){
        Objects.requireNonNull(id);
        Objects.requireNonNull(source);
        if(SOURCES.putIfAbsent(id, source) != null) throw new IllegalStateException("Duplicate wrench source " + id);
    }

    // Combine fresh source snapshots expressed about the requested root-body center of mass
    public static Wrench sample(Request request){
        java.util.List<Source> sources;
        synchronized(ScmWrenchSourceRegistry.class){ sources = java.util.List.copyOf(SOURCES.values()); }
        Vec3 force = Vec3.ZERO;
        Vec3 torque = Vec3.ZERO;
        Vec3 compensation = Vec3.ZERO;
        boolean active = false;
        Set<String> rotationActions = new java.util.LinkedHashSet<>();
        Set<String> translationActions = new java.util.LinkedHashSet<>();
        for(Source source : sources){
            Wrench val = Objects.requireNonNull(source.sample(request));
            if(!val.active()) continue;
            active = true;
            force = force.add(val.force());
            torque = torque.add(val.torque());
            compensation = compensation.add(val.compensationForce());
            rotationActions.addAll(val.rotationActions());
            translationActions.addAll(val.translationActions());
        }
        return new Wrench(active, force, torque, compensation, rotationActions, translationActions);
    }

    @FunctionalInterface
    public interface Source{
        Wrench sample(Request request);
    }

    public record Request(BlockEntity host, UUID rootSubLevelId, Vec3 centerOfMass, long tick){
        public Request{
            Objects.requireNonNull(host);
            Objects.requireNonNull(rootSubLevelId);
            finite(centerOfMass);
        }
    }

    public record Wrench(boolean active, Vec3 force, Vec3 torque, Vec3 compensationForce,
                         Set<String> rotationActions, Set<String> translationActions){
        public static final Wrench NONE = new Wrench(false, Vec3.ZERO, Vec3.ZERO);

        public Wrench(boolean active, Vec3 force, Vec3 torque){ this(active, force, torque, Vec3.ZERO); }

        // Retain source compatibility when no explicit attitude axes are claimed
        public Wrench(boolean active, Vec3 force, Vec3 torque, Vec3 compensationForce){
            this(active, force, torque, compensationForce, Set.of());
        }

        // Retain source compatibility when no explicit translation axes are claimed
        public Wrench(boolean active, Vec3 force, Vec3 torque, Vec3 compensationForce, Set<String> rotationActions){
            this(active, force, torque, compensationForce, rotationActions, Set.of());
        }

        public Wrench{
            finite(force);
            finite(torque);
            finite(compensationForce);
            rotationActions = rotationActions == null ? Set.of() : Set.copyOf(rotationActions);
            translationActions = translationActions == null ? Set.of() : Set.copyOf(translationActions);
        }

        // Move the torque reference between centers in the same coordinate frame
        public Wrench aboutCenter(Vec3 sourceCenter, Vec3 targetCenter){
            finite(sourceCenter);
            finite(targetCenter);
            return new Wrench(active, force, torque.add(sourceCenter.subtract(targetCenter).cross(force)),
                    compensationForce, rotationActions, translationActions);
        }
    }

    private static void finite(Vec3 val){
        if(val == null || !Double.isFinite(val.x) || !Double.isFinite(val.y) || !Double.isFinite(val.z)){
            throw new IllegalArgumentException("Wrench vectors must be finite");
        }
    }
}
