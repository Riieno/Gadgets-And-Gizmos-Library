package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.physics.CollisionBlockIndex;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.network.FriendlyByteBuf;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Invalidate occupancy for every block edit, including native plot updates
@Mixin(LevelChunkSection.class)
public abstract class CollisionBlockRevisionMixin implements CollisionBlockIndex.Revision{
    @Unique private long gadgetsngizmos$collisionRevision;
    @Override public long collisionBlockRevision(){ return gadgetsngizmos$collisionRevision; }

    @Inject(method = "read", at = @At("RETURN"))
    private void gadgetsngizmos$sectionReplaced(FriendlyByteBuf buffer, CallbackInfo ci){
        gadgetsngizmos$collisionRevision++;
    }

    @Inject(method = "setBlockState(IIILnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;", at = @At("RETURN"))
    private void gadgetsngizmos$blockChanged(int x, int y, int z, BlockState state, boolean lock,
            CallbackInfoReturnable<BlockState> ci){
        if(ci.getReturnValue() != state){
            gadgetsngizmos$collisionRevision++;
        }
    }
}
