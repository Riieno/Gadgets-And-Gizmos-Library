package com.rieno.gadgetsandgizmos.lib.mixin;

import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.fluids.tank.FluidTankRenderer;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// A distributed tank can have an empty controller while other members still contain visible fluid
@Mixin(value = FluidTankRenderer.class, remap = false)
public abstract class MultiFluidTankRendererMixin{
    @Redirect(method = "renderSafe(Lcom/simibubi/create/content/fluids/tank/FluidTankBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
            at = @At(value = "FIELD", target = "Lcom/simibubi/create/content/fluids/tank/FluidTankBlockEntity;tankInventory:Lnet/neoforged/neoforge/fluids/capability/templates/FluidTank;"))
    private FluidTank gadgetsngizmos$visibleMemberFluid(FluidTankBlockEntity tank){
        return tank.getTankInventory();
    }
}
