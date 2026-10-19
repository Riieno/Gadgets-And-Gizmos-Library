package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.client.create.BeltCasingModels;
import com.rieno.gadgetsandgizmos.lib.create.encasing.CustomBeltCasing;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Add the synchronized material to belt model data on the client
@Mixin(BeltBlockEntity.class)
public abstract class BeltCasingModelDataMixin{
    @Inject(method = "getModelData", at = @At("RETURN"), cancellable = true)
    private void addMaterial(CallbackInfoReturnable<ModelData> cir){
        ResourceLocation id = ((CustomBeltCasing) this).getCasingMaterial();
        if(id != null) cir.setReturnValue(cir.getReturnValue().derive().with(BeltCasingModels.MATERIAL_PROPERTY, id).build());
    }
}
