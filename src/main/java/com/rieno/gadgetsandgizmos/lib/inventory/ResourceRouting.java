package com.rieno.gadgetsandgizmos.lib.inventory;

import net.minecraft.world.item.ItemStack;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

// Route bounded amounts through real capabilities without treating a shared handler as extra storage
public final class ResourceRouting{
    private ResourceRouting(){}

    public enum Comparison{ GREATER, LESS, EQUAL }

    // Return a signed transfer amount which reaches a strict or exact cargo threshold
    public static long demand(Comparison comparison, long amount, long threshold){
        amount = Math.max(0, amount);
        threshold = Math.max(0, threshold);
        return switch(comparison){
            case GREATER -> amount > threshold ? 0L : (threshold == Long.MAX_VALUE ? threshold : threshold + 1L) - amount;
            case LESS -> amount < threshold ? 0L : Math.max(0L, threshold - 1L) - amount;
            case EQUAL -> threshold - amount;
        };
    }

    public record Pool(List<IItemHandler> items, List<IFluidHandler> fluids, List<IEnergyStorage> energy){
        public static final Pool EMPTY = new Pool(List.of(), List.of(), List.of());
        public Pool{ items = distinct(items); fluids = distinct(fluids); energy = distinct(energy); }
        public boolean isEmpty(){ return items.isEmpty() && fluids.isEmpty() && energy.isEmpty(); }
    }

    public static Pool combine(List<Pool> pools){
        if(pools.isEmpty()) return Pool.EMPTY;
        if(pools.size() == 1) return java.util.Objects.requireNonNull(pools.getFirst());
        List<IItemHandler> items = new ArrayList<>();
        List<IFluidHandler> fluids = new ArrayList<>();
        List<IEnergyStorage> energy = new ArrayList<>();
        for(Pool pool : pools){ items.addAll(pool.items()); fluids.addAll(pool.fluids()); energy.addAll(pool.energy()); }
        return new Pool(items, fluids, energy);
    }

    // Resolve loaded endpoints without forcing a chunk or sublevel to load
    public static Pool access(BlockEntity be, Direction face){
        if(be == null || be.isRemoved() || be.getLevel() == null || !be.getLevel().isLoaded(be.getBlockPos())) return Pool.EMPTY;
        if(be instanceof LocalResourceStorage storage) return storage.localResources();
        return new Pool(capabilities(be, face, Capabilities.ItemHandler.BLOCK),
                capabilities(be, face, Capabilities.FluidHandler.BLOCK),
                capabilities(be, face, Capabilities.EnergyStorage.BLOCK));
    }

    // Furnaces accept fuel horizontally; their unsided inventory also exposes the recipe input
    public static Pool consumerAccess(BlockEntity be, Direction face){
        if(face == null && be instanceof net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity) face = Direction.NORTH;
        return access(be, face);
    }

    private static <T> List<T> capabilities(BlockEntity be, Direction face, BlockCapability<T, Direction> capability){
        T direct = be.getLevel().getCapability(capability, be.getBlockPos(), be.getBlockState(), be, face);
        if(direct != null) return List.of(direct);
        if(face != null) return List.of();
        List<T> res = new ArrayList<>();
        for(Direction side : Direction.values()){
            T handler = be.getLevel().getCapability(capability, be.getBlockPos(), be.getBlockState(), be, side);
            if(handler != null) res.add(handler);
        }
        return distinct(res);
    }

    public static <T> List<T> distinct(List<T> handlers){
        if(handlers.isEmpty()) return List.of();
        if(handlers.size() == 1){
            T handler = handlers.getFirst();
            return handler == null ? List.of() : List.of(handler);
        }
        Set<T> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        List<T> res = new ArrayList<>();
        for(T handler : handlers) if(handler != null && seen.add(handler)) res.add(handler);
        return List.copyOf(res);
    }

    public static ItemStack insertItem(Pool target, ItemStack offered, boolean simulate){
        ItemStack remaining = offered.copy();
        for(IItemHandler handler : target.items()){
            remaining = ItemHandlerHelper.insertItemStacked(handler, remaining, simulate);
            if(remaining.isEmpty()) break;
        }
        return remaining;
    }

    public static ItemStack extractItem(Pool source, Predicate<ItemStack> filter, int limit, boolean simulate){
        ItemStack res = ItemStack.EMPTY;
        for(IItemHandler handler : source.items()){
            for(int slot = 0; slot < handler.getSlots() && res.getCount() < limit; slot++){
                int remaining = (res.isEmpty() ? limit : Math.min(limit, res.getMaxStackSize())) - res.getCount();
                if(remaining <= 0) break;
                ItemStack available = handler.extractItem(slot, remaining, true);
                if(available.isEmpty() || !filter.test(available)
                        || !res.isEmpty() && !ItemStack.isSameItemSameComponents(res, available)) continue;
                ItemStack extracted = handler.extractItem(slot, Math.min(remaining, available.getMaxStackSize()), simulate);
                if(res.isEmpty()) res = extracted.copy();
                else res.grow(extracted.getCount());
            }
        }
        return res;
    }

    public static int moveItems(Pool source, Pool target, Predicate<ItemStack> filter, int limit){
        int moved = 0;
        for(IItemHandler output : source.items()) for(IItemHandler input : target.items()){
            if(source.items().contains(input)) continue;
            moved += ItemTransfers.move(output, input, filter, limit - moved);
            if(moved >= limit) return moved;
        }
        return moved;
    }

    public static int insertFluid(Pool target, FluidStack offered, boolean simulate){
        if(offered.isEmpty()) return 0;
        int accepted = 0;
        for(IFluidHandler handler : target.fluids()){
            FluidStack remaining = offered.copyWithAmount(offered.getAmount() - accepted);
            accepted += handler.fill(remaining, simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE);
            if(accepted >= offered.getAmount()) break;
        }
        return accepted;
    }

    public static FluidStack extractFluid(Pool source, Predicate<FluidStack> filter, int limit, boolean simulate){
        FluidStack res = FluidStack.EMPTY;
        for(IFluidHandler handler : source.fluids()){
            for(int tank = 0; tank < handler.getTanks() && res.getAmount() < limit; tank++){
                FluidStack available = handler.getFluidInTank(tank);
                if(available.isEmpty() || !filter.test(available)
                        || !res.isEmpty() && !FluidStack.isSameFluidSameComponents(res, available)) continue;
                FluidStack drained = handler.drain(available.copyWithAmount(limit - res.getAmount()),
                        simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE);
                if(res.isEmpty()) res = drained.copy();
                else res.grow(drained.getAmount());
                if(!drained.isEmpty()) break;
            }
        }
        return res;
    }

    public static int moveFluids(Pool source, Pool target, Predicate<FluidStack> filter, int limit){
        target = new Pool(List.of(), target.fluids().stream().filter(input -> !source.fluids().contains(input)).toList(), List.of());
        int moved = 0;
        for(IFluidHandler output : source.fluids()){
            if(target.fluids().contains(output)) continue;
            for(int tank = 0; tank < output.getTanks() && moved < limit; tank++){
                FluidStack available = output.getFluidInTank(tank);
                if(available.isEmpty() || !filter.test(available)) continue;
                FluidStack offer = output.drain(available.copyWithAmount(limit - moved), IFluidHandler.FluidAction.SIMULATE);
                int accepted = insertFluid(target, offer, true);
                if(accepted == 0) continue;
                FluidStack drained = output.drain(offer.copyWithAmount(accepted), IFluidHandler.FluidAction.EXECUTE);
                int delivered = insertFluid(target, drained, false);
                moved += delivered;
                if(delivered < drained.getAmount()) output.fill(drained.copyWithAmount(drained.getAmount() - delivered), IFluidHandler.FluidAction.EXECUTE);
            }
        }
        return moved;
    }

    public static int insertEnergy(Pool target, int amount, boolean simulate){
        int moved = 0;
        for(IEnergyStorage handler : target.energy()){
            moved += handler.receiveEnergy(Math.max(0, amount - moved), simulate);
            if(moved >= amount) break;
        }
        return moved;
    }

    public static int extractEnergy(Pool source, int amount, boolean simulate){
        int moved = 0;
        for(IEnergyStorage handler : source.energy()){
            moved += handler.extractEnergy(Math.max(0, amount - moved), simulate);
            if(moved >= amount) break;
        }
        return moved;
    }

    public static int moveEnergy(Pool source, Pool target, int limit){
        target = new Pool(List.of(), List.of(), target.energy().stream().filter(input -> !source.energy().contains(input)).toList());
        int moved = 0;
        for(IEnergyStorage output : source.energy()){
            if(target.energy().contains(output)) continue;
            int offer = output.extractEnergy(Math.max(0, limit - moved), true);
            int accepted = insertEnergy(target, offer, true);
            int extracted = output.extractEnergy(accepted, false);
            int delivered = insertEnergy(target, extracted, false);
            moved += delivered;
            if(delivered < extracted) output.receiveEnergy(extracted - delivered, false);
            if(moved >= limit) break;
        }
        return moved;
    }
}
