package com.rieno.gadgetsandgizmos.lib.kinetics;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.simibubi.create.content.contraptions.DirectionalExtenderScrollOptionSlot;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

// Expose a propeller direction selector on the sides of a bearing body
public class PropellerDirectionBehaviour extends ScrollOptionBehaviour<PropellerThrustDirection> {
    private static final BehaviourType<PropellerDirectionBehaviour> TYPE = new BehaviourType<>();

    // Owning block's saved direction key
    private final String nbtKey;

    // Initialize the selector with a caller-owned title and saved key
    public PropellerDirectionBehaviour(Component title, SmartBlockEntity blockEntity, String nbtKey){
        super(PropellerThrustDirection.class, title, blockEntity, new PropellerValueBoxTransform());
        if(nbtKey == null || nbtKey.isBlank()) throw new IllegalArgumentException("Propeller direction key is required");
        this.nbtKey = nbtKey;
    }

    // Set the direction through the same saved and synchronized block selector
    public boolean setDirection(@Nullable String val){
        PropellerThrustDirection direction = PropellerThrustDirection.fromControlValue(val);
        if(direction == null) return false;
        setValue(direction.ordinal());
        return true;
    }

    // Keep the selector distinct from other scroll behaviours
    @Override
    public BehaviourType<?> getType(){
        return TYPE;
    }

    // Save the direction for world data and client updates
    @Override
    public void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket){
        tag.putInt(nbtKey, value);
    }

    // Restore the direction and default older bearings to clockwise pull
    @Override
    public void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket){
        value = Mth.clamp(tag.getInt(nbtKey), 0, PropellerThrustDirection.values().length - 1);
    }

    // Keep the direction when copying a bearing into a schematic
    @Override
    public boolean isSafeNBT(){
        return true;
    }

    // Save the direction without assembly state
    @Override
    public void writeSafe(CompoundTag tag, HolderLookup.Provider registries){
        write(tag, registries, false);
    }

    // Match the gyroscopic bearing's projection on all four body faces
    private static class PropellerValueBoxTransform extends DirectionalExtenderScrollOptionSlot {
        // Exclude the head and drive shaft faces
        private PropellerValueBoxTransform(){
            super((state, side) -> side.getAxis() != state.getValue(BlockStateProperties.FACING).getAxis());
        }

        // Move the projection toward the base of the bearing body
        @Override
        public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state){
            return super.getLocalOffset(level, pos, state)
                    .add(Vec3.atLowerCornerOf(state.getValue(BlockStateProperties.FACING).getNormal()).scale(-0.125D));
        }
    }
}
