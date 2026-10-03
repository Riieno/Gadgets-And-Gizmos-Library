package com.rieno.gadgetsandgizmos.lib.worker;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.UUID;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.function.Predicate;
import java.util.function.Supplier;

// Expose carried working stock without confusing it with cargo reserved by another operation
public class WorkerInventoryEndpoint implements WorkerEndpoint{
    private final UUID id;
    private final Supplier<BlockPos> position;
    private final IItemHandler inventory;
    private final HolderLookup.Provider registries;
    private final Predicate<ItemStack> usable;
    private final boolean insertion;
    private final Predicate<ItemStack> extraction;

    // Keep physical inventory ownership with the consumer
    public WorkerInventoryEndpoint(UUID id, Supplier<BlockPos> position, IItemHandler inventory,
                                    HolderLookup.Provider registries, Predicate<ItemStack> usable, boolean insertion){
        this(id, position, inventory, registries, usable, insertion, stack -> true);
    }

    // Restrict staged inputs without preventing completed outputs from stacking in working storage
    public WorkerInventoryEndpoint(UUID id, Supplier<BlockPos> position, IItemHandler inventory,
                                    HolderLookup.Provider registries, Predicate<ItemStack> usable, boolean insertion,
                                    Predicate<ItemStack> extraction){
        this.id = id;
        this.position = position;
        this.inventory = inventory;
        this.registries = registries;
        this.usable = usable;
        this.insertion = insertion;
        this.extraction = extraction;
    }

    @Override public UUID id(){ return id; }
    @Override public UUID subLevelId(){ return null; }
    @Override public BlockPos position(){ return position.get(); }
    @Override public String label(){ return "Worker inventory"; }
    @Override public boolean acceptsDelivery(){ return insertion; }
    @Override public int insertionPriority(WorkerResourceKey resource){ return -1; }
    @Override public boolean canExtract(WorkerResourceKey resource){ return resource.type() == WorkerResourceType.ITEM; }
    @Override public boolean canInsert(WorkerResourceKey resource){ return insertion && canExtract(resource); }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Snapshot usable working stock for dependency planning
    public Map<WorkerResourceKey, Long> contents(){
        Map<WorkerResourceKey, Long> contents = new LinkedHashMap<>();
        for(int idx = 0; idx < inventory.getSlots(); idx++){
            ItemStack stack = inventory.getStackInSlot(idx);
            if(stack.isEmpty() || !usable.test(stack) || !extraction.test(stack)) continue;
            var resource = new WorkerResourceKey(WorkerResourceType.ITEM, BuiltInRegistries.ITEM.getKey(stack.getItem()));
            contents.merge(resource, (long) inventory.extractItem(idx, stack.getCount(), true).getCount(), Long::sum);
        }
        return Map.copyOf(contents);
    }

    // Count only unreserved items belonging to the current working inventory
    @Override public long available(WorkerResourceKey resource){
        if(!canExtract(resource)) return 0L;
        long amount = 0L;
        for(int idx = 0; idx < inventory.getSlots(); idx++){
            ItemStack stack = inventory.getStackInSlot(idx);
            if(matches(stack, resource)) amount += inventory.extractItem(idx, stack.getCount(), true).getCount();
        }
        return amount;
    }

    // Simulate capacity without changing any inventory slots
    @Override public long space(WorkerResourceKey resource){
        if(!canInsert(resource)) return 0L;
        return insert(new WorkerResourcePacket(resource, Integer.MAX_VALUE, null), true);
    }

    // Withdraw a concrete ingredient while preserving its item components
    @Override public WorkerResourcePacket extract(WorkerResourceKey resource, long maximumAmount, boolean simulate){
        if(!canExtract(resource) || maximumAmount <= 0L) return WorkerResourcePacket.empty(resource);
        long amount = 0L;
        ItemStack template = ItemStack.EMPTY;
        for(int idx = 0; idx < inventory.getSlots() && amount < maximumAmount; idx++){
            ItemStack stored = inventory.getStackInSlot(idx);
            if(!matches(stored, resource) || !template.isEmpty() && !ItemStack.isSameItemSameComponents(template, stored)) continue;
            ItemStack stack = inventory.extractItem(idx, (int) Math.min(Integer.MAX_VALUE, maximumAmount - amount), simulate);
            if(stack.isEmpty()) continue;
            if(template.isEmpty()) template = stack.copy();
            amount += stack.getCount();
        }
        CompoundTag payload = new CompoundTag();
        if(!template.isEmpty()) payload.put("Stack", template.saveOptional(registries));
        return new WorkerResourcePacket(resource, amount, payload);
    }

    @Override public WorkerResourcePacket extractMatchingItem(WorkerResourceKey resource, long maximumAmount,
                                                               Predicate<ItemStack> predicate, boolean simulate){
        if(!canExtract(resource) || maximumAmount <= 0L) return WorkerResourcePacket.empty(resource);
        for(int slot = 0; slot < inventory.getSlots(); slot++){
            ItemStack stored = inventory.getStackInSlot(slot);
            if(!matches(stored, resource) || !predicate.test(stored)) continue;
            ItemStack removed = inventory.extractItem(slot, (int)Math.min(maximumAmount, stored.getCount()), simulate);
            if(removed.isEmpty()) continue;
            CompoundTag payload = new CompoundTag();
            payload.put("Stack", removed.copyWithCount(1).saveOptional(registries));
            return new WorkerResourcePacket(resource, removed.getCount(), payload);
        }
        return WorkerResourcePacket.empty(resource);
    }

    @Override public long availableMatchingItem(WorkerResourceKey resource, Predicate<ItemStack> predicate){
        if(!canExtract(resource)) return 0L;
        long total = 0L;
        for(int slot = 0; slot < inventory.getSlots(); slot++){
            ItemStack stack = inventory.getStackInSlot(slot);
            if(matches(stack, resource) && predicate.test(stack))
                total += inventory.extractItem(slot, stack.getCount(), true).getCount();
        }
        return total;
    }

    // Stage a recipe result as ordinary inventory stock for subsequent prerequisite operations
    @Override public long insert(WorkerResourcePacket packet, boolean simulate){
        if(!canInsert(packet.resource()) || packet.isEmpty()) return 0L;
        ItemStack template = packet.payload().contains("Stack")
                ? ItemStack.parseOptional(registries, packet.payload().getCompound("Stack"))
                : new ItemStack(BuiltInRegistries.ITEM.get(packet.resource().id()));
        if(template.isEmpty()) return 0L;
        long remaining = Math.min(Integer.MAX_VALUE, packet.amount());
        long offered = remaining;
        for(int idx = 0; idx < inventory.getSlots() && remaining > 0L; idx++){
            ItemStack stored = inventory.getStackInSlot(idx);
            if(!stored.isEmpty() && !usable.test(stored)) continue;
            ItemStack stack = template.copyWithCount((int) remaining);
            remaining = inventory.insertItem(idx, stack, simulate).getCount();
        }
        return offered - remaining;
    }

    // Exclude reserved cargo before matching its item identity
    private boolean matches(ItemStack stack, WorkerResourceKey resource){
        return !stack.isEmpty() && usable.test(stack) && extraction.test(stack)
                && BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(resource.id());
    }
}
