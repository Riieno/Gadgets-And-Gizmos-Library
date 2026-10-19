package com.rieno.gadgetsandgizmos.lib.create.encasing;

import com.simibubi.create.content.kinetics.simpleRelays.SimpleKineticBlockEntity;
import com.simibubi.create.content.kinetics.simpleRelays.encased.EncasedCogwheelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.function.Supplier;

// Use Create cog encasing with a block entity type owned by the consuming mod
public class CustomEncasedCogwheelBlock extends EncasedCogwheelBlock{
    private final Supplier<BlockEntityType<SimpleKineticBlockEntity>> type;

    public CustomEncasedCogwheelBlock(Properties props, boolean large, Supplier<Block> casing,
                                     Supplier<BlockEntityType<SimpleKineticBlockEntity>> type){
        super(props, large, casing);
        this.type = type;
    }

    @Override
    public BlockEntityType<? extends SimpleKineticBlockEntity> getBlockEntityType(){
        return type.get();
    }
}
