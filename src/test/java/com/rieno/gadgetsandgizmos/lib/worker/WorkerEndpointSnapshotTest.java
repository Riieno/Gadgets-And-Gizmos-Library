package com.rieno.gadgetsandgizmos.lib.worker;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WorkerEndpointSnapshotTest{
    private static final WorkerResourceKey INGOT = new WorkerResourceKey(WorkerResourceType.ITEM,
            ResourceLocation.withDefaultNamespace("iron_ingot"));

    // Retain picker eligibility when endpoint snapshots cross the server/client boundary
    @Test void storageEligibilityRoundTrips(){
        var snapshot = new WorkerEndpointSnapshot(UUID.randomUUID(), null, new BlockPos(1, 2, 3),
                "Vault", "test:vault", true, true, List.of(), true);
        assertTrue(WorkerEndpointSnapshot.fromTag(snapshot.toTag()).storageSelectorEligible());
        assertFalse(new WorkerEndpointSnapshot(snapshot.id(), null, snapshot.position(), snapshot.label(),
                snapshot.blockId(), true, true, List.of()).storageSelectorEligible());
    }

    // Claim only newly produced stock and require real extraction from the output port
    @Test void outputLedgerRejectsEarlierOrUnremovedItems(){
        var ledger = new WorkerOutputLedger(Map.of(INGOT, 7L));
        var restored = WorkerOutputLedger.fromTag(ledger.toTag());
        assertEquals(0L, restored.produced(INGOT, 7L));
        assertEquals(7L, restored.produced(INGOT, 14L));
        var packet = new WorkerResourcePacket(INGOT, 7L, null);
        assertFalse(WorkerOutputLedger.confirmsExtraction(packet, 7L, 7L));
        assertTrue(WorkerOutputLedger.confirmsExtraction(packet, 14L, 7L));
    }

    // A damaged operation id cannot turn a processing recipe into virtual crafting
    @Test void unknownRecipeOperationIsPhysical(){
        assertEquals(WorkerRecipePlan.Operation.PROCESSING,
                WorkerRecipePlan.Operation.byId("missing_operation"));
    }
}
