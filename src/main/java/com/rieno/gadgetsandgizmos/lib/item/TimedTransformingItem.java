package com.rieno.gadgetsandgizmos.lib.item;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;

import java.util.Objects;
import java.util.function.Supplier;

// Convert an item after a saved interval, regardless of where the stack is held.
public class TimedTransformingItem extends Item{
    private final Supplier<? extends Item> result;
    private final Supplier<DataComponentType<Long>> finishTick;
    private final int durationTicks;

    public TimedTransformingItem(Properties properties, Supplier<? extends Item> result,
            Supplier<DataComponentType<Long>> finishTick, int durationTicks){
        super(properties);
        if(durationTicks <= 0) throw new IllegalArgumentException("Duration must be positive");
        this.result = Objects.requireNonNull(result);
        this.finishTick = Objects.requireNonNull(finishTick);
        this.durationTicks = durationTicks;
    }

    public int durationTicks(){
        return durationTicks;
    }

    // Start once; copies and transfers retain the original completion time.
    public boolean beginTransformation(ItemStack stack, long now){
        DataComponentType<Long> component = finishTick.get();
        if(stack.has(component)) return false;
        stack.set(component, now + durationTicks);
        return true;
    }

    public long remainingTicks(ItemStack stack, long now){
        return Math.max(0L, stack.getOrDefault(finishTick.get(), now + durationTicks) - now);
    }

    // Return the original stack until complete, then preserve its count and other components.
    public ItemStack transform(ItemStack stack, long now){
        beginTransformation(stack, now);
        if(remainingTicks(stack, now) > 0L) return stack;
        return completeTransformation(stack);
    }

    public ItemStack completeTransformation(ItemStack stack){
        ItemStack converted = stack.transmuteCopy(result.get());
        converted.remove(finishTick.get());
        return converted;
    }

    // Consumers opt into immediate completion on contact with selected fluids.
    protected boolean canTransformInFluid(Fluid fluid){
        return false;
    }

    public ItemStack transformInFluid(ItemStack stack, Fluid fluid){
        return canTransformInFluid(fluid) ? completeTransformation(stack) : stack;
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected){
        if(level.isClientSide) return;
        ItemStack converted = TimedItemTransformations.transform(stack, level, entity.getBoundingBox());
        if(converted == stack){
            onWaitingInventoryTick(stack, level, entity, slot, selected);
            return;
        }
        if(entity instanceof Player player){
            for(int idx = 0; idx < player.getInventory().getContainerSize(); idx++){
                if(player.getInventory().getItem(idx) != stack) continue;
                player.getInventory().setItem(idx, converted);
                return;
            }
        }else if(entity instanceof LivingEntity living){
            for(EquipmentSlot equipmentSlot : EquipmentSlot.values()){
                if(living.getItemBySlot(equipmentSlot) != stack) continue;
                living.setItemSlot(equipmentSlot, converted);
                return;
            }
        }
    }

    // Consumers can apply gameplay effects while an inventory stack is waiting.
    protected void onWaitingInventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected){
    }

    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity){
        if(entity.level().isClientSide) return false;
        ItemStack converted = TimedItemTransformations.transform(stack, entity.level(), entity.getBoundingBox());
        if(converted != stack) entity.setItem(converted);
        return false;
    }
}
