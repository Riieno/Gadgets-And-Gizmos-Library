package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelActorLookup;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

// Use Sable's live actor map for exact block-entity lookups
@Mixin(LevelPlot.class)
public abstract class SableLevelPlotActorLookupMixin implements SubLevelActorLookup{
    @Shadow(remap = false) @Final
    protected Object2ObjectOpenHashMap<BlockPos, BlockEntitySubLevelActor> blockEntityActors;

    @Override
    public @Nullable BlockEntity gadgetsngizmos$actorBlockEntity(BlockPos pos){
        Object actor = blockEntityActors.get(pos);
        return actor instanceof BlockEntity blockEntity ? blockEntity : null;
    }
}
