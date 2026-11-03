package com.rieno.gadgetsandgizmos.lib.physics;

import com.rieno.gadgetsandgizmos.lib.geometry.CardinalRotation;
import dev.ryanhcode.sable.companion.math.Pose3d;
import net.minecraft.core.Direction;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// Keep wrench rotations consistent across all six directions and nested body frames
class SableAttachmentPoseTest{
    // Match every original head normal to every new bearing face
    @Test void cardinalRotationsMapAllAttachmentNormals(){
        for(Direction from : Direction.values()) for(Direction to : Direction.values()){
            var normal = new Vector3d(from.getStepX(), from.getStepY(), from.getStepZ());
            CardinalRotation.between(from, to).quaternion().transform(normal);
            assertTrue(normal.distance(new Vector3d(to.getStepX(), to.getStepY(), to.getStepZ())) < 1.0E-9,
                    from + " to " + to);
        }
    }

    // Preserve nonzero plot rotation points while aligning anchors inside a rotated parent
    @Test void attachmentAnchorsCoincideInTheWorld(){
        Pose3d parent = new Pose3d(), child = new Pose3d();
        parent.position().set(20, 30, 40);
        parent.rotationPoint().set(100, 5, 200);
        parent.orientation().rotateY(0.7D);
        child.position().set(-20, 3, 80);
        child.rotationPoint().set(500, 8, 700);
        var base = new Vector3d(101, 6, 202);
        var head = new Vector3d(503, 9, 702);
        var next = SableAttachmentPoseApi.alignedPose(parent, child, base, head,
                CardinalRotation.between(Direction.UP, Direction.EAST).quaternion());
        var expected = new Vector3d(base);
        parent.transformPosition(expected);
        var actual = new Vector3d(head);
        next.transformPosition(actual);
        assertTrue(expected.distance(actual) < 1.0E-8);
        assertEquals(new Vector3d(500, 8, 700), child.rotationPoint());
        assertEquals(new Vector3d(-20, 3, 80), child.position());
    }
}
