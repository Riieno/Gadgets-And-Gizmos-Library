package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.client.schematic.ClientSchematicSnapshots;
import com.simibubi.create.content.schematics.client.SchematicAndQuillHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Fetch opted-in block snapshots before Create saves the selected client structure
@Mixin(SchematicAndQuillHandler.class)
public abstract class CreateSchematicAndQuillSnapshotMixin{
    @Shadow public BlockPos firstPos;
    @Shadow public BlockPos secondPos;
    @Shadow public abstract void saveSchematic(String name, boolean convert);

    @Inject(method = "saveSchematic", at = @At("HEAD"), cancellable = true)
    private void gadgetsngizmos$prepareSnapshots(String name, boolean convert, CallbackInfo ci){
        BlockPos first = firstPos;
        BlockPos second = secondPos;
        if(ClientSchematicSnapshots.request(Minecraft.getInstance().level, first, second, () -> {
            firstPos = first;
            secondPos = second;
            saveSchematic(name, convert);
        })) ci.cancel();
    }
}
