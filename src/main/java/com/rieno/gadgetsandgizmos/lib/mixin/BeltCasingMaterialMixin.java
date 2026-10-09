package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.create.encasing.CreateCasingApi;
import com.rieno.gadgetsandgizmos.lib.create.encasing.CustomBeltCasing;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;

// Save and synchronize the material separately from Create's casing enum
@Mixin(BeltBlockEntity.class)
public abstract class BeltCasingMaterialMixin implements CustomBeltCasing{
    @Unique private static final String MATERIAL_KEY = "gadgetsngizmos:CasingMaterial";
    @Unique private ResourceLocation casingMaterial;

    @Override
    public @Nullable ResourceLocation getCasingMaterial(){
        return casingMaterial;
    }

    @Override
    public void setCasingMaterial(@Nullable ResourceLocation id){
        if(Objects.equals(casingMaterial, id)) return;
        casingMaterial = id;
        refreshCasing();
    }

    // Send material changes even when the vanilla casing type stays the same
    @Unique
    private void refreshCasing(){
        BeltBlockEntity belt = (BeltBlockEntity) (Object) this;
        if(!belt.hasLevel()) return;
        if(belt.getLevel().isClientSide){
            belt.requestModelDataUpdate();
            belt.getLevel().sendBlockUpdated(belt.getBlockPos(), belt.getBlockState(), belt.getBlockState(), 16);
            return;
        }
        belt.setChanged();
        belt.sendData();
    }

    // Restore the native material when a wrench or vanilla casing changes the belt
    @Inject(method = "setCasingType", at = @At("HEAD"))
    private void clearMaterial(BeltBlockEntity.CasingType type, CallbackInfo ci){
        if(casingMaterial == null) return;
        casingMaterial = null;
        if(((BeltBlockEntity) (Object) this).casing == type) refreshCasing();
    }

    @Inject(method = "write", at = @At("TAIL"))
    private void writeMaterial(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket, CallbackInfo ci){
        if(casingMaterial != null) tag.putString(MATERIAL_KEY, casingMaterial.toString());
        else tag.remove(MATERIAL_KEY);
    }

    @Inject(method = "read", at = @At("RETURN"))
    private void readMaterial(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket, CallbackInfo ci){
        ResourceLocation prev = casingMaterial;
        ResourceLocation id = ResourceLocation.tryParse(tag.getString(MATERIAL_KEY));
        casingMaterial = ((BeltBlockEntity) (Object) this).casing == BeltBlockEntity.CasingType.ANDESITE
                && CreateCasingApi.getBeltCasing(id) != null ? id : null;
        if(clientPacket && !Objects.equals(prev, casingMaterial)) refreshCasing();
    }
}
