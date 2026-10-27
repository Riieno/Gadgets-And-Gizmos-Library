package com.rieno.gadgetsandgizmos.lib.compat.simulated;

import com.rieno.gadgetsandgizmos.lib.inventory.ResourceRouting;
import dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorBlockEntity;
import dev.simulated_team.simulated.multiloader.inventory.neoforge.ContainerWrapper;
import dev.simulated_team.simulated.multiloader.tanks.SingleTank;
import dev.simulated_team.simulated.multiloader.tanks.neoforge.SingleTankWrapper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

// Expose only the resources physically owned by one connector, without following its peer or wireless views
public final class SimulatedConnectorStorage{
    private static final Map<BlockEntity, WeakReference<ResourceRouting.Pool>> POOLS = new WeakHashMap<>();
    private SimulatedConnectorStorage(){}

    // Implemented by the library mixin so local FE never includes a projected remote balance
    public interface EnergyBuffer{
        int gadgetsngizmos$getBufferEnergy();
        void gadgetsngizmos$setBufferEnergy(int amount);
    }

    // Use for immediate server-thread transfers; actual cross-ship access still requires a physical docking pair
    public static ResourceRouting.Pool localResources(BlockEntity endpoint){
        if(!(endpoint instanceof DockingConnectorBlockEntity connector) || endpoint.isRemoved()
                || endpoint.getLevel() == null || endpoint.getLevel().isClientSide
                || !endpoint.getLevel().isLoaded(endpoint.getBlockPos())) return ResourceRouting.Pool.EMPTY;
        var cached = POOLS.get(endpoint);
        var pool = cached == null ? null : cached.get();
        if(pool != null) return pool;
        var items = new ContainerWrapper<>(connector.inventory){
            @Override public boolean isItemValid(int slot, ItemStack stack){ return slot == 0 && !stack.isEmpty(); }
            @Override public void setStackInSlot(int slot, ItemStack stack){
                super.setStackInSlot(slot, stack);
                changed(connector);
            }
            @Override public ItemStack insertItem(int slot, ItemStack offered, boolean simulate){
                if(!isItemValid(slot, offered)) return offered;
                var current = getStackInSlot(slot);
                if(!current.isEmpty() && !ItemStack.isSameItemSameComponents(current, offered)) return offered;
                int amount = Math.min(offered.getCount(), Math.max(0,
                        Math.min(getSlotLimit(slot), offered.getMaxStackSize()) - current.getCount()));
                if(amount <= 0) return offered;
                if(!simulate) setStackInSlot(slot, offered.copyWithCount(current.getCount() + amount));
                return offered.copyWithCount(offered.getCount() - amount);
            }
            @Override public ItemStack extractItem(int slot, int amount, boolean simulate){
                var extracted = super.extractItem(slot, amount, simulate);
                if(!simulate && !extracted.isEmpty()) changed(connector);
                return extracted;
            }
        };
        var fluids = new IFluidHandler(){
            @Override public int getTanks(){ return 1; }
            @Override public FluidStack getFluidInTank(int tank){
                return tank != 0 || connector.tank.amount <= 0 || connector.tank.type == null || connector.tank.type.isBlank()
                        ? FluidStack.EMPTY : SingleTankWrapper.fromCType(connector.tank.type, (int)Math.min(Integer.MAX_VALUE, connector.tank.amount));
            }
            @Override public int getTankCapacity(int tank){ return tank == 0 ? (int)Math.min(Integer.MAX_VALUE, connector.tank.capacity) : 0; }
            @Override public boolean isFluidValid(int tank, FluidStack stack){ return tank == 0 && !stack.isEmpty(); }
            @Override public int fill(FluidStack stack, FluidAction action){
                if(stack.isEmpty()) return 0;
                var type = SingleTankWrapper.toCType(stack);
                int amount = (int)SingleTank.calculateInsert(connector.tank, type, stack.getAmount());
                if(amount > 0 && action.execute()){
                    SingleTank.applyInsert(connector.tank, type, amount);
                    changed(connector);
                }
                return amount;
            }
            @Override public FluidStack drain(FluidStack stack, FluidAction action){
                if(stack.isEmpty()) return FluidStack.EMPTY;
                int amount = (int)SingleTank.calculateExtract(connector.tank, SingleTankWrapper.toCType(stack), stack.getAmount());
                if(amount > 0 && action.execute()){
                    SingleTank.applyExtract(connector.tank, amount);
                    changed(connector);
                }
                return stack.copyWithAmount(amount);
            }
            @Override public FluidStack drain(int amount, FluidAction action){
                return amount <= 0 ? FluidStack.EMPTY : drain(getFluidInTank(0).copyWithAmount(amount), action);
            }
        };
        var energy = new IEnergyStorage(){
            @Override public int getEnergyStored(){
                return connector.battery instanceof EnergyBuffer buffer ? buffer.gadgetsngizmos$getBufferEnergy() : 0;
            }
            @Override public int getMaxEnergyStored(){ return connector.battery.maxEnergy; }
            @Override public boolean canExtract(){ return connector.battery.getThroughput() > 0; }
            @Override public boolean canReceive(){ return canExtract(); }
            @Override public int receiveEnergy(int amount, boolean simulate){
                return transfer(amount, simulate, true);
            }
            @Override public int extractEnergy(int amount, boolean simulate){
                return transfer(amount, simulate, false);
            }
            private int transfer(int requested, boolean simulate, boolean receive){
                if(!(connector.battery instanceof EnergyBuffer buffer)) return 0;
                int stored = getEnergyStored();
                int amount = Math.max(0, Math.min(requested, Math.min(connector.battery.getThroughput(),
                        receive ? getMaxEnergyStored() - stored : stored)));
                if(amount > 0 && !simulate){
                    buffer.gadgetsngizmos$setBufferEnergy(stored + (receive ? amount : -amount));
                    changed(connector);
                }
                return amount;
            }
        };
        pool = new ResourceRouting.Pool(List.of(items), List.of(fluids), List.of(energy));
        POOLS.put(endpoint, new WeakReference<>(pool));
        return pool;
    }

    private static void changed(DockingConnectorBlockEntity connector){
        connector.setChanged();
        connector.sendData();
    }
}
