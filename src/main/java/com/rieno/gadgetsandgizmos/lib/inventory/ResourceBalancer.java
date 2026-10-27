package com.rieno.gadgetsandgizmos.lib.inventory;

import com.rieno.gadgetsandgizmos.lib.inventory.ResourceRouting.Pool;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Predicate;

// Equalize compatible buffers by capacity using bounded, real capability transfers
public final class ResourceBalancer{
    private ResourceBalancer(){}

    public record Limits(int items, int fluids, int energy){
        public Limits{ items = Math.max(0, items); fluids = Math.max(0, fluids); energy = Math.max(0, energy); }
    }

    public record Result(int items, int fluids, int energy, Pool changed){
        public boolean moved(){ return items > 0 || fluids > 0 || energy > 0; }
    }

    private record ItemKey(Item item, DataComponentPatch components){}
    private record FluidKey(Fluid fluid, DataComponentPatch components){}

    public static Result balance(List<Pool> buffers, Predicate<ItemStack> items,
                                 Predicate<FluidStack> fluids, Limits limits){
        Pool all = ResourceRouting.combine(buffers);
        List<Pool> changed = new ArrayList<>();
        int itemMoved = 0, fluidMoved = 0, energyMoved = 0;
        if(limits.items() > 0){
            var types = new LinkedHashMap<ItemKey, ItemStack>();
            for(var handler : all.items()) for(int slot = 0; slot < handler.getSlots(); slot++){
                ItemStack stack = handler.getStackInSlot(slot);
                if(!stack.isEmpty() && items.test(stack)){
                    types.putIfAbsent(new ItemKey(stack.getItem(), stack.getComponentsPatch()), stack.copyWithCount(1));
                }
            }
            for(ItemStack type : types.values()){
                List<Buffer> nodes = new ArrayList<>();
                for(var handler : all.items()) nodes.add(itemBuffer(handler, type));
                itemMoved += equalize(nodes, limits.items() - itemMoved, changed,
                        (from, to, amount) -> ResourceRouting.moveItems(from.pool, to.pool,
                                stack -> ItemStack.isSameItemSameComponents(type, stack), amount));
                if(itemMoved >= limits.items()) break;
            }
        }
        if(limits.fluids() > 0){
            var types = new LinkedHashMap<FluidKey, FluidStack>();
            for(var handler : all.fluids()) for(int tank = 0; tank < handler.getTanks(); tank++){
                FluidStack stack = handler.getFluidInTank(tank);
                if(!stack.isEmpty() && fluids.test(stack)){
                    types.putIfAbsent(new FluidKey(stack.getFluid(), stack.getComponentsPatch()), stack.copyWithAmount(1));
                }
            }
            for(FluidStack type : types.values()){
                List<Buffer> nodes = new ArrayList<>();
                for(var handler : all.fluids()) nodes.add(fluidBuffer(handler, type));
                fluidMoved += equalize(nodes, limits.fluids() - fluidMoved, changed,
                        (from, to, amount) -> ResourceRouting.moveFluids(from.pool, to.pool,
                                stack -> FluidStack.isSameFluidSameComponents(type, stack), amount));
                if(fluidMoved >= limits.fluids()) break;
            }
        }
        if(limits.energy() > 0){
            List<Buffer> nodes = new ArrayList<>();
            for(var handler : all.energy()){
                nodes.add(new Buffer(new Pool(List.of(), List.of(), List.of(handler)),
                        handler.getEnergyStored(), handler.getMaxEnergyStored(),
                        handler.canExtract() && handler.extractEnergy(1, true) > 0,
                        handler.canReceive() && handler.receiveEnergy(1, true) > 0));
            }
            energyMoved = equalize(nodes, limits.energy(), changed,
                    (from, to, amount) -> ResourceRouting.moveEnergy(from.pool, to.pool, amount));
        }
        return new Result(itemMoved, fluidMoved, energyMoved, ResourceRouting.combine(changed));
    }

    private static Buffer itemBuffer(IItemHandler handler, ItemStack type){
        long amount = 0, capacity = 0;
        boolean donate = false, receive = false;
        for(int slot = 0; slot < handler.getSlots(); slot++){
            ItemStack stack = handler.getStackInSlot(slot);
            boolean matches = ItemStack.isSameItemSameComponents(stack, type);
            if(!stack.isEmpty() && !matches) continue;
            if(matches){
                amount += stack.getCount();
                ItemStack extracted = handler.extractItem(slot, 1, true);
                donate |= !extracted.isEmpty() && ItemStack.isSameItemSameComponents(extracted, type);
            }
            if(matches || handler.isItemValid(slot, type)){
                capacity += Math.min(type.getMaxStackSize(), handler.getSlotLimit(slot));
                receive |= handler.insertItem(slot, type, true).isEmpty();
            }
        }
        return new Buffer(new Pool(List.of(handler), List.of(), List.of()), amount, capacity, donate, receive);
    }

    private static Buffer fluidBuffer(IFluidHandler handler, FluidStack type){
        long amount = 0, capacity = 0;
        for(int tank = 0; tank < handler.getTanks(); tank++){
            FluidStack stack = handler.getFluidInTank(tank);
            boolean matches = FluidStack.isSameFluidSameComponents(stack, type);
            if(!stack.isEmpty() && !matches) continue;
            if(matches) amount += stack.getAmount();
            if(matches || handler.isFluidValid(tank, type)) capacity += handler.getTankCapacity(tank);
        }
        FluidStack extracted = amount == 0 ? FluidStack.EMPTY : handler.drain(type, IFluidHandler.FluidAction.SIMULATE);
        boolean donate = !extracted.isEmpty() && FluidStack.isSameFluidSameComponents(extracted, type);
        return new Buffer(new Pool(List.of(), List.of(handler), List.of()), amount, capacity, donate,
                handler.fill(type, IFluidHandler.FluidAction.SIMULATE) > 0);
    }

    private static int equalize(List<Buffer> nodes, int budget, List<Pool> changed, Mover mover){
        nodes.removeIf(node -> node.capacity <= 0);
        if(budget <= 0 || nodes.size() < 2) return 0;
        long total = 0, capacity = 0, assigned = 0;
        for(Buffer node : nodes){ total += node.amount; capacity += node.capacity; }
        for(Buffer node : nodes){
            double share = (double)total / capacity * node.capacity;
            node.target = Math.min(node.capacity, (long)Math.floor(share));
            node.remainder = share - node.target;
            assigned += node.target;
        }
        // Keep indivisible units where possible so rounding does not shuffle stable buffers
        nodes.sort(Comparator.comparingDouble((Buffer node) -> node.remainder).reversed()
                .thenComparingInt(node -> node.amount > node.target ? 0 : 1));
        long remaining = total - assigned;
        for(Buffer node : nodes){
            if(remaining <= 0) break;
            if(node.target < node.capacity){ node.target++; remaining--; }
        }
        List<Buffer> donors = nodes.stream().filter(node -> node.donate && node.amount > node.target)
                .sorted(Comparator.comparingDouble(Buffer::level).reversed()).toList();
        List<Buffer> receivers = nodes.stream().filter(node -> node.receive && node.amount < node.target)
                .sorted(Comparator.comparingDouble(Buffer::level)).toList();
        int moved = 0, donor = 0, receiver = 0;
        while(donor < donors.size() && receiver < receivers.size() && moved < budget){
            Buffer from = donors.get(donor), to = receivers.get(receiver);
            int requested = (int)Math.min(budget - moved, Math.min(from.amount - from.target, to.target - to.amount));
            int delivered = mover.move(from, to, requested);
            if(delivered > 0){
                from.amount -= delivered;
                to.amount += delivered;
                moved += delivered;
                changed.add(from.pool);
                changed.add(to.pool);
            }
            if(from.amount <= from.target) donor++;
            if(to.amount >= to.target || delivered < requested) receiver++;
        }
        return moved;
    }

    private interface Mover{ int move(Buffer from, Buffer to, int amount); }

    private static final class Buffer{
        private final Pool pool;
        private long amount;
        private final long capacity;
        private final boolean donate, receive;
        private long target;
        private double remainder;

        private Buffer(Pool pool, long amount, long capacity, boolean donate, boolean receive){
            this.pool = pool;
            this.amount = Math.max(0, amount);
            this.capacity = Math.max(this.amount, capacity);
            this.donate = donate;
            this.receive = receive;
        }

        private double level(){ return (double)amount / capacity; }
    }
}
