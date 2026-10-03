package com.rieno.gadgetsandgizmos.lib.worker;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.simibubi.create.content.kinetics.crafter.MechanicalCraftingRecipe;
import com.simibubi.create.content.kinetics.deployer.ItemApplicationRecipe;
import com.simibubi.create.content.kinetics.deployer.ManualApplicationRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import com.rieno.gadgetsandgizmos.lib.create.worker.CreateWorkerSequences;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import net.neoforged.neoforge.common.ItemAbilities;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

// Adapt the live recipe manager while preserving ingredient alternatives for backwards planning
public final class WorkerRecipeCatalog{
    public static final ResourceLocation WORLD_ITEM_APPLICATION = ResourceLocation.fromNamespaceAndPath(
            "createthrusters", "world_item_application");
    public static final ResourceLocation WORLD_AXE_STRIP = ResourceLocation.fromNamespaceAndPath(
            "createthrusters", "world_axe_strip");
    private static final Map<ResourceLocation, Adapter> ADAPTERS = new LinkedHashMap<>();
    private static final List<ResourceLocation> PORTABLE_CRAFTING_TOOLS = new ArrayList<>();
    private static volatile Snapshot cached;

    private record Snapshot(RecipeManager manager, WorkerRecipeIndex index){}

    static{
        ADAPTERS.put(WORLD_ITEM_APPLICATION, new WorldInteractionAdapter(false));
        ADAPTERS.put(WORLD_AXE_STRIP, new WorldInteractionAdapter(true));
    }

    private WorkerRecipeCatalog(){}

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Register recipes with custom ingredients, catalysts or outputs under their recipe type
    public static void register(ResourceLocation type, Adapter adapter){
        if(type == null || adapter == null) throw new IllegalArgumentException("A recipe adapter needs a type and implementation");
        if(ADAPTERS.putIfAbsent(type, adapter) != null) throw new IllegalArgumentException("Duplicate worker recipe adapter: " + type);
        invalidate();
    }

    // A portable workbench provides a second, worker-executable route for ordinary 3x3 recipes.
    public static synchronized void registerPortableCraftingTool(ResourceLocation itemId){
        if(itemId == null || PORTABLE_CRAFTING_TOOLS.contains(itemId)) return;
        register(itemId, new PortableCraftingToolAdapter(itemId));
        PORTABLE_CRAFTING_TOOLS.add(itemId);
        invalidate();
    }

    // Rebuild after recipe or tag reloads so ingredient alternatives stay current
    public static synchronized void invalidate(){
        cached = null;
    }

    // Resolve live recipes and tags once for each loaded recipe manager
    public static WorkerRecipeIndex index(Level level){
        RecipeManager manager = level.getRecipeManager();
        Snapshot snapshot = cached;
        if(snapshot != null && snapshot.manager() == manager) return snapshot.index();
        synchronized(WorkerRecipeCatalog.class){
            snapshot = cached;
            if(snapshot != null && snapshot.manager() == manager) return snapshot.index();
            List<WorkerRecipeDefinition> definitions = new ArrayList<>();
            for(RecipeHolder<?> holder : manager.getRecipes()){
                ResourceLocation type = BuiltInRegistries.RECIPE_TYPE.getKey(holder.value().getType());
                Adapter adapter = ADAPTERS.get(type);
                definitions.addAll(adapter == null ? standard(level, holder, type) : adapter.recipes(level, holder));
            }
            definitions.addAll(axeStrippingRecipes());
            WorkerRecipeIndex index = new WorkerRecipeIndex(definitions);
            cached = new Snapshot(manager, index);
            return index;
        }
    }

    public static List<WorkerRecipeDefinition> recipes(Level level){
        return index(level).definitions();
    }

    @SuppressWarnings("rawtypes")
    private static List<WorkerRecipeDefinition> standard(Level level, RecipeHolder<?> holder, ResourceLocation type){
        var recipe = holder.value();
        if(recipe instanceof SequencedAssemblyRecipe assembly){
            return List.of(CreateWorkerSequences.definition(level, holder, assembly));
        }
        WorkerRecipePlan.Operation operation = WorkerRecipePlan.Operation.PROCESSING;
        if(recipe instanceof MechanicalCraftingRecipe) operation = WorkerRecipePlan.Operation.CRAFTING;
        else if(recipe instanceof CraftingRecipe crafting){
            operation = crafting.canCraftInDimensions(2, 2)
                    ? WorkerRecipePlan.Operation.WORKER_CRAFTING : WorkerRecipePlan.Operation.CRAFTING;
        }else if(recipe instanceof StonecutterRecipe) operation = WorkerRecipePlan.Operation.CRAFTING;
        List<WorkerRecipeDefinition.Ingredient> ingredients = new ArrayList<>();
        for(Ingredient ingredient : recipe.getIngredients()){
            if(ingredient == Ingredient.EMPTY) continue;
            List<WorkerResourceKey> alternatives = itemAlternatives(ingredient);
            if(alternatives.isEmpty() && ingredient.isEmpty()) continue;
            ingredients.add(new WorkerRecipeDefinition.Ingredient(alternatives, 1L));
        }
        if(recipe instanceof ProcessingRecipe processing){
            for(Object val : processing.getFluidIngredients()){
                SizedFluidIngredient ingredient = (SizedFluidIngredient) val;
                ingredients.add(new WorkerRecipeDefinition.Ingredient(Arrays.stream(ingredient.getFluids())
                        .filter(stack -> !stack.isEmpty()).map(stack -> new WorkerResourceKey(WorkerResourceType.FLUID,
                                BuiltInRegistries.FLUID.getKey(stack.getFluid()))).toList(), ingredient.amount()));
            }
        }
        List<WorkerRecipeDefinition.Ingredient> originalIngredients = List.copyOf(ingredients);
        // Item-application recipes run in Create's deployer even though their recipe type has
        // a different id. Resolve the physical processor separately from the recipe identity.
        ResourceLocation processorType = recipe instanceof ItemApplicationRecipe
                ? ResourceLocation.fromNamespaceAndPath("create", "deploying") : type;
        // Load a deployer's held item or a spout's fluid before releasing the moving workpiece.
        if(!ingredients.isEmpty() && processorType != null
                && (processorType.toString().equals("create:deploying")
                || processorType.toString().equals("create:filling"))){
            ingredients.add(ingredients.removeFirst());
        }
        if(ingredients.isEmpty()) return List.of();
        List<WorkerRecipeDefinition> definitions = new ArrayList<>();
        ItemStack output = recipe.getResultItem(level.registryAccess());
        if(!output.isEmpty()){
            WorkerResourceKey result = new WorkerResourceKey(WorkerResourceType.ITEM,
                    BuiltInRegistries.ITEM.getKey(output.getItem()));
            definitions.add(new WorkerRecipeDefinition(holder.id(), processorType, operation, ingredients,
                    result, output.getCount()));
            if(recipe instanceof ManualApplicationRecipe && type != null
                    && type.toString().equals("create:item_application")
                    && originalIngredients.size() == 2
                    && output.getItem() instanceof BlockItem
                    && originalIngredients.getFirst().alternatives().stream().allMatch(input ->
                    BuiltInRegistries.ITEM.get(input.id()) instanceof BlockItem))
                definitions.add(new WorkerRecipeDefinition(holder.id(), WORLD_ITEM_APPLICATION,
                        WorkerRecipePlan.Operation.WORKER_CRAFTING, originalIngredients,
                        result, output.getCount()));
            if(recipe instanceof CraftingRecipe crafting && !crafting.canCraftInDimensions(2, 2)){
                for(ResourceLocation toolId : PORTABLE_CRAFTING_TOOLS){
                    if(BuiltInRegistries.ITEM.get(toolId) == net.minecraft.world.item.Items.AIR) continue;
                    List<WorkerRecipeDefinition.Ingredient> portableInputs = new ArrayList<>(ingredients);
                    portableInputs.add(new WorkerRecipeDefinition.Ingredient(List.of(
                            new WorkerResourceKey(WorkerResourceType.ITEM, toolId)), 1L));
                    definitions.add(new WorkerRecipeDefinition(holder.id(), toolId,
                            WorkerRecipePlan.Operation.WORKER_CRAFTING, portableInputs, result, output.getCount()));
                }
            }
        }
        if(recipe instanceof ProcessingRecipe processing){
            for(Object val : processing.getFluidResults()){
                FluidStack fluid = (FluidStack) val;
                if(fluid.isEmpty()) continue;
                definitions.add(new WorkerRecipeDefinition(holder.id(), processorType, operation, ingredients,
                        new WorkerResourceKey(WorkerResourceType.FLUID, BuiltInRegistries.FLUID.getKey(fluid.getFluid())), fluid.getAmount()));
            }
        }
        return definitions;
    }

    // Some modded ingredients accept registered items but do not enumerate them through getItems().
    // Resolve those opaque ingredients once while the startup recipe graph is built.
    private static List<WorkerResourceKey> itemAlternatives(Ingredient ingredient){
        LinkedHashSet<WorkerResourceKey> accepted = new LinkedHashSet<>();
        for(ItemStack stack : ingredient.getItems()){
            if(!stack.isEmpty()) accepted.add(new WorkerResourceKey(WorkerResourceType.ITEM,
                    BuiltInRegistries.ITEM.getKey(stack.getItem())));
        }
        if(!accepted.isEmpty()) return List.copyOf(accepted);
        for(var item : BuiltInRegistries.ITEM){
            ItemStack sample = item.getDefaultInstance();
            if(!sample.isEmpty() && ingredient.test(sample))
                accepted.add(new WorkerResourceKey(WorkerResourceType.ITEM,
                        BuiltInRegistries.ITEM.getKey(item)));
        }
        return List.copyOf(accepted);
    }

    // Derive real axe transformations from the registered blocks, without a hardcoded wood list.
    private static List<WorkerRecipeDefinition> axeStrippingRecipes(){
        List<WorkerResourceKey> axes = BuiltInRegistries.ITEM.stream()
                .filter(item -> item.canPerformAction(item.getDefaultInstance(), ItemAbilities.AXE_STRIP))
                .map(item -> new WorkerResourceKey(WorkerResourceType.ITEM,
                        BuiltInRegistries.ITEM.getKey(item))).toList();
        if(axes.isEmpty()) return List.of();
        List<WorkerRecipeDefinition> definitions = new ArrayList<>();
        for(var block : BuiltInRegistries.BLOCK){
            var stripped = AxeItem.getAxeStrippingState(block.defaultBlockState());
            if(stripped == null || !(block.asItem() instanceof BlockItem)
                    || !(stripped.getBlock().asItem() instanceof BlockItem)) continue;
            ResourceLocation source = BuiltInRegistries.ITEM.getKey(block.asItem());
            ResourceLocation result = BuiltInRegistries.ITEM.getKey(stripped.getBlock().asItem());
            if(source.equals(result)) continue;
            definitions.add(new WorkerRecipeDefinition(ResourceLocation.fromNamespaceAndPath(
                    "createthrusters", "world/axe_strip/" + source.getNamespace() + "/" + source.getPath()),
                    WORLD_AXE_STRIP, WorkerRecipePlan.Operation.WORKER_CRAFTING,
                    List.of(new WorkerRecipeDefinition.Ingredient(List.of(new WorkerResourceKey(
                                    WorkerResourceType.ITEM, source)), 1L),
                            new WorkerRecipeDefinition.Ingredient(axes, 1L)),
                    new WorkerResourceKey(WorkerResourceType.ITEM, result), 1L));
        }
        return definitions;
    }

    // Resolve an embedded processing stage using the original persistent recipe id
    public static Recipe<?> recipe(Level level, WorkerRecipePlan plan){
        if(plan == null) return null;
        var holder = level.getRecipeManager().byKey(plan.recipeId()).orElse(null);
        if(holder == null) return null;
        return holder.value() instanceof SequencedAssemblyRecipe assembly
                ? CreateWorkerSequences.recipe(plan, assembly) : holder.value();
    }

    // Let portable tool recipes produce their real output without requiring a placed block machine.
    public static WorkerCraftingGrid.Result craftWithAdapter(Level level, WorkerRecipePlan plan,
                                                              Map<WorkerResourceKey, ItemStack> tools){
        Adapter adapter = plan == null ? null : ADAPTERS.get(plan.processorType());
        return adapter == null ? WorkerCraftingGrid.Result.EMPTY : adapter.craft(level, plan, tools);
    }

    public static long reusableToolCredit(WorkerRecipeDefinition recipe, int ingredientIndex,
                                          Map<WorkerResourceKey, Long> equippedStock){
        Adapter adapter = recipe == null ? null : ADAPTERS.get(recipe.processorType());
        return adapter == null ? 0L : adapter.reusableToolCredit(recipe, ingredientIndex, equippedStock);
    }

    public static long maximumToolBatch(WorkerRecipePlan plan){
        Adapter adapter = plan == null ? null : ADAPTERS.get(plan.processorType());
        return adapter == null ? Long.MAX_VALUE : adapter.maximumBatch(plan);
    }

    public static boolean isPortableCraftingTool(ResourceLocation itemId){
        return itemId != null && PORTABLE_CRAFTING_TOOLS.contains(itemId);
    }

    // Item-operated recipes can execute virtually; in-world block transformations must remain physical.
    public static boolean isPortableToolPlan(WorkerRecipePlan plan){
        return plan != null && plan.operation() == WorkerRecipePlan.Operation.WORKER_CRAFTING
                && !WORLD_AXE_STRIP.equals(plan.processorType())
                && !WORLD_ITEM_APPLICATION.equals(plan.processorType())
                && !plan.inputs().isEmpty() && ADAPTERS.containsKey(plan.processorType())
                && isReusableToolInput(plan, plan.inputs().size() - 1);
    }

    public static boolean isReusableToolInput(WorkerRecipePlan plan, int ingredientIndex){
        Adapter adapter = plan == null ? null : ADAPTERS.get(plan.processorType());
        return adapter != null && adapter.isReusableToolInput(plan, ingredientIndex);
    }

    public static List<WorkerRecipePlan> stages(Level level, WorkerRecipePlan plan){
        var holder = level.getRecipeManager().byKey(plan.recipeId()).orElse(null);
        if(holder != null && holder.value() instanceof SequencedAssemblyRecipe assembly){
            return CreateWorkerSequences.stages(level, plan, assembly);
        }
        Adapter adapter = ADAPTERS.get(plan.processorType());
        return adapter == null ? List.of(plan) : adapter.stages(level, plan);
    }

    // Validate every stage before committing a composite recipe to the schedule
    public static boolean supported(Level level, WorkerRecipeDefinition recipe,
                                     java.util.function.Predicate<WorkerRecipePlan> supported){
        if(recipe.ingredients().stream().anyMatch(input -> input.alternatives().isEmpty())) return false;
        WorkerRecipePlan plan = new WorkerRecipePlan(recipe.recipeId(), recipe.processorType(), recipe.operation(),
                recipe.ingredients().stream().map(input -> new WorkerRecipePlan.Input(input.alternatives().getFirst(),
                        input.amount(), input.alternatives())).toList(), recipe.result(), recipe.resultAmount());
        if(supported.test(plan)) return true;
        for(int idx = 0; idx < recipe.ingredients().size(); idx++){
            WorkerRecipeDefinition.Ingredient ingredient = recipe.ingredients().get(idx);
            for(int alternative = 1; alternative < ingredient.alternatives().size(); alternative++){
                List<WorkerRecipePlan.Input> inputs = new ArrayList<>();
                for(WorkerRecipeDefinition.Ingredient selected : recipe.ingredients()){
                    inputs.add(new WorkerRecipePlan.Input(selected.alternatives().getFirst(), selected.amount(),
                            selected.alternatives()));
                }
                inputs.set(idx, new WorkerRecipePlan.Input(ingredient.alternatives().get(alternative),
                        ingredient.amount(), ingredient.alternatives()));
                WorkerRecipePlan selected = new WorkerRecipePlan(recipe.recipeId(), recipe.processorType(),
                        recipe.operation(), inputs, recipe.result(), recipe.resultAmount());
                if(supported.test(selected)) return true;
            }
        }
        List<WorkerRecipePlan> stages = stages(level, plan);
        return !stages.isEmpty() && stages.stream().allMatch(supported);
    }

    public static WorkerRecipeChain expand(Level level, WorkerRecipeChain chain){
        return expand(level, chain, plan -> false);
    }

    // Leave a composite recipe intact when a complete machine line can execute it directly
    public static WorkerRecipeChain expand(Level level, WorkerRecipeChain chain,
                                            java.util.function.Predicate<WorkerRecipePlan> completeMachine){
        List<WorkerRecipeChain.Step> expanded = new ArrayList<>();
        for(var step : chain.steps()){
            List<WorkerRecipePlan> stages = completeMachine.test(step.plan()) ? List.of(step.plan()) : stages(level, step.plan());
            if(stages.isEmpty()) return new WorkerRecipeChain(List.of());
            long batches = (step.requestedAmount() - 1L) / step.plan().resultAmount() + 1L;
            for(int idx = 0; idx < stages.size(); idx++){
                WorkerRecipePlan plan = stages.get(idx);
                expanded.add(new WorkerRecipeChain.Step(plan, idx == stages.size() - 1
                        ? step.requestedAmount() : batches * plan.resultAmount()));
            }
        }
        return new WorkerRecipeChain(expanded);
    }

    public static boolean matchesInput(Level level, WorkerRecipePlan plan, ItemStack stack){
        if(plan == null || plan.stage() < 0) return true;
        var holder = level.getRecipeManager().byKey(plan.recipeId()).orElse(null);
        return holder != null && (!(holder.value() instanceof SequencedAssemblyRecipe assembly)
                || CreateWorkerSequences.matchesInput(plan, assembly, stack));
    }

    // Replace a selected item only when the live recipe accepts both items in the same ingredient
    public static WorkerRecipePlan substituteInput(Level level, WorkerRecipePlan plan, int idx,
                                                    WorkerResourceKey resource){
        if(level == null || plan == null || resource == null || idx < 0 || idx >= plan.inputs().size()) return null;
        WorkerRecipePlan.Input selected = plan.inputs().get(idx);
        if(selected.resource().equals(resource)) return plan;
        if(selected.resource().type() != WorkerResourceType.ITEM || resource.type() != WorkerResourceType.ITEM) return null;
        Recipe<?> recipe = recipe(level, plan);
        if(recipe == null) return null;
        ItemStack original = new ItemStack(BuiltInRegistries.ITEM.get(selected.resource().id()));
        ItemStack substitute = new ItemStack(BuiltInRegistries.ITEM.get(resource.id()));
        if(original.isEmpty() || substitute.isEmpty() || recipe.getIngredients().stream()
                .noneMatch(ingredient -> ingredient.test(original) && ingredient.test(substitute))) return null;
        List<WorkerRecipePlan.Input> inputs = new ArrayList<>(plan.inputs());
        inputs.set(idx, new WorkerRecipePlan.Input(resource, selected.amount(), selected.alternatives()));
        WorkerRecipePlan updated = new WorkerRecipePlan(plan.recipeId(), plan.processorType(), plan.operation(),
                inputs, plan.result(), plan.resultAmount(), plan.stage());
        if((plan.operation() == WorkerRecipePlan.Operation.CRAFTING
                || plan.operation() == WorkerRecipePlan.Operation.WORKER_CRAFTING)
                && (recipe instanceof CraftingRecipe || recipe instanceof StonecutterRecipe)){
            ItemStack output = WorkerCraftingGrid.craft(level, updated).output();
            if(output.isEmpty() || !BuiltInRegistries.ITEM.getKey(output.getItem()).equals(plan.result().id())) return null;
        }
        return updated;
    }

    public static boolean matchesOutput(Level level, WorkerRecipePlan plan, ItemStack stack){
        if(plan == null || plan.stage() < 0) return true;
        var holder = level.getRecipeManager().byKey(plan.recipeId()).orElse(null);
        return holder != null && (!(holder.value() instanceof SequencedAssemblyRecipe assembly)
                || CreateWorkerSequences.matchesOutput(plan, assembly, stack));
    }

    // Recognize an intermediate that must return to the first station of its sequence
    public static boolean recirculates(Level level, WorkerRecipePlan plan, ItemStack stack){
        if(level == null || plan == null || stack == null || stack.isEmpty()) return false;
        var holder = level.getRecipeManager().byKey(plan.recipeId()).orElse(null);
        return holder != null && holder.value() instanceof SequencedAssemblyRecipe assembly
                && CreateWorkerSequences.recirculates(plan, assembly, stack);
    }

    // Recognize a completed assembly's chance byproduct so consumers may store it and schedule another attempt
    public static boolean alternateResult(Level level, WorkerRecipePlan plan, ItemStack stack){
        if(plan == null || stack.isEmpty()) return false;
        var holder = level.getRecipeManager().byKey(plan.recipeId()).orElse(null);
        return holder != null && holder.value() instanceof SequencedAssemblyRecipe assembly
                && (plan.stage() < 0 || plan.stage() + 1 == assembly.getLoops() * assembly.getSequence().size())
                && !BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(plan.result().id())
                && assembly.resultPool.stream().anyMatch(output -> ItemStack.isSameItemSameComponents(output.getStack(), stack));
    }

    // Keep chance-based assemblies to one live attempt so each success or failure is accounted for
    public static boolean hasChanceResult(Level level, WorkerRecipePlan plan){
        return plan != null && recipe(level, plan) instanceof SequencedAssemblyRecipe assembly
                && assembly.getOutputChance() < 1F;
    }

    public interface Adapter{
        List<WorkerRecipeDefinition> recipes(Level level, RecipeHolder<?> recipe);
        default List<WorkerRecipePlan> stages(Level level, WorkerRecipePlan plan){ return List.of(plan); }
        default WorkerCraftingGrid.Result craft(Level level, WorkerRecipePlan plan,
                                                Map<WorkerResourceKey, ItemStack> tools){
            return WorkerCraftingGrid.Result.EMPTY;
        }
        default long reusableToolCredit(WorkerRecipeDefinition recipe, int ingredientIndex,
                                        Map<WorkerResourceKey, Long> equippedStock){ return 0L; }
        default long maximumBatch(WorkerRecipePlan plan){ return Long.MAX_VALUE; }
        default boolean isReusableToolInput(WorkerRecipePlan plan, int ingredientIndex){ return false; }
    }

    private record PortableCraftingToolAdapter(ResourceLocation toolId) implements Adapter{
        @Override public List<WorkerRecipeDefinition> recipes(Level level, RecipeHolder<?> recipe){
            return List.of();
        }

        @Override public WorkerCraftingGrid.Result craft(Level level, WorkerRecipePlan plan,
                                                         Map<WorkerResourceKey, ItemStack> tools){
            if(plan.inputs().isEmpty() || !isReusableToolInput(plan, plan.inputs().size() - 1))
                return WorkerCraftingGrid.Result.EMPTY;
            WorkerResourceKey key = plan.inputs().getLast().resource();
            ItemStack tool = tools.get(key);
            if(tool == null || tool.isEmpty()) return WorkerCraftingGrid.Result.EMPTY;
            WorkerRecipePlan ordinary = new WorkerRecipePlan(plan.recipeId(),
                    ResourceLocation.fromNamespaceAndPath("minecraft", "crafting"),
                    WorkerRecipePlan.Operation.CRAFTING,
                    plan.inputs().subList(0, plan.inputs().size() - 1),
                    plan.result(), plan.resultAmount(), plan.stage());
            WorkerCraftingGrid.Result crafted = WorkerCraftingGrid.craft(level, ordinary);
            if(crafted.output().isEmpty()) return crafted;
            List<ItemStack> remainders = new ArrayList<>(crafted.remainders());
            remainders.add(tool.copy());
            return new WorkerCraftingGrid.Result(crafted.output(), remainders);
        }

        @Override public long reusableToolCredit(WorkerRecipeDefinition recipe, int ingredientIndex,
                                                 Map<WorkerResourceKey, Long> stock){
            return ingredientIndex == recipe.ingredients().size() - 1
                    && stock.getOrDefault(new WorkerResourceKey(WorkerResourceType.ITEM, toolId), 0L) > 0L
                    ? Long.MAX_VALUE : 0L;
        }

        @Override public long maximumBatch(WorkerRecipePlan plan){ return 1L; }

        @Override public boolean isReusableToolInput(WorkerRecipePlan plan, int ingredientIndex){
            return plan != null && ingredientIndex == plan.inputs().size() - 1
                    && plan.inputs().get(ingredientIndex).resource().id().equals(toolId);
        }
    }

    private record WorldInteractionAdapter(boolean needsAxe) implements Adapter{
        @Override public List<WorkerRecipeDefinition> recipes(Level level, RecipeHolder<?> recipe){
            return List.of();
        }

        @Override public long reusableToolCredit(WorkerRecipeDefinition recipe, int ingredientIndex,
                                                 Map<WorkerResourceKey, Long> stock){
            if(!needsAxe || ingredientIndex != recipe.ingredients().size() - 1) return 0L;
            return recipe.ingredients().get(ingredientIndex).alternatives().stream()
                    .anyMatch(key -> stock.getOrDefault(key, 0L) > 0L) ? Long.MAX_VALUE : 0L;
        }

        @Override public boolean isReusableToolInput(WorkerRecipePlan plan, int ingredientIndex){
            return needsAxe && ingredientIndex == plan.inputs().size() - 1;
        }

        @Override public long maximumBatch(WorkerRecipePlan plan){ return 1L; }
    }
}
