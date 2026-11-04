package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.util.ThreadContextStack;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import it.unimi.dsi.fastutil.Function;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Keep asynchronous terrain queries away from another thread's temporary render pose
@Mixin(value = ClientLevel.class, priority = 500)
public abstract class SableClientPoseContextMixin{
    @Unique
    private final ThreadContextStack<Function<SubLevel, Pose3dc>> gadgetsngizmos$poses =
            new ThreadContextStack<>(() -> val -> ((SubLevel)val).logicalPose());

    // Preserve nested pose overrides on the calling thread
    @Inject(method = "sable$pushPoseSupplier", at = @At("HEAD"), cancellable = true, remap = false)
    private void pushPose(Function<SubLevel, Pose3dc> supplier, CallbackInfo ci){
        gadgetsngizmos$poses.push(supplier);
        ci.cancel();
    }

    // Restore the context that preceded this thread's render or interaction operation
    @Inject(method = "sable$popPoseSupplier", at = @At("HEAD"), cancellable = true, remap = false)
    private void popPose(CallbackInfo ci){
        gadgetsngizmos$poses.pop();
        ci.cancel();
    }

    // Retain Sable's logical-pose default when this thread has no override
    @Inject(method = "sable$getPose", at = @At("HEAD"), cancellable = true, remap = false)
    private void currentPose(SubLevel subLevel, CallbackInfoReturnable<Pose3dc> cir){
        cir.setReturnValue(gadgetsngizmos$poses.current().apply(subLevel));
    }
}
