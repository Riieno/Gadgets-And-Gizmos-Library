package com.rieno.gadgetsandgizmos.lib.worker;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import net.neoforged.neoforge.items.wrapper.RangedWrapper;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

// Use real furnace slots and inventory-free vanilla crafting stations
final class VanillaWorkerMachines implements WorkerMachineRegistry.Adapter{
    @Override
    public WorkerMachine resolve(WorkerMachineRegistry.Context ctx){
        var state = ctx.level().getBlockState(ctx.pos());
        if(state.getBlock() instanceof net.minecraft.world.level.block.CraftingTableBlock) return new Station(ctx, "minecraft:crafting");
        if(state.getBlock() instanceof net.minecraft.world.level.block.StonecutterBlock) return new Station(ctx, "minecraft:stonecutting");
        if(ctx.level().getBlockEntity(ctx.pos()) instanceof AbstractFurnaceBlockEntity furnace){
            String type = furnace instanceof net.minecraft.world.level.block.entity.BlastFurnaceBlockEntity ? "minecraft:blasting"
                    : furnace instanceof net.minecraft.world.level.block.entity.SmokerBlockEntity ? "minecraft:smoking" : "minecraft:smelting";
            return new Furnace(ctx, furnace, type);
        }
        return null;
    }

    private record Station(WorkerMachineRegistry.Context ctx, String type) implements WorkerMachine{
        @Override public boolean maySupportProcessor(ResourceLocation processorType){
            return processorType != null && type.equals(processorType.toString());
        }
        @Override public boolean mayHavePreloadedInputs(WorkerRecipeDefinition recipe){ return false; }
        @Override public boolean supports(WorkerRecipePlan plan){
            if(plan == null || !type.equals(plan.processorType().toString())) return false;
            var recipe = WorkerRecipeCatalog.recipe(ctx.level(), plan);
            return recipe instanceof net.minecraft.world.item.crafting.CraftingRecipe crafting
                    ? crafting.canCraftInDimensions(3, 3) : recipe instanceof net.minecraft.world.item.crafting.StonecutterRecipe;
        }
        @Override public boolean virtualCrafting(){ return true; }
        @Override public List<BlockPos> members(){ return List.of(ctx.pos()); }
    }

    private record Furnace(WorkerMachineRegistry.Context ctx, AbstractFurnaceBlockEntity furnace,
                           String type) implements WorkerMachine{
        @Override public boolean mayProbePreloadedInputsBeforeSupport(){ return true; }
        @Override public boolean maySupportProcessor(ResourceLocation processorType){
            return processorType != null && type.equals(processorType.toString());
        }
        @Override public boolean mayHavePreloadedInputs(WorkerRecipeDefinition recipe){
            return recipe != null && type.equals(recipe.processorType().toString())
                    && !furnace.getItem(0).isEmpty();
        }
        @Override public boolean supports(WorkerRecipePlan plan){
            if(plan == null || !type.equals(plan.processorType().toString()) || plan.inputs().size() != 1
                    || plan.inputs().getFirst().resource().type() != WorkerResourceType.ITEM) return false;
            var recipe = WorkerRecipeCatalog.recipe(ctx.level(), plan);
            if(!(recipe instanceof net.minecraft.world.item.crafting.AbstractCookingRecipe cooking)) return false;
            ItemStack input = new ItemStack(BuiltInRegistries.ITEM.get(plan.inputs().getFirst().resource().id()));
            ItemStack output = cooking.getResultItem(ctx.level().registryAccess());
            return !input.isEmpty() && cooking.getIngredients().getFirst().test(input)
                    && !output.isEmpty() && BuiltInRegistries.ITEM.getKey(output.getItem()).equals(plan.result().id());
        }
        @Override public List<BlockPos> members(){ return List.of(ctx.pos()); }
        @Override public List<Long> preloadedInputs(WorkerRecipePlan plan, WorkerArea area){
            if(plan == null || plan.inputs().size() != 1 || area != null && !area.contains(ctx.pos()))
                return List.of();
            ItemStack input = furnace.getItem(0);
            if(input.isEmpty() || !plan.inputs().getFirst().alternatives().contains(new WorkerResourceKey(
                    WorkerResourceType.ITEM, BuiltInRegistries.ITEM.getKey(input.getItem())))) return List.of(0L);
            return List.of((long)input.getCount());
        }
        @Override public List<IItemHandler> itemInputs(WorkerRecipePlan plan){
            return List.of(new RangedWrapper(new InvWrapper(furnace), plan == null ? 1 : 0, plan == null ? 2 : 1));
        }
        @Override public List<IItemHandler> itemOutputs(WorkerRecipePlan plan){
            return List.of(new RangedWrapper(new InvWrapper(furnace), 1, 3));
        }
        @Override public Supply supply(WorkerRecipePlan plan, Map<WorkerResourceKey, Long> available){
            return supply(plan, available, 1L);
        }
        @Override public Supply supply(WorkerRecipePlan plan, Map<WorkerResourceKey, Long> available, long batches){
            RecipeType<?> recipeType = BuiltInRegistries.RECIPE_TYPE.get(plan.processorType());
            var recipe = WorkerRecipeCatalog.recipe(ctx.level(), plan);
            int duration = recipe instanceof net.minecraft.world.item.crafting.AbstractCookingRecipe cooking
                    ? cooking.getCookingTime() : 200;
            long neededBurn = duration * Math.max(1L, batches);
            ItemStack fuel = furnace.getItem(1);
            int burnTime = fuel.getBurnTime(recipeType);
            if(!fuel.isEmpty() && burnTime <= 0) return new Supply(new WorkerResourceKey(WorkerResourceType.ITEM,
                    BuiltInRegistries.ITEM.getKey(fuel.getItem())), fuel.getCount(), "Returning furnace container", true);
            if((long)fuel.getCount() * burnTime >= neededBurn) return Supply.READY;
            return available.entrySet().stream().filter(entry -> entry.getValue() > 0L
                            && entry.getKey().type() == WorkerResourceType.ITEM)
                    .filter(entry -> fuel.isEmpty() || BuiltInRegistries.ITEM.getKey(fuel.getItem()).equals(entry.getKey().id()))
                    .filter(entry -> new ItemStack(BuiltInRegistries.ITEM.get(entry.getKey().id())).getBurnTime(recipeType) > 0)
                    .sorted(Comparator.comparingInt(entry -> -new ItemStack(
                            BuiltInRegistries.ITEM.get(entry.getKey().id())).getBurnTime(recipeType)))
                    .map(entry -> new Supply(entry.getKey(), Math.max(1L, (neededBurn - 1L)
                            / new ItemStack(BuiltInRegistries.ITEM.get(entry.getKey().id())).getBurnTime(recipeType)
                            + 1L - fuel.getCount()), "Refuelling furnace"))
                    .findFirst().orElse(new Supply(null, 0L, "No furnace fuel is available in the selected sources"));
        }
    }
}
