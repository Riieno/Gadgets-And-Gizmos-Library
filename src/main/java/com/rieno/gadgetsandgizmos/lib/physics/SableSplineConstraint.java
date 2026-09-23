package com.rieno.gadgetsandgizmos.lib.physics;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.navigation.SplineConstraintFrame;
import com.rieno.gadgetsandgizmos.lib.navigation.WaypointSpline;
import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.Set;

// Retain a solver-owned route constraint while leaving longitudinal motion free
public final class SableSplineConstraint implements AutoCloseable{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Set<ConstraintJointAxis> FLIGHT_AXES = Set.of(
            ConstraintJointAxis.LINEAR_X, ConstraintJointAxis.LINEAR_Y);
    private static final Set<ConstraintJointAxis> GROUND_AXES = Set.of(
            ConstraintJointAxis.LINEAR_X);
    private static final double FRAME_DIRECTION_COS = Math.cos(Math.toRadians(0.25D));
    private static final double FRAME_LATERAL_TOLERANCE_SQR = 0.0001D;
    private static final double DEFAULT_CAPTURE_RADIUS = 0.05D;
    private static final double DEFAULT_RETAINED_RADIUS = 1.0D;
    private static final double DEFAULT_CAPTURE_HEADING = Math.toRadians(3.0D);
    private static final double DEFAULT_CAPTURE_LATERAL_SPEED = 0.15D;
    private static final System.Logger LOGGER = System.getLogger(SableSplineConstraint.class.getName());

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           DEFAULTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private PhysicsConstraintHandle handle;
    private Stage stage = Stage.DETACHED;
    private SubLevelPhysicsSystem system;
    private ServerSubLevel body;
    private WaypointSpline spline;
    private Vec3 anchor = Vec3.ZERO;
    private Vec3 requestAnchor = Vec3.ZERO;
    private boolean requestAnchorWorld;
    private Vec3 framePosition = Vec3.ZERO;
    private Vec3 frameDirection = Vec3.ZERO;
    private Quaterniond localFrame = new Quaterniond();
    private SplineConstraintFrame.AxisPolicy axes = SplineConstraintFrame.AxisPolicy.ALL;
    private WaypointSpline.Projection projection = WaypointSpline.Projection.notFound();
    private double handoffDistance;
    private ClearancePredicate clearance;
    private boolean frameRefreshRequired;
    private AttachmentRequest requested;
    private String diagnostic = "not requested";
    private long queuedUpdates;
    private long physicsSteps;
    private long jointInstalls;
    private long jointReleases;
    private final Vector3d velocity = new Vector3d();
    private final Pose3d physicsPose = new Pose3d();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the spline constraint and its native physics lifecycle
    public SableSplineConstraint(){
        SableSplineConstraintEvents.bootstrap();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Attach or retarget a loaded body after the host has certified route capture
    public synchronized boolean update(ServerSubLevel body, WaypointSpline spline,
                                       WaypointSpline.Projection projection, Vec3 localAnchor,
                                       Vec3 localForward, Vec3 localUp,
                                       SplineConstraintFrame.AxisPolicy axes,
                                       double handoffDistance, ClearancePredicate clearance)
            throws ReflectiveOperationException{
        return update(body, spline, projection, localAnchor, localForward, localUp, axes,
                CaptureSettings.rigidOnly(DEFAULT_CAPTURE_RADIUS, DEFAULT_CAPTURE_HEADING,
                        DEFAULT_CAPTURE_LATERAL_SPEED, DEFAULT_RETAINED_RADIUS),
                handoffDistance, clearance);
    }

    // Queue one attachment with caller-defined capture tolerances
    public synchronized boolean update(ServerSubLevel body, WaypointSpline spline,
                                       WaypointSpline.Projection projection, Vec3 localAnchor,
                                       Vec3 localForward, Vec3 localUp,
                                       SplineConstraintFrame.AxisPolicy axes,
                                       double captureRadius, double maximumHeadingAngle,
                                       double maximumLateralSpeed, double handoffDistance,
                                       ClearancePredicate clearance)
            throws ReflectiveOperationException{
        return update(body, spline, projection, localAnchor, localForward, localUp, axes,
                CaptureSettings.rigidOnly(captureRadius, maximumHeadingAngle,
                        maximumLateralSpeed, Math.max(DEFAULT_RETAINED_RADIUS,
                                Math.max(0.0D, finite(captureRadius)))),
                handoffDistance, clearance);
    }

    // Queue one attachment with separate capture and retained-route tolerances
    public synchronized boolean update(ServerSubLevel body, WaypointSpline spline,
                                       WaypointSpline.Projection projection, Vec3 localAnchor,
                                       Vec3 localForward, Vec3 localUp,
                                       SplineConstraintFrame.AxisPolicy axes,
                                       double captureRadius, double maximumHeadingAngle,
                                       double maximumLateralSpeed, double retainedRadius,
                                       double handoffDistance, ClearancePredicate clearance)
            throws ReflectiveOperationException{
        return update(body, spline, projection, localAnchor, localForward, localUp, axes,
                CaptureSettings.rigidOnly(captureRadius, maximumHeadingAngle,
                        maximumLateralSpeed, retainedRadius),
                handoffDistance, clearance);
    }

    // Queue a staged soft-guide to rigid attachment
    public synchronized boolean update(ServerSubLevel body, WaypointSpline spline,
                                       WaypointSpline.Projection projection, Vec3 localAnchor,
                                       Vec3 localForward, Vec3 localUp,
                                       SplineConstraintFrame.AxisPolicy axes,
                                       CaptureSettings capture, double handoffDistance,
                                       ClearancePredicate clearance)
            throws ReflectiveOperationException{
        return queue(body, spline, projection, localAnchor, localForward, localUp, axes,
                capture, handoffDistance, clearance, false, false);
    }

    // Queue a staged attachment whose material anchor is resolved from the native pose
    public synchronized boolean updateWorldAnchor(
            ServerSubLevel body,
            WaypointSpline spline,
            WaypointSpline.Projection projection,
            Vec3 worldAnchor,
            Vec3 localForward,
            Vec3 localUp,
            SplineConstraintFrame.AxisPolicy axes,
            CaptureSettings capture,
            double handoffDistance,
            ClearancePredicate clearance
    ) throws ReflectiveOperationException{
        return queue(body, spline, projection, worldAnchor, localForward, localUp, axes,
                capture, handoffDistance, clearance, false, true);
    }

    // Queue capture after the caller has certified live hull overlap
    public synchronized boolean updateCaptured(
            ServerSubLevel body,
            WaypointSpline spline,
            WaypointSpline.Projection projection,
            Vec3 localAnchor,
            Vec3 localForward,
            Vec3 localUp,
            SplineConstraintFrame.AxisPolicy axes,
            CaptureSettings capture,
            double handoffDistance,
            ClearancePredicate clearance
    ) throws ReflectiveOperationException{
        return queue(body, spline, projection, localAnchor, localForward, localUp, axes,
                capture, handoffDistance, clearance, true, false);
    }

    // Queue certified hull capture and resolve its material anchor from the native pose
    public synchronized boolean updateCapturedWorldAnchor(
            ServerSubLevel body,
            WaypointSpline spline,
            WaypointSpline.Projection projection,
            Vec3 worldAnchor,
            Vec3 localForward,
            Vec3 localUp,
            SplineConstraintFrame.AxisPolicy axes,
            CaptureSettings capture,
            double handoffDistance,
            ClearancePredicate clearance
    ) throws ReflectiveOperationException{
        return queue(body, spline, projection, worldAnchor, localForward, localUp, axes,
                capture, handoffDistance, clearance, true, true);
    }

    // Validate and retain one queued attachment request
    private boolean queue(ServerSubLevel body, WaypointSpline spline,
                          WaypointSpline.Projection projection, Vec3 localAnchor,
                          Vec3 localForward, Vec3 localUp,
                          SplineConstraintFrame.AxisPolicy axes,
                          CaptureSettings capture, double handoffDistance,
                          ClearancePredicate clearance, boolean overlapCertified,
                          boolean worldAnchor)
            throws ReflectiveOperationException{
        CaptureSettings settings = capture == null ? CaptureSettings.rigidOnly(
                DEFAULT_CAPTURE_RADIUS, DEFAULT_CAPTURE_HEADING,
                DEFAULT_CAPTURE_LATERAL_SPEED, DEFAULT_RETAINED_RADIUS) : capture;
        if(body == null || body.isRemoved() || spline == null || spline.isEmpty()
                || localAnchor == null || localForward == null || localUp == null
                || clearance == null
                || !SplineConstraintFrame.beforeHandoff(spline, projection, handoffDistance)){
            diagnostic = "invalid route or handoff";
            close();
            return false;
        }
        SubLevelPhysicsSystem nextSystem = SubLevelPhysicsSystem.get(body.getLevel());
        RigidBodyHandle rigidBody = nextSystem == null ? null : nextSystem.getPhysicsHandle(body);
        if(rigidBody == null || !rigidBody.isValid()){
            diagnostic = "physics body unavailable";
            close();
            return false;
        }
        SplineConstraintFrame.AxisPolicy policy = axes == null
                ? SplineConstraintFrame.AxisPolicy.ALL : axes;
        if(requested == null) diagnostic = "queued for physics";
        requested = new AttachmentRequest(body, spline, projection, localAnchor,
                localForward, localUp, SplineConstraintFrame.orientation(localForward, localUp),
                policy, nextSystem, settings,
                Math.max(0.0D, finite(handoffDistance)), clearance,
                overlapCertified, worldAnchor);
        queuedUpdates++;
        SableSplineConstraintEvents.retain(this, nextSystem);
        return true;
    }

    // Check whether the solver still owns this attachment
    public synchronized boolean active(){
        return stage != Stage.DETACHED && requested != null && handle != null && handle.isValid()
                && body != null && !body.isRemoved();
    }

    // Check whether capture has completed as a rigid transverse attachment
    public synchronized boolean rigid(){
        return active() && stage == Stage.RIGID;
    }

    // Get the current attachment stage
    public synchronized Stage stage(){
        return active() ? stage : Stage.DETACHED;
    }

    // Describe the last solver capture or release decision
    public synchronized String diagnostic(){
        return diagnostic;
    }

    // Snapshot the native joint lifecycle without exposing its mutable handles
    public synchronized DebugState debugState(){
        return new DebugState(stage, requested != null,
                handle != null && handle.isValid(), diagnostic,
                queuedUpdates, physicsSteps, jointInstalls, jointReleases);
    }

    // Get the current constrained route direction
    public synchronized Vec3 direction(){
        return active() ? SplineConstraintFrame.tangent(projection, axes) : Vec3.ZERO;
    }

    // Apply queued joint lifecycle changes before every physics substep
    synchronized void step(double timeStep){
        AttachmentRequest nextRequest = requested;
        if(nextRequest == null){
            diagnostic = "request released";
            detachNow();
            return;
        }
        if(!Double.isFinite(timeStep) || timeStep <= 0.0D) return;
        physicsSteps++;
        try{
            if(nextRequest.body().isRemoved()){
                diagnostic = "physics body removed";
                requested = null;
                detachNow();
                return;
            }
            Pose3d pose = nextRequest.system().getPipeline().readPose(
                    nextRequest.body(), physicsPose);
            if(pose == null){
                diagnostic = "native pose unavailable";
                requested = null;
                detachNow();
                return;
            }
            // Native pipelines update translation and rotation, while the plot-space
            // rotation point and scale remain owned by the SubLevel pose. Constraint
            // anchors must stay in plot block coordinates for Sable validation.
            Pose3d logicalPose = nextRequest.body().logicalPose();
            if(logicalPose != null){
                pose.rotationPoint().set(logicalPose.rotationPoint());
                pose.scale().set(logicalPose.scale());
            }
            boolean changedBody = body != null && (body != nextRequest.body()
                    || system != nextRequest.system() || spline != nextRequest.spline()
                    || axes != nextRequest.axes());
            boolean changedRequestAnchor = body == null || changedBody
                    || requestAnchorWorld != nextRequest.worldAnchor()
                    || requestAnchor.distanceToSqr(nextRequest.anchor()) > 1.0E-12D;
            Vec3 nextLocalAnchor = nextRequest.worldAnchor() && changedRequestAnchor
                    ? pose.transformPositionInverse(nextRequest.anchor())
                    : nextRequest.worldAnchor() ? anchor : nextRequest.anchor();
            boolean changedFrame = body != null && (anchor.distanceToSqr(nextLocalAnchor)
                    > 1.0E-12D || !localFrame.equals(nextRequest.localFrame(), 1.0E-8D));
            if(changedBody || changedFrame) detachHandle();
            body = nextRequest.body();
            spline = nextRequest.spline();
            system = nextRequest.system();
            axes = nextRequest.axes();
            anchor = nextLocalAnchor;
            requestAnchor = nextRequest.anchor();
            requestAnchorWorld = nextRequest.worldAnchor();
            localFrame = nextRequest.localFrame();
            handoffDistance = nextRequest.handoffDistance();
            clearance = nextRequest.clearance();
            Vec3 position = pose.transformPosition(anchor);
            // A certified hull overlap is more current than a detached joint's old cursor.
            WaypointSpline.Projection previous = handle == null && nextRequest.overlapCertified()
                    ? nextRequest.projection()
                    : projection.found() ? projection : nextRequest.projection();
            WaypointSpline.Projection next = orderedProjection(spline, position, previous);
            RigidBodyHandle rigidBody = system.getPhysicsHandle(body);
            if(rigidBody == null || !rigidBody.isValid()){
                diagnostic = "physics body unavailable";
                requested = null;
                detachNow();
                return;
            }
            rigidBody.getLinearVelocity(velocity);
            Vec3 prev = new Vec3(velocity.x, velocity.y, velocity.z);
            if(!SplineConstraintFrame.beforePredictedHandoff(
                    spline, next, prev, axes, timeStep, handoffDistance)){
                diagnostic = "route handoff reached";
                requested = null;
                detachNow();
                return;
            }
            Vec3 dir = SplineConstraintFrame.anticipatedTangent(
                    spline, next, prev, axes, timeStep);
            if(dir.lengthSqr() <= 1.0E-12D || !clearance.clear(position, dir, timeStep)){
                diagnostic = "direction or clearance rejected";
                detachHandle();
                return;
            }
            projection = next;
            Vec3 worldForward = transform(pose, nextRequest.localForward());
            Vec3 worldUp = transform(pose, nextRequest.localUp());
            if(handle == null){
                boolean rigidCapture = nextRequest.overlapCertified()
                        && SplineConstraintFrame.canAttach(
                        position, worldForward, worldUp, next, axes,
                        nextRequest.capture().rigidRadius(),
                        nextRequest.capture().maximumRigidHeadingAngle());
                if(!rigidCapture && !SplineConstraintFrame.canCapture(
                        position, prev, worldForward, worldUp,
                        next, axes, nextRequest.capture().rigidRadius(),
                        nextRequest.capture().maximumRigidHeadingAngle(),
                        nextRequest.capture().maximumRigidLateralSpeed())){
                    if(nextRequest.capture().guideRadius()
                            <= nextRequest.capture().rigidRadius()
                            || !SplineConstraintFrame.canGuide(position, prev, worldForward,
                            next, axes, nextRequest.capture().guideRadius())){
                        diagnostic = "anchor outside capture radius";
                        return;
                    }
                    if(!installFrame(next.position(), dir, Stage.GUIDING,
                            nextRequest.capture())){
                        if(!"guide mass unavailable".equals(diagnostic))
                            diagnostic = "solver rejected guide";
                        detachHandle();
                    }else diagnostic = "guiding";
                    return;
                }
                if(!installFrame(next.position(), dir, Stage.RIGID,
                        nextRequest.capture())){
                    diagnostic = "solver rejected rigid joint";
                    detachHandle();
                }else diagnostic = "rigid";
                return;
            }
            double routeErrorSqr = axes.filter(position.subtract(next.position())).lengthSqr();
            if(routeErrorSqr > nextRequest.capture().retainedRadius()
                    * nextRequest.capture().retainedRadius()){
                diagnostic = "anchor outside retained radius";
                detachHandle();
                return;
            }
            if(stage == Stage.GUIDING && SplineConstraintFrame.canCapture(
                    position, prev, worldForward, worldUp, next, axes,
                    nextRequest.capture().rigidRadius(),
                    nextRequest.capture().maximumRigidHeadingAngle(),
                    nextRequest.capture().maximumRigidLateralSpeed())){
                if(!installFrame(next.position(), dir, Stage.RIGID,
                        nextRequest.capture())){
                    diagnostic = "solver rejected rigid joint";
                    detachHandle();
                }else diagnostic = "rigid";
                return;
            }
            if(frameRefreshRequired || frameNeedsRefresh(next.position(), dir)){
                if(!installFrame(next.position(), dir, stage,
                        nextRequest.capture())){
                    diagnostic = "solver rejected frame refresh";
                    detachHandle();
                }
                frameRefreshRequired = false;
            }
        }catch(ReflectiveOperationException | RuntimeException err){
            diagnostic = "solver exception: " + err.getClass().getSimpleName();
            LOGGER.log(System.Logger.Level.WARNING, "Could not update spline constraint frame", err);
            requested = null;
            detachNow();
        }
    }

    // Release the solver constraint without teleporting or resetting momentum
    @Override
    public synchronized void close(){
        requested = null;
        if(handle == null) clearAttachment();
    }

    // Release immediately while the physics lifecycle is already stopped
    synchronized void closeImmediately(){
        requested = null;
        detachNow();
    }

    // Remove only the solver joint on the physics callback
    private void detachHandle(){
        try{
            if(handle != null && handle.isValid()){
                handle.remove();
                jointReleases++;
            }
        }catch(RuntimeException err){
            LOGGER.log(System.Logger.Level.WARNING, "Could not release spline constraint", err);
        }
        handle = null;
        stage = Stage.DETACHED;
        framePosition = Vec3.ZERO;
        frameDirection = Vec3.ZERO;
        frameRefreshRequired = false;
    }

    // Remove the solver joint and forget its active body
    private void detachNow(){
        detachHandle();
        clearAttachment();
    }

    // Forget attachment state without touching the physics body
    private void clearAttachment(){
        SableSplineConstraintEvents.release(this);
        system = null;
        body = null;
        spline = null;
        projection = WaypointSpline.Projection.notFound();
        requestAnchor = Vec3.ZERO;
        requestAnchorWorld = false;
        clearance = null;
    }

    // Convert an immutable Minecraft vector
    private static Vector3d vector(Vec3 val){
        return new Vector3d(val.x, val.y, val.z);
    }

    // Replace a world frame instead of moving an existing solver anchor
    private boolean installFrame(Vec3 position, Vec3 direction, Stage nextStage,
                                 CaptureSettings capture)
            throws ReflectiveOperationException{
        if(system == null || body == null) return false;
        if(handle != null && handle.isValid()){
            handle.remove();
            jointReleases++;
        }
        handle = null;
        Quaterniond worldFrame = SplineConstraintFrame.orientation(
                direction, new Vec3(0.0D, 1.0D, 0.0D));
        Set<ConstraintJointAxis> locked = nextStage == Stage.RIGID
                ? axes == SplineConstraintFrame.AxisPolicy.HORIZONTAL ? GROUND_AXES : FLIGHT_AXES
                : Set.of();
        Object config = SableConstraintApi.genericConfiguration(
                vector(position), vector(anchor), worldFrame, localFrame,
                locked);
        Object res = SableConstraintApi.addConstraint(
                system.getPipeline(), null, body, config);
        if(!(res instanceof PhysicsConstraintHandle created) || !created.isValid()) return false;
        handle = created;
        jointInstalls++;
        stage = nextStage;
        if(nextStage == Stage.GUIDING){
            double maximumForce = guideMaximumForce(body, capture.maximumGuideAcceleration());
            if(maximumForce <= 0.0D){
                diagnostic = "guide mass unavailable";
                detachHandle();
                return false;
            }
            created.setMotor(ConstraintJointAxis.LINEAR_X, 0.0D,
                    capture.guideStiffness(), capture.guideDamping(), true, maximumForce);
            if(axes != SplineConstraintFrame.AxisPolicy.HORIZONTAL){
                created.setMotor(ConstraintJointAxis.LINEAR_Y, 0.0D,
                        capture.guideStiffness(), capture.guideDamping(), true, maximumForce);
            }
            SableConstraintApi.wakeUp(system.getPipeline(), body);
        }
        framePosition = position;
        frameDirection = direction;
        return true;
    }

    // Scale a transverse acceleration limit to the current body mass
    private static double guideMaximumForce(ServerSubLevel body, double acceleration){
        MassData mass = body == null ? null : body.getMassTracker();
        double value = mass == null ? 0.0D : finite(mass.getMass());
        return Math.max(0.0D, value) * Math.max(0.0D, finite(acceleration));
    }

    // Keep one straight prismatic frame until its route axes actually change
    private boolean frameNeedsRefresh(Vec3 position, Vec3 direction){
        if(frameDirection.lengthSqr() <= 1.0E-12D
                || frameDirection.dot(direction) < FRAME_DIRECTION_COS) return true;
        Vec3 offset = axes.filter(position.subtract(framePosition));
        Vec3 lateral = offset.subtract(frameDirection.scale(offset.dot(frameDirection)));
        return lateral.lengthSqr() > FRAME_LATERAL_TOLERANCE_SQR;
    }

    // Project through adjacent legs without jumping across a crossing
    private static WaypointSpline.Projection orderedProjection(
            WaypointSpline spline,
            Vec3 position,
            WaypointSpline.Projection previous
    ){
        if(previous == null || !previous.found() || spline == null || spline.isEmpty()){
            return WaypointSpline.Projection.notFound();
        }
        int idx = Math.max(0, Math.min(previous.segmentIndex(), spline.segments().size() - 1));
        WaypointSpline.Projection next = spline.projectSegment(position, idx, previous.fraction());
        while(next.found() && next.fraction() >= 0.999D && idx + 1 < spline.segments().size()){
            next = spline.projectSegment(position, ++idx, 0.0D);
        }
        return next;
    }

    // Transform one body-local direction through the native pose
    private static Vec3 transform(Pose3d pose, Vec3 direction){
        Vector3d res = pose.orientation().transform(
                new Vector3d(direction.x, direction.y, direction.z));
        return new Vec3(res.x, res.y, res.z);
    }

    // Replace invalid floating-point configuration with zero
    private static double finite(double val){
        return Double.isFinite(val) ? val : 0.0D;
    }

    // Store one server request until the physics callback can apply it
    private record AttachmentRequest(
            ServerSubLevel body,
            WaypointSpline spline,
            WaypointSpline.Projection projection,
            Vec3 anchor,
            Vec3 localForward,
            Vec3 localUp,
            Quaterniond localFrame,
            SplineConstraintFrame.AxisPolicy axes,
            SubLevelPhysicsSystem system,
            CaptureSettings capture,
            double handoffDistance,
            ClearancePredicate clearance,
            boolean overlapCertified,
            boolean worldAnchor
    ){}

    // Expose immutable solver lifecycle counters for host diagnostics
    public record DebugState(
            Stage stage,
            boolean requested,
            boolean handleValid,
            String diagnostic,
            long queuedUpdates,
            long physicsSteps,
            long jointInstalls,
            long jointReleases
    ){}

    // Configure staged route capture without applying longitudinal propulsion
    public record CaptureSettings(
            double guideRadius,
            double rigidRadius,
            double maximumRigidHeadingAngle,
            double maximumRigidLateralSpeed,
            double guideStiffness,
            double guideDamping,
            double maximumGuideAcceleration,
            double retainedRadius
    ){
        // Normalize public capture values once at the API boundary
        public CaptureSettings{
            guideRadius = Math.max(0.0D, finite(guideRadius));
            rigidRadius = Math.max(0.0D, finite(rigidRadius));
            maximumRigidHeadingAngle = Math.max(0.0D,
                    Math.min(Math.PI, finite(maximumRigidHeadingAngle)));
            maximumRigidLateralSpeed = Math.max(0.0D, finite(maximumRigidLateralSpeed));
            guideStiffness = Math.max(0.0D, finite(guideStiffness));
            guideDamping = Math.max(0.0D, finite(guideDamping));
            maximumGuideAcceleration = Math.max(0.0D, finite(maximumGuideAcceleration));
            retainedRadius = Math.max(Math.max(guideRadius, rigidRadius), finite(retainedRadius));
        }

        // Preserve the strict one-stage behavior used by existing callers
        public static CaptureSettings rigidOnly(double radius, double maximumHeadingAngle,
                                                double maximumLateralSpeed,
                                                double retainedRadius){
            return new CaptureSettings(radius, radius, maximumHeadingAngle,
                    maximumLateralSpeed, 0.0D, 0.0D, 0.0D, retainedRadius);
        }
    }

    // Identify whether the route owns no joint, a compliant guide or a rigid guide
    public enum Stage{
        DETACHED,
        GUIDING,
        RIGID
    }

    // Let the host release attachment before a hazard or control handoff
    @FunctionalInterface
    public interface ClearancePredicate{
        boolean clear(Vec3 position, Vec3 direction, double timeStep);
    }
}
