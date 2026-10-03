package com.rieno.gadgetsandgizmos.lib.inventory;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.access.WorldAccessPolicy;
import com.rieno.gadgetsandgizmos.lib.probe.BlockEntityLookupApi;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerEndpointIdentity;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerItemRequest;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerOrchestrator;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerWorkOrder;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.IntSupplier;

// Tick loaded configured containers with bounded transfer and replenishment work
public final class ContainerAutomationRuntime{
    private static IntSupplier transferLimit = () -> 64;
    private ContainerAutomationRuntime(){}

    public static void setTransferLimit(IntSupplier limit){ transferLimit = Objects.requireNonNull(limit); }

    // Spread container work over twenty ticks without loading absent targets
    public static void tick(ServerLevel level){
        for(var entry : ContainerAutomationStore.get(level).entries()){
            if(Math.floorMod(entry.getKey().pos().asLong(), 20) != Math.floorMod(level.getGameTime(), 20)) continue;
            var key = entry.getKey();
            var config = entry.getValue();
            BlockEntity target = BlockEntityLookupApi.findLoadedExact(level, key.subLevelId(), key.pos());
            if(target == null) continue;
            var owner = level.getServer().getPlayerList().getPlayer(config.owner());
            if(config.owner() == null) continue;
            if(owner == null) owner = net.neoforged.neoforge.common.util.FakePlayerFactory.get(level, new com.mojang.authlib.GameProfile(config.owner(), "Manifest"));
            if(!WorldAccessPolicy.canAccessLocal(owner, level, key.subLevelId(), key.pos())) continue;
            IItemHandler inventory = target.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, key.pos(), null);
            if(inventory == null) continue;
            java.util.function.Consumer<ItemStack> recovery = stack -> {
                var body = com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi.subLevel(level, key.subLevelId());
                var point = com.rieno.gadgetsandgizmos.lib.physics.SableTransformApi.toWorldPosition(body, key.pos().getCenter());
                var item = new net.minecraft.world.entity.item.ItemEntity(level, point.x, point.y, point.z, stack);
                item.setDefaultPickUpDelay();
                item.setTarget(config.owner());
                level.addFreshEntity(item);
            };
            List<IItemHandler> adjacent = new ArrayList<>();
            for(Direction side : Direction.values()){
                var pos = key.pos().relative(side);
                if(!target.getLevel().isLoaded(pos) || !WorldAccessPolicy.canAccessLocal(owner, level, key.subLevelId(), pos)) continue;
                IItemHandler handler = target.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, pos, side.getOpposite());
                if(handler != null && handler != inventory) adjacent.add(handler);
            }
            int budget = Math.max(1, Math.min(1024, transferLimit.getAsInt()));
            if(config.pull()) for(IItemHandler source : adjacent){
                budget -= ItemTransfers.move(source, inventory, stack -> config.accepts(target.getLevel(), stack), budget, recovery);
                if(budget <= 0) break;
            }
            List<Map.Entry<ContainerAutomation.StockTarget, Long>> shortages = new ArrayList<>();
            for(var stock : config.targets()){
                long deficit = stock.deficit(ItemTransfers.count(inventory, stock.item()), 0);
                for(IItemHandler source : adjacent){
                    if(deficit == 0 || budget == 0) break;
                    int moved = ItemTransfers.move(source, inventory, stack -> ItemStack.isSameItemSameComponents(stack, stock.item()), (int) Math.min(deficit, budget), recovery);
                    deficit -= moved;
                    budget -= moved;
                }
                if(deficit > 0) shortages.add(Map.entry(stock, deficit));
            }
            if(!shortages.isEmpty()){
                var preferred = config.controllerPos() == null ? null
                        : BlockEntityLookupApi.findLoadedExact(level, config.controllerSubLevelId(), config.controllerPos());
                WorkerOrchestrator workers = preferred instanceof WorkerOrchestrator val
                        && WorldAccessPolicy.canAccessLocal(owner, level, config.controllerSubLevelId(), config.controllerPos()) ? val
                        : NearbyWorkerOrchestrators.find(level, target, key.subLevelId(), owner, 32);
                if(workers != null) for(var shortage : shortages) replenish(level, key, workers, shortage.getKey(), shortage.getValue());
            }
            if(config.push() && budget > 0){
                for(int idx = 0; idx < inventory.getSlots() && budget > 0; idx++){
                    ItemStack item = inventory.getStackInSlot(idx).copyWithCount(1);
                    if(item.isEmpty() || !config.accepts(target.getLevel(), item)) continue;
                    long keep = config.targets().stream().filter(stock -> ItemStack.isSameItemSameComponents(stock.item(), item)).mapToLong(ContainerAutomation.StockTarget::amount).max().orElse(0);
                    long excess = Math.max(0, ItemTransfers.count(inventory, item) - keep);
                    for(IItemHandler destination : adjacent){
                        int moved = ItemTransfers.move(inventory, destination, stack -> ItemStack.isSameItemSameComponents(stack, item), (int) Math.min(excess, budget), recovery);
                        budget -= moved;
                        excess -= moved;
                        if(excess == 0 || budget == 0) break;
                    }
                }
            }
        }
    }

    // Include queued deliveries so an unfinished worker request does not create duplicate stock orders
    private static void replenish(ServerLevel level, ContainerAutomationStore.Key key, WorkerOrchestrator workers,
                                  ContainerAutomation.StockTarget stock, long deficit){
        if(!stock.item().getComponentsPatch().isEmpty()) return;
        UUID destination = WorkerEndpointIdentity.of(level.dimension().location(), key.subLevelId(), key.pos(), null);
        var itemId = BuiltInRegistries.ITEM.getKey(stock.item().getItem());
        long pending = workers.managedWorkers().stream().flatMap(worker -> {
            List<WorkerWorkOrder> orders = new ArrayList<>(worker.plannedOrders());
            if(worker.currentOrder() != null) orders.add(worker.currentOrder());
            return orders.stream();
        }).filter(order -> destination.equals(order.destinationEndpointId()) && itemId.equals(order.outputResource().id()))
                .mapToLong(order -> order.processing() ? Math.max(order.outputAmount(), order.task().remainingAmount()) : order.task().remainingAmount()).sum();
        long amount = Math.max(0, deficit - pending);
        if(amount == 0) return;
        String task = stock.task();
        workers.requestItems(new WorkerItemRequest(null, itemId, (int) Math.min(amount, 4096), "craft".equals(task),
                "craft".equals(task) || "transfer".equals(task) ? "" : task, destination));
    }
}
