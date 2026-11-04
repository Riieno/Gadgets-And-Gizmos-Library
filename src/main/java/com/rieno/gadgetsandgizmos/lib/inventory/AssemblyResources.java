package com.rieno.gadgetsandgizmos.lib.inventory;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

// Forward resources into loaded assemblies without a buffer or an extraction path
public final class AssemblyResources implements AssemblyResourceAccess{
    private final Supplier<List<BlockEntity>> blocks;
    private final Predicate<BlockEntity> consumer;
    private final Function<BlockEntity, IFluidHandler> fluid;
    private final Function<BlockEntity, IEnergyStorage> energy;
    private final ToIntFunction<IntSupplier> fillScope;
    private final IFluidHandler fluidInput = new FluidInput();
    private final IEnergyStorage energyInput = new EnergyInput();

    public AssemblyResources(Supplier<List<BlockEntity>> blocks, Predicate<BlockEntity> consumer,
            Function<BlockEntity, IFluidHandler> fluid, Function<BlockEntity, IEnergyStorage> energy,
            ToIntFunction<IntSupplier> fillScope){
        this.blocks = blocks;
        this.consumer = consumer;
        this.fluid = fluid;
        this.energy = energy;
        this.fillScope = fillScope;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    @Override
    public IFluidHandler getAssemblyFluidHandler(){ return fluidInput; }

    @Override
    public IEnergyStorage getAssemblyEnergyHandler(){ return energyInput; }

    // Ignore assembly bridges to prevent recursive capability discovery
    private List<BlockEntity> targets(){
        return blocks.get().stream().filter(be -> be != null && !be.isRemoved()
                && !(be instanceof AssemblyResourceAccess)).toList();
    }

    private List<IFluidHandler> fluids(){
        List<IFluidHandler> res = new ArrayList<>();
        for(BlockEntity be : targets()) res.add(fluid.apply(be));
        return ResourceRouting.distinct(res);
    }

    private List<IEnergyStorage> energy(){
        List<IEnergyStorage> res = new ArrayList<>();
        for(BlockEntity be : targets()){
            IEnergyStorage handler = energy.apply(be);
            if(handler != null && handler.canReceive()) res.add(handler);
        }
        return ResourceRouting.distinct(res);
    }

    // Refill consumers from storage mounted on the same assembly
    public void refillConsumers(){
        List<BlockEntity> targets = targets();
        List<IFluidHandler> sources = new ArrayList<>();
        for(BlockEntity be : targets) if(!consumer.test(be)) sources.add(fluid.apply(be));
        sources = ResourceRouting.distinct(sources);
        for(BlockEntity be : targets){
            if(!consumer.test(be)) continue;
            IFluidHandler input = fluid.apply(be);
            if(input == null) continue;
            for(IFluidHandler source : sources){
                if(source == input) continue;
                for(int idx = 0; idx < source.getTanks(); idx++){
                    FluidStack available = source.getFluidInTank(idx);
                    if(available.isEmpty()) continue;
                    FluidStack offered = source.drain(available.copy(), IFluidHandler.FluidAction.SIMULATE);
                    if(offered.isEmpty()) continue;
                    int accepted = input.fill(offered, IFluidHandler.FluidAction.SIMULATE);
                    if(accepted <= 0) continue;
                    FluidStack drained = source.drain(offered.copyWithAmount(accepted), IFluidHandler.FluidAction.EXECUTE);
                    if(drained.isEmpty()) continue;
                    int delivered = input.fill(drained, IFluidHandler.FluidAction.EXECUTE);
                    if(delivered < drained.getAmount()){
                        FluidStack remainder = drained.copyWithAmount(drained.getAmount() - delivered);
                        fillScope.applyAsInt(() -> source.fill(remainder, IFluidHandler.FluidAction.EXECUTE));
                    }
                    if(delivered > 0) be.setChanged();
                }
            }
        }
    }

    // Plan each receiver's share once so simulation cannot reuse its free space
    private static int[] shares(int[] limits, int amount){
        int[] res = new int[limits.length];
        int remaining = Math.max(0, amount);
        while(remaining > 0){
            int active = 0;
            for(int idx = 0; idx < limits.length; idx++) if(res[idx] < limits[idx]) active++;
            if(active == 0) break;
            int share = (int)(((long)remaining + active - 1L) / active);
            for(int idx = 0; idx < limits.length && remaining > 0; idx++){
                int accepted = Math.min(remaining, Math.min(share, limits[idx] - res[idx]));
                res[idx] += accepted;
                remaining -= accepted;
            }
        }
        return res;
    }

    private static int bounded(long val){ return (int)Math.min(Integer.MAX_VALUE, Math.max(0L, val)); }

    private final class FluidInput implements IFluidHandler{
        @Override
        public int getTanks(){ return 1; }

        @Override
        public FluidStack getFluidInTank(int tank){
            if(tank != 0) return FluidStack.EMPTY;
            FluidStack res = FluidStack.EMPTY;
            for(IFluidHandler handler : fluids()) for(int idx = 0; idx < handler.getTanks(); idx++){
                FluidStack val = handler.getFluidInTank(idx);
                if(val.isEmpty()) continue;
                if(res.isEmpty()) res = val.copy();
                else{
                    if(!FluidStack.isSameFluidSameComponents(res, val)) return FluidStack.EMPTY;
                    res.setAmount(bounded((long)res.getAmount() + val.getAmount()));
                }
            }
            return res;
        }

        @Override
        public int getTankCapacity(int tank){
            if(tank != 0) return 0;
            long total = 0L;
            for(IFluidHandler handler : fluids()) for(int idx = 0; idx < handler.getTanks(); idx++){
                total += handler.getTankCapacity(idx);
            }
            return bounded(total);
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack){
            return tank == 0 && !stack.isEmpty() && fill(stack.copyWithAmount(1), FluidAction.SIMULATE) > 0;
        }

        @Override
        public int fill(FluidStack resource, FluidAction action){
            if(resource.isEmpty()) return 0;
            List<IFluidHandler> targets = fluids();
            int[] limits = new int[targets.size()];
            for(int idx = 0; idx < limits.length; idx++){
                IFluidHandler target = targets.get(idx);
                limits[idx] = Math.max(0, fillScope.applyAsInt(() -> target.fill(resource.copy(), FluidAction.SIMULATE)));
            }
            int[] allocations = shares(limits, resource.getAmount());
            int accepted = 0;
            for(int idx = 0; idx < allocations.length; idx++){
                if(allocations[idx] <= 0) continue;
                IFluidHandler target = targets.get(idx);
                FluidStack offered = resource.copyWithAmount(allocations[idx]);
                accepted += action.simulate() ? allocations[idx]
                        : fillScope.applyAsInt(() -> target.fill(offered, action));
            }
            return accepted;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action){ return FluidStack.EMPTY; }

        @Override
        public FluidStack drain(int amount, FluidAction action){ return FluidStack.EMPTY; }
    }

    private final class EnergyInput implements IEnergyStorage{
        @Override
        public int receiveEnergy(int amount, boolean simulate){
            if(amount <= 0) return 0;
            List<IEnergyStorage> targets = energy();
            int[] limits = new int[targets.size()];
            for(int idx = 0; idx < limits.length; idx++) limits[idx] = Math.max(0, targets.get(idx).receiveEnergy(amount, true));
            int[] allocations = shares(limits, amount);
            int accepted = 0;
            for(int idx = 0; idx < allocations.length; idx++){
                if(allocations[idx] <= 0) continue;
                accepted += simulate ? allocations[idx] : targets.get(idx).receiveEnergy(allocations[idx], false);
            }
            return accepted;
        }

        @Override
        public int extractEnergy(int amount, boolean simulate){ return 0; }

        @Override
        public int getEnergyStored(){
            long total = 0L;
            for(IEnergyStorage target : energy()) total += target.getEnergyStored();
            return bounded(total);
        }

        @Override
        public int getMaxEnergyStored(){
            long total = 0L;
            for(IEnergyStorage target : energy()) total += target.getMaxEnergyStored();
            return bounded(total);
        }

        @Override
        public boolean canExtract(){ return false; }

        @Override
        public boolean canReceive(){ return true; }
    }
}
