package com.rieno.gadgetsandgizmos.lib.physics.archive;

import com.simibubi.create.foundation.utility.BlockHelper;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.rieno.gadgetsandgizmos.lib.inventory.ItemStackNbtSanitizer;
import dev.ryanhcode.sable.api.schematic.SubLevelSchematicSerializationContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

// Adapt both vanilla structure NBT and Create's safe configuration without requiring an id tag
final class SchematicBlockData{
    private SchematicBlockData(){}

    // Create a detached block entity for requirement and safe NBT callbacks
    static BlockEntity create(ServerLevel level, BlockPos pos, BlockState state, CompoundTag data){
        if(!(state.getBlock() instanceof EntityBlock block)) return null;
        BlockEntity be = block.newBlockEntity(pos, state);
        if(be == null) return null;
        // Mark detached Create entities before loading configuration so callbacks can skip world changes
        if(be instanceof SmartBlockEntity smart) smart.markVirtual();
        be.setLevel(level);
        var prev = SubLevelSchematicSerializationContext.getCurrentContext();
        try{
            SubLevelSchematicSerializationContext.setCurrentContext(null);
            be.loadWithComponents(ItemStackNbtSanitizer.withoutUnavailableItems(data), level.registryAccess());
        }finally{ SubLevelSchematicSerializationContext.setCurrentContext(prev); }
        return be;
    }

    // Retain only configuration approved by Create's safe NBT callbacks
    static CompoundTag safe(ServerLevel level, BlockState state, BlockEntity be){
        CompoundTag data = BlockHelper.prepareBlockEntityData(level, state, be);
        if(data == null) return new CompoundTag();
        data.remove("x"); data.remove("y"); data.remove("z");
        if(be != null) data.putString("id", BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType()).toString());
        return data;
    }
}
