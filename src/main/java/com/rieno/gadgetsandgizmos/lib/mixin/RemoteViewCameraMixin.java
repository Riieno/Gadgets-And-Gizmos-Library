package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.client.view.RemoteViewClient;
import com.rieno.gadgetsandgizmos.lib.client.view.ViewCameraAccess;
import com.rieno.gadgetsandgizmos.lib.client.view.ViewSceneRenderer;
import com.rieno.gadgetsandgizmos.lib.view.ViewPose;
import net.minecraft.client.Camera;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Apply the lens quaternion after normal player and SubLevel camera setup
@Mixin(value = Camera.class, priority = 500)
public abstract class RemoteViewCameraMixin implements ViewCameraAccess{
    @Shadow private Vec3 position;
    @Shadow @Final private BlockPos.MutableBlockPos blockPosition;
    @Shadow @Final private Quaternionf rotation;
    @Shadow @Final private Vector3f forwards;
    @Shadow @Final private Vector3f up;
    @Shadow @Final private Vector3f left;
    @Shadow private float xRot;
    @Shadow private float yRot;
    @Shadow private float roll;
    @Shadow private boolean detached;
    // Replace the body camera only while a view or scene capture is active
    @Inject(method = "setup", at = @At("TAIL"))
    private void applyLens(BlockGetter level, Entity entity, boolean detached, boolean reverse,
                           float partialTick, CallbackInfo ci){
        ViewPose pose = ViewSceneRenderer.capturePose();
        if(pose == null) pose = RemoteViewClient.pose(partialTick);
        if(pose != null) gadgetsngizmos$applyView(pose);
    }
    // Keep camera position, quaternion and derived vectors consistent
    @Override
    public void gadgetsngizmos$applyView(ViewPose pose){
        position = pose.position();
        blockPosition.set(position.x, position.y, position.z);
        rotation.set(pose.orientation());
        rotation.transform(forwards.set(0, 0, -1));
        rotation.transform(up.set(0, 1, 0));
        rotation.transform(left.set(-1, 0, 0));
        yRot = (float) Math.toDegrees(Math.atan2(-forwards.x, forwards.z));
        xRot = (float) Math.toDegrees(-Math.asin(Math.clamp(forwards.y, -1, 1)));
        Vector3f angles = rotation.getEulerAnglesYXZ(new Vector3f());
        roll = (float) -Math.toDegrees(angles.z);
        detached = true;
    }
}
