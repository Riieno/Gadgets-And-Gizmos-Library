package com.rieno.gadgetsandgizmos.lib.scm;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

// Allow an integration to allocate physical forces with its native engine constraints
public final class ScmVectorAllocationRegistry{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Map<ResourceLocation, Allocator> ENTRIES = new LinkedHashMap<>();

    private ScmVectorAllocationRegistry(){}

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Register one optional allocator under its owning namespace
    public static synchronized void register(ResourceLocation id, Allocator allocator){
        if(ENTRIES.putIfAbsent(Objects.requireNonNull(id), Objects.requireNonNull(allocator)) != null){
            throw new IllegalStateException("Duplicate vector allocator: " + id);
        }
    }

    // Select the first allocator which supports the complete request
    public static Optional<Result> allocate(Request request){
        return allocateBatch(List.of(request)).map(res -> res.getFirst());
    }

    // Solve every carriage before publishing any control command
    public static Optional<List<Result>> allocateBatch(List<Request> requests){
        requests = List.copyOf(requests);
        if(requests.isEmpty()) return Optional.empty();
        List<Allocator> allocators;
        synchronized(ScmVectorAllocationRegistry.class){ allocators = List.copyOf(ENTRIES.values()); }
        for(Allocator allocator : allocators){
            if(!allocator.supportsBatch(requests)) continue;
            var results = new java.util.ArrayList<Result>(requests.size());
            for(Request request : requests){
                Result res = Objects.requireNonNull(allocator.allocate(request));
                if(res.forces().size() != request.units().size()){
                    throw new IllegalStateException("A vector allocation must retain every unit index");
                }
                results.add(res);
            }
            List<Result> res = List.copyOf(results);
            allocator.apply(requests, res);
            return Optional.of(res);
        }
        return Optional.empty();
    }

    public interface Allocator{
        boolean supports(Request request);
        Result allocate(Request request);
        default boolean supportsBatch(List<Request> requests){ return requests.stream().allMatch(this::supports); }
        default void apply(List<Request> requests, List<Result> results){}
    }

    public record Unit(ScmTarget target, @Nullable BlockEntity blockEntity, Vec3 momentArm,
                       Vec3 fullForce, Vec3 fullTorque){
        public Unit{
            Objects.requireNonNull(target);
            requireFinite(momentArm);
            requireFinite(fullForce);
            requireFinite(fullTorque);
        }
    }

    public record Request(@Nullable BlockEntity host, @Nullable java.util.UUID rootSubLevelId,
                          @Nullable Vec3 centerOfMass,
                          List<Unit> units, Vec3 force, Vec3 torque, boolean prioritizeTorque){
        public Request{
            units = List.copyOf(units);
            requireFinite(force);
            requireFinite(torque);
            if(centerOfMass != null) requireFinite(centerOfMass);
        }
        // Preserve balanced allocation for existing callers
        public Request(@Nullable BlockEntity host, @Nullable java.util.UUID rootSubLevelId,
                @Nullable Vec3 centerOfMass, List<Unit> units, Vec3 force, Vec3 torque){
            this(host, rootSubLevelId, centerOfMass, units, force, torque, false);
        }
        public Request(@Nullable BlockEntity host, @Nullable java.util.UUID rootSubLevelId,
                List<Unit> units, Vec3 force, Vec3 torque){ this(host, rootSubLevelId, null, units, force, torque); }
        public Request(List<Unit> units, Vec3 force, Vec3 torque){ this(null, null, null, units, force, torque); }
    }

    public record Result(List<Vec3> forces, double residual){
        public Result{
            forces = List.copyOf(forces);
            forces.forEach(ScmVectorAllocationRegistry::requireFinite);
            if(!Double.isFinite(residual) || residual < 0) throw new IllegalArgumentException("Invalid residual");
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static void requireFinite(Vec3 val){
        Objects.requireNonNull(val);
        if(!Double.isFinite(val.x) || !Double.isFinite(val.y) || !Double.isFinite(val.z)){
            throw new IllegalArgumentException("Non-finite allocation vector");
        }
    }
}
