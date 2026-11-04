package com.rieno.gadgetsandgizmos.lib.create;

import com.simibubi.create.content.logistics.vault.ItemVaultBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import com.rieno.gadgetsandgizmos.lib.access.LoadedBlockEntities;

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

    // A saved controller may arrive after its members; never create an empty replacement
    @Override
    public ItemVaultBlockEntity getControllerBE(){
        return isController() ? this : getLoadedMember(getController());
    }

    // Read only block entities already installed in a loaded chunk
    protected ConnectedVaultBlockEntity getLoadedMember(BlockPos pos){
        var entity = LoadedBlockEntities.get(level, pos);
        return entity instanceof ConnectedVaultBlockEntity member && member.getType() == getType() ? member : null;
    }

    @Override
    public void lazyTick(){
        super.lazyTick();
        if(level == null || level.isClientSide) return;
        if(!isController() && level.isLoaded(getController()) && getLoadedMember(getController()) == null){
            if(!level.getBlockState(getController()).is(getBlockState().getBlock())) removeController(true);
        }
        if(isController() && getWidth() == 1 && getHeight() == 1) queueNearbyControllers();
    }

    // Wait for saved block entities as well as chunks before rebuilding a controller
    @Override
    protected void updateConnectivity(){
        if(level == null || level.isClientSide) return;
        if(isController()){
            BlockPos opposite = getMainConnectionAxis() == Direction.Axis.X
                    ? worldPosition.offset(Math.max(1, getHeight()) - 1, Math.max(1, getWidth()) - 1, Math.max(1, getWidth()) - 1)
                    : worldPosition.offset(Math.max(1, getWidth()) - 1, Math.max(1, getWidth()) - 1, Math.max(1, getHeight()) - 1);
            if(!level.hasChunksAt(worldPosition, opposite)) return;
            for(BlockPos pos : BlockPos.betweenClosed(worldPosition, opposite)){
                if(!memberAvailable(pos)) return;
                for(Direction face : Direction.values()) if(!memberAvailable(pos.relative(face))) return;
            }
        }
        super.updateConnectivity();
    }

    private boolean memberAvailable(BlockPos pos){
        return !level.hasChunkAt(pos) || !level.getBlockState(pos).is(getBlockState().getBlock()) || getLoadedMember(pos) != null;
    }

    // Defer formation until the placed or loaded block entities are available
    private void queueNearbyControllers(){
        if(level == null || level.isClientSide) return;
        queueController(worldPosition);
        for(Direction face : Direction.values()) queueController(worldPosition.relative(face));
    }

    private void queueController(BlockPos pos){
        if(!level.isLoaded(pos)) return;
        var member = getLoadedMember(pos);
        if(member == null || member.getMainConnectionAxis() != getMainConnectionAxis()
                || !level.isLoaded(member.getController())) return;
        if(member.getControllerBE() instanceof ConnectedVaultBlockEntity root && root.getType() == getType()){
            root.updateConnectivity = true;
        }
    }
}
