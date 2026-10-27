package com.rieno.gadgetsandgizmos.lib.create;

import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.kinetics.steamEngine.SteamEngineBlockEntity;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;

// Resolve a steam engine's boiler and burners as the resources which actually power it
public final class CreateFuelConsumers{
    private CreateFuelConsumers(){}

    // Follow Create's data maps and furnace fallback when valuing stored burner fuel
    public static int burnTicks(ItemStack stack){
        if(stack.isEmpty()) return 0;
        var holder = stack.getItem().builtInRegistryHolder();
        var special = holder.getData(com.simibubi.create.api.registry.CreateDataMaps.SUPERHEATED_BLAZE_BURNER_FUELS);
        if(special != null) return special.burnTime();
        var regular = holder.getData(com.simibubi.create.api.registry.CreateDataMaps.REGULAR_BLAZE_BURNER_FUELS);
        if(regular != null) return regular.burnTime();
        if(com.simibubi.create.AllTags.AllItemTags.BLAZE_BURNER_FUEL_SPECIAL.matches(stack)) return 3200;
        int vanilla = stack.getBurnTime(net.minecraft.world.item.crafting.RecipeType.SMELTING);
        if(vanilla > 0) return vanilla;
        return com.simibubi.create.AllTags.AllItemTags.BLAZE_BURNER_FUEL_REGULAR.matches(stack) ? 1600 : 0;
    }

    public static List<BlockEntity> expand(BlockEntity selected){
        if(!(selected instanceof SteamEngineBlockEntity engine)) return List.of(selected);
        FluidTankBlockEntity tank = engine.getTank();
        if(tank == null || tank.getLevel() == null) return List.of();
        FluidTankBlockEntity root = tank.getControllerBE();
        if(root == null) return List.of();
        List<BlockEntity> res = new ArrayList<>();
        res.add(root);
        int width = Math.min(9, root.getWidth());
        for(int x = 0; x < width; x++) for(int z = 0; z < width; z++){
            BlockPos pos = root.getBlockPos().offset(x, -1, z);
            if(!root.getLevel().isLoaded(pos)) continue;
            BlockEntity burner = root.getLevel().getBlockEntity(pos);
            if(burner instanceof BlazeBurnerBlockEntity) res.add(burner);
        }
        return List.copyOf(res);
    }

    // Feed burners using Create's own validation, overflow and simulation rules
    public static IItemHandler burnerInput(BlazeBurnerBlockEntity burner){
        return new IItemHandler(){
            @Override public int getSlots(){ return 1; }
            @Override public ItemStack getStackInSlot(int slot){ return ItemStack.EMPTY; }
            @Override public int getSlotLimit(int slot){ return 1; }
            @Override public boolean isItemValid(int slot, ItemStack stack){
                return insertItem(slot, stack.copyWithCount(1), true).isEmpty();
            }
            @Override public ItemStack extractItem(int slot, int amount, boolean simulate){ return ItemStack.EMPTY; }
            @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate){
                if(slot != 0 || stack.isEmpty() || burner.isRemoved() || burner.isCreative()
                        || burner.getLevel() == null || burner.getLevel().isClientSide) return stack;
                var res = BlazeBurnerBlock.tryInsert(burner.getBlockState(), burner.getLevel(),
                        burner.getBlockPos(), stack.copyWithCount(1), false, false, simulate);
                if(!res.getResult().consumesAction()) return stack;
                ItemStack remaining = stack.copy();
                remaining.shrink(1);
                return remaining;
            }
        };
    }
}
