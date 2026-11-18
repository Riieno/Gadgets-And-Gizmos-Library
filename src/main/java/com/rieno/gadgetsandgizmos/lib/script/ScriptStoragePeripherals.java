package com.rieno.gadgetsandgizmos.lib.script;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.inventory.ItemTransfers;
import com.rieno.gadgetsandgizmos.lib.inventory.ResourceRouting;
import com.rieno.gadgetsandgizmos.lib.probe.ContainerData;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

// Expose standard storage methods through real capabilities and host-scoped remote lookup
public final class ScriptStoragePeripherals{
    public record Target(BlockEntity blockEntity, Direction side){}
    private ScriptStoragePeripherals(){}

    public static Set<String> bind(ScriptMethods methods, Target target, Function<String, Target> lookup){
        var pool = access(target); Set<String> types = new LinkedHashSet<>();
        if(!pool.items().isEmpty()){
            types.add("inventory");
            methods.bind("size", args -> new Object[]{inventory(target).getSlots()});
            methods.bind("list", args -> {
                IItemHandler inventory = inventory(target); Map<Integer, Object> items = new LinkedHashMap<>();
                for(int idx = 0; idx < inventory.getSlots(); idx++){
                    ItemStack stack = inventory.getStackInSlot(idx); if(!stack.isEmpty()) items.put(idx + 1, item(stack, false));
                }
                return new Object[]{items};
            });
            methods.bind("getItemDetail", args -> {
                var inventory = inventory(target); var stack = inventory.getStackInSlot(slot(args, 0, inventory.getSlots()));
                return new Object[]{stack.isEmpty() ? null : item(stack, true)};
            });
            methods.bind("getItemLimit", args -> {
                var inventory = inventory(target); return new Object[]{inventory.getSlotLimit(slot(args, 0, inventory.getSlots()))};
            });
            methods.bind("pushItems", args -> new Object[]{moveItems(target, remote(lookup, args), args)});
            methods.bind("pullItems", args -> new Object[]{moveItems(remote(lookup, args), target, args)});
        }
        if(!pool.fluids().isEmpty()){
            types.add("fluid_storage");
            methods.bind("tanks", args -> {
                Map<Integer, Object> tanks = new LinkedHashMap<>(); int idx = 0;
                for(var handler : access(target).fluids()) for(int tank = 0; tank < handler.getTanks(); tank++){
                    idx++; var fluid = handler.getFluidInTank(tank);
                    if(!fluid.isEmpty()) tanks.put(idx, Map.of("name", BuiltInRegistries.FLUID.getKey(fluid.getFluid()).toString(), "amount", fluid.getAmount()));
                }
                return new Object[]{tanks};
            });
            methods.bind("pushFluid", args -> new Object[]{moveFluids(target, remote(lookup, args), args)});
            methods.bind("pullFluid", args -> new Object[]{moveFluids(remote(lookup, args), target, args)});
        }
        if(!pool.energy().isEmpty()){
            types.add("energy_storage");
            methods.bind("getEnergy", args -> new Object[]{access(target).energy().stream().mapToLong(storage -> storage.getEnergyStored()).sum()});
            methods.bind("getEnergyCapacity", args -> new Object[]{access(target).energy().stream().mapToLong(storage -> storage.getMaxEnergyStored()).sum()});
        }
        if(target.blockEntity() instanceof com.rieno.gadgetsandgizmos.lib.inventory.ContainerDataSource){
            methods.bind("getData", args -> new Object[]{ContainerData.capture(target.blockEntity(), target.side()).values()});
            methods.bind("getItems", args -> new Object[]{ContainerData.capture(target.blockEntity(), target.side()).items()});
            methods.bind("getItemCount", args -> new Object[]{ContainerData.capture(target.blockEntity(), target.side()).itemCount()});
            methods.bind("getItemCapacity", args -> new Object[]{ContainerData.capture(target.blockEntity(), target.side()).itemCapacity()});
        }
        return Set.copyOf(types);
    }

    private static Target remote(Function<String, Target> lookup, List<Object> args){
        if(args.isEmpty() || !(args.getFirst() instanceof String name)) throw new ScriptException("Expected a peripheral name");
        Target target = lookup.apply(name); if(target == null) throw new ScriptException("Peripheral is unavailable: " + name); return target;
    }
    private static ResourceRouting.Pool access(Target target){
        if(target == null || target.blockEntity() == null || target.blockEntity().isRemoved() || target.blockEntity().getLevel() == null)
            throw new ScriptException("Storage is no longer available");
        return ContainerData.resources(target.blockEntity(), target.side());
    }
    private static IItemHandler inventory(Target target){ return new Inventory(access(target).items(), -1); }
    private static int slot(List<Object> args, int idx, int size){
        if(idx >= args.size() || !(args.get(idx) instanceof Number number) || number.doubleValue() != number.intValue()
                || number.intValue() < 1 || number.intValue() > size) throw new ScriptException("Slot is out of range");
        return ((Number)args.get(idx)).intValue() - 1;
    }
    private static int limit(List<Object> args, int idx){
        if(args.size() <= idx || args.get(idx) == null) return Integer.MAX_VALUE;
        if(!(args.get(idx) instanceof Number num) || num.doubleValue() < 0 || num.doubleValue() != Math.floor(num.doubleValue())) throw new ScriptException("Invalid transfer limit");
        return (int)Math.min(Integer.MAX_VALUE, ((Number)args.get(idx)).doubleValue());
    }
    private static int moveItems(Target source, Target target, List<Object> args){
        var sourcePool = access(source); var targetPool = access(target);
        IItemHandler sourceInventory = new Inventory(sourcePool.items(), -1);
        IItemHandler targetInventory = new Inventory(targetPool.items(), -1);
        int from = slot(args, 1, sourceInventory.getSlots());
        int to = args.size() > 3 && args.get(3) != null ? slot(args, 3, targetInventory.getSlots()) : -1;
        if(source.blockEntity() == target.blockEntity() && source.side() == target.side() && (to == from || to < 0)) return 0;
        return ItemTransfers.move(new Inventory(sourcePool.items(), from), new Inventory(targetPool.items(), to), stack -> true, limit(args, 2));
    }
    private static int moveFluids(Target source, Target target, List<Object> args){
        String filter = args.size() > 2 && args.get(2) instanceof String name ? name : null;
        return ResourceRouting.moveFluids(access(source), access(target), fluid -> filter == null || BuiltInRegistries.FLUID.getKey(fluid.getFluid()).toString().equals(filter), limit(args, 1));
    }
    private static Map<String, Object> item(ItemStack stack, boolean detailed){
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()); data.put("count", stack.getCount());
        if(detailed){
            data.put("displayName", stack.getHoverName().getString()); data.put("maxCount", stack.getMaxStackSize());
            Map<String, Boolean> tags = new LinkedHashMap<>(); stack.getTags().forEach(tag -> tags.put(tag.location().toString(), true)); data.put("tags", tags);
            if(stack.isDamageableItem()){ data.put("damage", stack.getDamageValue()); data.put("maxDamage", stack.getMaxDamage()); data.put("durability", 1.0 - stack.getDamageValue() / (double)stack.getMaxDamage()); }
        }
        return data;
    }

    // Retain one-based script slots while routing to the original ordered handlers
    private static final class Inventory implements IItemHandler{
        private final List<IItemHandler> handlers;
        private final int selected;
        private Inventory(List<IItemHandler> handlers, int selected){ this.handlers = handlers; this.selected = selected; }
        public int getSlots(){ return selected >= 0 ? 1 : handlers.stream().mapToInt(IItemHandler::getSlots).sum(); }
        private Slot resolve(int slot){
            int idx = selected >= 0 ? selected : slot;
            for(var handler : handlers){ if(idx < handler.getSlots()) return new Slot(handler, idx); idx -= handler.getSlots(); }
            throw new ScriptException("Slot is out of range");
        }
        public ItemStack getStackInSlot(int slot){ var entry = resolve(slot); return entry.handler.getStackInSlot(entry.idx); }
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate){ var entry = resolve(slot); return entry.handler.insertItem(entry.idx, stack, simulate); }
        public ItemStack extractItem(int slot, int amount, boolean simulate){ var entry = resolve(slot); return entry.handler.extractItem(entry.idx, amount, simulate); }
        public int getSlotLimit(int slot){ var entry = resolve(slot); return entry.handler.getSlotLimit(entry.idx); }
        public boolean isItemValid(int slot, ItemStack stack){ var entry = resolve(slot); return entry.handler.isItemValid(entry.idx, stack); }
        private record Slot(IItemHandler handler, int idx){}
    }
}
