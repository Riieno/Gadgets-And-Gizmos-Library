package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.client.create.BeltCasingModels;
import com.simibubi.create.content.kinetics.belt.BeltModel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

// Render registered belt casing textures and matching break particles
@Mixin(BeltModel.class)
public abstract class BeltCasingModelMixin{
    @Inject(method = "getQuads", at = @At("RETURN"), cancellable = true)
    private void replaceCasing(BlockState state, Direction side, RandomSource rand, ModelData data, RenderType renderType,
                               CallbackInfoReturnable<List<BakedQuad>> cir){
        if(data.has(BeltCasingModels.MATERIAL_PROPERTY)) cir.setReturnValue(BeltCasingModels.replaceQuads(cir.getReturnValue(), data));
    }

    @Inject(method = "getParticleIcon", at = @At("HEAD"), cancellable = true)
    private void casingParticles(ModelData data, CallbackInfoReturnable<TextureAtlasSprite> cir){
        TextureAtlasSprite sprite = BeltCasingModels.getSprite(data);
        if(sprite != null) cir.setReturnValue(sprite);
    }
}
