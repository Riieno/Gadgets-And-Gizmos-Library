package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.client.render.SubLevelCameraFrameRegistry;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.mixinhelpers.camera.camera_rotation.EntitySubLevelRotationHelper;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.joml.Quaterniond;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Function;

// Apply the interaction frame through Sable's camera rotation pipeline
@Mixin(value = EntitySubLevelRotationHelper.class, remap = false)
public abstract class SubLevelCameraFrameMixin{
    @Inject(method = "getSubLevelInheritedOrientation", at = @At("HEAD"), cancellable = true)
    private static void gadgetsngizmos$cameraFrame(Entity camera, Function<SubLevel, Pose3dc> poses,
            EntitySubLevelRotationHelper.Type type, CallbackInfoReturnable<Quaterniond> cir){
        if(type != EntitySubLevelRotationHelper.Type.CAMERA || !EntitySubLevelRotationHelper.shouldCameraRotate()) return;
        var val = SubLevelCameraFrameRegistry.orientation(camera, Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true));
        if(val != null) cir.setReturnValue(val);
    }
}
