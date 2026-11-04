package com.rieno.gadgetsandgizmos.lib.create;

import com.rieno.gadgetsandgizmos.lib.inventory.CombinedFluidTank;
import com.rieno.gadgetsandgizmos.lib.inventory.IndependentFluidContainer;
import com.rieno.gadgetsandgizmos.lib.inventory.SharedCapacityFluidTank;
import com.rieno.gadgetsandgizmos.lib.inventory.TransferMeter;
import com.rieno.gadgetsandgizmos.lib.inventory.TransferTrackedStorage;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerContainerAccess;
import com.rieno.gadgetsandgizmos.lib.access.LoadedBlockEntities;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.foundation.fluid.SmartFluidTank;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import java.util.ArrayList;
import java.util.List;

// Keep fluids on physical members while retaining Create's tank shape, windows, boilers and level animation
public abstract class MultiFluidTankBlockEntity extends FluidTankBlockEntity implements IndependentFluidContainer, TransferTrackedStorage{
    private final CombinedFluidTank combined = new CombinedFluidTank(() -> members().stream().map(MultiFluidTankBlockEntity::localTank).toList());
    private List<MultiFluidTankBlockEntity> cachedMembers = List.of();
    private long membersTick = Long.MIN_VALUE;
    private int cachedWidth;
    private int cachedHeight;
    private TransferMeter transferMeter;
    private boolean redistributing;

    // Create the meter lazily because Create constructs the local inventory in its superclass constructor
    private TransferMeter transferMeter(){
        if(transferMeter == null) transferMeter = new TransferMeter(() -> level == null ? 0 : level.getGameTime());
        return transferMeter;
    }

    @Override public TransferMeter.Rates transferRates(){
        var res = TransferMeter.Rates.ZERO;
        for(var member : members()) res = res.plus(member.transferMeter().rates());
        return res;
    }

    protected MultiFluidTankBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state){ super(type, pos, state); }

    protected abstract int capacityPerBlock();

    @Override protected SmartFluidTank createInventory(){
        return new SharedCapacityFluidTank(capacityPerBlock(), this::onFluidStackChanged){
            @Override public FluidTank setCapacity(int capacity){ return super.setCapacity(capacityPerBlock()); }
            @Override protected void onTransfer(int input, int output){ if(!redistributing) transferMeter().record(input, output); }
        };
    }

    public SharedCapacityFluidTank localTank(){ return (SharedCapacityFluidTank)tankInventory; }

    // Apply window models only after the controller's saved members are installed
    public boolean trySetWindows(boolean visible){
        if(level == null || level.isClientSide || !isController() || width < 1 || height < 1) return false;
        for(int y = 0; y < height; y++) for(int x = 0; x < width; x++) for(int z = 0; z < width; z++){
            var member = LoadedBlockEntities.get(level, worldPosition.offset(x, y, z));
            if(!(member instanceof MultiFluidTankBlockEntity tank) || tank.getType() != getType()
                    || !tank.getController().equals(worldPosition)) return false;
        }
        setWindows(visible && !boiler.isActive());
        setChanged();
        sendData();
        return true;
    }

    @Override public MultiFluidTankBlockEntity getControllerBE(){
        if(!isController() && (level == null || !WorkerContainerAccess.isLoaded(level, getController()))) return null;
        var root = super.getControllerBE();
        return root instanceof MultiFluidTankBlockEntity multi ? multi : null;
    }

    public List<MultiFluidTankBlockEntity> members(){
        var root = getControllerBE();
        if(root != null && root != this) return root.members();
        if(level == null || root == null) return List.of(this);
        long tick = level.getGameTime();
        if(membersTick == tick && cachedWidth == width && cachedHeight == height) return cachedMembers;
        List<MultiFluidTankBlockEntity> res = new ArrayList<>();
        for(int y = 0; y < Math.max(1, height); y++) for(int x = 0; x < Math.max(1, width); x++) for(int z = 0; z < Math.max(1, width); z++){
            var pos = worldPosition.offset(x, y, z);
            if(!WorkerContainerAccess.isLoaded(level, pos)) continue;
            if(level.getBlockEntity(pos) instanceof MultiFluidTankBlockEntity member
                    && !member.isRemoved() && member.getController().equals(worldPosition)) res.add(member);
        }
        cachedMembers = res.isEmpty() ? List.of(this) : List.copyOf(res);
        membersTick = tick;
        cachedWidth = width;
        cachedHeight = height;
        return cachedMembers;
    }

    @Override public FluidTank getTankInventory(){
        var root = getControllerBE();
        return root == null ? localTank() : root.combined;
    }

    @Override public float getFillState(){
        var tank = combined == null ? tankInventory : getTankInventory();
        return tank == null || tank.getCapacity() <= 0 ? 0 : Math.min(1F, (float)tank.getFluidAmount() / tank.getCapacity());
    }

    @Override public void applyFluidTankSize(int blocks){
        tankInventory.setCapacity(capacityPerBlock());
        membersTick = Long.MIN_VALUE;
        forceFluidLevelUpdate = true;
    }

    @Override public void notifyMultiUpdated(){
        membersTick = Long.MIN_VALUE;
        super.notifyMultiUpdated();
    }

    @Override protected void onFluidStackChanged(FluidStack stack){
        var root = getControllerBE();
        if(root != null && root != this){
            setChanged();
            sendData();
            root.onFluidStackChanged(root.getTankInventory().getFluid());
        }else super.onFluidStackChanged(stack);
    }

    @Override public void tick(){
        super.tick();
        if(level == null || level.isClientSide || !isController()) return;
        var members = members();
        if(members.stream().noneMatch(member -> member.localTank().getFluidAmount() > member.localTank().getCapacity())) return;
        for(var member : members) member.redistributing = true;
        try{
            redistributeOverflow();
        }finally{
            for(var member : members) member.redistributing = false;
        }
    }

    private void redistributeOverflow(){
        // Legacy controllers stored the entire multiblock in one NBT entry; spread only their overflow.
        for(var member : members()){
            var source = member.localTank();
            int overflow = source.getFluidAmount() - source.getCapacity();
            if(overflow <= 0) continue;
            for(var target : members()){
                if(target == member || target.localTank().getSpace() <= 0) continue;
                for(int idx = source.getTanks() - 1; idx >= 0 && overflow > 0; idx--){
                    var stack = source.getFluidInTank(idx).copyWithAmount(overflow);
                    int accepted = target.localTank().fill(stack, IFluidHandler.FluidAction.SIMULATE);
                    if(accepted <= 0) continue;
                    var drained = source.drain(stack.copyWithAmount(accepted), IFluidHandler.FluidAction.EXECUTE);
                    int moved = target.localTank().fill(drained, IFluidHandler.FluidAction.EXECUTE);
                    if(moved < drained.getAmount()) source.restore(drained.copyWithAmount(drained.getAmount() - moved));
                    overflow -= moved;
                }
            }
        }
    }

    @Override protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket){
        super.read(tag, registries, clientPacket);
        if(!isController()) localTank().readFromNBT(registries, tag.getCompound("TankContent"));
        membersTick = Long.MIN_VALUE;
    }

    @Override public void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket){
        super.write(tag, registries, clientPacket);
        tag.put("TankContent", localTank().writeToNBT(registries, new CompoundTag()));
    }
}
