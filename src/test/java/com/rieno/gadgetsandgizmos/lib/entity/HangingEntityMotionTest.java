package com.rieno.gadgetsandgizmos.lib.entity;

import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import dev.ryanhcode.sable.companion.math.Pose3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// Keep one stable hanging controller for static and moving grips
class HangingEntityMotionTest{
    @Test void suspendedBodiesConvergeWithoutAConstantPull(){
        Vec3 target = HangingEntityMotion.feetBelowGrip(new Vec3(4, 9, 3), 1.8D, 0.125D);
        Vec3 pos = new Vec3(6, 8, 2), velocity = Vec3.ZERO;
        double prev = pos.distanceTo(target);
        for(int tick = 0; tick < 120; tick++){
            velocity = HangingEntityMotion.velocity(velocity, target.subtract(pos));
            pos = pos.add(velocity);
            assertTrue(Double.isFinite(pos.lengthSqr()));
        }
        assertTrue(pos.distanceTo(target) < 1.0E-6D);
        assertTrue(velocity.length() < 1.0E-6D);
        assertTrue(pos.distanceTo(target) < prev);
    }

    @Test void theGripKeepsDifferentEntityHeightsBelowTheAnchor(){
        Vec3 grip = new Vec3(-4, 2, 6);
        for(double height : new double[]{0.25D, 1.4D, 1.8D, 2.9D}){
            Vec3 feet = HangingEntityMotion.feetBelowGrip(grip, height, 0.125D);
            assertEquals(grip.x, feet.x);
            assertEquals(grip.z, feet.z);
            assertEquals(grip.y - 0.125D, feet.y + height, 1.0E-9D);
        }
    }

    @Test void aMovingGripSettlesToItsTravelVelocity(){
        Vec3 pos = Vec3.ZERO, target = new Vec3(1, 2, 3), velocity = Vec3.ZERO;
        Vec3 travel = new Vec3(0.07D, 0.02D, -0.05D);
        for(int tick = 0; tick < 200; tick++){
            target = target.add(travel);
            velocity = HangingEntityMotion.velocity(velocity, target.subtract(pos));
            pos = pos.add(velocity);
        }
        assertTrue(velocity.distanceTo(travel) < 1.0E-8D);
        assertTrue(pos.distanceTo(target) < 0.2D);
    }

    @Test void rotatedAndTranslatedOwnersKeepEveryGripBelowTheirBounds(){
        BlockPos pos = new BlockPos(100000, 12, -200000);
        Pose3d pose = new Pose3d();
        pose.rotationPoint().set(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
        pose.position().set(20, 30, -40);
        pose.orientation().rotateXYZ(1.1D, 0.4D, -0.6D);
        for(Direction dir : Direction.values()){
            Vec3 local = pos.getCenter().add(dir.getStepX() * 0.5D, dir.getStepY() * 0.5D, dir.getStepZ() * 0.5D);
            Vec3 grip = HangingEntityMotion.blockGrip(pos, pose, local);
            Vec3 expected = pose.transformPosition(local);
            assertEquals(expected.x, grip.x, 1.0E-8D);
            assertEquals(expected.z, grip.z, 1.0E-8D);
            for(int x = 0; x <= 1; x++) for(int y = 0; y <= 1; y++) for(int z = 0; z <= 1; z++){
                Vec3 corner = pose.transformPosition(new Vec3(pos.getX() + x, pos.getY() + y, pos.getZ() + z));
                assertTrue(grip.y <= corner.y + 1.0E-8D);
            }
            Vec3 worldGrip = HangingEntityMotion.blockGrip(pos, null, local);
            assertEquals(pos.getY(), worldGrip.y, 1.0E-8D);
        }
    }
}
