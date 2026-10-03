package com.rieno.gadgetsandgizmos.lib.scm;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScmArticulatedIkTest {
    @Test
    void crossesTheNativeHipSeamWithoutTakingTheLongRoute(){
        for(double sign : new double[]{1, -1}){
            double hip = -0.6D;
            double knee = 1.2D;
            double nativeHip = sign * (Math.PI - 0.02D);
            Vec3 axis = new Vec3(sign, 0, 0);
            ScmArticulatedIk.Joint mountedHip = new ScmArticulatedIk.Joint(Vec3.ZERO, axis,
                    nativeHip, nativeHip - Math.PI, nativeHip + Math.PI, nativeHip);
            mountedHip = ScmArticulatedIk.restrictSwing(mountedHip, segment(hip),
                    new Vec3(0, -1, 0), Math.toRadians(80));
            List<ScmArticulatedIk.Joint> joints = List.of(mountedHip,
                    new ScmArticulatedIk.Joint(segment(hip), axis, sign * knee,
                            -Math.PI, Math.PI, sign * knee));
            ScmArticulatedIk.Solution res = ScmArticulatedIk.solve(foot(hip, knee),
                    foot(hip + 0.06D, knee), joints);
            assertEquals(0.06D, sign * (res.angles().getFirst() - nativeHip), 0.001D);
            assertTrue(res.residual().length() < 0.001D);
        }
    }

    @Test
    void keepsTheThighBelowTheHipWhenTheFootTargetIsAboveTheBody(){
        for(double sign : new double[]{1, -1}){
            ScmArticulatedIk.Joint hip = ScmArticulatedIk.restrictSwing(
                    new ScmArticulatedIk.Joint(Vec3.ZERO, new Vec3(sign, 0, 0),
                            0.7D, -4, 4, 0.7D), new Vec3(0, -1.5D, 0),
                    new Vec3(0, -1, 0), Math.toRadians(80));
            List<ScmArticulatedIk.Joint> joints = List.of(hip,
                    new ScmArticulatedIk.Joint(new Vec3(0, -1.5D, 0), new Vec3(sign, 0, 0),
                            0, -2.8D, 2.8D, sign * 1.2D));
            ScmArticulatedIk.Solution res = ScmArticulatedIk.solve(new Vec3(0, -3, 0),
                    new Vec3(0, 2, 0.1D), joints);
            double delta = res.angles().getFirst() - hip.angle();
            assertTrue(Math.abs(delta) <= Math.toRadians(80) + 1.0E-8D);
            assertTrue(segment(sign * delta).y < -0.25D);
            assertTrue(res.residual().length() > 1.0D, "An unsafe target must remain unreachable");
        }
    }

    @Test
    void walksWithTerrainBalanceNativeFeedbackAndChangingRootOrientation(){
        for(double direction : new double[]{1, -1}){
            ScmLeggedLocomotion.GaitState gait = new ScmLeggedLocomotion.GaitState();
            List<ScmLeggedLocomotion.Limb> limbs = new ArrayList<>();
            for(int idx = 0; idx < 2; idx++){
                limbs.add(new ScmLeggedLocomotion.Limb("leg_" + idx, ScmLeggedLocomotion.LimbKind.LEG,
                        new Vec3(idx == 0 ? -0.6D : 0.6D, 0, 0), new Vec3(0, -4.92D, 0),
                        0, 3, 3, idx * 0.5D, 0, 0, 0));
            }
            double[] hips = {-1.7D, 0};
            double[] knees = {0, 0};
            double[] minKnee = {10, 10};
            double[] maxKnee = {0, 0};
            double prevOrder = 0;
            int swaps = 0;
            for(int tick = 0; tick < 360; tick++){
                Quaterniond orientation = new Quaterniond().rotationXYZ(
                        0.3D * Math.sin(tick * 0.03D), 0.5D, 0.4D * Math.cos(tick * 0.02D));
                ScmLocomotionFrame frame = ScmLocomotionFrame.fromUpAndForward(
                        toRoot(new Vec3(0, 1, 0), orientation), toRoot(new Vec3(0, 0, 1), orientation));
                double phase = gait.advancePhase(1, 0.05D, 0.7D);
                ScmLeggedLocomotion.Input input = new ScmLeggedLocomotion.Input(Vec3.ZERO,
                        new Vec3(0, 0, direction), 0, 0, phase, 0.35D, 1.25D, 0.45D, 0.35D, 0.85D);
                // Settle the chassis from full reach onto bent support legs.
                double ground = -6.0D + Math.min(tick / 60.0D, 1.0D) * 1.08D;
                List<ScmLeggedLocomotion.Contact> contacts = new ArrayList<>();
                for(int idx = 0; idx < 2; idx++){
                    Vec3 measured = foot(hips[idx], knees[idx]).scale(2);
                    Vec3 terrain = new Vec3(measured.x, ground, measured.z);
                    contacts.add(new ScmLeggedLocomotion.Contact("leg_" + idx,
                            Math.abs(measured.y - ground) <= 0.125D,
                            limbs.get(idx).hipPosition().add(terrain), terrain, true));
                }
                ScmLeggedLocomotion.Plan preview = ScmLeggedLocomotion.solve(input, limbs, contacts);
                List<ScmLeggedLocomotion.Contact> planned = new ArrayList<>();
                for(int idx = 0; idx < 2; idx++){
                    Vec3 requested = preview.targets().get("leg_" + idx).footPosition();
                    ScmLeggedLocomotion.Contact measured = contacts.get(idx);
                    planned.add(new ScmLeggedLocomotion.Contact(measured.limbId(), measured.grounded(),
                            measured.position(), new Vec3(requested.x, ground, requested.z), true));
                }
                ScmLeggedLocomotion.Plan plan = ScmLeggedLocomotion.solve(input, limbs, planned,
                        new ScmLeggedLocomotion.BodyMotion(new Vec3(0, 0, direction * 0.25D), 0.05D), gait);
                int swinging = 0;
                for(int idx = 0; idx < 2; idx++){
                    ScmLeggedLocomotion.LimbTarget target = plan.targets().get("leg_" + idx);
                    if(!target.stance()) swinging++;
                    double sign = idx == 0 ? 1 : -1;
                    Vec3 end = toRoot(foot(hips[idx], knees[idx]).scale(2), orientation);
                    Vec3 knee = toRoot(segment(hips[idx]).scale(2), orientation);
                    Vec3 axis = toRoot(new Vec3(sign, 0, 0), orientation);
                    double nativeHip = sign * hips[idx] + 0.2D;
                    double nativeKnee = sign * knees[idx] - 0.3D;
                    double preferred = ScmArticulatedIk.preferredBendAngle(Vec3.ZERO, knee, end, axis,
                            frame.up(), frame.forward(), nativeKnee, target.angles().knee());
                    ScmArticulatedIk.Joint hipJoint = ScmArticulatedIk.restrictSwing(
                            new ScmArticulatedIk.Joint(Vec3.ZERO, axis, nativeHip,
                                    nativeHip - Math.PI, nativeHip + Math.PI, nativeHip),
                            knee, frame.up().scale(-1), Math.toRadians(80));
                    List<ScmArticulatedIk.Joint> joints = List.of(
                            hipJoint,
                            new ScmArticulatedIk.Joint(knee, axis, nativeKnee, -Math.PI, Math.PI, preferred));
                    ScmArticulatedIk.Solution res = ScmArticulatedIk.solve(end,
                            frame.toBody(target.footPosition()), joints);
                    hips[idx] += sign * Mth.clamp(res.angles().get(0) - nativeHip, -0.35D, 0.35D) * 0.8D;
                    knees[idx] += sign * Mth.clamp(res.angles().get(1) - nativeKnee, -0.35D, 0.35D) * 0.8D;
                    if(tick > 100){
                        minKnee[idx] = Math.min(minKnee[idx], knees[idx]);
                        maxKnee[idx] = Math.max(maxKnee[idx], knees[idx]);
                        Vec3 measured = foot(hips[idx], knees[idx]).scale(2);
                        assertEquals(target.footPosition().y, measured.y, 0.18D);
                        assertEquals(target.footPosition().z, measured.z, 0.18D);
                    }
                }
                assertTrue(swinging <= 1);
                double order = foot(hips[0], knees[0]).z - foot(hips[1], knees[1]).z;
                if(tick > 100 && order * prevOrder < 0) swaps++;
                prevOrder = order;
            }
            assertTrue(swaps >= 16, "Both feet must exchange front and rear positions throughout the run");
            for(int idx = 0; idx < 2; idx++){
                assertTrue(minKnee[idx] > 0.7D, "The knee must stay on its bent branch");
                assertTrue(maxKnee[idx] - minKnee[idx] > 0.2D, "Each knee must flex on successive swings");
            }
        }
    }

    private static Vec3 toRoot(Vec3 vector, Quaterniond orientation){
        Vector3d res = orientation.transformInverse(new Vector3d(vector.x, vector.y, vector.z));
        return new Vec3(res.x, res.y, res.z);
    }

    @Test
    void keepsTheKneeBendBranchWhenTheShinPassesHorizontal(){
        for(double hip : new double[]{-1.4D, -1.7D}){
            double preferred = ScmArticulatedIk.preferredBendAngle(Vec3.ZERO,
                    segment(hip), foot(hip, 0), new Vec3(1, 0, 0),
                    new Vec3(0, 0, 1), 0, 1.2D);
            assertEquals(1.2D, preferred, 1.0E-8D, "A moving shin must not reverse the bend branch");
        }
    }

    @Test
    void mapsBendPreferencesToTheMountedMotorAxis(){
        Vec3 knee = new Vec3(0, -1.5D, 0);
        Vec3 end = new Vec3(0, -3, 0);
        assertEquals(1.7D, ScmArticulatedIk.preferredBendAngle(Vec3.ZERO, knee, end,
                new Vec3(1, 0, 0), new Vec3(0, 0, 1), 0.2D, 1.5D), 1.0E-8D);
        assertEquals(-1.3D, ScmArticulatedIk.preferredBendAngle(Vec3.ZERO, knee, end,
                new Vec3(-1, 0, 0), new Vec3(0, 0, 1), 0.2D, 1.5D), 1.0E-8D);
    }

    @Test
    void solvesLateralAndForwardTargetsWithAdditionalRotaryJoints(){
        Vec3 pitch = new Vec3(1, 0, 0);
        List<ScmArticulatedIk.Joint> joints = List.of(
                new ScmArticulatedIk.Joint(Vec3.ZERO, new Vec3(0, 0, 1), 0, -2, 2, 0),
                new ScmArticulatedIk.Joint(Vec3.ZERO, pitch, 0, -2, 2, -0.5D),
                new ScmArticulatedIk.Joint(new Vec3(0, -1.5D, 0), pitch, 0, -2, 2, 1.5D),
                new ScmArticulatedIk.Joint(new Vec3(0, -3, 0), pitch, 0, -2, 2, -0.5D));
        Vec3 end = new Vec3(0, -3.5D, 0);
        Vec3 target = new Vec3(0.5D, -2.7D, 0.5D);
        ScmArticulatedIk.Solution res = ScmArticulatedIk.solve(end, target, joints);
        assertEquals(4, res.angles().size());
        assertTrue(res.angles().get(2) > 0.6D, res::toString);
        assertTrue(res.residual().length() < 0.001D);
        assertTrue(ScmArticulatedIk.forward(end, joints, res.angles()).distanceTo(target) < 0.001D);
    }

    @Test
    void bendsAStraightKneeToShortenTheLeg(){
        List<ScmArticulatedIk.Joint> joints = chain(0.0D, 0.0D, 1.0D);
        Vec3 foot = foot(0.0D, 0.0D);
        Vec3 target = new Vec3(0.0D, -2.3D, 0.0D);
        ScmArticulatedIk.Solution res = ScmArticulatedIk.solve(foot, target, joints);
        assertTrue(res.angles().get(1) > 1.0D, "The knee must bend, not remain at zero");
        assertTrue(res.residual().length() < 0.001D);
        assertTrue(foot(res.angles().get(0), res.angles().get(1)).distanceTo(target) < 0.001D);
    }

    @Test
    void honoursMirroredMotorAxesAndNativeOffsets(){
        Vec3 end = foot(0.0D, 0.0D);
        Vec3 target = new Vec3(0.0D, -2.2D, 0.5D);
        List<ScmArticulatedIk.Joint> joints = List.of(
                new ScmArticulatedIk.Joint(Vec3.ZERO, new Vec3(-1, 0, 0), 0.7D, -3, 3, 0.7D),
                new ScmArticulatedIk.Joint(new Vec3(0, -1.5D, 0), new Vec3(-1, 0, 0),
                        -0.3D, -3, 3, -1.7D));
        ScmArticulatedIk.Solution res = ScmArticulatedIk.solve(end, target, joints);
        assertTrue(res.residual().length() < 0.001D);
        assertTrue(res.angles().get(1) < -1.0D);
        assertTrue(foot(-(res.angles().get(0) - 0.7D), -(res.angles().get(1) + 0.3D))
                .distanceTo(target) < 0.001D);
    }

    @Test
    void returnsBoundedTargetsAndAnHonestResidualForUnreachableFeet(){
        List<ScmArticulatedIk.Joint> joints = List.of(
                new ScmArticulatedIk.Joint(Vec3.ZERO, new Vec3(1, 0, 0), 0, -0.1D, 0.1D, 0),
                new ScmArticulatedIk.Joint(new Vec3(0, -1.5D, 0), new Vec3(1, 0, 0),
                        0, 0, 0.3D, 0.3D));
        Vec3 target = new Vec3(0, -1, 2);
        ScmArticulatedIk.Solution res = ScmArticulatedIk.solve(new Vec3(0, -3, 0), target, joints);
        assertTrue(Math.abs(res.angles().get(0)) <= 0.1D);
        assertTrue(res.angles().get(1) >= 0 && res.angles().get(1) <= 0.3D);
        assertEquals(target.distanceTo(foot(res.angles().get(0), res.angles().get(1))),
                res.residual().length(), 1.0E-8D);
    }

    @Test
    void walksSeveralCyclesWithMeasuredJointFeedbackAndAlternatingKnees(){
        for(double direction : new double[]{1.0D, -1.0D}){
            ScmLeggedLocomotion.GaitState gait = new ScmLeggedLocomotion.GaitState();
            List<ScmLeggedLocomotion.Limb> limbs = new ArrayList<>();
            for(int idx = 0; idx < 2; idx++){
                limbs.add(gait.referenceLimb(new ScmLeggedLocomotion.Limb("leg_" + idx,
                        ScmLeggedLocomotion.LimbKind.LEG, Vec3.ZERO, new Vec3(0, -2.46D, 0),
                        0, 1.5D, 1.5D, idx * 0.5D, 0, 0, 0)));
            }
            double[] hips = {-Math.PI * 0.5D, 0.0D};
            double[] knees = {0.0D, 0.0D};
            double[] minKnee = {10, 10};
            double[] maxKnee = {0, 0};
            int swaps = 0;
            double prevOrder = 0;
            for(int tick = 0; tick < 300; tick++){
                double phase = gait.advancePhase(1, 0.05D, 0.7D);
                ScmLeggedLocomotion.Input input = new ScmLeggedLocomotion.Input(Vec3.ZERO,
                        new Vec3(0, 0, direction), 0, 0, phase, 0.35D, 1.25D, 0.45D, 0, 0);
                ScmLeggedLocomotion.Plan plan = ScmLeggedLocomotion.solve(input, limbs, List.of(),
                        ScmLeggedLocomotion.BodyMotion.NONE, gait);
                int swinging = 0;
                for(int idx = 0; idx < 2; idx++){
                    ScmLeggedLocomotion.LimbTarget target = plan.targets().get("leg_" + idx);
                    if(!target.stance()) swinging++;
                    double sign = idx == 0 ? 1.0D : -1.0D;
                    ScmArticulatedIk.Solution res = ScmArticulatedIk.solve(foot(hips[idx], knees[idx]),
                            target.footPosition(), chain(hips[idx], knees[idx], sign));
                    // Follow bounded motor targets with lag; recompute the next pose independently.
                    hips[idx] += Mth.clamp(sign * res.angles().get(0) - hips[idx], -0.35D, 0.35D) * 0.8D;
                    knees[idx] += Mth.clamp(sign * res.angles().get(1) - knees[idx], -0.35D, 0.35D) * 0.8D;
                    if(tick > 40){
                        minKnee[idx] = Math.min(minKnee[idx], knees[idx]);
                        maxKnee[idx] = Math.max(maxKnee[idx], knees[idx]);
                        assertTrue(foot(hips[idx], knees[idx]).distanceTo(target.footPosition()) < 0.16D);
                    }
                }
                assertTrue(swinging <= 1);
                double order = foot(hips[0], knees[0]).z - foot(hips[1], knees[1]).z;
                if(tick > 40 && order * prevOrder < 0) swaps++;
                prevOrder = order;
            }
            assertTrue(swaps > 12, "Both feet must repeatedly exchange front and rear positions");
            for(int idx = 0; idx < 2; idx++){
                assertTrue(minKnee[idx] > 0.7D);
                assertTrue(maxKnee[idx] - minKnee[idx] > 0.4D, "Each knee must flex during swing");
            }
        }
    }

    private static List<ScmArticulatedIk.Joint> chain(double hip, double knee, double sign){
        Vec3 axis = new Vec3(sign, 0, 0);
        return List.of(new ScmArticulatedIk.Joint(Vec3.ZERO, axis, sign * hip, -Math.PI, Math.PI, sign * hip),
                new ScmArticulatedIk.Joint(segment(hip), axis, sign * knee, -Math.PI, Math.PI, sign * 1.5D));
    }

    private static Vec3 foot(double hip, double knee){
        return segment(hip).add(segment(hip + knee));
    }

    private static Vec3 segment(double angle){
        return new Vec3(0, -1.5D * Math.cos(angle), -1.5D * Math.sin(angle));
    }
}
