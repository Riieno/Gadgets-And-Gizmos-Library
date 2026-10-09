package com.rieno.gadgetsandgizmos.lib.client.render;

import com.rieno.gadgetsandgizmos.lib.scm.ScmOrientation;
import dev.ryanhcode.sable.companion.math.Pose3d;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

// Keep rotating projections near their world anchor even when their plot origin is far away
class SubLevelProjectionMatrixTest{
    @Test void everyMountAndCameraAnglePreservesTheProjectedAnchorAndBasis(){
        Vec3 origin = new Vec3(20493320.5, 100.5, 20481032.5);
        Vec3 camera = new Vec3(12, 180, 8);
        Vec3 anchor = new Vec3(13, 182, 3);
        for(Direction forward : Direction.values()) for(Direction up : Direction.values()){
            if(!ScmOrientation.isValid(forward, up)) continue;
            var frame = new ScmOrientation(forward, up);
            for(double angle : new double[]{-170, -90, -40, 0, 40, 90, 170}){
                var rotation = new Quaterniond().rotateY(Math.toRadians(angle)).rotateX(0.7).mul(frame.rotation());
                var pose = new Pose3d(new Vector3d(anchor.x, anchor.y, anchor.z), rotation,
                        new Vector3d(origin.x, origin.y, origin.z), new Vector3d(1, 1, 1));
                var view = new Matrix4f().rotateY((float) Math.toRadians(angle)).rotateX(-0.3F);
                var matrix = SubLevelClientRenderApi.localModelView(pose, origin, camera, view);
                var expected = view.transformPosition(new Vector3f(1, 2, -5));
                var actual = matrix.transformPosition(new Vector3f());
                assertEquals(0, expected.distance(actual), 1.0E-5);
                for(var axis : new Vector3f[]{new Vector3f(1, 0, 0), new Vector3f(0, 1, 0), new Vector3f(0, 0, -1)}){
                    var expectedAxis = view.transformDirection(new Quaternionf(rotation).transform(new Vector3f(axis)));
                    var actualAxis = matrix.transformDirection(new Vector3f(axis));
                    assertEquals(0, expectedAxis.distance(actualAxis), 1.0E-5);
                }
            }
        }
    }
}
