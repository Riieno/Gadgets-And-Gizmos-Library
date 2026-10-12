package com.rieno.gadgetsandgizmos.lib.item;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.ContainerEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;

// Tick active item handlers and passive containers without retaining their owners.
public final class TimedItemTransformations{
    private static final Map<IItemHandlerModifiable, Boolean> HANDLERS = new WeakHashMap<>();
    private static final Map<Container, Level> CONTAINERS = new WeakHashMap<>();
    private static final Map<Container, Boolean> ACTIVE_CONTAINERS = new WeakHashMap<>();

    private TimedItemTransformations(){
    }

    public static void register(){
        NeoForge.EVENT_BUS.addListener(TimedItemTransformations::onServerTick);
        NeoForge.EVENT_BUS.addListener(TimedItemTransformations::onEntityJoin);
        NeoForge.EVENT_BUS.addListener(TimedItemTransformations::onServerStopped);
    }

    // Use one persistent clock for transfers between dimensions.
    public static long gameTime(Level level){
        MinecraftServer server = level.getServer();
        return server == null || server.overworld() == null
                ? level.getGameTime() : server.overworld().getGameTime();
    }

    public static ItemStack transform(ItemStack stack, Level level){
        if(level.isClientSide || stack.isEmpty()) return stack;
        return transform(stack, gameTime(level));
    }

    public static ItemStack transform(ItemStack stack, long now){
        return !stack.isEmpty() && stack.getItem() instanceof TimedTransformingItem item
                ? item.transform(stack, now) : stack;
    }

    // Check exposed stacks against fluid surfaces and filled cauldron interiors.
    public static ItemStack transform(ItemStack stack, Level level, AABB bounds){
        if(level.isClientSide || stack.isEmpty() || !(stack.getItem() instanceof TimedTransformingItem item)) return stack;
        AABB contact = bounds.deflate(0.0001D);
        for(BlockPos pos : BlockPos.betweenClosed(Mth.floor(contact.minX), Mth.floor(contact.minY),
                Mth.floor(contact.minZ), Mth.floor(contact.maxX), Mth.floor(contact.maxY), Mth.floor(contact.maxZ))){
            if(!level.hasChunkAt(pos)) continue;
            FluidState fluid = level.getFluidState(pos);
            if(!fluid.isEmpty() && contact.minY < pos.getY() + fluid.getHeight(level, pos)){
                ItemStack converted = item.transformInFluid(stack, fluid.getType());
                if(converted != stack) return converted;
            }
            BlockState state = level.getBlockState(pos);
            Fluid contents;
            double height;
            if(state.is(Blocks.WATER_CAULDRON)){
                contents = Fluids.WATER;
                height = (6.0D + state.getValue(LayeredCauldronBlock.LEVEL) * 3.0D) / 16.0D;
            }else if(state.is(Blocks.LAVA_CAULDRON)){
                contents = Fluids.LAVA;
                height = 15.0D / 16.0D;
            }else{
                continue;
            }
            AABB inside = new AABB(pos.getX() + 0.125D, pos.getY() + 0.25D, pos.getZ() + 0.125D,
                    pos.getX() + 0.875D, pos.getY() + height, pos.getZ() + 0.875D);
            if(!contact.intersects(inside)) continue;
            ItemStack converted = item.transformInFluid(stack, contents);
            if(converted != stack) return converted;
        }
        return item.transform(stack, gameTime(level));
    }

    // Fluid handlers do not expose their level; call this on the owning server thread.
    public static ItemStack transformInFluids(ItemStack stack, IFluidHandler fluids){
        if(stack.isEmpty() || !(stack.getItem() instanceof TimedTransformingItem item)) return stack;
        for(int tank = 0; tank < fluids.getTanks(); tank++){
            FluidStack fluid = fluids.getFluidInTank(tank);
            if(fluid.isEmpty()) continue;
            ItemStack converted = item.transformInFluid(stack, fluid.getFluid());
            if(converted != stack) return converted;
        }
        return stack;
    }

    public static boolean transformInFluids(IItemHandlerModifiable items, IFluidHandler fluids){
        boolean changed = false;
        for(int slot = 0; slot < items.getSlots(); slot++){
            ItemStack stack = items.getStackInSlot(slot);
            ItemStack converted = transformInFluids(stack, fluids);
            if(converted == stack) continue;
            items.setStackInSlot(slot, converted);
            changed = true;
        }
        return changed;
    }

    public static boolean beginTransformation(ItemStack stack, Level level){
        return !level.isClientSide && !stack.isEmpty()
                && stack.getItem() instanceof TimedTransformingItem item
                && item.beginTransformation(stack, gameTime(level));
    }

    // Called when a real inventory slot is read or written, never for a simulated insertion.
    public static void observe(IItemHandlerModifiable handler, int slot, ItemStack stack){
        if(stack.isEmpty() || !(stack.getItem() instanceof TimedTransformingItem item)) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if(server == null || !server.isSameThread()) return;
        HANDLERS.put(handler, Boolean.TRUE);
        if(server.overworld() == null) return;
        if(item.beginTransformation(stack, server.overworld().getGameTime())){
            handler.setStackInSlot(slot, stack);
        }
    }

    // Recipe probes can attach temporary containers without a world
    public static void track(Container container, @Nullable Level level){
        if(level == null || level.isClientSide) return;
        CONTAINERS.put(container, level);
    }

    // Include standalone inventories such as ender chests and entity storage.
    public static void observe(Container container, ItemStack stack){
        if(stack.isEmpty() || !(stack.getItem() instanceof TimedTransformingItem item)) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if(server == null || !server.isSameThread() || server.overworld() == null) return;
        track(container, server.overworld());
        ACTIVE_CONTAINERS.put(container, Boolean.TRUE);
        if(item.beginTransformation(stack, server.overworld().getGameTime())) container.setChanged();
    }

    // Report whether any stacks are still waiting.
    public static boolean tick(IItemHandlerModifiable handler, long now){
        boolean waiting = false;
        for(int slot = 0; slot < handler.getSlots(); slot++){
            ItemStack stack = handler.getStackInSlot(slot);
            ItemStack converted = transform(stack, now);
            if(converted != stack) handler.setStackInSlot(slot, converted);
            waiting |= !converted.isEmpty() && converted.getItem() instanceof TimedTransformingItem;
        }
        return waiting;
    }

    public static boolean tick(Container container, long now){
        // Reading unopened loot inventories would generate their contents without a player.
        if(container instanceof RandomizableContainer loot && loot.getLootTable() != null) return false;
        if(container instanceof ContainerEntity loot && loot.getLootTable() != null) return false;
        boolean waiting = false;
        for(int slot = 0; slot < container.getContainerSize(); slot++){
            ItemStack stack = container.getItem(slot);
            boolean starting = !stack.isEmpty() && stack.getItem() instanceof TimedTransformingItem item
                    && item.beginTransformation(stack, now);
            ItemStack converted = transform(stack, now);
            if(starting || converted != stack){
                container.setItem(slot, converted);
                container.setChanged();
            }
            waiting |= !converted.isEmpty() && converted.getItem() instanceof TimedTransformingItem;
        }
        return waiting;
    }

    private static void onServerTick(ServerTickEvent.Post evt){
        long now = evt.getServer().overworld().getGameTime();
        for(IItemHandlerModifiable handler : new ArrayList<>(HANDLERS.keySet())){
            if(!tick(handler, now)) HANDLERS.remove(handler);
        }
        for(Container container : new ArrayList<>(ACTIVE_CONTAINERS.keySet())){
            if(!isAvailable(container, CONTAINERS.get(container)) || !tick(container, now)){
                ACTIVE_CONTAINERS.remove(container);
            }
        }
        if(now % 20L != 0L) return;
        for(Container container : new ArrayList<>(CONTAINERS.keySet())){
            if(ACTIVE_CONTAINERS.containsKey(container)) continue;
            Level level = CONTAINERS.get(container);
            if(isAvailable(container, level) && tick(container, now)){
                ACTIVE_CONTAINERS.put(container, Boolean.TRUE);
            }
        }
    }

    private static boolean isAvailable(Container container, Level level){
        if(level == null) return false;
        if(container instanceof BlockEntity be && (be.isRemoved() || !level.hasChunkAt(be.getBlockPos()))) return false;
        return !(container instanceof Entity entity) || !entity.isRemoved();
    }

    private static void onEntityJoin(EntityJoinLevelEvent evt){
        if(evt.getEntity() instanceof Container container) track(container, evt.getLevel());
    }

    private static void onServerStopped(ServerStoppedEvent evt){
        HANDLERS.clear();
        CONTAINERS.clear();
        ACTIVE_CONTAINERS.clear();
    }
}
