package com.rieno.gadgetsandgizmos.lib.scm;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

// Verify complete batch validation before any native control command is published
class ScmVectorAllocationRegistryTest{
    @Test
    void carriageCommandsPublishOnlyAfterAllIndicesAreValidated(){
        var target = new ScmTarget(null, BlockPos.ZERO, "allocation_test:engine", "");
        var unit = new ScmVectorAllocationRegistry.Unit(target, null, Vec3.ZERO, new Vec3(0, 100, 0), Vec3.ZERO);
        var first = new ScmVectorAllocationRegistry.Request(List.of(unit), new Vec3(0, 20, 0), Vec3.ZERO);
        var second = new ScmVectorAllocationRegistry.Request(List.of(unit), new Vec3(0, 40, 0), Vec3.ZERO);
        var solved = new AtomicInteger();
        var applied = new AtomicInteger();
        var invalid = new java.util.concurrent.atomic.AtomicBoolean();
        ScmVectorAllocationRegistry.register(ResourceLocation.fromNamespaceAndPath("allocation_test", "native"),
                new ScmVectorAllocationRegistry.Allocator(){
                    public boolean supports(ScmVectorAllocationRegistry.Request request){
                        return request.units().getFirst().target().blockId().startsWith("allocation_test:");
                    }
                    public ScmVectorAllocationRegistry.Result allocate(ScmVectorAllocationRegistry.Request request){
                        solved.incrementAndGet();
                        return new ScmVectorAllocationRegistry.Result(invalid.get() && request == second
                                ? List.of() : List.of(request.force()), 0);
                    }
                    public void apply(List<ScmVectorAllocationRegistry.Request> requests, List<ScmVectorAllocationRegistry.Result> results){
                        assertEquals(2, solved.get());
                        assertEquals(first.force(), results.getFirst().forces().getFirst());
                        assertEquals(second.force(), results.getLast().forces().getFirst());
                        assertThrows(UnsupportedOperationException.class, () -> results.clear());
                        applied.incrementAndGet();
                    }
                });
        assertEquals(2, ScmVectorAllocationRegistry.allocateBatch(List.of(first, second)).orElseThrow().size());
        assertEquals(1, applied.get());
        solved.set(0);
        invalid.set(true);
        assertThrows(IllegalStateException.class, () -> ScmVectorAllocationRegistry.allocateBatch(List.of(first, second)));
        assertEquals(1, applied.get());
        assertTrue(ScmVectorAllocationRegistry.allocateBatch(List.of()).isEmpty());
    }

    @Test
    void nonFiniteControlInputsCannotReachAnAllocator(){
        var invalid = new Vec3(Double.NaN, 0, 0);
        var target = new ScmTarget(null, BlockPos.ZERO, "allocation_test:engine", "");
        assertThrows(IllegalArgumentException.class, () -> new ScmVectorAllocationRegistry.Unit(target, null, invalid, Vec3.ZERO, Vec3.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new ScmVectorAllocationRegistry.Request(List.of(), invalid, Vec3.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new ScmVectorAllocationRegistry.Result(List.of(invalid), 0));
        assertThrows(IllegalArgumentException.class, () -> new ScmVectorAllocationRegistry.Result(List.of(), -1));
    }
}
