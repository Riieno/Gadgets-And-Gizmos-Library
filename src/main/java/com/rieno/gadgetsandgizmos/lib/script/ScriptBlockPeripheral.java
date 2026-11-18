package com.rieno.gadgetsandgizmos.lib.script;

import net.minecraft.world.level.block.entity.BlockEntity;
import java.util.Objects;

// Share block identity and liveness without importing an optional peripheral implementation
public abstract class ScriptBlockPeripheral<T extends BlockEntity> implements ScriptPeripheralBinding{
    protected final T blockEntity;
    private final String type;
    protected ScriptBlockPeripheral(T blockEntity, String type){ this.blockEntity = Objects.requireNonNull(blockEntity); this.type = Objects.requireNonNull(type); }
    @Override public final String getType(){ return type; }
    @Override public Object getTarget(){ return blockEntity; }
    @Override public final boolean equals(ScriptPeripheralBinding other){
        return other != null && other.getClass() == getClass() && ((ScriptBlockPeripheral<?>)other).blockEntity == blockEntity && other.getType().equals(type);
    }
    protected final T requireTarget(){
        if(blockEntity.isRemoved() || blockEntity.getLevel() == null) throw new ScriptException("peripheral target is no longer available");
        return blockEntity;
    }
}
