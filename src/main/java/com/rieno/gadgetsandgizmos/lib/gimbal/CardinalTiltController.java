package com.rieno.gadgetsandgizmos.lib.gimbal;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

// Resolve pitch and roll demand into four cardinal actuator outputs
public final class CardinalTiltController {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the cardinal tilt
    private CardinalTiltController() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the builder
    public static Builder builder() {
        return new Builder();
    }

    // Create the cardinal tilt from direction
    public static CardinalPulls fromDirection(Vec3 direction) {
        if (direction == null || direction.lengthSqr() < 1.0E-6D) {
            return CardinalPulls.ZERO;
        }

        Vec3 normalized = direction.normalize();
        return new CardinalPulls(
                positiveComponent(Direction.NORTH, normalized),
                positiveComponent(Direction.SOUTH, normalized),
                positiveComponent(Direction.EAST, normalized),
                positiveComponent(Direction.WEST, normalized));
    }

    // Get the positive component
    public static double positiveComponent(Direction side, Vec3 direction) {
        if (direction == null || direction.lengthSqr() < 1.0E-6D) {
            return 0.0D;
        }

        return switch (side) {
            case NORTH -> Math.max(0.0D, -direction.z);
            case SOUTH -> Math.max(0.0D, direction.z);
            case EAST -> Math.max(0.0D, direction.x);
            case WEST -> Math.max(0.0D, -direction.x);
            default -> 0.0D;
        };
    }

    // Normalize the magnitude
    public static double normalizeMagnitude(double value, double maxValue) {
        if (maxValue <= 1.0E-6D) {
            return 0.0D;
        }
        return Mth.clamp(Math.abs(value) / maxValue, 0.0D, 1.0D);
    }

    // Resolve the cardinal tilt
    public static Vec3 resolve(Direction facing, CardinalPulls pulls, double neutralBias, double deadzone) {
        Vec3 facingNormal = Vec3.atLowerCornerOf(facing.getNormal());
        double x = facingNormal.x * neutralBias + pulls.east() - pulls.west();
        double y = facingNormal.y * neutralBias;
        double z = facingNormal.z * neutralBias + pulls.south() - pulls.north();

        Vec3 combined = new Vec3(x, y, z);
        if (combined.lengthSqr() <= deadzone * deadzone) {
            return facingNormal;
        }
        return combined.normalize();
    }

    // Store the cardinal pulls
    public record CardinalPulls(double north, double south, double east, double west) {
        public static final CardinalPulls ZERO = new CardinalPulls(0.0D, 0.0D, 0.0D, 0.0D);

        // Add the cardinal pulls
        public CardinalPulls add(CardinalPulls other) {
            return new CardinalPulls(
                    north + other.north,
                    south + other.south,
                    east + other.east,
                    west + other.west);
        }

        // Get the cardinal pulls value
        public double get(Direction side) {
            return switch (side) {
                case NORTH -> north;
                case SOUTH -> south;
                case EAST -> east;
                case WEST -> west;
                default -> 0.0D;
            };
        }
    }

    // Build the cardinal tilt configuration
    public static final class Builder {
        // Current north
        private double north;
        // Current south
        private double south;
        // Current east
        private double east;
        // Current west
        private double west;

        // Initialize the builder
        private Builder() {
        }

        // Add the builder
        public Builder add(Direction side, double amount) {
            if (amount <= 0.0D) {
                return this;
            }

            switch (side) {
                case NORTH -> north += amount;
                case SOUTH -> south += amount;
                case EAST -> east += amount;
                case WEST -> west += amount;
                default -> {
                }
            }
            return this;
        }

        // Add the builder
        public Builder add(CardinalPulls pulls) {
            north += pulls.north();
            south += pulls.south();
            east += pulls.east();
            west += pulls.west();
            return this;
        }

        // Build the builder
        public CardinalPulls build() {
            return new CardinalPulls(north, south, east, west);
        }
    }
}
