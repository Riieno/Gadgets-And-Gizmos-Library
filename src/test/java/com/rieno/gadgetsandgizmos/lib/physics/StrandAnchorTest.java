package com.rieno.gadgetsandgizmos.lib.physics;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

// Check saved anchors through missing, queued, restored, and broken parent strands
class StrandAnchorTest{
    // Retain strand identity and both owner coordinate spaces across a restart
    @Test void savedAnchorSurvivesReload(){
        StrandAnchor anchor = new StrandAnchor(UUID.randomUUID(), 3.25f,
                new BlockPos(-18, 72, 31), UUID.randomUUID());
        CompoundTag tag = new CompoundTag();
        anchor.write(tag);
        assertEquals(anchor, StrandAnchor.read(tag));
    }

    // Wait for an unloaded owner and its queued strand before confirming removal
    @Test void reloadOrderDoesNotBreakTheAnchor(){
        UUID strandId = UUID.randomUUID();
        StrandAnchor anchor = new StrandAnchor(strandId, 2.0f, BlockPos.ZERO, null);
        assertFalse(anchor.isBroken(false, null));
        assertFalse(anchor.isBroken(true, strandId));
        assertEquals(new Vec3(2.0D, 0.0D, 0.0D),
                anchor.sample(List.of(new Vector3d(), new Vector3d(5.0D, 0.0D, 0.0D))));
        assertTrue(anchor.isBroken(true, null));
        assertTrue(anchor.isBroken(true, UUID.randomUUID()));
    }

    // Read the existing save format and discover its owner after restoration
    @Test void legacyAnchorsWaitUntilTheirOwnerIsKnown(){
        UUID strandId = UUID.randomUUID();
        CompoundTag tag = new CompoundTag();
        tag.putUUID("AnchorRopeUUID", strandId);
        tag.putFloat("AnchorPathPosition", 1.5f);
        StrandAnchor anchor = StrandAnchor.read(tag);
        assertNotNull(anchor);
        assertFalse(anchor.isBroken(true, null));
        StrandAnchor discovered = anchor.withOwner(new BlockPos(20, 80, -3), null);
        discovered.write(tag);
        assertEquals(discovered, StrandAnchor.read(tag));
        assertTrue(discovered.isBroken(true, null));
    }

    // Sample both ends of a bridge independently as its parent ropes move
    @Test void movingStrandsRetainTheirOwnAttachmentPositions(){
        StrandAnchor first = new StrandAnchor(UUID.randomUUID(), 2.0f, BlockPos.ZERO, null);
        StrandAnchor second = new StrandAnchor(UUID.randomUUID(), 1.0f, new BlockPos(5, 0, 0), null);
        assertEquals(new Vec3(2.0D, 0.0D, 0.0D),
                first.sample(List.of(new Vector3d(), new Vector3d(4.0D, 0.0D, 0.0D))));
        assertEquals(new Vec3(5.0D, 1.0D, 0.0D),
                second.sample(List.of(new Vector3d(5.0D, 0.0D, 0.0D), new Vector3d(5.0D, 4.0D, 0.0D))));
        assertEquals(new Vec3(2.0D, 3.0D, 0.0D),
                first.sample(List.of(new Vector3d(0.0D, 3.0D, 0.0D), new Vector3d(4.0D, 3.0D, 0.0D))));
    }

    // Skip collapsed points and clamp arc distances at the strand ends
    @Test void collapsedSegmentsAndEndpointDistancesRemainUsable(){
        List<Vector3d> points = List.of(new Vector3d(), new Vector3d(), new Vector3d(4.0D, 0.0D, 0.0D));
        assertEquals(new Vec3(2.0D, 0.0D, 0.0D), StrandAnchor.samplePath(points, 2.0f));
        assertEquals(Vec3.ZERO, StrandAnchor.samplePath(points, -1.0f));
        assertEquals(new Vec3(4.0D, 0.0D, 0.0D), StrandAnchor.samplePath(points, 20.0f));
        assertNull(StrandAnchor.samplePath(List.of(), 1.0f));
        assertNull(StrandAnchor.samplePath(points, Float.NaN));
    }

    // Copy mutable block positions and reject invalid saved path values
    @Test void savedOwnersAreImmutableAndInvalidPositionsAreRejected(){
        BlockPos.MutableBlockPos owner = new BlockPos.MutableBlockPos(1, 2, 3);
        StrandAnchor anchor = new StrandAnchor(UUID.randomUUID(), 1.0f, owner, null);
        owner.set(4, 5, 6);
        assertEquals(new BlockPos(1, 2, 3), anchor.ownerPos());
        CompoundTag tag = new CompoundTag();
        anchor.write(tag);
        tag.putFloat("AnchorPathPosition", Float.NaN);
        assertNull(StrandAnchor.read(tag));
        assertThrows(IllegalArgumentException.class,
                () -> new StrandAnchor(UUID.randomUUID(), Float.POSITIVE_INFINITY, null, null));
    }
}
