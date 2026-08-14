package com.rieno.gadgetsandgizmos.lib.kinetics;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.util.Mth;

// Plan bounded servo motion without depending on a block entity
public final class ServoMotionController {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Servo motion settings
    private final ServoMotionConfig config;
    // Current angle
    private float currentAngle;
    // Planned target angle
    private float plannedTargetAngle;
    // Remaining motion in degrees
    private float remainingMotionDegrees;
    // Current motion ticks remaining
    private int motionTicksRemaining;
    // Generated speed
    private float generatedSpeed;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the servo motion
    public ServoMotionController(ServoMotionConfig config) {
        this.config = config;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the current angle
    public float getCurrentAngle() {
        return currentAngle;
    }

    // Get the generated speed
    public float getGeneratedSpeed() {
        return generatedSpeed;
    }

    // Check if this is moving
    public boolean isMoving() {
        return motionTicksRemaining > 0 && Math.abs(generatedSpeed) > 1.0E-3f;
    }

    // Snap the servo motion
    public void snapTo(float targetAngle) {
        currentAngle = targetAngle;
        stop();
    }

    // Stop the servo motion
    public void stop() {
        generatedSpeed = 0.0f;
        plannedTargetAngle = currentAngle;
        remainingMotionDegrees = 0.0f;
        motionTicksRemaining = 0;
    }

    // Apply the synced state
    public void applySyncedState(float angle, float speed) {
        currentAngle = angle;
        generatedSpeed = speed;
        plannedTargetAngle = angle;
        remainingMotionDegrees = 0.0f;
        motionTicksRemaining = Math.abs(speed) > 1.0E-3f ? 1 : 0;
    }

    // Update the servo motion
    public void update(float targetAngle) {
        float requestedError = targetAngle - currentAngle;
        if (Math.abs(requestedError) <= config.angleTolerance()) {
            currentAngle = targetAngle;
            stop();
            return;
        }

        if (motionTicksRemaining <= 0 || Math.abs(targetAngle - plannedTargetAngle) > config.angleTolerance()) {
            planMotion(targetAngle, requestedError);
        }

        float executingError = plannedTargetAngle - currentAngle;
        if (Math.abs(executingError) <= config.angleTolerance() || motionTicksRemaining <= 0) {
            currentAngle = plannedTargetAngle;
            stop();
            return;
        }

        float maxStep = Math.max(Math.abs(generatedSpeed) * config.degreesPerTickPerRpm(), config.angleTolerance());
        float allowedTravel = Math.min(Math.abs(executingError), Math.min(remainingMotionDegrees, maxStep));
        if (allowedTravel <= config.angleTolerance()) {
            currentAngle = plannedTargetAngle;
            stop();
            return;
        }

        currentAngle += Math.copySign(allowedTravel, executingError);
        remainingMotionDegrees = Math.max(0.0f, remainingMotionDegrees - allowedTravel);
        motionTicksRemaining = Math.max(0, motionTicksRemaining - 1);

        if (motionTicksRemaining == 0 || remainingMotionDegrees <= config.angleTolerance()
                || Math.abs(plannedTargetAngle - currentAngle) <= config.angleTolerance()) {
            currentAngle = plannedTargetAngle;
            stop();
        }
    }

    // Plan the motion
    private void planMotion(float targetAngle, float requestedError) {
        plannedTargetAngle = targetAngle;
        remainingMotionDegrees = Math.abs(requestedError);
        generatedSpeed = Mth.clamp(requestedError * config.rpmPerDegreeError(), -config.maxRpm(), config.maxRpm());
        float maxStep = Math.max(Math.abs(generatedSpeed) * config.degreesPerTickPerRpm(), config.angleTolerance());
        motionTicksRemaining = Math.max(1, Mth.ceil(remainingMotionDegrees / maxStep) + 1);
    }

    // Store servo motion settings
    public record ServoMotionConfig(float maxRpm, float rpmPerDegreeError, float degreesPerTickPerRpm,
                                    float angleTolerance) {
    }
}
