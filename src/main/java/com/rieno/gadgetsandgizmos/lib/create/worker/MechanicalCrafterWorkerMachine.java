package com.rieno.gadgetsandgizmos.lib.create.worker;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.worker.*;
import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlockEntity;
import com.simibubi.create.content.kinetics.crafter.RecipeGridHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;

// Feed each slot of the connected crafting grid and collect from its real output receiver
final class MechanicalCrafterWorkerMachine implements WorkerMachine{
    private final WorkerMachineRegistry.Context ctx;
    private final MechanicalCrafterBlockEntity root;

    MechanicalCrafterWorkerMachine(WorkerMachineRegistry.Context ctx, MechanicalCrafterBlockEntity root){
        this.ctx = ctx;
        this.root = root;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private List<MechanicalCrafterBlockEntity> chain(){
        var chain = RecipeGridHandler.getAllCraftersOfChain(root);
        return chain == null ? List.of() : chain;
    }

    @Override public boolean supports(WorkerRecipeDefinition recipe){
        return WorkerMachine.super.supports(recipe);
    }

    @Override public boolean maySupportProcessor(ResourceLocation processorType){
        return processorType != null && supportedType(processorType.toString());
    }

    @Override public boolean mayHavePreloadedInputs(WorkerRecipeDefinition recipe){ return false; }

    @Override public boolean supports(WorkerRecipePlan plan){
        return plan != null && supportedType(plan.processorType().toString())
                && !layout(plan).isEmpty() && !itemOutputs(plan).isEmpty();
    }

    private static boolean supportedType(String type){
        return type.equals("minecraft:crafting") || type.equals("create:mechanical_crafting");
    }

    @Override public List<BlockPos> members(){ return chain().stream().map(MechanicalCrafterBlockEntity::getBlockPos).toList(); }

    @Override public List<IItemHandler> itemInputs(WorkerRecipePlan plan){
        if(plan == null) return chain().stream().map(crafter -> (IItemHandler) crafter.getInventory()).toList();
        List<IItemHandler> ports = new ArrayList<>();
        layout(plan).forEach((crafter, stack) -> ports.add(new WorkerItemPort(crafter.getInventory(),
                offered -> ItemStack.isSameItemSameComponents(stack, offered))));
        return ports;
    }

    @Override public List<IItemHandler> stagedItemInputs(WorkerRecipePlan plan, WorkerArea area){
        return chain().stream().filter(crafter -> area == null || area.contains(crafter.getBlockPos()))
                .map(crafter -> (IItemHandler)crafter.getInventory()).toList();
    }

    @Override public List<IItemHandler> stagedItemInputsAt(WorkerRecipePlan plan, WorkerMachineSite site){
        return stagedItemInputs(plan, site == null ? null : site.area());
    }

    @Override public List<Long> preloadedInputs(WorkerRecipePlan plan, WorkerArea area, long batches){
        if(plan == null || batches <= 0L) return List.of();
        Map<WorkerResourceKey, Long> occupied = new LinkedHashMap<>();
        for(var cell : layout(plan).entrySet()){
            if(area != null && !area.contains(cell.getKey().getBlockPos())) continue;
            ItemStack stored = cell.getKey().getInventory().getStackInSlot(0);
            if(stored.isEmpty() || !ItemStack.isSameItemSameComponents(cell.getValue(), stored)) continue;
            WorkerResourceKey key = new WorkerResourceKey(WorkerResourceType.ITEM,
                    BuiltInRegistries.ITEM.getKey(stored.getItem()));
            occupied.merge(key, (long)stored.getCount(), Long::sum);
        }
        List<Long> credited = new ArrayList<>(Collections.nCopies(plan.inputs().size(), 0L));
        for(int idx = 0; idx < plan.inputs().size(); idx++){
            WorkerRecipePlan.Input input = plan.inputs().get(idx);
            for(WorkerResourceKey alternative : input.alternatives()){
                long available = occupied.getOrDefault(alternative, 0L);
                long needed = batches > Long.MAX_VALUE / input.amount()
                        ? Long.MAX_VALUE : batches * input.amount();
                long amount = Math.min(available, needed);
                if(amount <= 0L) continue;
                credited.set(idx, amount);
                occupied.put(alternative, available - amount);
                break;
            }
        }
        return List.copyOf(credited);
    }

    @Override public List<IItemHandler> itemOutputs(WorkerRecipePlan plan){
        List<IItemHandler> outputs = new ArrayList<>();
        for(var crafter : chain()){
            if(RecipeGridHandler.getTargetingCrafter(crafter) != null) continue;
            BlockPos output = crafter.getBlockPos().relative(crafter.getTargetDirection());
            outputs.addAll(WorkerContainerAccess.itemHandlers(ctx.level(), output, crafter.getTargetDirection().getOpposite()));
        }
        return outputs;
    }

    @Override public void start(WorkerRecipePlan plan){ root.checkCompletedRecipe(true); }

    private Map<MechanicalCrafterBlockEntity, ItemStack> layout(WorkerRecipePlan plan){
        var holder = ctx.level().getRecipeManager().byKey(plan.recipeId()).orElse(null);
        if(holder == null) return Map.of();
        CraftingInput input = WorkerCraftingGrid.create(holder.value(), plan);
        if(input.isEmpty()) return Map.of();
        Map<Long, MechanicalCrafterBlockEntity> grid = grid();
        for(int[] origin : placements(grid, input)){
            Map<MechanicalCrafterBlockEntity, ItemStack> selected = new LinkedHashMap<>();
            for(int y = 0; y < input.height(); y++){
                for(int x = 0; x < input.width(); x++){
                    ItemStack stack = input.getItem(x, y);
                    if(!stack.isEmpty()) selected.put(grid.get(key(origin[0] + x, origin[1] + y)), stack);
                }
            }
            return selected;
        }
        return Map.of();
    }

    private Map<Long, MechanicalCrafterBlockEntity> grid(){
        Map<Long, MechanicalCrafterBlockEntity> grid = new LinkedHashMap<>();
        Direction right = root.getBlockState().getValue(HorizontalKineticBlock.HORIZONTAL_FACING).getCounterClockWise();
        for(var crafter : chain()){
            BlockPos offset = crafter.getBlockPos().subtract(root.getBlockPos());
            int x = offset.getX() * right.getStepX() + offset.getZ() * right.getStepZ();
            grid.put(key(x, -offset.getY()), crafter);
        }
        return grid;
    }

    // Empty recipe cells do not require physical crafters, including missing outer corners
    private static List<int[]> placements(Map<Long, MechanicalCrafterBlockEntity> grid, CraftingInput input){
        List<int[]> origins = new ArrayList<>();
        java.util.Set<Long> candidates = new java.util.LinkedHashSet<>();
        for(long pos : grid.keySet()){
            int gridX = (int)(pos >> 32);
            int gridY = (int)pos;
            for(int y = 0; y < input.height(); y++){
                for(int x = 0; x < input.width(); x++){
                    if(!input.getItem(x, y).isEmpty()) candidates.add(key(gridX - x, gridY - y));
                }
            }
        }
        for(long candidate : candidates){
            int startX = (int)(candidate >> 32);
            int startY = (int)candidate;
            boolean fits = true;
            for(int y = 0; y < input.height() && fits; y++){
                for(int x = 0; x < input.width(); x++){
                    if(!input.getItem(x, y).isEmpty() && !grid.containsKey(key(startX + x, startY + y))){ fits = false; break; }
                }
            }
            if(fits) origins.add(new int[]{startX, startY});
        }
        return origins;
    }

    private static long key(int x, int y){ return (long)x << 32 | y & 0xffffffffL; }
}
