package com.rieno.gadgetsandgizmos.lib.control.math;

// Predict motion in seconds with world linear values and body angular values
public final class Kinematics{
    private static final int MAX_STEPS = 4096;
    private Kinematics(){}

    public record LinearState(Vector3 position, Vector3 velocity, Vector3 acceleration){}

    // Acceleration and angular acceleration use the rotating body frame
    public record RigidState(Vector3 position, Vector3 velocity, Vector3 acceleration,
                             Quaternion orientation, Vector3 angularVelocity, Vector3 angularAcceleration){}


    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Integrate constant jerk exactly; pass ZERO for constant acceleration
    public static LinearState predict(LinearState state, Vector3 jerk, double seconds){
        requireTime(seconds);
        double dt2 = seconds * seconds;
        return new LinearState(state.position().add(state.velocity().scale(seconds))
                .add(state.acceleration().scale(dt2 / 2)).add(jerk.scale(dt2 * seconds / 6)),
                state.velocity().add(state.acceleration().scale(seconds)).add(jerk.scale(dt2 / 2)),
                state.acceleration().add(jerk.scale(seconds)));
    }

    // Couple evolving orientation, body thrust and anisotropic linear/angular damping with RK4
    public static RigidState predict(RigidState state, Vector3 jerk, Vector3 angularJerk,
                                     Matrix3 drag, Matrix3 angularDrag, double seconds, int steps){
        requireTime(seconds);
        if(steps < 1 || steps > MAX_STEPS) throw new IllegalArgumentException("Steps must be between 1 and 4096");
        if(seconds == 0) return normalized(state);
        double dt = seconds / steps;
        double rate = Math.max(drag.maxAbs(), angularDrag.maxAbs()) * 3;
        // Bound each step for damping stiffness and angular travel
        double turn = state.angularVelocity().magnitude()
                + state.angularAcceleration().magnitude() * seconds + angularJerk.magnitude() * seconds * seconds / 2;
        double required = Math.ceil(seconds * Math.max(rate, turn) * 8);
        if(!Double.isFinite(required) || required > MAX_STEPS){
            throw new IllegalArgumentException("Prediction requires more than 4096 integration steps");
        }
        steps = Math.max(steps, (int) required);
        dt = seconds / steps;
        RigidState res = normalized(state);
        for(int idx = 0; idx < steps; idx++){
            RigidState a = derivative(res, jerk, angularJerk, drag, angularDrag);
            RigidState b = derivative(add(res, a, dt / 2), jerk, angularJerk, drag, angularDrag);
            RigidState c = derivative(add(res, b, dt / 2), jerk, angularJerk, drag, angularDrag);
            RigidState d = derivative(add(res, c, dt), jerk, angularJerk, drag, angularDrag);
            res = normalized(add(add(add(add(res, a, dt / 6), b, dt / 3), c, dt / 3), d, dt / 6));
            if(!finite(res)) throw new IllegalArgumentException("Prediction produced non-finite motion");
        }
        return res;
    }


    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static RigidState derivative(RigidState s, Vector3 jerk, Vector3 angularJerk,
                                         Matrix3 drag, Matrix3 angularDrag){
        Quaternion q = s.orientation().normalized();
        Vector3 bodyVelocity = q.conjugate().rotate(s.velocity());
        Vector3 acceleration = q.rotate(s.acceleration().subtract(drag.transform(bodyVelocity)));
        Quaternion omega = new Quaternion(s.angularVelocity().x(), s.angularVelocity().y(), s.angularVelocity().z(), 0);
        return new RigidState(s.velocity(), acceleration, jerk, s.orientation().multiply(omega).scale(0.5),
                s.angularAcceleration().subtract(angularDrag.transform(s.angularVelocity())), angularJerk);
    }

    private static RigidState add(RigidState s, RigidState d, double dt){
        return new RigidState(s.position().add(d.position().scale(dt)), s.velocity().add(d.velocity().scale(dt)),
                s.acceleration().add(d.acceleration().scale(dt)), s.orientation().add(d.orientation().scale(dt)),
                s.angularVelocity().add(d.angularVelocity().scale(dt)),
                s.angularAcceleration().add(d.angularAcceleration().scale(dt)));
    }

    private static RigidState normalized(RigidState s){
        return new RigidState(s.position(), s.velocity(), s.acceleration(), s.orientation().normalized(),
                s.angularVelocity(), s.angularAcceleration());
    }

    private static boolean finite(RigidState s){
        return s.position().isFinite() && s.velocity().isFinite() && s.acceleration().isFinite()
                && s.angularVelocity().isFinite() && s.angularAcceleration().isFinite();
    }

    private static void requireTime(double seconds){
        if(!Double.isFinite(seconds) || seconds < 0) throw new IllegalArgumentException("Time must be finite and non-negative");
    }
}
