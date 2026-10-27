package com.rieno.gadgetsandgizmos.lib.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;

import java.lang.reflect.Constructor;

// Defer optional interfaces and their implementing classes until the owning mod is available
public final class OptionalBlockEntityFactory{
    private OptionalBlockEntityFactory(){}

    public static <T extends BlockEntity> BlockEntityType.BlockEntitySupplier<T> optional(String modId, String implementation,
            Class<T> base, BlockEntityType.BlockEntitySupplier<T> fallback){
        return new BlockEntityType.BlockEntitySupplier<>(){
            private Constructor<? extends T> constructor;

            @Override public T create(BlockPos pos, BlockState state){
                if(!ModList.get().isLoaded(modId)) return fallback.create(pos, state);
                try{
                    if(constructor == null) constructor = Class.forName(implementation, true, base.getClassLoader())
                            .asSubclass(base).getConstructor(BlockPos.class, BlockState.class);
                    return constructor.newInstance(pos, state);
                }catch(ReflectiveOperationException err){
                    throw new IllegalStateException("Cannot create optional block entity " + implementation, err);
                }
            }
        };
    }
}
