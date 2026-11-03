package com.rieno.gadgetsandgizmos.lib.client.render;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;

// Preserve world translation and rotation when a held item's view-space transform stays nearly fixed
public record FluidMotionInput(double x, double y, double z, float upX, float upY, float upZ,
                               float rotationX, float rotationY, float rotationZ, float rotationW, double time){
    public FluidMotionInput{
        if(!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || !Double.isFinite(time)
                || !Float.isFinite(upX) || !Float.isFinite(upY) || !Float.isFinite(upZ)
                || !Float.isFinite(rotationX) || !Float.isFinite(rotationY) || !Float.isFinite(rotationZ) || !Float.isFinite(rotationW)){
            throw new IllegalArgumentException("Fluid motion input must be finite");
        }
        var up = new Vector3f(upX, upY, upZ);
        var rotation = new Quaternionf(rotationX, rotationY, rotationZ, rotationW);
        if(up.lengthSquared() < 1.0E-8F || rotation.lengthSquared() < 1.0E-8F) throw new IllegalArgumentException("Fluid directions must be non-zero");
        up.normalize(); rotation.normalize();
        upX = up.x; upY = up.y; upZ = up.z;
        rotationX = rotation.x; rotationY = rotation.y; rotationZ = rotation.z; rotationW = rotation.w;
    }

    public Vector3f up(){ return new Vector3f(upX, upY, upZ); }
    public Quaternionf rotation(){ return new Quaternionf(rotationX, rotationY, rotationZ, rotationW); }

    // GUI positions are screen coordinates and do not represent movement of a physical container
    public static FluidMotionInput capture(Matrix4fc modelView, Quaternionfc cameraRotation, Vec3 cameraPosition,
                                           boolean gui, double time){
        var camera = new Quaternionf(cameraRotation).normalize();
        var viewUp = gui ? new Vector3f(0, -1, 0) : camera.transformInverse(new Vector3f(0, 1, 0));
        var up = FluidSurfaceMotion.localUp(modelView, viewUp);
        var matrix = new Matrix4f(modelView);
        if(new Matrix3f(matrix).determinant() < 0) matrix.scale(-1, 1, 1);
        var rotation = matrix.getUnnormalizedRotation(new Quaternionf()).normalize();
        var offset = modelView.getTranslation(new Vector3f());
        if(gui){
            offset.zero();
        }else{
            rotation.premul(camera);
            camera.transform(offset);
        }
        return new FluidMotionInput(gui ? 0 : cameraPosition.x + offset.x,
                gui ? 0 : cameraPosition.y + offset.y, gui ? 0 : cameraPosition.z + offset.z,
                up.x, up.y, up.z, rotation.x, rotation.y, rotation.z, rotation.w, time);
    }
}
