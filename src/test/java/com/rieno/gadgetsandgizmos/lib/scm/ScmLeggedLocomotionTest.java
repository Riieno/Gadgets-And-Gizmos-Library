package com.rieno.gadgetsandgizmos.lib.scm;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScmLeggedLocomotionTest {
    @Test
    void usesDetectedTerrainWithoutTreatingARaisedFootAsSupport(){
        ScmLeggedLocomotion.Limb limb = new ScmLeggedLocomotion.Limb("leg",
                ScmLeggedLocomotion.LimbKind.LEG, Vec3.ZERO, new Vec3(0, -2, 0),
                0, 1.5D, 1.5D, 0, 0, 0, 0);
        ScmLeggedLocomotion.Contact contact = new ScmLeggedLocomotion.Contact("leg", false,
                new Vec3(0, -2.2D, 0), new Vec3(0, -2.2D, 0), true);
        ScmLeggedLocomotion.Plan plan = ScmLeggedLocomotion.solve(
                ScmLeggedLocomotion.Input.idle(), List.of(limb), List.of(contact));
        assertEquals(0, plan.supportCount());
        assertEquals(-2.2D, plan.targets().get("leg").footPosition().y, 1.0E-8D);
    }

    @Test
    void updatesTheHipFrameWithoutRecalibratingTheMovingFoot(){
        ScmLeggedLocomotion.GaitState state = new ScmLeggedLocomotion.GaitState();
        ScmLeggedLocomotion.Limb original = new ScmLeggedLocomotion.Limb("leg",
                ScmLeggedLocomotion.LimbKind.LEG, new Vec3(1, 0, 0), new Vec3(0, -2, 0),
                0, 1.5D, 1.5D, 0, 0, 0, 0);
        state.referenceLimb(original);
        ScmLeggedLocomotion.Limb moved = new ScmLeggedLocomotion.Limb("leg",
                ScmLeggedLocomotion.LimbKind.LEG, new Vec3(0.5D, -0.2D, 0), new Vec3(0, -1, 1),
                0, 1, 1, 0, 0, 0, 0);
        ScmLeggedLocomotion.Limb ref = state.referenceLimb(moved);
        assertEquals(moved.hipPosition(), ref.hipPosition());
        assertEquals(original.defaultFootPosition(), ref.defaultFootPosition());
        assertEquals(original.upperLength(), ref.upperLength());
    }

    @Test
    void retainsSwingClearanceWhenTerrainIsAtFullLegReach(){
        ScmLeggedLocomotion.Limb limb = new ScmLeggedLocomotion.Limb("leg",
                ScmLeggedLocomotion.LimbKind.LEG, Vec3.ZERO, new Vec3(0, -4.92D, 0),
                0, 3, 3, 0, 0, 0, 0);
        List<ScmLeggedLocomotion.Contact> contacts = List.of(new ScmLeggedLocomotion.Contact(
                "leg", true, new Vec3(0, -6, 0)));
        ScmLeggedLocomotion.LimbTarget[] targets = new ScmLeggedLocomotion.LimbTarget[2];
        double[] phases = {0.175D, 0.675D};
        for(int idx = 0; idx < phases.length; idx++){
            ScmLeggedLocomotion.Input input = new ScmLeggedLocomotion.Input(Vec3.ZERO,
                    new Vec3(0, 0, 1), 0, 0, phases[idx], 0.35D, 1.25D, 0.45D, 0, 0);
            targets[idx] = ScmLeggedLocomotion.solve(input, List.of(limb), contacts)
                    .targets().get("leg");
        }
        assertEquals(0.45D, targets[0].footPosition().y - targets[1].footPosition().y, 1.0E-6D);
        assertTrue(targets[0].angles().knee() > targets[1].angles().knee() + 0.2D);
    }

    @Test
    void keepsGaitUpAlignedToGravityWhenTheControllerIsTilted(){
        ScmLocomotionFrame frame = ScmLocomotionFrame.fromUpAndForward(
                new Vec3(0.6D, 0.8D, 0.0D), new Vec3(0.0D, 0.0D, 1.0D));
        Vec3 foot = frame.toBody(new Vec3(0.0D, -2.0D, 1.0D));
        assertEquals(-2.0D, foot.dot(frame.up()), 1.0E-8D);
        assertEquals(1.0D, foot.dot(frame.forward()), 1.0E-8D);
        assertEquals(new Vec3(0.0D, -2.0D, 1.0D), frame.toLocal(foot));
        assertEquals(0.0D, frame.forward().dot(frame.up()), 1.0E-8D);
        assertEquals(0.0D, frame.right().dot(frame.up()), 1.0E-8D);
    }

    @Test
    void retainsTheReferenceGeometryWhileTheMeasuredFootMoves(){
        ScmLeggedLocomotion.GaitState state = new ScmLeggedLocomotion.GaitState();
        ScmLeggedLocomotion.Limb original = new ScmLeggedLocomotion.Limb("leg",
                ScmLeggedLocomotion.LimbKind.LEG, Vec3.ZERO, new Vec3(0, -2, 0),
                0, 1.5D, 1.5D, 0, 0, 0, 0);
        ScmLeggedLocomotion.Limb moved = new ScmLeggedLocomotion.Limb("leg",
                ScmLeggedLocomotion.LimbKind.LEG, Vec3.ZERO, new Vec3(0, 0, 3),
                0, 1.5D, 1.5D, 0, 0, 0, 0);
        assertEquals(original, state.referenceLimb(original));
        assertEquals(original, state.referenceLimb(moved));
        assertTrue(ScmLeggedLocomotion.solve(ScmLeggedLocomotion.Input.idle(),
                List.of(original), List.of()).targets().get("leg").stance());
        state.clear();
        assertEquals(moved, state.referenceLimb(moved));
    }

    @Test
    void retainsGroundedStanceFootAgainstBodyMotion(){
        ScmLeggedLocomotion.Limb limb = new ScmLeggedLocomotion.Limb(
                "leg", ScmLeggedLocomotion.LimbKind.LEG, Vec3.ZERO,
                new Vec3(0.0D, -2.0D, 0.0D), 0.0D, 1.0D, 1.0D,
                0.0D, 0.0D, 0.0D, 0.0D);
        ScmLeggedLocomotion.Input input = new ScmLeggedLocomotion.Input(
                Vec3.ZERO, Vec3.ZERO, 0.0D, 0.0D, 0.5D,
                0.35D, 1.0D, 0.4D, 0.3D, 0.3D);
        ScmLeggedLocomotion.Contact contact = new ScmLeggedLocomotion.Contact(
                "leg", true, Vec3.ZERO, new Vec3(0.0D, -2.0D, 0.0D));
        ScmLeggedLocomotion.GaitState state = new ScmLeggedLocomotion.GaitState();

        ScmLeggedLocomotion.LimbTarget first = ScmLeggedLocomotion.solve(
                input, List.of(limb), List.of(contact), ScmLeggedLocomotion.BodyMotion.NONE, state)
                .targets().get("leg");
        ScmLeggedLocomotion.LimbTarget next = ScmLeggedLocomotion.solve(
                input, List.of(limb), List.of(contact),
                new ScmLeggedLocomotion.BodyMotion(new Vec3(1.0D, 0.0D, 0.0D), 0.1D), state)
                .targets().get("leg");

        assertTrue(next.footPosition().x < first.footPosition().x);
        assertTrue(next.angles().knee() > 1.0D);
    }

    @Test
    void advancesBipedLegsThroughAlternatingSwingAndStance(){
        ScmLeggedLocomotion.Limb left = new ScmLeggedLocomotion.Limb(
                "left", ScmLeggedLocomotion.LimbKind.LEG, Vec3.ZERO,
                new Vec3(-0.5D, -2.0D, 0.0D), 0.0D, 1.5D, 1.5D,
                0.0D, 0.0D, 0.0D, 0.0D);
        ScmLeggedLocomotion.Limb right = new ScmLeggedLocomotion.Limb(
                "right", ScmLeggedLocomotion.LimbKind.LEG, Vec3.ZERO,
                new Vec3(0.5D, -2.0D, 0.0D), 0.0D, 1.5D, 1.5D,
                0.5D, 0.0D, 0.0D, 0.0D);
        ScmLeggedLocomotion.GaitState state = new ScmLeggedLocomotion.GaitState();

        double firstPhase = state.advancePhase(1.0D, 0.25D, 1.0D);
        ScmLeggedLocomotion.Plan first = ScmLeggedLocomotion.solve(
                locomotionInput(firstPhase), List.of(left, right), List.of());
        double pausedPhase = state.advancePhase(0.0D, 0.05D, 1.0D);
        double secondPhase = state.advancePhase(1.0D, 0.25D, 1.0D);
        ScmLeggedLocomotion.Plan second = ScmLeggedLocomotion.solve(
                locomotionInput(secondPhase), List.of(left, right), List.of());

        assertFalse(first.targets().get("left").stance());
        assertTrue(first.targets().get("right").stance());
        assertEquals(firstPhase, pausedPhase, 1.0E-9D);
        assertTrue(second.targets().get("left").stance());
        assertFalse(second.targets().get("right").stance());
    }

    @Test
    void sweepsTheStanceFootRearwardWhenTheBodyHasNotYetTranslated(){
        ScmLeggedLocomotion.Limb left = new ScmLeggedLocomotion.Limb(
                "left", ScmLeggedLocomotion.LimbKind.LEG, Vec3.ZERO,
                new Vec3(-0.45D, -2.0D, 0.0D), 0.0D, 1.5D, 1.5D,
                0.0D, 0.0D, 0.0D, 0.0D);
        ScmLeggedLocomotion.Limb right = new ScmLeggedLocomotion.Limb(
                "right", ScmLeggedLocomotion.LimbKind.LEG, Vec3.ZERO,
                new Vec3(0.45D, -2.0D, 0.0D), 0.0D, 1.5D, 1.5D,
                0.5D, 0.0D, 0.0D, 0.0D);
        ScmLeggedLocomotion.GaitState state = new ScmLeggedLocomotion.GaitState();

        ScmLeggedLocomotion.Plan touchdown = ScmLeggedLocomotion.solve(
                locomotionInput(0.35D), List.of(left, right), List.of(),
                ScmLeggedLocomotion.BodyMotion.NONE, state);
        ScmLeggedLocomotion.Plan lateStance = ScmLeggedLocomotion.solve(
                locomotionInput(0.65D), List.of(left, right), List.of(),
                ScmLeggedLocomotion.BodyMotion.NONE, state);

        ScmLeggedLocomotion.LimbTarget plantedLeft = touchdown.targets().get("left");
        ScmLeggedLocomotion.LimbTarget sweepingLeft = lateStance.targets().get("left");
        ScmLeggedLocomotion.LimbTarget liftingRight = lateStance.targets().get("right");
        assertTrue(plantedLeft.stance());
        assertTrue(sweepingLeft.stance());
        assertFalse(liftingRight.stance());
        assertTrue(sweepingLeft.footPosition().z < plantedLeft.footPosition().z);
        assertTrue(liftingRight.footPosition().y > -1.8D);
    }

    @Test
    void appliesCrouchAndJumpPosturesWithoutChangingTheGaitCommand(){
        ScmLeggedLocomotion.Limb limb = new ScmLeggedLocomotion.Limb(
                "leg", ScmLeggedLocomotion.LimbKind.LEG, Vec3.ZERO,
                new Vec3(0.0D, -2.0D, 0.0D), 0.0D, 2.0D, 2.0D,
                0.0D, 0.0D, 0.0D, 0.0D);
        ScmLeggedLocomotion.Input input = locomotionInput(0.5D);
        ScmLeggedLocomotion.Contact contact = new ScmLeggedLocomotion.Contact(
                "leg", true, Vec3.ZERO, new Vec3(0.0D, -2.0D, 0.0D));

        ScmLeggedLocomotion.LimbTarget standing = ScmLeggedLocomotion.solve(
                input, List.of(limb), List.of(contact), ScmLeggedLocomotion.BodyMotion.NONE,
                new ScmLeggedLocomotion.GaitState(), ScmLeggedLocomotion.Posture.NONE)
                .targets().get("leg");
        ScmLeggedLocomotion.LimbTarget crouching = ScmLeggedLocomotion.solve(
                input, List.of(limb), List.of(contact), ScmLeggedLocomotion.BodyMotion.NONE,
                new ScmLeggedLocomotion.GaitState(),
                new ScmLeggedLocomotion.Posture(1.0D, 0.0D, false))
                .targets().get("leg");
        ScmLeggedLocomotion.LimbTarget jumping = ScmLeggedLocomotion.solve(
                input, List.of(limb), List.of(contact), ScmLeggedLocomotion.BodyMotion.NONE,
                new ScmLeggedLocomotion.GaitState(),
                new ScmLeggedLocomotion.Posture(0.0D, 1.0D, false))
                .targets().get("leg");
        ScmLeggedLocomotion.LimbTarget airborne = ScmLeggedLocomotion.solve(
                input, List.of(limb), List.of(contact), ScmLeggedLocomotion.BodyMotion.NONE,
                new ScmLeggedLocomotion.GaitState(),
                new ScmLeggedLocomotion.Posture(0.0D, 1.0D, true))
                .targets().get("leg");

        assertTrue(crouching.footPosition().y > standing.footPosition().y);
        assertTrue(jumping.footPosition().y < standing.footPosition().y);
        assertTrue(airborne.footPosition().y > standing.footPosition().y);
    }

    @Test
    void boundsProceduralStepLengthToTheLegReach(){
        ScmLeggedLocomotion.Limb limb = new ScmLeggedLocomotion.Limb(
                "leg", ScmLeggedLocomotion.LimbKind.LEG, Vec3.ZERO,
                new Vec3(0.0D, -2.0D, 0.0D), 0.0D, 1.0D, 1.0D,
                0.0D, 0.0D, 0.0D, 0.0D);
        ScmLeggedLocomotion.Input input = new ScmLeggedLocomotion.Input(
                Vec3.ZERO, new Vec3(0.0D, 0.0D, 1.0D), 0.0D, 0.0D, 0.5D,
                0.35D, 20.0D, 0.4D, 0.3D, 0.3D);

        ScmLeggedLocomotion.LimbTarget target = ScmLeggedLocomotion.solve(
                input, List.of(limb), List.of()).targets().get("leg");

        assertTrue(Math.abs(target.footPosition().z) <= 0.92D);
    }

    @Test
    void keepsAGroundedStanceLegAwayFromFullExtension(){
        ScmLeggedLocomotion.Limb limb = new ScmLeggedLocomotion.Limb(
                "leg", ScmLeggedLocomotion.LimbKind.LEG, Vec3.ZERO,
                new Vec3(0.0D, -2.0D, 0.0D), 0.0D, 1.0D, 1.0D,
                0.0D, 0.0D, 0.0D, 0.0D);
        ScmLeggedLocomotion.Input input = new ScmLeggedLocomotion.Input(
                Vec3.ZERO, Vec3.ZERO, 0.0D, 0.0D, 0.5D,
                0.35D, 1.0D, 0.4D, 0.3D, 0.3D);
        ScmLeggedLocomotion.Contact contact = new ScmLeggedLocomotion.Contact(
                "leg", true, Vec3.ZERO, new Vec3(0.0D, -2.0D, 0.0D));

        ScmLeggedLocomotion.LimbTarget target = ScmLeggedLocomotion.solve(
                input, List.of(limb), List.of(contact)).targets().get("leg");

        assertTrue(target.stance());
        assertTrue(target.footPosition().y > -1.75D);
        assertTrue(target.angles().knee() > 1.0D);
    }

    @Test
    void retainsBalanceOffsetWithoutDisplacingFeetLaterally(){
        ScmLeggedLocomotion.Limb left = new ScmLeggedLocomotion.Limb(
                "left", ScmLeggedLocomotion.LimbKind.LEG, new Vec3(-0.6D, 0.0D, 0.0D),
                new Vec3(-0.6D, -2.0D, 0.0D), 0.0D, 1.0D, 1.0D,
                0.0D, 0.0D, 0.0D, 0.0D);
        ScmLeggedLocomotion.Limb right = new ScmLeggedLocomotion.Limb(
                "right", ScmLeggedLocomotion.LimbKind.LEG, new Vec3(0.6D, 0.0D, 0.0D),
                new Vec3(0.6D, -2.0D, 0.0D), 0.0D, 1.0D, 1.0D,
                0.5D, 0.0D, 0.0D, 0.0D);
        ScmLeggedLocomotion.Input input = new ScmLeggedLocomotion.Input(
                Vec3.ZERO, new Vec3(0.0D, 0.0D, 1.0D), 0.0D, 0.0D, 0.1D,
                0.35D, 1.0D, 0.4D, 0.3D, 0.85D);

        ScmLeggedLocomotion.Plan plan = ScmLeggedLocomotion.solve(
                input, List.of(left, right), List.of(), ScmLeggedLocomotion.BodyMotion.NONE,
                new ScmLeggedLocomotion.GaitState());

        assertTrue(plan.balanceOffset().x > 0.45D);
        assertTrue(plan.targets().get("left").footPosition().x < 0.0D);
        assertTrue(plan.targets().get("right").footPosition().x > 0.0D);
    }

    @Test
    void counterRotatesTheAnkleAgainstHipAndKneeMotion(){
        assertEquals(-0.7D, ScmLeggedLocomotion.ankleCompensation(
                new ScmLeggedLocomotion.JointAngles(0.0D, 0.5D, 0.8D, 2.0D),
                new ScmLeggedLocomotion.JointAngles(0.0D, 0.1D, 0.5D, 2.0D)), 1.0E-9D);
    }

    @Test
    void distributesLiveFootCorrectionAcrossRedundantJoints(){
        ScmLeggedLocomotion.RedundantIkSolution solution = ScmLeggedLocomotion.solveRedundantIk(
                new Vec3(1.0D, 0.0D, 0.0D), new Vec3(1.0D, 0.0D, 0.2D), List.of(
                        new ScmLeggedLocomotion.ArticulatedJoint(
                                Vec3.ZERO, new Vec3(0.0D, 1.0D, 0.0D), 1.0D, 0.12D),
                        new ScmLeggedLocomotion.ArticulatedJoint(
                                new Vec3(0.5D, 0.0D, 0.0D),
                                new Vec3(0.0D, 1.0D, 0.0D), 1.0D, 0.12D)), 0.16D, 0.12D);

        assertEquals(2, solution.jointDeltas().size());
        assertTrue(solution.jointDeltas().getFirst() < 0.0D);
        assertTrue(solution.jointDeltas().get(1) < 0.0D);
        assertTrue(Math.abs(solution.jointDeltas().getFirst()) <= 0.12D);
        assertTrue(Math.abs(solution.jointDeltas().get(1)) <= 0.12D);
    }

    private static ScmLeggedLocomotion.Input locomotionInput(double phase){
        return new ScmLeggedLocomotion.Input(
                Vec3.ZERO, new Vec3(0.0D, 0.0D, 1.0D), 0.0D, 0.0D, phase,
                0.35D, 1.0D, 0.4D, 0.3D, 0.3D);
    }
}
