package com.rieno.gadgetsandgizmos.lib.scm;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;

import java.util.List;
import java.util.Optional;

// Hold one configured craft attitude axis without damping the other axes
public final class ScmAngleControl{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Keep attitude feedback behind the shared control API
    private ScmAngleControl(){}

    // Select direct rotation or a retained angle target
    public enum Mode{
        FREE, ANGLE;

        // Preserve direct rotation for missing or unknown modes
        public static Mode fromId(String id){
            return "angle".equalsIgnoreCase(id) ? ANGLE : FREE;
        }

        // Get the rotation mode choices
        public static List<String> options(){
            return List.of("Free", "Angle");
        }
    }

    // Select the world frame or the craft frame captured by a pulse
    public enum Reference{
        NORTH_RELATIVE, CURRENT_VECTOR;

        // Preserve the world frame for existing graphs
        public static Reference fromId(String id){
            return "Current Vector".equalsIgnoreCase(id) ? CURRENT_VECTOR : NORTH_RELATIVE;
        }

        // Get the angle reference choices
        public static List<String> options(){
            return List.of("North Relative", "Current Vector");
        }
    }

    // Keep the existing signed angle limits
    public enum Axis{
        PITCH(90.0D), YAW(360.0D), ROLL(360.0D);

        private final double limit;

        // Store the supported angle range
        Axis(double limit){
            this.limit = limit;
        }

        // Get the largest supported angle
        public double limit(){
            return limit;
        }

        // Keep invalid or out-of-range requests inside the supported range
        public double clamp(double degrees){
            return Double.isFinite(degrees) ? Math.clamp(degrees, -limit, limit) : 0.0D;
        }
    }

    // Capture the authored craft frame before the first hold correction
    public static Hold capture(Axis axis, double targetDegrees, Reference reference,
                               Quaterniondc bodyRotation, ScmOrientation frame){
        Quaterniond rotation = new Quaterniond(bodyRotation).mul(frame.rotation()).normalize();
        Reference selected = reference == null ? Reference.NORTH_RELATIVE : reference;
        Quaterniond baseline = selected == Reference.CURRENT_VECTOR ? rotation : new Quaterniond();
        Quaterniond relative = new Quaterniond(baseline).conjugate().mul(rotation);
        return new Hold(axis, axis.clamp(targetDegrees), selected, baseline,
                ScmAttitude.measure(relative, ScmOrientation.fromMount(null, null)));
    }

    // Positive targets follow Free control: nose up, yaw left from north, and right side down
    public static Feedback sample(Axis axis, double targetDegrees, Quaterniondc bodyRotation,
                                  ScmOrientation frame, Vec3 angularVelocity){
        ScmAttitude attitude = ScmAttitude.measure(bodyRotation, frame);
        return feedback(axis, targetDegrees, bodyRotation, frame, angularVelocity, attitude);
    }

    // Measure feedback on the selected continuous attitude branch
    private static Feedback feedback(Axis axis, double targetDegrees, Quaterniondc bodyRotation,
                                     ScmOrientation frame, Vec3 angularVelocity, ScmAttitude attitude){
        double heading = Math.toRadians(attitude.yaw());
        Vector3d forward = new Quaterniond(bodyRotation).mul(frame.rotation())
                .transform(new Vector3d(0, 0, -1));
        Vec3 direction = switch(axis){
            case PITCH -> new Vec3(Math.cos(heading), 0, Math.sin(heading));
            case YAW -> new Vec3(0, 1, 0);
            case ROLL -> new Vec3(forward.x, forward.y, forward.z).normalize();
        };
        double current = switch(axis){
            case PITCH -> attitude.pitch();
            case YAW -> -attitude.yaw();
            case ROLL -> attitude.roll();
        };
        double error = ScmRotaryAngles.wrap(Math.toRadians(axis.clamp(targetDegrees) - current));
        Vec3 velocity = angularVelocity == null ? Vec3.ZERO : angularVelocity;
        double rate = velocity.dot(direction);
        double cosine = Math.cos(Math.toRadians(attitude.pitch()));
        if(axis != Axis.PITCH && Math.abs(cosine) > 1.0E-6D){
            double rollRate = velocity.dot(new Vec3(Math.sin(heading), 0, -Math.cos(heading))) / cosine;
            rate = axis == Axis.ROLL ? rollRate : velocity.y - forward.y * rollRate;
        }
        return new Feedback(direction, error, Double.isFinite(rate) ? rate : 0.0D);
    }

    // Retain one pulse's baseline and keep Euler branches continuous through overshoot
    public static final class Hold{
        // Store the requested axis and bounded angle
        private final Axis axis;
        private final double targetDegrees;
        // Own the captured frame without exposing its mutable quaternion
        private final Reference reference;
        private final Quaterniond baseline;
        // Retain the last measured branch across feedback and completion checks
        private ScmAttitude prev;

        // Own a captured frame and its first continuous measurement
        private Hold(Axis axis, double targetDegrees, Reference reference, Quaterniondc baseline, ScmAttitude prev){
            this.axis = axis;
            this.targetDegrees = targetDegrees;
            this.reference = reference;
            this.baseline = new Quaterniond(baseline).normalize();
            this.prev = prev;
        }

        // Get the retained reference mode
        public Reference reference(){
            return reference;
        }

        // Measure world-space correction without recapturing the baseline
        public Feedback sample(Quaterniondc bodyRotation, ScmOrientation frame, Vec3 angularVelocity){
            Quaterniond inverse = new Quaterniond(baseline).conjugate();
            Quaterniond relative = new Quaterniond(inverse).mul(bodyRotation).mul(frame.rotation()).normalize();
            ScmOrientation canonical = ScmOrientation.fromMount(null, null);
            ScmAttitude measured = measure(relative, canonical);
            ScmAttitude first = near(measured, prev);
            ScmAttitude second = near(new ScmAttitude(180.0D - measured.pitch(),
                    measured.yaw() + 180.0D, measured.roll() + 180.0D), prev);
            prev = distance(first, prev) <= distance(second, prev) ? first : second;
            Vec3 velocity = transform(inverse, angularVelocity == null ? Vec3.ZERO : angularVelocity);
            Feedback res = feedback(axis, targetDegrees, relative, canonical, velocity, prev);
            return new Feedback(transform(baseline, res.axis()), res.errorRadians(), res.rateRadians());
        }

        // Save the pulse baseline and measured branch for reload recovery
        public CompoundTag toTag(){
            CompoundTag tag = new CompoundTag();
            tag.putString("Axis", axis.name());
            tag.putDouble("Angle", targetDegrees);
            tag.putString("Reference", reference == Reference.CURRENT_VECTOR ? "Current Vector" : "North Relative");
            tag.putDouble("X", baseline.x);
            tag.putDouble("Y", baseline.y);
            tag.putDouble("Z", baseline.z);
            tag.putDouble("W", baseline.w);
            tag.putDouble("Pitch", prev.pitch());
            tag.putDouble("Yaw", prev.yaw());
            tag.putDouble("Roll", prev.roll());
            return tag;
        }

        // Restore valid retained holds without inventing a baseline for legacy data
        public static Optional<Hold> fromTag(CompoundTag tag){
            if(tag == null || tag.isEmpty()) return Optional.empty();
            Axis axis;
            try{
                axis = Axis.valueOf(tag.getString("Axis"));
            }catch(IllegalArgumentException err){
                return Optional.empty();
            }
            double angle = tag.getDouble("Angle");
            Quaterniond baseline = new Quaterniond(tag.getDouble("X"), tag.getDouble("Y"),
                    tag.getDouble("Z"), tag.getDouble("W"));
            ScmAttitude prev = new ScmAttitude(tag.getDouble("Pitch"), tag.getDouble("Yaw"), tag.getDouble("Roll"));
            if(!Double.isFinite(angle) || !baseline.isFinite() || baseline.lengthSquared() < 1.0E-12D
                    || !Double.isFinite(prev.pitch()) || !Double.isFinite(prev.yaw()) || !Double.isFinite(prev.roll())){
                return Optional.empty();
            }
            Reference reference = Reference.fromId(tag.getString("Reference"));
            if(reference == Reference.NORTH_RELATIVE) baseline.identity();
            return Optional.of(new Hold(axis, axis.clamp(angle), reference, baseline, prev));
        }

        // Retain heading at a vertical nose and resolve the remaining bank from the quaternion
        private ScmAttitude measure(Quaterniondc rotation, ScmOrientation frame){
            ScmAttitude res = ScmAttitude.measure(rotation, frame);
            if(Math.abs(Math.cos(Math.toRadians(res.pitch()))) > 1.0E-6D) return res;
            Quaterniond bank = new Quaterniond().rotateY(-Math.toRadians(prev.yaw()))
                    .rotateX(Math.toRadians(res.pitch())).conjugate().mul(rotation);
            double roll = Math.toDegrees(ScmRotaryAngles.wrap(-2.0D * Math.atan2(bank.z, bank.w)));
            return new ScmAttitude(res.pitch(), prev.yaw(), roll);
        }

        // Unwrap all three coordinates around the previous physical pose
        private static ScmAttitude near(ScmAttitude val, ScmAttitude prev){
            return new ScmAttitude(near(val.pitch(), prev.pitch()), near(val.yaw(), prev.yaw()), near(val.roll(), prev.roll()));
        }

        // Keep equivalent degree values on the previous branch
        private static double near(double val, double prev){
            return Math.toDegrees(ScmRotaryAngles.unwrapNear(Math.toRadians(val), Math.toRadians(prev)));
        }

        // Prefer the equivalent Euler representation nearest the last physical pose
        private static double distance(ScmAttitude val, ScmAttitude prev){
            double pitch = val.pitch() - prev.pitch();
            double yaw = val.yaw() - prev.yaw();
            double roll = val.roll() - prev.roll();
            return pitch * pitch + yaw * yaw + roll * roll;
        }

        // Rotate vectors without exposing mutable JOML state
        private static Vec3 transform(Quaterniondc rotation, Vec3 val){
            Vector3d res = rotation.transform(new Vector3d(val.x, val.y, val.z));
            return new Vec3(res.x, res.y, res.z);
        }
    }

    // Feedback vectors and rates use world coordinates and radians
    public record Feedback(Vec3 axis, double errorRadians, double rateRadians){
        // Apply the available angular authority to this axis alone
        public Vec3 acceleration(ScmAdaptiveStateModel model){
            if(model == null) return Vec3.ZERO;
            return axis.scale(model.feedback(axis, true, errorRadians, rateRadians).acceleration());
        }

        // Require both angular position and motion to settle
        public boolean reached(double toleranceDegrees, double rateTolerance){
            return Math.abs(errorRadians) <= Math.toRadians(Math.max(0.0D, toleranceDegrees))
                    && Math.abs(rateRadians) <= Math.max(0.0D, rateTolerance);
        }
    }
}
