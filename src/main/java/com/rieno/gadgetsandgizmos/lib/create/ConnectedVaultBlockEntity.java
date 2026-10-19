package com.rieno.gadgetsandgizmos.lib.create;

import com.simibubi.create.content.logistics.vault.ItemVaultBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

// Recover custom vault connectivity after placement, transfer and chunk loading
public class ConnectedVaultBlockEntity extends ItemVaultBlockEntity{
    public ConnectedVaultBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state){
        super(type, pos, state);
        setLazyTickRate(20);
        updateConnectivity = true;
    }

    @Override
    public void initialize(){
        super.initialize();
        queueNearbyControllers();
    }

    @Override
    public void lazyTick(){
        super.lazyTick();
        if(level == null || level.isClientSide) return;
        if(!isController() && level.isLoaded(getController())){
            var root = level.getBlockEntity(getController());
            if(root == null || root.getType() != getType()) removeController(true);
        }
        if(isController() && getWidth() == 1 && getHeight() == 1) queueNearbyControllers();
    }

    // Wait for the saved geometry's chunks before rebuilding a controller
    @Override
    protected void updateConnectivity(){
        if(level == null || level.isClientSide) return;
        if(isController()){
            BlockPos opposite = getMainConnectionAxis() == Direction.Axis.X
                    ? worldPosition.offset(getHeight() - 1, getWidth() - 1, getWidth() - 1)
                    : worldPosition.offset(getWidth() - 1, getWidth() - 1, getHeight() - 1);
            if(!level.hasChunksAt(worldPosition, opposite)) return;
        }
        super.updateConnectivity();
    }

    // Defer formation until the placed or loaded block entities are available
    private void queueNearbyControllers(){
        if(level == null || level.isClientSide) return;
        queueController(worldPosition);
        for(Direction face : Direction.values()) queueController(worldPosition.relative(face));
    }

    private void queueController(BlockPos pos){
        if(!level.isLoaded(pos)) return;
        if(!(level.getBlockEntity(pos) instanceof ConnectedVaultBlockEntity member)
                || member.getType() != getType() || member.getMainConnectionAxis() != getMainConnectionAxis()
                || !level.isLoaded(member.getController())) return;
        if(member.getControllerBE() instanceof ConnectedVaultBlockEntity root && root.getType() == getType()){
            root.updateConnectivity = true;
        }
    }
}
