package com.rieno.gadgetsandgizmos.lib.power.powergrid;

import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.patryk3211.powergrid.collections.ModdedDataComponents;
import org.patryk3211.powergrid.equipment.portablebattery.BatteryUtils;
import org.patryk3211.powergrid.equipment.portablebattery.PortableBatteryItem;

// Adapt Power Grid's native portable charge component without requiring the optional mod on other hosts
public final class PowerGridPortableBattery{
    private PowerGridPortableBattery(){}

    public static ItemStack item(){
        return PowerGridUnits.available() ? Native.item() : ItemStack.EMPTY;
    }

    // Portable charge is the same FE amount consumed by Power Grid's equipment charging API
    public static IEnergyStorage storage(ItemStack stack){
        return stack.isEmpty() || !PowerGridUnits.available() ? null : Native.storage(stack);
    }

    private static final class Native{
        private static ItemStack item(){
            return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("powergrid", "portable_battery")));
        }
        private static IEnergyStorage storage(ItemStack stack){
            if(!(stack.getItem() instanceof PortableBatteryItem)) return null;
            return new IEnergyStorage(){
                @Override public int receiveEnergy(int maximum, boolean simulate){
                    int stored = getEnergyStored();
                    int received = Math.min(Math.max(0, maximum), getMaxEnergyStored() - stored);
                    if(!simulate && received > 0) stack.set(ModdedDataComponents.PORTABLE_BATTERY_CHARGE.get(), stored + received);
                    return received;
                }
                @Override public int extractEnergy(int maximum, boolean simulate){
                    int stored = getEnergyStored();
                    int extracted = Math.min(Math.max(0, maximum), stored);
                    if(!simulate && extracted > 0) stack.set(ModdedDataComponents.PORTABLE_BATTERY_CHARGE.get(), stored - extracted);
                    return extracted;
                }
                @Override public int getEnergyStored(){ return Math.clamp(BatteryUtils.getCurrentCharge(stack), 0, getMaxEnergyStored()); }
                @Override public int getMaxEnergyStored(){ return Math.max(0, BatteryUtils.getMaxCharge(stack)); }
                @Override public boolean canExtract(){ return true; }
                @Override public boolean canReceive(){ return true; }
            };
        }
    }
}
