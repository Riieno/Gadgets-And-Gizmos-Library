package com.rieno.gadgetsandgizmos.lib.inventory;

import com.rieno.gadgetsandgizmos.lib.worker.WorkerContainerAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.List;

// Keep cached automation capabilities attached to the live, loaded storage network of their entry block
public final class ContainerNetworkAccess implements IItemHandler, IFluidHandler, IEnergyStorage{
    private final BlockEntity entry;
    private final Availability entryAvailability;
    private long tick = Long.MIN_VALUE;
    private long revision = Long.MIN_VALUE;
    private Level routedLevel;
    private BlockPos routedPos;
    private List<Endpoint> endpoints = List.of();
    private List<ItemPort> items = List.of();
    private List<FluidPort> fluids = List.of();
    private ResourceRouting.Pool combined;
    private int slots;
    private int tanks;

    public ContainerNetworkAccess(BlockEntity entry){
        this.entry = entry;
        entryAvailability = new Availability(entry);
    }

    // Resolve physical resources for an immediate server-thread transfer, without re-entering network capabilities
    public static ResourceRouting.Pool pool(BlockEntity entry){
        return pool(entry, target -> true);
    }

    // Select physical storage members while excluding bridges or staging buffers from a transfer destination
    public static ResourceRouting.Pool pool(BlockEntity entry, java.util.function.Predicate<? super BlockEntity> include){
        List<ResourceRouting.Pool> pools = new ArrayList<>();
        for(BlockEntity target : ContainerStorageNetworks.connected(entry)){
            if(include.test(target)) pools.add(ResourceRouting.access(target, null));
        }
        return ResourceRouting.combine(pools);
    }

    private boolean refresh(){
        if(entry == null || entry.getLevel() == null) return false;
        long time = entry.getLevel().getGameTime();
        long version = ContainerStorageNetworks.revision();
        if(!entryAvailability.available(entry.getLevel(), time, version)) return false;
        if(tick == time && revision == version && routedLevel == entry.getLevel()
                && entry.getBlockPos().equals(routedPos)) return true;
        List<Endpoint> next = new ArrayList<>();
        List<ItemPort> nextItems = new ArrayList<>();
        List<FluidPort> nextFluids = new ArrayList<>();
        Set<Object> seenItems = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Object> seenFluids = Collections.newSetFromMap(new IdentityHashMap<>());
        int nextSlots = 0;
        int nextTanks = 0;
        for(BlockEntity target : ContainerStorageNetworks.connected(entry)){
            var pool = ResourceRouting.access(target, null);
            Availability availability = target == entry ? entryAvailability : new Availability(target);
            next.add(new Endpoint(availability, pool));
            for(var handler : pool.items()){
                if(!seenItems.add(handler)) continue;
                int end = (int)Math.min(Integer.MAX_VALUE, (long)nextSlots + handler.getSlots());
                nextItems.add(new ItemPort(availability, handler, nextSlots, end));
                nextSlots = end;
            }
            for(var handler : pool.fluids()){
                if(!seenFluids.add(handler)) continue;
                int end = (int)Math.min(Integer.MAX_VALUE, (long)nextTanks + handler.getTanks());
                nextFluids.add(new FluidPort(availability, handler, nextTanks, end));
                nextTanks = end;
            }
        }
        endpoints = List.copyOf(next);
        items = List.copyOf(nextItems);
        fluids = List.copyOf(nextFluids);
        combined = null;
        slots = nextSlots;
        tanks = nextTanks;
        tick = time;
        revision = version;
        routedLevel = entry.getLevel();
        routedPos = entry.getBlockPos().immutable();
        return true;
    }

    private ResourceRouting.Pool activePool(){
        if(!refresh()) return ResourceRouting.Pool.EMPTY;
        boolean complete = true;
        for(Endpoint endpoint : endpoints){
            if(!endpoint.availability().available(routedLevel, tick, revision)) complete = false;
        }
        if(complete){
            if(combined == null) combined = ResourceRouting.combine(endpoints.stream().map(Endpoint::pool).toList());
            return combined;
        }
        return ResourceRouting.combine(endpoints.stream().filter(endpoint -> endpoint.availability().available(routedLevel, tick, revision))
                .map(Endpoint::pool).toList());
    }

    private ItemPort item(int slot){
        if(slot < 0 || !refresh() || slot >= slots) return null;
        int low = 0, high = items.size() - 1;
        while(low <= high){
            int idx = (low + high) >>> 1;
            ItemPort port = items.get(idx);
            if(slot < port.start()) high = idx - 1;
            else if(slot >= port.end()) low = idx + 1;
            else return port.availability().available(routedLevel, tick, revision) ? port : null;
        }
        return null;
    }

    private FluidPort fluid(int tank){
        if(tank < 0 || !refresh() || tank >= tanks) return null;
        int low = 0, high = fluids.size() - 1;
        while(low <= high){
            int idx = (low + high) >>> 1;
            FluidPort port = fluids.get(idx);
            if(tank < port.start()) high = idx - 1;
            else if(tank >= port.end()) low = idx + 1;
            else return port.availability().available(routedLevel, tick, revision) ? port : null;
        }
        return null;
    }

    @Override public int getSlots(){ return refresh() ? slots : 0; }
    @Override public ItemStack getStackInSlot(int slot){
        var port = item(slot);
        return port == null ? ItemStack.EMPTY : port.handler().getStackInSlot(slot - port.start());
    }
    @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate){
        var port = item(slot);
        return port == null ? stack : port.handler().insertItem(slot - port.start(), stack, simulate);
    }
    @Override public ItemStack extractItem(int slot, int amount, boolean simulate){
        var port = item(slot);
        return port == null ? ItemStack.EMPTY : port.handler().extractItem(slot - port.start(), amount, simulate);
    }
    @Override public int getSlotLimit(int slot){
        var port = item(slot);
        return port == null ? 0 : port.handler().getSlotLimit(slot - port.start());
    }
    @Override public boolean isItemValid(int slot, ItemStack stack){
        var port = item(slot);
        return port != null && port.handler().isItemValid(slot - port.start(), stack);
    }

    private boolean allowsExtraction(FluidStack stack){
        return !(entry instanceof FluidExtractionPolicy policy) || policy.allowsFluidExtraction(stack);
    }

    @Override public int getTanks(){ return refresh() ? tanks : 0; }
    @Override public FluidStack getFluidInTank(int tank){
        var port = fluid(tank);
        if(port == null) return FluidStack.EMPTY;
        var stack = port.handler().getFluidInTank(tank - port.start());
        return allowsExtraction(stack) ? stack : FluidStack.EMPTY;
    }
    @Override public int getTankCapacity(int tank){
        var port = fluid(tank);
        return port == null ? 0 : port.handler().getTankCapacity(tank - port.start());
    }
    @Override public boolean isFluidValid(int tank, FluidStack stack){
        var port = fluid(tank);
        return port != null && port.handler().isFluidValid(tank - port.start(), stack);
    }
    @Override public int fill(FluidStack stack, FluidAction action){
        return ResourceRouting.insertFluid(activePool(), stack, action.simulate());
    }
    @Override public FluidStack drain(FluidStack stack, FluidAction action){
        if(stack.isEmpty()) return FluidStack.EMPTY;
        return ResourceRouting.extractFluid(activePool(), available -> allowsExtraction(available) && FluidStack.isSameFluidSameComponents(stack, available),
                stack.getAmount(), action.simulate());
    }
    @Override public FluidStack drain(int amount, FluidAction action){
        return ResourceRouting.extractFluid(activePool(), this::allowsExtraction, amount, action.simulate());
    }

    @Override public int receiveEnergy(int amount, boolean simulate){ return ResourceRouting.insertEnergy(activePool(), amount, simulate); }
    @Override public int extractEnergy(int amount, boolean simulate){ return ResourceRouting.extractEnergy(activePool(), amount, simulate); }
    @Override public int getEnergyStored(){ return energy(false); }
    @Override public int getMaxEnergyStored(){ return energy(true); }
    @Override public boolean canExtract(){ return activePool().energy().stream().anyMatch(IEnergyStorage::canExtract); }
    @Override public boolean canReceive(){ return activePool().energy().stream().anyMatch(IEnergyStorage::canReceive); }

    private int energy(boolean capacity){
        long total = 0L;
        for(var handler : activePool().energy()){
            total += Math.max(0, capacity ? handler.getMaxEnergyStored() : handler.getEnergyStored());
            if(total >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
        }
        return (int)total;
    }

    // Reuse positive load checks while removal, movement and chunk unloads invalidate the snapshot
    private static final class Availability{
        private final BlockEntity owner;
        private Level level;
        private BlockPos pos;
        private long tick = Long.MIN_VALUE;
        private long revision = Long.MIN_VALUE;

        private Availability(BlockEntity owner){ this.owner = owner; }

        private boolean available(Level snapshotLevel, long snapshotTick, long snapshotRevision){
            Level current = owner == null ? null : owner.getLevel();
            if(current == null || current.isClientSide || owner.isRemoved()){
                level = null;
                return false;
            }
            BlockPos position = owner.getBlockPos();
            long time = current == snapshotLevel ? snapshotTick : current.getGameTime();
            long version = snapshotRevision;
            if(level == current && tick == time && revision == version && position.equals(pos)) return true;
            level = null;
            if(!WorkerContainerAccess.isLoaded(current, position)) return false;
            level = current;
            pos = position.immutable();
            tick = time;
            revision = version;
            return true;
        }
    }

    private record Endpoint(Availability availability, ResourceRouting.Pool pool){}
    private record ItemPort(Availability availability, IItemHandler handler, int start, int end){}
    private record FluidPort(Availability availability, IFluidHandler handler, int start, int end){}
}
