package com.rieno.gadgetsandgizmos.lib.create.encasing;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.simpleRelays.encased.EncasedShaftBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.function.Supplier;

// Use Create shaft encasing with a block entity type owned by the consuming mod
public class CustomEncasedShaftBlock extends EncasedShaftBlock{
    private final Supplier<BlockEntityType<KineticBlockEntity>> type;

    public CustomEncasedShaftBlock(Properties props, Supplier<Block> casing, Supplier<BlockEntityType<KineticBlockEntity>> type){
        super(props, casing);
        this.type = type;
    }

    @Override
    public BlockEntityType<? extends KineticBlockEntity> getBlockEntityType(){
        return type.get();
    }
}
