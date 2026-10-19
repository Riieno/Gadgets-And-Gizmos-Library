package com.rieno.gadgetsandgizmos.lib.virtualkinetics;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;

// Identify the source slot retained by a driven kinetic block entity
public interface VirtualKineticSourceLink{
    // Get the connected virtual source slot, or -1 for an ordinary source
    int ct$getConnectedVirtualKineticSlot();

    // Match both the source position and its virtual slot
    static boolean isSourcedBy(KineticBlockEntity consumer, KineticBlockEntity source){
        if(!consumer.hasSource() || !consumer.source.equals(source.getBlockPos())) return false;
        int connectedSlot = consumer instanceof VirtualKineticSourceLink link
                ? link.ct$getConnectedVirtualKineticSlot()
                : consumer.source instanceof VirtualKineticPos pos ? pos.ct$getVirtualSlot() : -1;
        int sourceSlot = source instanceof VirtualKineticBlockEntity virtual ? virtual.ct$getVirtualKineticSlot() : -1;
        return connectedSlot == sourceSlot;
    }
}
