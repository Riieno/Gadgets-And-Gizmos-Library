package com.rieno.gadgetsandgizmos.lib.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.rieno.gadgetsandgizmos.lib.physics.SubLevelAttachmentApi;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;

// Carry attachments through assembly, disassembly and native block transfers
@Mixin(value = SubLevelAssemblyHelper.class, remap = false)
public abstract class SubLevelAssemblyAttachmentsMixin{
    // Include attachments before assembly calculates its plot and tracking bounds
    @WrapMethod(method = "assembleBlocks")
    private static ServerSubLevel gadgetsngizmos$assembleAttachments(ServerLevel level, BlockPos anchor,
            Iterable<BlockPos> blocks, BoundingBox3ic bounds, Operation<ServerSubLevel> original){
        var selected = SubLevelAttachmentApi.includeAttachments(level, blocks);
        return original.call(level, anchor, selected, selected.isEmpty() ? bounds : BoundingBox3i.from(selected));
    }

    // Use Sable's transfer while protecting attached support checks
    @WrapMethod(method = "moveBlocks")
    private static void gadgetsngizmos$moveAttachments(ServerLevel level,
            SubLevelAssemblyHelper.AssemblyTransform transform, Iterable<BlockPos> blocks, Operation<Void> original){
        var selected = SubLevelAttachmentApi.includeAttachments(level, blocks);
        SubLevelAttachmentApi.moveBlocks(level, transform, selected, () -> original.call(level, transform, selected));
    }

    // Include face blocks outside the original structure's bounds
    @WrapMethod(method = "moveTrackingPoints")
    private static void gadgetsngizmos$trackAttachments(ServerLevel level, BoundingBox3ic bounds,
            ServerSubLevel destination, SubLevelAssemblyHelper.AssemblyTransform transform, Operation<Void> original){
        original.call(level, SubLevelAttachmentApi.trackingBounds(transform, bounds), destination, transform);
    }
}
