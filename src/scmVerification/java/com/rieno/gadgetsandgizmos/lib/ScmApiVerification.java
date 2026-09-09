package com.rieno.gadgetsandgizmos.lib;

import com.rieno.gadgetsandgizmos.lib.control.IDirectControlReceiver;
import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import com.rieno.gadgetsandgizmos.lib.navigation.OrientedHull;
import com.rieno.gadgetsandgizmos.lib.navigation.GroundPathPlanner;
import com.rieno.gadgetsandgizmos.lib.navigation.RouteTrafficPriority;
import com.rieno.gadgetsandgizmos.lib.scm.AutopilotDebugSnapshot;
import com.rieno.gadgetsandgizmos.lib.navigation.SablePathfinder;
import com.rieno.gadgetsandgizmos.lib.physics.SableTransformApi;
import com.rieno.gadgetsandgizmos.lib.physics.SableAssemblyBoundsApi;
import com.rieno.gadgetsandgizmos.lib.physics.SubLevelParticleOcclusion;
import com.rieno.gadgetsandgizmos.lib.scm.ScmOrientation;
import com.rieno.gadgetsandgizmos.lib.scm.ScmControlMode;
import com.rieno.gadgetsandgizmos.lib.scm.ScmControlModeRegistry;
import com.rieno.gadgetsandgizmos.lib.scm.ScmControlAxes;
import com.rieno.gadgetsandgizmos.lib.scm.ScmSpeedControl;
import com.rieno.gadgetsandgizmos.lib.scm.ScmTarget;
import com.rieno.gadgetsandgizmos.lib.shipping.ShipDockScheduler;
import dev.ryanhcode.sable.companion.math.Pose3d;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Dependency-free regression entry point run by Gradle check. */
public final class ScmApiVerification {
    public static void main(String[] args){
        verifyAirshipApproach();
        verifySablePathfinding();
        verifyRouteTrafficPriority();
        verifyShipDockScheduler();
        verifyAutopilotDebugSnapshot();
        int frames = 0;
        List<Vec3> hull = new ArrayList<>();
        for(double x : new double[]{-2, 2}){
            for(double y : new double[]{-3, 3}){
                for(double z : new double[]{-4, 4}) hull.add(new Vec3(x, y, z));
            }
        }
        for(Direction forward : Direction.values()){
            for(Direction up : Direction.values()){
                if(!ScmOrientation.isValid(forward, up)){
                    boolean rejected = false;
                    try { new ScmOrientation(forward, up); }
                    catch(IllegalArgumentException expected){ rejected = true; }
                    require(rejected, "Parallel frame accepted");
                    continue;
                }
                frames++;
                ScmOrientation frame = new ScmOrientation(forward, up);
                require(frame.equals(ScmOrientation.fromTag(frame.toTag()).orElseThrow()), "Frame round trip");
                near(frame.rightVector().cross(frame.forwardVector()), frame.upVector());
                near(frame.forwardVector(), Vec3.atLowerCornerOf(forward.getNormal()));
                require(Math.abs(frame.rightVector().lengthSqr() - 1) < 1.0E-9D, "Unit right axis");
                double extent = switch(forward.getAxis()){ case X -> 2; case Y -> 3; case Z -> 4; };
                Vec3 front = OrientedHull.targetPoint(hull, frame.rightVector(), frame.upVector(),
                        frame.forwardVector(), 0, 0, 1, Vec3.ZERO);
                near(front, frame.forwardVector().scale(extent));
                Vec3 rear = OrientedHull.targetPoint(hull, frame.rightVector(), frame.upVector(),
                        frame.forwardVector(), 0, 0, -1, Vec3.ZERO);
                near(rear, front.scale(-1));

                Pose3d source = new Pose3d();
                source.orientation().rotateXYZ(0.31, Math.PI, -0.46);
                source.position().set(100, -50, 600);
                Pose3d target = new Pose3d();
                target.orientation().rotateXYZ(-0.27, 0.65, 0.13);
                target.position().set(-500, 200, 20);
                Vec3 rotated = SableTransformApi.transformDirectionBetween(target, source, frame.forwardVector());
                near(SableTransformApi.transformDirectionBetween(source, target, rotated), frame.forwardVector());
                near(SableTransformApi.transformDirectionBetween(target, source, frame.forwardVector().scale(3)),
                        rotated.scale(3));
                Vec3 rotatedUp = SableTransformApi.transformDirectionBetween(target, source, frame.upVector());
                near(SableTransformApi.transformDirectionBetween(target, source, frame.rightVector()),
                        rotated.cross(rotatedUp));
                Vec3 point = new Vec3(7, 8, 9);
                near(SableTransformApi.transformPositionBetween(source, target,
                        SableTransformApi.transformPositionBetween(target, source, point)), point);
            }
        }
        require(frames == 24, "All 24 signed frames covered");
        verifyAutopilotGroups();
        List<String> exclusiveSignals = new ArrayList<>();
        IDirectControlReceiver exclusiveReceiver = (channel, value) ->
                exclusiveSignals.add(channel + '=' + value);
        exclusiveReceiver.applyExclusiveDirectControllerSignal("right", "left", 1.0F);
        require(exclusiveSignals.equals(List.of("left=0.0", "right=1.0")),
                "Exclusive direct control did not clear the opposing channel first");
        ScmTarget leftFace = new ScmTarget(null, new BlockPos(1, 2, 3),
                "test:gearshift", "Gearshift", BlockPos.ZERO, Direction.WEST);
        ScmTarget rightFace = new ScmTarget(null, new BlockPos(1, 2, 3),
                "test:gearshift", "Gearshift", BlockPos.ZERO, Direction.EAST);
        require(leftFace.blockStableId().equals(rightFace.blockStableId())
                        && !leftFace.stableId().equals(rightFace.stableId()),
                "SCM target physical identity did not group opposing block faces");
        ScmSpeedControl.Demand cruiseSpeed = ScmSpeedControl.plan(
                new ScmSpeedControl.Request(8.0D, 8.0D, 8.0D,
                        0.75D, 0.35D));
        require(cruiseSpeed.exclusive() && cruiseSpeed.acceleration() == 0.75D
                        && cruiseSpeed.deceleration() == 0.0D
                        && cruiseSpeed.brake() == 0.0D,
                "Cruise speed did not remain on the direction-independent Acceleration channel");
        ScmSpeedControl.Demand reducedSpeed = ScmSpeedControl.plan(
                new ScmSpeedControl.Request(8.0D, 4.0D, 8.0D,
                        0.75D, 0.35D));
        require(reducedSpeed.exclusive() && reducedSpeed.acceleration() == 0.0D
                        && reducedSpeed.deceleration() > 0.0D
                        && reducedSpeed.brake() == 0.0D,
                "A lower non-zero speed envelope did not select Deceleration alone");
        ScmSpeedControl.Demand stoppedSpeed = ScmSpeedControl.plan(
                new ScmSpeedControl.Request(4.0D, 0.0D, 8.0D,
                        0.75D, 0.35D));
        require(stoppedSpeed.exclusive() && stoppedSpeed.acceleration() == 0.0D
                        && stoppedSpeed.deceleration() == 0.0D
                        && stoppedSpeed.brake() > 0.0D,
                "A zero safe-speed envelope did not select Brake alone");
        double lookahead = ScmSpeedControl.collisionLookahead(
                4.0D, 3.0D, 0.35D, 2.5D, 8.0D);
        require(lookahead >= 8.0D
                        && ScmSpeedControl.safeSpeed(
                        lookahead, 3.0D, 0.35D, 2.5D) >= 4.0D
                        && ScmSpeedControl.stoppingSpeed(
                        lookahead, 3.0D, 0.35D, 2.5D) >= 4.0D,
                "Reusable live stopping envelope did not round-trip its speed");
        require(ScmSpeedControl.captureApproachSpeed(
                        3.1D, 3.0D, 0.35D, 2.5D, 1.5D) == 1.5D
                        && ScmSpeedControl.captureApproachSpeed(
                        3.0D, 3.0D, 0.35D, 2.5D, 1.5D) == 0.0D
                        && ScmSpeedControl.captureApproachSpeed(
                        0.1D, 0.0D, 0.35D, 2.5D, 1.5D) < 1.5D,
                "Capture approach speed stalled outside or drove inside the target radius");
        require(ScmOrientation.fromTag(new CompoundTag()).isEmpty(), "Legacy frame must be automatic");
        require(ScmOrientation.fromTag(null).isEmpty(), "Missing frame");
        CompoundTag invalid = new CompoundTag();
        invalid.putString("Forward", "north");
        invalid.putString("Up", "south");
        require(ScmOrientation.fromTag(invalid).isEmpty(), "Opposite axes rejected");
        invalid.putString("Up", "unknown");
        require(ScmOrientation.fromTag(invalid).isEmpty(), "Unknown direction rejected");
        for(Direction facing : Direction.Plane.HORIZONTAL){
            require(ScmOrientation.fromMount(facing, Direction.UP).forward() == facing, "ACC facing sign");
            ScmOrientation frame = new ScmOrientation(facing, Direction.UP);
            for(String mode : List.of("car", "plane", "airship")){
                ScmControlMode.ControlInput input = new ScmControlMode.ControlInput(
                        Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, frame.forwardVector(), frame.upVector(),
                        frame.rightVector(), frame.forwardVector().scale(50), frame.forwardVector(),
                        Vec3.ZERO, 8, 1, 1, 100, 100, false, true);
                ScmControlMode.ControlOutput output = ScmControlModeRegistry.resolve(mode).navigate(input);
                require(output.force().dot(frame.forwardVector()) > 0, mode + " drove backward for " + facing);
            }
            ScmControlMode.ControlInput reverse = new ScmControlMode.ControlInput(
                    Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, frame.forwardVector(), frame.upVector(),
                    frame.rightVector(), frame.forwardVector().scale(-50), frame.forwardVector().scale(-1),
                    Vec3.ZERO, 8, 1, 1, 100, 100, false, true, true);
            ScmControlMode.ControlOutput recovery = ScmControlModeRegistry.resolve("car").navigate(reverse);
            require(recovery.driveDirection() < 0 && recovery.force().dot(frame.forwardVector()) < 0,
                    "Intentional reverse recovery must remain available");
            require(ScmControlAxes.longitudinalDrive(recovery.force(), frame.forwardVector(),
                    recovery.driveDirection()) < 0.0D,
                    "Intentional reverse recovery must select the Backwards control group");
        }
        Vec3 fallback = new Vec3(1, 2, 3);
        near(OrientedHull.targetPoint(List.of(), Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, 0, 0, 1, fallback), fallback);
        System.out.println("SCM APIs: 24 frames, NBT, signed hull targets and rotated body transforms passed.");
    }

    private static void verifyShipDockScheduler(){
        UUID dockId = UUID.fromString("00000000-0000-0000-0000-000000000010");
        UUID farId = UUID.fromString("00000000-0000-0000-0000-000000000011");
        UUID nearId = UUID.fromString("00000000-0000-0000-0000-000000000012");
        ShipDockScheduler.DockSlot connector = ShipDockScheduler.DockSlot.resource(
                dockId, "connector|north");
        ShipDockScheduler scheduler = new ShipDockScheduler();
        ShipDockScheduler.Lease far = scheduler.request(
                farId, List.of(connector), Set.of(), null,
                new ShipDockScheduler.RequestPriority(1_000.0D, 1_000L, false), 0L);
        require(far.granted(), "Initial distant dock request was not granted");
        ShipDockScheduler.Lease near = scheduler.request(
                nearId, List.of(connector), Set.of(), null,
                new ShipDockScheduler.RequestPriority(4.0D, 4L, false), 1L);
        require(near.granted(), "Nearby dock request did not preempt distant uncommitted request");
        far = scheduler.request(
                farId, List.of(connector), Set.of(), null,
                new ShipDockScheduler.RequestPriority(1_000.0D, 1_000L, false), 2L);
        require(!far.granted(), "Distant dock request retained a provision over nearby traffic");

        ShipDockScheduler fifo = new ShipDockScheduler();
        ShipDockScheduler.Lease first = fifo.request(
                farId, List.of(connector), Set.of(), null,
                new ShipDockScheduler.RequestPriority(8.0D, 8L, false), 0L);
        ShipDockScheduler.Lease second = fifo.request(
                nearId, List.of(connector), Set.of(), null,
                new ShipDockScheduler.RequestPriority(8.0D, 8L, false), 1L);
        require(first.granted() && !second.granted(),
                "Equal-distance dock requests did not preserve FIFO order");

        ShipDockScheduler independent = new ShipDockScheduler();
        ShipDockScheduler.DockSlot arrival = ShipDockScheduler.DockSlot.resource(
                dockId, "arrival|" + dockId);
        require(independent.request(farId, List.of(connector), Set.of(), null, 0L).granted()
                        && independent.request(nearId, List.of(arrival), Set.of(), null, 1L).granted(),
                "Independent dock arrival and connector resources conflicted");
        Vec3 holding = ShipDockScheduler.resolveHoldingPosition(
                Vec3.ZERO, new Vec3(1.0D, 0.0D, 0.0D),
                new ShipDockScheduler.HoldingPlacement(12.0D, 0.0D, 0.0D),
                0.0D, List.of(List.of(
                        new Vec3(-20.0D, 0.0D, 0.0D),
                        new Vec3(20.0D, 0.0D, 0.0D))), 6.0D);
        require(Math.abs(holding.z) >= 6.0D,
                "Dock holding placement remained inside the retained ingress route");
    }

    private static void verifySablePathfinding(){
        var movingEnvelope = new net.minecraft.world.phys.AABB(
                -1.0D, -1.0D, -1.0D, 1.0D, 1.0D, 1.0D);
        require(Math.abs(SubLevelParticleOcclusion.findEnvelopeBlockingDistance(
                        new Vec3(1.0D, 0.0D, 0.0D), 20.0D,
                        List.of(movingEnvelope), List.of(
                                new net.minecraft.world.phys.AABB(
                                        8.0D, -2.0D, -2.0D,
                                        10.0D, 2.0D, 2.0D))) - 7.0D) < 1.0E-6D,
                "Moving envelope collision distance was incorrect");
        require(Math.abs(SubLevelParticleOcclusion.findEnvelopeBlockingDistance(
                        new Vec3(1.0D, 0.0D, 0.0D), 20.0D,
                        List.of(movingEnvelope), List.of(
                                new net.minecraft.world.phys.AABB(
                                        8.0D, -2.0D, 4.0D,
                                        10.0D, 2.0D, 6.0D))) - 20.0D) < 1.0E-6D,
                "Separated moving envelopes reported a collision");
        SablePathfinder.Safety safety = new SablePathfinder.Safety(.2D, 1.0D, .5D);
        SablePathfinder.Validator detourValidator = query -> {
            var expanded = new net.minecraft.world.phys.AABB(2.7D, -1.0D, -.7D,
                    3.3D, 2.0D, .7D).inflate(query.safety().horizontalRadius());
            return segmentIntersects(query.start(), query.end(), expanded)
                    ? SablePathfinder.Traversal.blocked()
                    : SablePathfinder.Traversal.clear(query.mode());
        };
        SablePathfinder.Request detour = new SablePathfinder.Request(null,
                SablePathfinder.Location.world(Vec3.ZERO),
                SablePathfinder.Location.world(new Vec3(6.0D, 0.0D, 0.0D)),
                safety, new SablePathfinder.Movement(Set.of(SablePathfinder.RouteMode.GROUND), 0),
                1.0D, 12.0D, 2048, .1D, detourValidator);
        SablePathfinder.Result route = SablePathfinder.plan(detour);
        require(route.reachedDestination(), "Generic pathfinder did not find a safe detour");
        require(route.waypoints().stream().anyMatch(waypoint -> Math.abs(waypoint.position().z) > .7D),
                "Generic pathfinder crossed the blocked gap");

        SablePathfinder.Request longDetour = new SablePathfinder.Request(null,
                SablePathfinder.Location.world(Vec3.ZERO),
                SablePathfinder.Location.world(new Vec3(160.0D, 0.0D, 0.0D)),
                safety, new SablePathfinder.Movement(Set.of(SablePathfinder.RouteMode.GROUND), 0),
                1.0D, 180.0D, 32_768, .1D, query -> {
            var expanded = new net.minecraft.world.phys.AABB(79.7D, -1.0D, -.7D,
                    80.3D, 2.0D, .7D).inflate(query.safety().horizontalRadius());
            return segmentIntersects(query.start(), query.end(), expanded)
                    ? SablePathfinder.Traversal.blocked()
                    : SablePathfinder.Traversal.clear(query.mode());
        });
        SablePathfinder.Result simplifiedLongRoute = SablePathfinder.plan(longDetour);
        require(simplifiedLongRoute.reachedDestination()
                        && simplifiedLongRoute.waypoints().size() <= 4,
                "Completed pathfinder routes retained grid-only turns after a long safe run");

        SablePathfinder.Request forwardFirst = new SablePathfinder.Request(null,
                SablePathfinder.Location.world(Vec3.ZERO),
                SablePathfinder.Location.world(new Vec3(-4.0D, 0.0D, 0.0D)),
                safety, new SablePathfinder.Movement(Set.of(SablePathfinder.RouteMode.GROUND), 0),
                1.0D, 8.0D, 4096, .1D,
                query -> SablePathfinder.Traversal.clear(query.mode()),
                new SablePathfinder.RoutePreferences(new Vec3(1.0D, 0.0D, 0.0D),
                        .15D, 1.0D, 3.0D, .05D, .2D, 1.0D));
        SablePathfinder.Result turnAround = SablePathfinder.plan(forwardFirst);
        require(turnAround.reachedDestination(),
                "Forward-first pathfinder did not retain a turn-around route");
        require(!turnAround.waypoints().isEmpty()
                        && turnAround.waypoints().getFirst().position().x > 0.0D,
                "Forward-first pathfinder selected an immediate reverse shortcut");

        SablePathfinder.Request unavailable = new SablePathfinder.Request(null,
                SablePathfinder.Location.world(Vec3.ZERO),
                SablePathfinder.Location.world(new Vec3(4.0D, 0.0D, 0.0D)),
                safety, SablePathfinder.Movement.GROUND,
                1.0D, 6.0D, 256, .1D, query -> query.end().x > 1.0D
                ? SablePathfinder.Traversal.unavailable()
                : SablePathfinder.Traversal.clear(query.mode()));
        SablePathfinder.Result pending = SablePathfinder.plan(unavailable);
        require(pending.outcome() == SablePathfinder.Outcome.UNAVAILABLE
                        && !pending.reachedDestination(),
                "Unavailable chunks must not become traversable");

        List<SablePathfinder.Waypoint> mutableDebugWaypoints = new ArrayList<>(route.waypoints());
        SablePathfinder.DebugRoute debugRoute = new SablePathfinder.DebugRoute(
                "verification", Vec3.ZERO, new Vec3(6.0D, 0.0D, 0.0D),
                mutableDebugWaypoints, route.outcome());
        mutableDebugWaypoints.clear();
        require(!debugRoute.waypoints().isEmpty()
                        && debugRoute.outcome() == SablePathfinder.Outcome.COMPLETE,
                "Pathfinder debug routes must retain detached planner snapshots");
        SablePathfinder.DebugRoute cachedDebugRoute = new SablePathfinder.DebugRoute(
                "cached-verification", Vec3.ZERO, new Vec3(6.0D, 0.0D, 0.0D),
                route.waypoints(), route.outcome(), List.of(), true,
                SablePathfinder.DebugRouteStyle.CACHED);
        require(cachedDebugRoute.style() == SablePathfinder.DebugRouteStyle.CACHED,
                "Pathfinder cached debug routes lost their rendering style");

        SablePathfinder.QueuedPlan queued = SablePathfinder.queue(detour);
        require(!queued.debugRoute("queued-verification-live").checkedSegments().isEmpty(),
                "Queued pathfinder did not retain bounded live search diagnostics");
        while (!queued.finished()) {
            queued.advance(4);
        }
        require(queued.result().reachedDestination(),
                "Queued pathfinder did not retain the completed full route");
        SablePathfinder.DebugRoute queuedDebug = queued.debugRoute("queued-verification");
        require(queuedDebug.checkedSegments().isEmpty(),
                "Completed routes must discard stale live search diagnostics");
        SablePathfinder.QueuedPlan directQueued = SablePathfinder.queue(new SablePathfinder.Request(
                null, SablePathfinder.Location.world(Vec3.ZERO),
                SablePathfinder.Location.world(new Vec3(4.0D, 0.0D, 0.0D)),
                safety, SablePathfinder.Movement.GROUND, 1.0D, 6.0D, 256, .1D,
                query -> SablePathfinder.Traversal.clear(query.mode())));
        require(directQueued.finished() && directQueued.result().reachedDestination()
                        && directQueued.result().waypoints().size() == 1,
                "Clear direct routes did not complete without grid expansion");
        SablePathfinder.Validator sweptValidator = SablePathfinder.sableCollisionValidator(
                SablePathfinder.CollisionOptions.DEFAULT,
                SablePathfinder.GroundContactPolicy.NONE,
                SablePathfinder.CollisionPrecision.SWEPT);
        require(sweptValidator.validate(new SablePathfinder.Query(null, Vec3.ZERO,
                new Vec3(1.0D, 0.0D, 0.0D), safety,
                SablePathfinder.RouteMode.GROUND)).result()
                        == SablePathfinder.TraversalResult.UNAVAILABLE,
                "Swept Sable collision validation did not retain unloaded-world safety");
        SablePathfinder.WorkBudget workBudget = new SablePathfinder.WorkBudget(3);
        require(workBudget.claim(20L, 2) == 2 && workBudget.claim(20L, 2) == 1
                        && workBudget.claim(20L, 1) == 0 && workBudget.claim(21L, 3) == 3,
                "Pathfinder work budget did not bound work per tick");
        SablePathfinder.WorkBudget fairWorkBudget = new SablePathfinder.WorkBudget(2);
        Object firstPlanner = new Object();
        Object secondPlanner = new Object();
        require(fairWorkBudget.claim(firstPlanner, 30L, 2) == 2
                        && fairWorkBudget.claim(secondPlanner, 30L, 1) == 0
                        && fairWorkBudget.claim(firstPlanner, 31L, 1) == 0
                        && fairWorkBudget.claim(secondPlanner, 31L, 1) == 1,
                "Pathfinder work budget did not rotate deferred planner work");
        fairWorkBudget.release(firstPlanner);
        fairWorkBudget.release(secondPlanner);
        SablePathfinder.WaypointAdvance overlap = SablePathfinder.advanceWaypointOverlap(
                List.of(new Vec3(1.0D, 0.0D, 0.0D), new Vec3(2.0D, 0.0D, 0.0D)),
                0, Vec3.ZERO, new Vec3(2.0D, 0.0D, 0.0D), safety, 0.0D);
        require(overlap.nextWaypointIndex() == 2 && overlap.skippedWaypoints() == 2,
                "Swept pathfinder hull overlap did not clear traversed route checkpoints");
        SablePathfinder.WaypointAdvance exactOverlap = SablePathfinder.advanceWaypointOverlap(
                List.of(new Vec3(1.0D, 0.0D, 0.0D), new Vec3(2.0D, 0.0D, 0.0D)),
                0, Vec3.ZERO, new Vec3(2.0D, 0.0D, 0.0D), 0.0D,
                (previous, current, waypoint, padding) -> waypoint.x <= 1.0D);
        require(exactOverlap.nextWaypointIndex() == 1 && exactOverlap.skippedWaypoints() == 1,
                "Exact pathfinder hull overlap did not preserve the first unchecked checkpoint");
        SablePathfinder.WaypointAdvance routeOverlap = SablePathfinder.advanceRouteOverlap(
                List.of(new Vec3(5.0D, 0.0D, 0.0D),
                        new Vec3(5.0D, 0.0D, 5.0D),
                        new Vec3(10.0D, 0.0D, 5.0D)),
                0, Vec3.ZERO, new Vec3(7.0D, 0.0D, 5.0D),
                new Vec3(7.0D, 0.0D, 5.0D), safety, 0.0D);
        require(routeOverlap.nextWaypointIndex() == 2
                        && routeOverlap.skippedWaypoints() == 2,
                "Pathfinder did not skip unreachable checkpoints behind an overlapped later route leg");
        SablePathfinder.WaypointAdvance exactRouteOverlap = SablePathfinder.advanceRouteOverlap(
                List.of(new Vec3(4.0D, 0.0D, 0.0D),
                        new Vec3(8.0D, 0.0D, 0.0D),
                        new Vec3(12.0D, 0.0D, 0.0D)),
                0, Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, 0.0D,
                (previous, current, legStart, legEnd, padding) ->
                        legStart.x == 4.0D && legEnd.x == 8.0D);
        require(exactRouteOverlap.nextWaypointIndex() == 1
                        && exactRouteOverlap.skippedWaypoints() == 1,
                "Exact pathfinder route-leg overlap did not resume at the selected leg endpoint");
        SablePathfinder.RouteRejoin rejoin = SablePathfinder.findRouteRejoin(null,
                List.of(new SablePathfinder.Waypoint(new Vec3(1.0D, 0.0D, 0.0D),
                                SablePathfinder.RouteMode.GROUND),
                        new SablePathfinder.Waypoint(new Vec3(3.0D, 0.0D, 0.0D),
                                SablePathfinder.RouteMode.GROUND),
                        new SablePathfinder.Waypoint(new Vec3(4.0D, 0.0D, 0.0D),
                                SablePathfinder.RouteMode.GROUND)),
                0, Vec3.ZERO, safety, query -> query.start().x < .1D && query.end().x < 1.1D
                        ? SablePathfinder.Traversal.blocked()
                        : SablePathfinder.Traversal.clear(query.mode()));
        require(rejoin.found() && rejoin.waypointIndex() == 1 && rejoin.directlyReachable(),
                "Pathfinder did not select a later direct live-clear route leg");
        List<SablePathfinder.Waypoint> retainedRoute = List.of(
                new SablePathfinder.Waypoint(new Vec3(1.0D, 0.0D, 0.0D),
                        SablePathfinder.RouteMode.GROUND),
                new SablePathfinder.Waypoint(new Vec3(2.0D, 0.0D, 0.0D),
                        SablePathfinder.RouteMode.GROUND),
                new SablePathfinder.Waypoint(new Vec3(3.0D, 0.0D, 0.0D),
                        SablePathfinder.RouteMode.GROUND));
        SablePathfinder.RouteRejoinScan firstRejoinScan = SablePathfinder.scanRouteRejoin(
                null, retainedRoute, 0, Vec3.ZERO, safety, query ->
                        query.start().x > .9D && query.start().x < 1.1D
                                && query.end().x > 1.9D && query.end().x < 2.1D
                                ? SablePathfinder.Traversal.blocked()
                                : query.start().x < .1D && query.end().x < 2.1D
                                ? SablePathfinder.Traversal.blocked()
                                : SablePathfinder.Traversal.clear(query.mode()), 1);
        require(!firstRejoinScan.found() && !firstRejoinScan.exhausted()
                        && firstRejoinScan.nextWaypointIndex() == 1,
                "Bounded route-rejoin scanning did not preserve its suffix cursor");
        SablePathfinder.RouteRejoinScan secondRejoinScan = SablePathfinder.scanRouteRejoin(
                null, retainedRoute, firstRejoinScan.nextWaypointIndex(), Vec3.ZERO, safety,
                query -> query.start().x < .1D && query.end().x < 2.1D
                        ? SablePathfinder.Traversal.blocked()
                        : SablePathfinder.Traversal.clear(query.mode()), 1);
        require(secondRejoinScan.found() && !secondRejoinScan.rejoin().directlyReachable()
                        && secondRejoinScan.rejoin().waypointIndex() == 1,
                "Bounded route-rejoin scanning did not select the next safe local detour leg");
        SablePathfinder.RouteCursor cursor = SablePathfinder.routeCursor(
                List.of(new SablePathfinder.Waypoint(new Vec3(8.0D, 0.0D, 0.0D),
                                SablePathfinder.RouteMode.GROUND),
                        new SablePathfinder.Waypoint(new Vec3(8.0D, 0.0D, 8.0D),
                                SablePathfinder.RouteMode.GROUND)),
                0, Vec3.ZERO, new Vec3(8.0D, 0.0D, 3.0D));
        require(cursor.found() && cursor.nextWaypointIndex() == 1,
                "Pathfinder did not resume after the nearest retained route leg");
        List<SablePathfinder.Waypoint> projectionRoute = List.of(
                new SablePathfinder.Waypoint(new Vec3(8.0D, 0.0D, 0.0D),
                        SablePathfinder.RouteMode.GROUND),
                new SablePathfinder.Waypoint(new Vec3(8.0D, 0.0D, 8.0D),
                        SablePathfinder.RouteMode.GROUND));
        SablePathfinder.RouteProjection projection = SablePathfinder.routeProjection(
                projectionRoute, 0, Vec3.ZERO, new Vec3(10.0D, 0.0D, 3.0D));
        require(projection.found() && projection.nextWaypointIndex() == 1
                        && projection.position().distanceToSqr(
                        new Vec3(8.0D, 0.0D, 3.0D)) < 1.0E-8D
                        && Math.abs(projection.legProgress() - 0.375D) < 1.0E-8D,
                "Pathfinder did not project an off-route vehicle onto the nearest route leg");
        SablePathfinder.RouteProjection raisedGroundProjection =
                SablePathfinder.routeLegProjection(
                        0, Vec3.ZERO, new Vec3(10.0D, 10.0D, 0.0D),
                        new Vec3(5.0D, 100.0D, 2.0D),
                        SablePathfinder.RouteMode.GROUND);
        require(Math.abs(raisedGroundProjection.legProgress() - 0.5D) < 1.0E-8D
                        && Math.abs(raisedGroundProjection.distanceToRouteSqr() - 4.0D)
                        < 1.0E-8D,
                "Ground route projection incorrectly included center-of-mass height");
        SablePathfinder.RouteProjection suffixProjection = SablePathfinder.routeProjection(
                List.of(new SablePathfinder.Waypoint(new Vec3(4.0D, 0.0D, 0.0D),
                                SablePathfinder.RouteMode.GROUND),
                        new SablePathfinder.Waypoint(new Vec3(8.0D, 0.0D, 0.0D),
                                SablePathfinder.RouteMode.GROUND),
                        new SablePathfinder.Waypoint(new Vec3(12.0D, 0.0D, 0.0D),
                                SablePathfinder.RouteMode.GROUND)),
                2, Vec3.ZERO, new Vec3(6.0D, 0.0D, 1.0D));
        require(suffixProjection.nextWaypointIndex() == 2
                        && suffixProjection.position().distanceToSqr(
                        new Vec3(8.0D, 0.0D, 0.0D)) < 1.0E-8D,
                "Pathfinder projected a resumed route from its original origin instead of the prior waypoint");
        SablePathfinder.RouteLegRejoin projectedRejoin =
                SablePathfinder.findRouteLegRejoin(
                        null, projectionRoute, 0, Vec3.ZERO,
                        new Vec3(10.0D, 0.0D, 3.0D), safety,
                        query -> SablePathfinder.Traversal.clear(query.mode()));
        require(projectedRejoin.found() && projectedRejoin.waypointIndex() == 1
                        && projectedRejoin.directlyReachable()
                        && projectedRejoin.position().distanceToSqr(
                        new Vec3(8.0D, 0.0D, 3.0D)) < 1.0E-8D,
                "Pathfinder route rejoin targeted a waypoint instead of the nearest leg point");
        SablePathfinder.RouteLegRejoin blockedFinalLegRejoin =
                SablePathfinder.scanRouteLegRejoin(
                        null, List.of(new SablePathfinder.Waypoint(
                                new Vec3(10.0D, 0.0D, 0.0D),
                                SablePathfinder.RouteMode.GROUND)),
                        0, Vec3.ZERO, new Vec3(2.0D, 0.0D, 0.0D), safety,
                        query -> SablePathfinder.Traversal.blocked(), 1).rejoin();
        require(blockedFinalLegRejoin.found()
                        && blockedFinalLegRejoin.waypointIndex() == 0
                        && !blockedFinalLegRejoin.directlyReachable()
                        && blockedFinalLegRejoin.position().distanceToSqr(
                        new Vec3(10.0D, 0.0D, 0.0D)) < 1.0E-8D,
                "Blocked final route leg did not expose an endpoint beyond the obstacle for a live detour");
        SablePathfinder.OrientedRoute reversedRoute = SablePathfinder.orientRoute(
                projectionRoute, Vec3.ZERO, new Vec3(8.0D, 0.0D, 6.0D),
                Vec3.ZERO, 0.01D);
        require(reversedRoute.found() && reversedRoute.reversed()
                        && reversedRoute.origin().equals(new Vec3(8.0D, 0.0D, 8.0D))
                        && reversedRoute.target().equals(Vec3.ZERO)
                        && reversedRoute.waypoints().stream()
                        .map(SablePathfinder.Waypoint::position).toList()
                        .equals(List.of(new Vec3(8.0D, 0.0D, 0.0D), Vec3.ZERO)),
                "Pathfinder did not orient safe route geometry toward its reverse endpoint");
        Vec3 stopA = new Vec3(10.0D, 0.0D, 0.0D);
        Vec3 stopB = new Vec3(20.0D, 0.0D, 0.0D);
        Vec3 stopC = new Vec3(20.0D, 0.0D, 10.0D);
        SablePathfinder.StitchedRoute stitched = SablePathfinder.stitchRouteGraph(List.of(
                routeLeg(stopC, stopA),
                routeLeg(stopA, new Vec3(40.0D, 0.0D, 40.0D)),
                routeLeg(stopA, stopB),
                routeLeg(stopB, stopC)), 0, 0.01D);
        require(stitched.found() && stitched.closed() && stitched.legCount() == 3
                        && stitched.origin().equals(stopA) && stitched.target().equals(stopA)
                        && stitched.waypoints().stream().map(SablePathfinder.Waypoint::position)
                        .toList().equals(List.of(stopB, stopC, stopA)),
                "Route graph stitching followed a stale dead-end instead of the connected schedule cycle");
        SablePathfinder.StitchedRoute openRoute = SablePathfinder.stitchRouteGraph(List.of(
                routeLeg(stopA, stopB), routeLeg(stopB, stopC),
                routeLeg(new Vec3(-10.0D, 0.0D, 0.0D), stopC)), 1, 0.01D);
        require(openRoute.found() && !openRoute.closed() && openRoute.legCount() == 2
                        && openRoute.origin().equals(stopA) && openRoute.target().equals(stopC),
                "Route graph stitching did not retain the connected predecessor chain");
        Vec3 graphA = Vec3.ZERO;
        Vec3 graphB = new Vec3(0.0D, 0.0D, 10.0D);
        Vec3 graphC = new Vec3(10.0D, 0.0D, 10.0D);
        Vec3 graphD = new Vec3(10.0D, 0.0D, 0.0D);
        List<SablePathfinder.RouteLeg> routeGraph = List.of(
                routeLeg(graphA, graphB), routeLeg(graphB, graphC),
                routeLeg(graphC, graphD), routeLeg(graphD, graphA));
        SablePathfinder.OrientedRoute nearestReverse = SablePathfinder.routeGraphPath(
                routeGraph, new Vec3(-1.0D, 0.0D, 5.0D), graphD, 0.01D);
        require(nearestReverse.found() && nearestReverse.reversed()
                        && nearestReverse.origin().equals(new Vec3(0.0D, 0.0D, 5.0D))
                        && nearestReverse.target().equals(graphD)
                        && nearestReverse.waypoints().stream()
                        .map(SablePathfinder.Waypoint::position).toList()
                        .equals(List.of(graphA, graphD)),
                "Route graph path did not join its nearest leg and select the shorter reverse direction");
        SablePathfinder.OrientedRoute nearestForward = SablePathfinder.routeGraphPath(
                routeGraph, new Vec3(-1.0D, 0.0D, 5.0D), graphC, 0.01D);
        require(nearestForward.found() && !nearestForward.reversed()
                        && nearestForward.waypoints().stream()
                        .map(SablePathfinder.Waypoint::position).toList()
                        .equals(List.of(graphB, graphC)),
                "Route graph path did not select the shorter forward direction");
        require(!SablePathfinder.routeGraphPath(
                        routeGraph, graphC, graphC, 0.01D).found(),
                "Route graph path sent a vehicle around a loop after it reached its destination");
        require(SablePathfinder.routeGraphPath(routeGraph,
                        new Vec3(-1.0D, 0.0D, 5.0D),
                        graphC.add(0.5D, 0.0D, 0.0D), 0.01D, 1.0D).found(),
                "Route graph path coupled exact graph topology to the live destination capture radius");
        List<SablePathfinder.RouteLeg> denseGraph = new ArrayList<>();
        Vec3 denseA = new Vec3(0.0D, 0.0D, -10.0D);
        Vec3 denseB = Vec3.ZERO;
        Vec3 denseJoin = new Vec3(100.0D, 0.0D, 0.0D);
        Vec3 denseDestination = new Vec3(110.0D, 0.0D, 0.0D);
        denseGraph.add(routeLeg(denseA, denseB));
        List<Vec3> denseDistractors = new ArrayList<>();
        denseDistractors.add(denseB);
        for (int index = 1; index < 12; index++) {
            denseDistractors.add(new Vec3(index * 3.0D, 0.0D, 20.0D + index));
        }
        for (int first = 0; first < denseDistractors.size(); first++) {
            for (int second = first + 1; second < denseDistractors.size(); second++) {
                denseGraph.add(routeLeg(
                        denseDistractors.get(first), denseDistractors.get(second)));
            }
        }
        denseGraph.add(routeLeg(denseB, denseJoin));
        denseGraph.add(routeLeg(denseJoin, denseDestination));
        Vec3 densePosition = new Vec3(0.0D, 0.0D, -5.0D);
        SablePathfinder.OrientedRoute denseRoute = SablePathfinder.routeGraphPath(
                denseGraph, densePosition, denseDestination, 0.01D);
        require(denseRoute.found() && denseRoute.origin().equals(densePosition)
                        && denseRoute.target().equals(denseDestination)
                        && denseRoute.waypoints().stream()
                        .map(SablePathfinder.Waypoint::position).toList()
                        .equals(List.of(denseB, denseJoin, denseDestination)),
                "Dense route graph lost the valid continuation from its nearest physical leg");
        GroundPathPlanner.VehicleCapabilities steeringVehicle =
                new GroundPathPlanner.VehicleCapabilities(
                        4.0D, Math.toRadians(45.0D), 0.0D, 0.0D, 0.0D, true);
        Vec3 farSteering = GroundPathPlanner.forwardRouteSteering(
                Vec3.ZERO, new Vec3(4.0D, 0.0D, 0.0D), Vec3.ZERO,
                new Vec3(10.0D, 0.0D, 0.0D),
                new Vec3(10.0D, 0.0D, 10.0D), steeringVehicle);
        Vec3 bendSteering = GroundPathPlanner.forwardRouteSteering(
                new Vec3(8.0D, 0.0D, 0.0D), new Vec3(10.0D, 0.0D, 0.0D),
                Vec3.ZERO, new Vec3(10.0D, 0.0D, 0.0D),
                new Vec3(10.0D, 0.0D, 10.0D), steeringVehicle);
        require(farSteering.x > 0.99D && Math.abs(farSteering.z) < 0.01D
                        && bendSteering.x > 0.1D && bendSteering.z > 0.1D,
                "Ground route steering did not anticipate the retained left bend");
        List<Vec3> longStraightTurn = List.of(
                new Vec3(20.0D, 0.0D, 0.0D), new Vec3(40.0D, 0.0D, 0.0D),
                new Vec3(60.0D, 0.0D, 0.0D), new Vec3(80.0D, 0.0D, 0.0D),
                new Vec3(100.0D, 0.0D, 0.0D), new Vec3(100.0D, 0.0D, 20.0D));
        GroundPathPlanner.ForwardRouteControl earlyTurnControl =
                GroundPathPlanner.forwardRouteControl(
                        Vec3.ZERO, new Vec3(4.0D, 0.0D, 0.0D), Vec3.ZERO,
                        longStraightTurn, 0, steeringVehicle,
                        28.0D, 1.0D, 2.5D, 0.35D, 0.35D);
        GroundPathPlanner.ForwardRouteControl tangentTurnControl =
                GroundPathPlanner.forwardRouteControl(
                        new Vec3(97.0D, 0.0D, 0.0D),
                        new Vec3(99.0D, 0.0D, 0.0D), Vec3.ZERO,
                        longStraightTurn, 4, steeringVehicle,
                        28.0D, 1.0D, 2.5D, 0.35D, 0.35D);
        require(earlyTurnControl.turnAhead()
                        && earlyTurnControl.cornerWaypointIndex() == 4
                        && earlyTurnControl.permittedSpeed() < 28.0D
                        && earlyTurnControl.steeringDirection().x > 0.99D
                        && tangentTurnControl.steeringDirection().x > 0.1D
                        && tangentTurnControl.steeringDirection().z > 0.1D
                        && tangentTurnControl.permittedSpeed()
                        <= tangentTurnControl.cornerSpeed() + 1.0E-6D,
                "Ground route control did not look through straight checkpoints and brake before the tangent");
        GroundPathPlanner.Plan forwardRecovery = GroundPathPlanner.planForwardRecovery(
                new GroundPathPlanner.PoseRequest(
                        Vec3.ZERO, new Vec3(8.0D, 0.0D, 8.0D),
                        new Vec3(1.0D, 0.0D, 0.0D), steeringVehicle,
                        24.0D, 1.0D, 256, (from, to) -> true));
        require(!forwardRecovery.waypoints().isEmpty()
                        && forwardRecovery.waypoints().stream()
                        .noneMatch(GroundPathPlanner.Waypoint::reverse),
                "Ground forward collision recovery did not retain forward-only bicycle curves");
        GroundPathPlanner.Plan smoothRejoin = GroundPathPlanner.planForwardRouteRejoin(
                new GroundPathPlanner.RouteRejoinRequest(
                        new Vec3(0.0D, 0.0D, -6.0D),
                        new Vec3(1.0D, 0.0D, 0.0D),
                        Vec3.ZERO, new Vec3(1.0D, 0.0D, 0.0D),
                        24.0D, steeringVehicle, 32.0D, 1.0D, 192,
                        (from, to) -> true));
        require(smoothRejoin.reachesGoal() && !smoothRejoin.curves().isEmpty()
                        && smoothRejoin.curves().getLast().endTangent()
                        .dot(new Vec3(1.0D, 0.0D, 0.0D)) > 0.97D,
                "Ground route rejoin did not merge with the retained leg tangent");
        Vec3 angledFacing = new Vec3(1.0D, 0.0D, 1.0D).normalize();
        GroundPathPlanner.Plan angledRejoin = GroundPathPlanner.planForwardRouteRejoin(
                new GroundPathPlanner.RouteRejoinRequest(
                        new Vec3(0.0D, 0.0D, -6.0D), angledFacing,
                        Vec3.ZERO, new Vec3(1.0D, 0.0D, 0.0D),
                        24.0D, steeringVehicle, 32.0D, 1.0D, 192,
                        (from, to) -> true));
        require(angledRejoin.reachesGoal() && !angledRejoin.curves().isEmpty()
                        && angledRejoin.curves().getFirst().startTangent()
                        .dot(angledFacing) > 0.999D
                        && angledRejoin.curves().getLast().endTangent()
                        .dot(new Vec3(1.0D, 0.0D, 0.0D)) > 0.97D,
                "Angled ground route rejoin did not preserve both endpoint poses");
        SablePathfinder.RouteTerminal terminal = SablePathfinder.routeTerminal(
                stopA, new Vec3(0.0D, 0.0D, -2.0D), 12.0D, 3.0D, 4.0D);
        near(terminal.position(), new Vec3(10.0D, 3.0D, -12.0D));
        require(terminal.reached(new Vec3(10.0D, 3.0D, -8.0D))
                        && !terminal.reached(new Vec3(10.0D, 3.0D, -7.9D)),
                "Route terminal did not preserve its bounded live-control handoff area");
    }

    private static SablePathfinder.RouteLeg routeLeg(Vec3 origin, Vec3 target){
        return new SablePathfinder.RouteLeg(origin, target,
                List.of(new SablePathfinder.Waypoint(target, SablePathfinder.RouteMode.GROUND)));
    }

    private static boolean segmentIntersects(Vec3 start, Vec3 end, net.minecraft.world.phys.AABB bounds){
        Vec3 delta = end.subtract(start);
        double[] range = {0.0D, 1.0D};
        return clip(start.x, delta.x, bounds.minX, bounds.maxX, range)
                && clip(start.y, delta.y, bounds.minY, bounds.maxY, range)
                && clip(start.z, delta.z, bounds.minZ, bounds.maxZ, range);
    }

    private static boolean clip(double origin, double delta, double min, double max, double[] range){
        if(Math.abs(delta) < 1.0E-9D){
            return origin >= min && origin <= max;
        }
        double first = (min - origin) / delta;
        double second = (max - origin) / delta;
        if(first > second){
            double swap = first;
            first = second;
            second = swap;
        }
        range[0] = Math.max(range[0], first);
        range[1] = Math.min(range[1], second);
        return range[0] <= range[1];
    }

    private static void near(Vec3 actual, Vec3 expected){
        require(actual.distanceToSqr(expected) < 1.0E-12D, actual + " != " + expected);
    }

    private static void verifyRouteTrafficPriority(){
        UUID firstId = new UUID(0L, 1L);
        UUID secondId = new UUID(0L, 2L);
        RouteTrafficPriority.Participant crossingFirst =
                new RouteTrafficPriority.Participant(
                        firstId, new Vec3(-4.0D, 0.0D, 0.0D),
                        new Vec3(2.0D, 0.0D, 0.0D),
                        List.of(new Vec3(-4.0D, 0.0D, 0.0D),
                                new Vec3(10.0D, 0.0D, 0.0D)),
                        SablePathfinder.RouteMode.GROUND, 1.0D, 1.0D, 2.0D);
        RouteTrafficPriority.Participant crossingSecond =
                new RouteTrafficPriority.Participant(
                        secondId, new Vec3(0.0D, 0.0D, -4.0D),
                        new Vec3(0.0D, 0.0D, 2.0D),
                        List.of(new Vec3(0.0D, 0.0D, -4.0D),
                                new Vec3(0.0D, 0.0D, 10.0D)),
                        SablePathfinder.RouteMode.GROUND, 1.0D, 1.0D, 2.0D);
        RouteTrafficPriority.Decision firstDecision = RouteTrafficPriority.resolve(
                crossingFirst, List.of(crossingSecond),
                RouteTrafficPriority.Settings.DEFAULT);
        RouteTrafficPriority.Decision secondDecision = RouteTrafficPriority.resolve(
                crossingSecond, List.of(crossingFirst),
                RouteTrafficPriority.Settings.DEFAULT);
        require(firstDecision.conflict() && !firstDecision.yield()
                        && secondDecision.conflict() && secondDecision.yield(),
                "Crossing traffic did not choose exactly one deterministic priority vehicle");
        require(RouteTrafficPriority.requiresRetreat(
                        secondDecision, 2.0D, 4.0D)
                        && !RouteTrafficPriority.requiresRetreat(
                        secondDecision, 8.0D, 4.0D),
                "Crossing traffic retreat did not follow live clearance");

        RouteTrafficPriority.Participant lateCrossing =
                new RouteTrafficPriority.Participant(
                        secondId, new Vec3(0.0D, 0.0D, -12.0D),
                        new Vec3(0.0D, 0.0D, 2.0D),
                        List.of(new Vec3(0.0D, 0.0D, -12.0D),
                                new Vec3(0.0D, 0.0D, 10.0D)),
                        SablePathfinder.RouteMode.GROUND, 1.0D, 1.0D, 2.0D);
        require(!RouteTrafficPriority.resolve(
                        crossingFirst, List.of(lateCrossing),
                        RouteTrafficPriority.Settings.DEFAULT).conflict(),
                "Safely separated crossing traffic was made to wait");
        RouteTrafficPriority.Participant raisedCrossing =
                new RouteTrafficPriority.Participant(
                        secondId, new Vec3(0.0D, 20.0D, -4.0D),
                        new Vec3(0.0D, 0.0D, 2.0D),
                        List.of(new Vec3(0.0D, 20.0D, -4.0D),
                                new Vec3(0.0D, 20.0D, 10.0D)),
                        SablePathfinder.RouteMode.GROUND,
                        1.0D, 1.0D, 2.0D);
        require(!RouteTrafficPriority.resolve(
                        crossingFirst, List.of(raisedCrossing),
                        RouteTrafficPriority.Settings.DEFAULT).conflict(),
                "Vertically separated ground routes were treated as intersecting traffic");

        RouteTrafficPriority.Participant trailing =
                new RouteTrafficPriority.Participant(
                        firstId, Vec3.ZERO, new Vec3(4.0D, 0.0D, 0.0D),
                        List.of(Vec3.ZERO, new Vec3(20.0D, 0.0D, 0.0D)),
                        SablePathfinder.RouteMode.GROUND, 1.0D, 1.0D, 4.0D);
        RouteTrafficPriority.Participant leading =
                new RouteTrafficPriority.Participant(
                        secondId, new Vec3(3.0D, 0.0D, 0.0D),
                        new Vec3(1.0D, 0.0D, 0.0D),
                        List.of(new Vec3(3.0D, 0.0D, 0.0D),
                                new Vec3(20.0D, 0.0D, 0.0D)),
                        SablePathfinder.RouteMode.GROUND, 1.0D, 1.0D, 1.0D);
        require(RouteTrafficPriority.resolve(
                        trailing, List.of(leading),
                        RouteTrafficPriority.Settings.DEFAULT).yield()
                        && !RouteTrafficPriority.resolve(
                        leading, List.of(trailing),
                        RouteTrafficPriority.Settings.DEFAULT).yield(),
                "Same-direction traffic did not yield only the closing follower");
        require(!RouteTrafficPriority.requiresRetreat(
                        RouteTrafficPriority.resolve(
                                trailing, List.of(leading),
                                RouteTrafficPriority.Settings.DEFAULT),
                        0.0D, 4.0D),
                "Same-direction traffic incorrectly requested a reverse retreat");

        RouteTrafficPriority.Participant headOn =
                new RouteTrafficPriority.Participant(
                        secondId, new Vec3(10.0D, 0.0D, 0.0D),
                        new Vec3(-2.0D, 0.0D, 0.0D),
                        List.of(new Vec3(10.0D, 0.0D, 0.0D),
                                new Vec3(-10.0D, 0.0D, 0.0D)),
                        SablePathfinder.RouteMode.GROUND, 1.0D, 1.0D, 2.0D);
        require(!RouteTrafficPriority.resolve(
                        crossingFirst, List.of(headOn),
                        RouteTrafficPriority.Settings.DEFAULT).yield()
                        && RouteTrafficPriority.resolve(
                        headOn, List.of(crossingFirst),
                        RouteTrafficPriority.Settings.DEFAULT).yield(),
                "Head-on traffic did not retain one stable priority vehicle");
    }

    private static void verifyAutopilotDebugSnapshot(){
        UUID id = new UUID(7L, 9L);
        AutopilotDebugSnapshot snapshot = new AutopilotDebugSnapshot(
                id, "Test Vehicle", new Vec3(1.0D, 2.0D, 3.0D),
                AutopilotDebugSnapshot.State.FOLLOWING_ROUTE, 42L,
                List.of(new AutopilotDebugSnapshot.Section("Route", List.of(
                        new AutopilotDebugSnapshot.Entry(
                                "Cursor", "3 / 8",
                                AutopilotDebugSnapshot.Tone.ACCENT)))));
        require(snapshot.vehicleId().equals(id)
                        && snapshot.sections().size() == 1
                        && snapshot.sections().getFirst().entries().getFirst().tone()
                        == AutopilotDebugSnapshot.Tone.ACCENT,
                "Autopilot debug snapshot did not retain structured brain state");
        GraphValue graph = snapshot.graphValue();
        require("map".equals(graph.type()) && graph.value() instanceof Map<?, ?> values
                        && id.toString().equals(values.get("vehicle_id"))
                        && values.get("sections") instanceof List<?> graphSections
                        && graphSections.size() == 1,
                "Autopilot debug snapshot did not expose its complete portable graph value");
        SableAssemblyBoundsApi.Envelope envelope =
                new SableAssemblyBoundsApi.Envelope(3.0D, 6.0D, 3.0D, 5.0D);
        require(Math.abs(envelope.targetOverlapTolerance(2.0D) - 7.0D) <= 1.0E-9D,
                "Assembly target overlap tolerance did not include full hull radius and padding");
    }

    private static void verifyAirshipApproach(){
        var mode = ScmControlModeRegistry.resolve("airship");
        Vec3 forward = new Vec3(1, 0, 0);
        Vec3 up = new Vec3(0, 1, 0);
        Vec3 target = new Vec3(100, 0, 30);
        var direct = mode.navigate(new ScmControlMode.ControlInput(Vec3.ZERO, Vec3.ZERO, Vec3.ZERO,
                forward, up, forward.cross(up), target, target.normalize(), Vec3.ZERO,
                8, .5, 1, false, 100, 100, 8, -1, true, true, false));
        require(direct.torque().dot(up) < -.01, "Clear distant airship target suppressed initial yaw");
        var transit = mode.navigate(new ScmControlMode.ControlInput(Vec3.ZERO, forward.scale(5), Vec3.ZERO,
                forward, up, forward.cross(up), forward.scale(2), forward, Vec3.ZERO,
                8, .5, 1, true, 100, 100, 8, -1, true, true, false));
        require(transit.force().dot(forward) > 0, "Short transit checkpoint reduced airship cruise speed");
    }

    private static void verifyAutopilotGroups(){
        ScmControlMode car = ScmControlModeRegistry.resolve("car");
        Vec3 up = new Vec3(0, 1, 0);
        for(Direction facing : Direction.Plane.HORIZONTAL){
            ScmOrientation frame = new ScmOrientation(facing, Direction.UP);
            Vec3 forward = frame.forwardVector();
            for(double side : new double[]{-1, 1}){
                for(boolean reverse : new boolean[]{false, true}){
                    double travelSign = reverse ? -1 : 1;
                    Vec3 path = forward.scale(travelSign).add(frame.rightVector().scale(side)).normalize();
                    ScmControlMode.ControlOutput output = car.navigate(carInput(forward, path.scale(50),
                            forward.scale(travelSign * 2), reverse));
                    double rightSteering = ScmControlAxes.yawRightDemand(output.torque(), up, reverse);
                    Vec3 desiredHeading = path.scale(travelSign);
                    double error = headingError(forward, desiredHeading);
                    // A right wheel input produces negative physical yaw while moving forward.
                    double yawStep = -rightSteering * travelSign * 0.01D;
                    Vec3 turned = rotateYaw(forward, yawStep);
                    require(Math.abs(headingError(turned, desiredHeading)) < Math.abs(error),
                            "Group steering increased target error: " + facing + " reverse=" + reverse);
                }
            }
            near(ScmControlAxes.yawTorque(up, 0.6), up.scale(-0.6));
            require(ScmControlAxes.yawRightDemand(ScmControlAxes.yawTorque(up, 0.6), up, false) > 0,
                    "Manual right action round trip");
            ScmControlMode.ControlOutput cruise = car.navigate(carInput(forward, forward.scale(50),
                    forward.scale(2), false));
            require(cruise.force().dot(forward) > 0, "Distant target must not be treated as one block away");
            require(cruise.driveStrength() > 0.0D,
                    "Built-in car mode did not retain its direction-independent speed setpoint");
            ScmControlMode.ControlOutput braking = car.navigate(carInput(forward, forward.scale(0.5),
                    forward.scale(8), false));
            require(braking.force().dot(forward) < 0, "Overspeed must request braking");
            require(ScmControlAxes.longitudinalDrive(braking.force(), forward, braking.driveDirection()) == 0,
                    "Braking must not energize forward or reverse gear groups");
            require(ScmControlAxes.longitudinalDrive(forward.scale(-0.4), forward, 0) < 0,
                    "Free-vector reverse thrust must remain available");

            // Exercise the whole mode -> named groups -> simple vehicle response loop.
            for(double side : new double[]{-1, 1}){
                Vec3 target = forward.scale(-20).add(frame.rightVector().scale(side * 20));
                Vec3 position = Vec3.ZERO;
                Vec3 heading = forward;
                double speed = 0;
                for(int tick = 0; tick < 3000 && position.distanceTo(target) > 1.5D; tick++){
                    Vec3 offset = target.subtract(position);
                    ScmControlMode.ControlOutput output = car.navigate(carInput(heading, offset,
                            heading.scale(speed), false));
                    double steering = ScmControlAxes.yawRightDemand(output.torque(), up, false);
                    heading = rotateYaw(heading, -steering * 0.04D);
                    double drive = ScmControlAxes.longitudinalDrive(output.force(), heading, output.driveDirection());
                    // First-order drivetrain response; report actual speed back to the controller.
                    speed += (Math.max(0, drive) * 4 - speed) * 0.2D;
                    position = position.add(heading.scale(speed * 0.04D));
                }
                require(position.distanceTo(target) <= 1.5D,
                        "Forward/backward groups failed to converge: " + facing + " side=" + side
                                + " remaining=" + position.distanceTo(target));
            }
        }
        System.out.println("Autopilot groups: left/right convergence, reverse steering, braking and target distance passed.");
    }

    private static ScmControlMode.ControlInput carInput(Vec3 forward, Vec3 target, Vec3 velocity, boolean reverse){
        Vec3 up = new Vec3(0, 1, 0);
        return new ScmControlMode.ControlInput(Vec3.ZERO, velocity, Vec3.ZERO, forward, up,
                forward.cross(up), target, target.normalize(), Vec3.ZERO,
                8, 1, 0.25, 100, 100, false, true, reverse);
    }

    private static double headingError(Vec3 from, Vec3 to){
        return Math.atan2(from.cross(to).y, from.dot(to));
    }

    private static Vec3 rotateYaw(Vec3 value, double angle){
        return new Vec3(value.x * Math.cos(angle) + value.z * Math.sin(angle), value.y,
                -value.x * Math.sin(angle) + value.z * Math.cos(angle));
    }

    private static void require(boolean condition, String message){
        if(!condition) throw new AssertionError(message);
    }
}
