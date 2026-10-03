package com.rieno.gadgetsandgizmos.lib.worker;

import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.Comparator;

// Bound requested deliveries and choose storage for their surplus
public final class WorkerDelivery{
    private WorkerDelivery(){
    }

    // Offer only the still-requested quantity while retaining the packet's components
    public static WorkerResourcePacket requested(WorkerResourcePacket cargo, long remaining){
        return new WorkerResourcePacket(cargo.resource(), Math.min(cargo.amount(), Math.max(0L, remaining)), cargo.payload());
    }

    // Prefer matching stocked or filtered storage, then the nearest open destination
    public static WorkerEndpoint storage(Collection<? extends WorkerEndpoint> endpoints,
                                          WorkerResourcePacket cargo, Vec3 origin){
        if(cargo == null || cargo.isEmpty()) return null;
        return endpoints.stream().filter(endpoint -> endpoint.isAvailable() && endpoint.acceptsDelivery()
                        && endpoint.canInsert(cargo.resource()) && endpoint.insert(cargo, true) > 0L)
                .min(Comparator.comparingInt((WorkerEndpoint endpoint) -> endpoint.insertionPriority(cargo.resource()))
                        .thenComparingDouble(endpoint -> origin.distanceToSqr(Vec3.atCenterOf(endpoint.position()))))
                .orElse(null);
    }
}
