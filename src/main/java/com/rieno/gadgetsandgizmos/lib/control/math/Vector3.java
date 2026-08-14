package com.rieno.gadgetsandgizmos.lib.control.math;

// Provide immutable three-axis math for controller and graph values
public record Vector3(double x, double y, double z) {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final Vector3 ZERO = new Vector3(0.0D, 0.0D, 0.0D);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Add another vector
    public Vector3 add(Vector3 other) {
        return new Vector3(x + other.x, y + other.y, z + other.z);
    }

    // Subtract another vector
    public Vector3 subtract(Vector3 other) {
        return new Vector3(x - other.x, y - other.y, z - other.z);
    }

    // Multiply the control workspace matrix
    public Vector3 multiply(Vector3 other) {
        return new Vector3(x * other.x, y * other.y, z * other.z);
    }

    // Invert the vector
    public Vector3 invert() {
        return new Vector3(-x, -y, -z);
    }

    // Get the magnitude
    public double magnitude() {
        return Math.sqrt(x * x + y * y + z * z);
    }

    // Get the difference
    public Vector3 difference(Vector3 other) {
        return new Vector3(Math.abs(x - other.x), Math.abs(y - other.y), Math.abs(z - other.z));
    }

    // Get the distance
    public double distance(Vector3 other) {
        return subtract(other).magnitude();
    }
}
