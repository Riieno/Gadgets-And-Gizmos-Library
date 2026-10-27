package com.rieno.gadgetsandgizmos.lib.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.rieno.gadgetsandgizmos.lib.display.DisplayLinkData;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.api.behaviour.display.DisplayTarget;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

// Observe transferred text without sampling a source a second time
@Mixin(DisplaySource.class)
public abstract class CreateDisplayLinkCaptureMixin{
    @WrapOperation(method = "transferData", at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/api/behaviour/display/DisplayTarget;acceptText(ILjava/util/List;Lcom/simibubi/create/content/redstone/displayLink/DisplayLinkContext;)V"), remap = false)
    private void gadgetsngizmos$capture(DisplayTarget target, int line, List<MutableComponent> text,
                                        DisplayLinkContext ctx, Operation<Void> original){
        if(!ctx.level().isClientSide) DisplayLinkData.capture(ctx, line, text);
        original.call(target, line, text, ctx);
    }
}
