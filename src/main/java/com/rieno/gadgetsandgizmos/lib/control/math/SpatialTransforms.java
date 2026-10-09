package com.rieno.gadgetsandgizmos.lib.control.math;

// Convert points, directions and tensors using a local-to-world orientation
public final class SpatialTransforms{
    private SpatialTransforms(){}

    public static Vector3 localToWorld(Vector3 point, Vector3 origin, Quaternion orientation){
        return orientation.rotate(point).add(origin);
    }

    public static Vector3 worldToLocal(Vector3 point, Vector3 origin, Quaternion orientation){
        return orientation.normalized().conjugate().rotate(point.subtract(origin));
    }

    public static Matrix3 tensorToWorld(Matrix3 tensor, Quaternion orientation){
        Matrix3 rotation = orientation.rotationMatrix();
        return rotation.multiply(tensor).multiply(rotation.transpose());
    }

    public static Matrix3 tensorToLocal(Matrix3 tensor, Quaternion orientation){
        return tensorToWorld(tensor, orientation.normalized().conjugate());
    }
}
