package com.rieno.gadgetsandgizmos.lib.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.rieno.gadgetsandgizmos.lib.client.view.ViewSceneRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

// Leave Iris's player framebuffer and shader callbacks outside native camera LOD draws
@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.coreapi.DependencyInjection.ApiEventInjector", remap = false)
public abstract class ViewSceneDistantHorizonsEventsMixin{
    @WrapOperation(method = "fireAllEvents", at = @At(value = "INVOKE",
            target = "Lcom/seibel/distanthorizons/api/methods/events/interfaces/IDhApiEvent;fireEvent(Lcom/seibel/distanthorizons/api/methods/events/sharedParameterObjects/DhApiEventParam;)V"), require = 0, remap = false)
    private void keepPlayerCallbacks(@Coerce Object event, @Coerce Object param, Operation<Void> original){
        if(ViewSceneRenderer.isCapturing() && event.getClass().getName().startsWith("net.irisshaders.iris.")) return;
        original.call(event, param);
    }
}
