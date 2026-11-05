package com.rieno.gadgetsandgizmos.lib.power;

import net.neoforged.neoforge.energy.IEnergyStorage;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

// Route exact FE amounts through live stores without duplicating shared members
public final class CombinedEnergyStorage implements LongEnergyStorage{
    private final Supplier<List<LongEnergyStorage>> members;

    public CombinedEnergyStorage(Supplier<List<LongEnergyStorage>> members){ this.members = Objects.requireNonNull(members); }

    // Adapt an ordinary capability while retaining its integer transfer limits
    public static LongEnergyStorage adapt(IEnergyStorage storage){
        return new Adapter(Objects.requireNonNull(storage));
    }

    @Override public long receiveEnergy(long amount, boolean simulate){ return transfer(amount, simulate, true); }
    @Override public long extractEnergy(long amount, boolean simulate){ return transfer(amount, simulate, false); }
    @Override public long getEnergyStored(){ return total(false); }
    @Override public long getMaxEnergyStored(){ return total(true); }
    @Override public boolean canExtract(){ return members.get().stream().anyMatch(LongEnergyStorage::canExtract); }
    @Override public boolean canReceive(){ return members.get().stream().anyMatch(LongEnergyStorage::canReceive); }

    private long transfer(long amount, boolean simulate, boolean receive){
        if(amount <= 0) return 0;
        long moved = 0;
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for(var storage : members.get()){
            if(!seen.add(identity(storage)) || !(receive ? storage.canReceive() : storage.canExtract())) continue;
            long remaining = amount - moved;
            long changed = receive ? storage.receiveEnergy(remaining, simulate) : storage.extractEnergy(remaining, simulate);
            moved += Math.clamp(changed, 0, remaining);
            if(moved == amount) break;
        }
        return moved;
    }

    private long total(boolean capacity){
        long total = 0;
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for(var storage : members.get()){
            if(!seen.add(identity(storage))) continue;
            long amount = Math.max(0, capacity ? storage.getMaxEnergyStored() : storage.getEnergyStored());
            if(amount > Long.MAX_VALUE - total) return Long.MAX_VALUE;
            total += amount;
        }
        return total;
    }

    private static int bounded(long amount){ return (int)Math.clamp(amount, 0, Integer.MAX_VALUE); }
    private static Object identity(LongEnergyStorage storage){ return storage instanceof Adapter adapter ? adapter.storage() : storage; }

    private record Adapter(IEnergyStorage storage) implements LongEnergyStorage{
        @Override public long receiveEnergy(long amount, boolean simulate){ return storage.receiveEnergy(bounded(amount), simulate); }
        @Override public long extractEnergy(long amount, boolean simulate){ return storage.extractEnergy(bounded(amount), simulate); }
        @Override public long getEnergyStored(){ return storage.getEnergyStored(); }
        @Override public long getMaxEnergyStored(){ return storage.getMaxEnergyStored(); }
        @Override public boolean canExtract(){ return storage.canExtract(); }
        @Override public boolean canReceive(){ return storage.canReceive(); }
    }
}
