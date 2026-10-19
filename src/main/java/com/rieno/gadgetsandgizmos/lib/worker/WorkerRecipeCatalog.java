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
import com.rieno.gadgetsandgizmos.lib.util.DeferredLookup;
import com.rieno.gadgetsandgizmos.lib.util.DeferredWorkScheduler;
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
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.IdentityHashMap;
import java.util.concurrent.CompletableFuture;

// Adapt the live recipe manager while preserving ingredient alternatives for backwards planning
@EventBusSubscriber(modid = "gadgetsngizmos")
public final class WorkerRecipeCatalog{
    public static final ResourceLocation WORLD_ITEM_APPLICATION = ResourceLocation.fromNamespaceAndPath(
            "createthrusters", "world_item_application");
    public static final ResourceLocation WORLD_AXE_STRIP = ResourceLocation.fromNamespaceAndPath(
            "createthrusters", "world_axe_strip");
    private static final Map<ResourceLocation, Adapter> ADAPTERS = new LinkedHashMap<>();
    private static final List<ResourceLocation> PORTABLE_CRAFTING_TOOLS = new ArrayList<>();
    private static final Map<RecipeManager, CompletableFuture<WorkerRecipeIndex>> PENDING = new IdentityHashMap<>();
    private static final Map<RecipeManager, CompletableFuture<RecipeSources>> SOURCES = new IdentityHashMap<>();
    private static final Map<RecipeManager, DeferredLookup<WorkerResourceKey, WorkerRecipeIndex>> LOOKUPS = new IdentityHashMap<>();
    private static final Map<RecipeManager, WorkerRecipeSource> RECIPE_SOURCES = new IdentityHashMap<>();
    private static final Map<Ingredient, List<WorkerResourceKey>> ITEM_ALTERNATIVES = java.util.Collections.synchronizedMap(new IdentityHashMap<>());
    private static final Map<Ingredient, WorkerRecipeDefinition.Ingredient> ITEM_INGREDIENTS = new IdentityHashMap<>();
    private static final Map<RecipeManager, Map<WorkerResourceKey, List<RecipeHolder<?>>>> OUTPUT_RECIPES = new IdentityHashMap<>();
    private static final Map<RecipeManager, WorkerRecipeIndex> INDEXES = new IdentityHashMap<>();
    private static volatile long revision;

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
        INDEXES.clear();
        revision++;
        PENDING.values().forEach(res -> res.cancel(false));
        PENDING.clear();
        SOURCES.values().forEach(res -> res.cancel(false));
        SOURCES.clear();
        LOOKUPS.values().forEach(DeferredLookup::clear);
        LOOKUPS.clear();
        RECIPE_SOURCES.clear();
        ITEM_ALTERNATIVES.clear();
        ITEM_INGREDIENTS.clear();
        OUTPUT_RECIPES.clear();
        WorkerModdedRecipes.invalidate();
        WorkerRecipeRoutes.invalidate();
    }

    @SubscribeEvent
    public static void reload(AddReloadListenerEvent evt){
        evt.addListener((PreparableReloadListener)(barrier, resources, preparations, reload, background, game) ->
                CompletableFuture.completedFuture(Boolean.TRUE).thenCompose(barrier::wait)
                        .thenRunAsync(WorkerRecipeCatalog::invalidate, game));
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent evt){ invalidate(); }

    // Warm the new generation after the server finishes applying recipe and tag reloads
    @SubscribeEvent
    public static void synced(OnDatapackSyncEvent evt){
        if(evt.getPlayer() == null) prepareRelationships(evt.getPlayerList().getServer().overworld());
    }

    // Warm output names without resolving every ingredient in the pack
    public static CompletableFuture<Void> prepareLookup(Level level){
        return prepareSources(level).thenAccept(sources -> {});
    }

    // Warm detached relationships once while keeping live recipe reads within the shared tick budget
    public static CompletableFuture<WorkerRecipeIndex> prepareRelationships(Level level){
        return prepareIndex(level);
    }

    // Share producer definitions across workers without building the complete dependency tree
    public static synchronized WorkerRecipeSource deferredSource(Level level){
        RecipeManager manager = level.getRecipeManager();
        WorkerRecipeSource ready = RECIPE_SOURCES.get(manager);
        if(ready != null) return ready;
        WorkerRecipeIndex index = INDEXES.get(manager);
        if(index != null){
            WorkerRecipeSource source = index;
            RECIPE_SOURCES.put(manager, source);
            return source;
        }
        CompletableFuture<RecipeSources> prepared = prepareSources(level);
        if(!prepared.isDone()) throw new DeferredWorkScheduler.Pending();
        RecipeSources sources = prepared.join();
        DeferredWorkScheduler scheduler = DeferredWorkScheduler.forServer(level.getServer());
        WorkerRecipeSource source = new WorkerRecipeSource(){
            @Override public List<WorkerRecipeDefinition> producing(WorkerResourceKey resource){
                return sources.producing(scheduler, level, resource);
            }
            @Override public WorkerRecipeRelationships relationships(java.util.Collection<WorkerResourceKey> outputs){
                WorkerRecipeIndex ready;
                synchronized(WorkerRecipeCatalog.class){
                    ready = INDEXES.get(manager);
                }
                if(ready != null) return ready.relationships(outputs);
                return sources.relationships(this, outputs);
            }
        };
        RECIPE_SOURCES.put(manager, source);
        return source;
    }

    // Inspect an already adapted recipe on the owner thread without starting another graph lookup
    public static synchronized List<WorkerRecipeDefinition> resolvedRecipes(Level level, ResourceLocation recipeId){
        RecipeManager manager = level.getRecipeManager();
        WorkerRecipeIndex index = INDEXES.get(manager);
        if(index != null) return index.recipes(recipeId);
        CompletableFuture<RecipeSources> prepared = SOURCES.get(manager);
        if(prepared == null || !prepared.isDone() || prepared.isCompletedExceptionally()) return List.of();
        RecipeHolder<?> holder = manager.byKey(recipeId).orElse(null);
        if(holder == null) return List.of();
        ResolvedRecipe resolved = prepared.join().adapted().get(holder);
        return resolved == null ? List.of() : resolved.definitions();
    }

    private static synchronized CompletableFuture<RecipeSources> prepareSources(Level level){
        RecipeManager manager = level.getRecipeManager();
        CompletableFuture<RecipeSources> ready = SOURCES.get(manager);
        if(ready != null && !ready.isCancelled()) return ready;
        DeferredWorkScheduler scheduler = DeferredWorkScheduler.forServer(level.getServer());
        long version = revision;
        CompletableFuture<RecipeSources> res = scheduler.submit(() -> {
            List<RecipeHolder<?>> holders = scheduler.onOwnerThread(() -> List.copyOf(manager.getRecipes()));
            Map<WorkerResourceKey, List<RecipeHolder<?>>> outputs = new LinkedHashMap<>();
            for(int idx = 0; idx < holders.size();){
                int start = idx;
                List<RecipeSource> batch = scheduler.onOwnerThread(() -> {
                    List<RecipeSource> found = new ArrayList<>();
                    long deadline = System.nanoTime() + 2_000_000L;
                    for(int slot = start; slot < Math.min(holders.size(), start + 32); slot++){
                        RecipeHolder<?> holder = holders.get(slot);
                        Adapter adapter = ADAPTERS.get(BuiltInRegistries.RECIPE_TYPE.getKey(holder.value().getType()));
                        found.add(new RecipeSource(holder, adapter == null ? recipeOutputs(level, holder)
                                : adapter.outputs(level, holder)));
                        if(System.nanoTime() >= deadline) break;
                    }
                    return found;
                });
                idx += batch.size();
                for(RecipeSource entry : batch){
                    for(WorkerResourceKey output : entry.outputs())
                        outputs.computeIfAbsent(output, key -> new ArrayList<>()).add(entry.holder());
                }
            }
            List<net.minecraft.world.item.Item> items = scheduler.onOwnerThread(() -> BuiltInRegistries.ITEM.stream().toList());
            List<WorkerResourceKey> axes = new ArrayList<>();
            for(int idx = 0; idx < items.size(); idx += 32){
                List<net.minecraft.world.item.Item> batch = items.subList(idx, Math.min(items.size(), idx + 32));
                axes.addAll(scheduler.onOwnerThread(() -> batch.stream()
                        .filter(item -> item.canPerformAction(item.getDefaultInstance(), ItemAbilities.AXE_STRIP))
                        .map(item -> new WorkerResourceKey(WorkerResourceType.ITEM, BuiltInRegistries.ITEM.getKey(item))).toList()));
            }
            List<WorkerRecipeDefinition> virtual = new ArrayList<>();
            if(!axes.isEmpty()){
                List<net.minecraft.world.level.block.Block> blocks = scheduler.onOwnerThread(() -> BuiltInRegistries.BLOCK.stream().toList());
                for(int idx = 0; idx < blocks.size(); idx += 32){
                    List<net.minecraft.world.level.block.Block> batch = blocks.subList(idx, Math.min(blocks.size(), idx + 32));
                    virtual.addAll(scheduler.onOwnerThread(() -> axeStrippingRecipes(axes, batch)));
                }
            }
            Map<WorkerResourceKey, List<RecipeHolder<?>>> indexed = new LinkedHashMap<>();
            outputs.forEach((key, val) -> indexed.put(key, List.copyOf(val)));
            Map<WorkerResourceKey, List<RecipeHolder<?>>> frozen = Map.copyOf(indexed);
            RecipeSources sources = new RecipeSources(frozen, new WorkerRecipeIndex(virtual), items, holders);
            synchronized(WorkerRecipeCatalog.class){
                if(version == revision) OUTPUT_RECIPES.put(manager, frozen);
            }
            return sources;
        });
        SOURCES.put(manager, res);
        return res;
    }

    // Resolve only producers reachable from this output and share adapted recipes between requests
    public static WorkerRecipeIndex deferredIndex(Level level, WorkerResourceKey output){
        RecipeManager manager = level.getRecipeManager();
        synchronized(WorkerRecipeCatalog.class){
            WorkerRecipeIndex ready = INDEXES.get(manager);
            if(ready != null) return ready;
        }
        CompletableFuture<RecipeSources> prepared = prepareSources(level);
        if(!prepared.isDone()) throw new DeferredWorkScheduler.Pending();
        RecipeSources sources = prepared.join();
        DeferredLookup<WorkerResourceKey, WorkerRecipeIndex> lookup;
        synchronized(WorkerRecipeCatalog.class){
            lookup = LOOKUPS.computeIfAbsent(manager, key -> new DeferredLookup<>(128, Long.MAX_VALUE / 2));
        }
        DeferredWorkScheduler scheduler = DeferredWorkScheduler.forServer(level.getServer());
        return lookup.get(scheduler, output, () -> {
            Map<ResourceLocation, List<WorkerRecipeDefinition>> resolved = new LinkedHashMap<>();
            java.util.Set<WorkerResourceKey> visited = new HashSet<>();
            java.util.Set<WorkerRecipeDefinition> definitions = new LinkedHashSet<>();
            ArrayDeque<WorkerResourceKey> pending = new ArrayDeque<>();
            pending.add(output);
            visited.add(output);
            while(!pending.isEmpty()){
                DeferredWorkScheduler.checkpoint();
                WorkerResourceKey resource = pending.removeFirst();
                List<WorkerRecipeDefinition> producing = new ArrayList<>(sources.virtual().producing(resource));
                for(RecipeHolder<?> holder : sources.outputs().getOrDefault(resource, List.of())){
                    List<WorkerRecipeDefinition> found = resolved.get(holder.id());
                    if(found == null){
                        ResolvedRecipe entry = scheduler.onOwnerThread(() -> sources.resolve(level, holder));
                        if(!entry.opaque().isEmpty()){
                            for(Ingredient ingredient : entry.opaque()) prepareAlternatives(scheduler, ingredient, sources.items());
                            entry = scheduler.onOwnerThread(() -> sources.resolve(level, holder));
                        }
                        found = entry.definitions();
                        resolved.put(holder.id(), found);
                    }
                    for(WorkerRecipeDefinition def : found) if(def.result().equals(resource)) producing.add(def);
                }
                for(WorkerRecipeDefinition def : producing){
                    if(!definitions.add(def)) continue;
                    for(WorkerRecipeDefinition.Ingredient ingredient : def.ingredients()){
                        for(WorkerResourceKey alternative : ingredient.alternatives())
                            if(visited.add(alternative)) pending.addLast(alternative);
                    }
                }
            }
            return new WorkerRecipeIndex(List.copyOf(definitions));
        });
    }

    private record RecipeSource(RecipeHolder<?> holder, List<WorkerResourceKey> outputs){}

    private record RecipeSources(Map<WorkerResourceKey, List<RecipeHolder<?>>> outputs,
                                  WorkerRecipeIndex virtual, List<net.minecraft.world.item.Item> items,
                                  List<RecipeHolder<?>> holders,
                                  Map<RecipeHolder<?>, ResolvedRecipe> adapted,
                                  Map<WorkerResourceKey, List<WorkerRecipeDefinition>> producers,
                                  Map<List<WorkerResourceKey>, WorkerRecipeRelationships> relationships){
        private RecipeSources(Map<WorkerResourceKey, List<RecipeHolder<?>>> outputs,
                                WorkerRecipeIndex virtual, List<net.minecraft.world.item.Item> items,
                                List<RecipeHolder<?>> holders){
            this(outputs, virtual, items, holders, new IdentityHashMap<>(), new java.util.concurrent.ConcurrentHashMap<>(),
                    new LinkedHashMap<>());
        }

        private WorkerRecipeRelationships relationships(WorkerRecipeSource source,
                                                         java.util.Collection<WorkerResourceKey> outputs){
            List<WorkerResourceKey> key = List.copyOf(outputs);
            synchronized(relationships){
                WorkerRecipeRelationships ready = relationships.get(key);
                if(ready != null) return ready;
            }
            WorkerRecipeRelationships res = WorkerRecipeRelationships.discover(source, outputs);
            synchronized(relationships){
                if(relationships.size() >= 64) relationships.remove(relationships.keySet().iterator().next());
                relationships.put(key, res);
            }
            return res;
        }
        private ResolvedRecipe resolve(Level level, RecipeHolder<?> holder){
            ResolvedRecipe ready = adapted.get(holder);
            if(ready != null) return ready;
            ResolvedRecipe res = resolveRecipe(level, holder);
            if(res.opaque().isEmpty()) adapted.put(holder, res);
            return res;
        }

        private List<WorkerRecipeDefinition> producing(DeferredWorkScheduler scheduler, Level level,
                                                        WorkerResourceKey resource){
            return producers.computeIfAbsent(resource, key -> {
                List<WorkerRecipeDefinition> found = new ArrayList<>(virtual.producing(key));
                List<RecipeHolder<?>> holders = outputs.getOrDefault(key, List.of());
                for(int idx = 0; idx < holders.size();){
                    int start = idx;
                    List<RecipeAdaptation> batch = scheduler.onOwnerThread(() -> {
                        List<RecipeAdaptation> entries = new ArrayList<>();
                        long deadline = System.nanoTime() + 2_000_000L;
                        for(int slot = start; slot < Math.min(holders.size(), start + 32); slot++){
                            RecipeHolder<?> holder = holders.get(slot);
                            entries.add(new RecipeAdaptation(holder, resolve(level, holder)));
                            if(System.nanoTime() >= deadline) break;
                        }
                        return entries;
                    });
                    idx += batch.size();
                    for(RecipeAdaptation entry : batch){
                        ResolvedRecipe resolved = entry.resolved();
                        if(!resolved.opaque().isEmpty()){
                            for(Ingredient ingredient : resolved.opaque()) prepareAlternatives(scheduler, ingredient, items);
                            resolved = scheduler.onOwnerThread(() -> resolve(level, entry.holder()));
                        }
                        for(WorkerRecipeDefinition def : resolved.definitions())
                            if(def.result().equals(key)) found.add(def);
                    }
                }
                return List.copyOf(found);
            });
        }
    }

    private record RecipeAdaptation(RecipeHolder<?> holder, ResolvedRecipe resolved){}

    // Start one shared recipe index without adapting every recipe on the requesting server tick
    public static synchronized CompletableFuture<WorkerRecipeIndex> prepareIndex(Level level){
        RecipeManager manager = level.getRecipeManager();
        WorkerRecipeIndex ready = INDEXES.get(manager);
        if(ready != null) return CompletableFuture.completedFuture(ready);
        CompletableFuture<WorkerRecipeIndex> prev = PENDING.get(manager);
        if(prev != null && !prev.isCancelled()) return prev;
        DeferredWorkScheduler scheduler = DeferredWorkScheduler.forServer(level.getServer());
        long version = revision;
        CompletableFuture<WorkerRecipeIndex> res = prepareSources(level).thenCompose(sources -> scheduler.submit(() -> {
            List<WorkerRecipeDefinition> definitions = new ArrayList<>(sources.virtual().definitions());
            for(int idx = 0; idx < sources.holders().size();){
                if(version != revision) throw new java.util.concurrent.CancellationException("Recipes reloaded");
                int start = idx;
                List<RecipeAdaptation> batch = scheduler.onOwnerThread(() -> {
                    List<RecipeAdaptation> found = new ArrayList<>();
                    long deadline = System.nanoTime() + 2_000_000L;
                    for(int slot = start; slot < Math.min(sources.holders().size(), start + 256); slot++){
                        RecipeHolder<?> holder = sources.holders().get(slot);
                        found.add(new RecipeAdaptation(holder, sources.resolve(level, holder)));
                        if(System.nanoTime() >= deadline) break;
                    }
                    return found;
                });
                idx += batch.size();
                for(RecipeAdaptation entry : batch){
                    ResolvedRecipe resolved = entry.resolved();
                    if(!resolved.opaque().isEmpty()){
                        for(Ingredient ingredient : resolved.opaque()) prepareAlternatives(scheduler, ingredient, sources.items());
                        resolved = scheduler.onOwnerThread(() -> sources.resolve(level, entry.holder()));
                    }
                    definitions.addAll(resolved.definitions());
                }
            }
            WorkerRecipeIndex index = new WorkerRecipeIndex(definitions);
            index.relationships(List.of());
            synchronized(WorkerRecipeCatalog.class){
                if(version == revision) INDEXES.put(manager, index);
            }
            return index;
        }));
        PENDING.put(manager, res);
        return res;
    }

    // Adapt ordinary recipes in one query and leave opaque ingredients for sliced probing
    private static ResolvedRecipe resolveRecipe(Level level, RecipeHolder<?> holder){
        ResourceLocation declared = BuiltInRegistries.RECIPE_TYPE.getKey(holder.value().getType());
        if(!ADAPTERS.containsKey(declared)){
            var recipes = WorkerModdedRecipes.recipes(level, holder);
            if(recipes != null) return new ResolvedRecipe(recipes,
                    recipes.stream().map(WorkerRecipeDefinition::result).distinct().toList(), List.of());
        }
        List<Ingredient> opaque = new ArrayList<>();
        for(Ingredient ingredient : holder.value().getIngredients()){
            if(ingredient == Ingredient.EMPTY || ITEM_ALTERNATIVES.containsKey(ingredient)) continue;
            List<WorkerResourceKey> accepted = Arrays.stream(ingredient.getItems()).filter(stack -> !stack.isEmpty())
                    .map(stack -> new WorkerResourceKey(WorkerResourceType.ITEM, BuiltInRegistries.ITEM.getKey(stack.getItem())))
                    .distinct().toList();
            if(accepted.isEmpty()) opaque.add(ingredient);
            else ITEM_ALTERNATIVES.put(ingredient, accepted);
        }
        if(!opaque.isEmpty()) return new ResolvedRecipe(List.of(), List.of(), opaque);
        ResourceLocation type = BuiltInRegistries.RECIPE_TYPE.getKey(holder.value().getType());
        Adapter adapter = ADAPTERS.get(type);
        return new ResolvedRecipe(adapter == null ? standard(level, holder, type) : adapter.recipes(level, holder),
                recipeOutputs(level, holder), List.of());
    }

    private record ResolvedRecipe(List<WorkerRecipeDefinition> definitions, List<WorkerResourceKey> outputs,
                                   List<Ingredient> opaque){}

    // Poll the shared server index; consumers resume after Pending on a later tick
    public static WorkerRecipeIndex deferredIndex(Level level){
        CompletableFuture<WorkerRecipeIndex> res = prepareIndex(level);
        if(!res.isDone()) throw new DeferredWorkScheduler.Pending();
        return res.join();
    }

    // Find only recipes that declare this result, including Create chance outputs and fluids
    public static List<RecipeHolder<?>> producingRecipes(Level level, WorkerResourceKey output){
        synchronized(WorkerRecipeCatalog.class){
            Map<WorkerResourceKey, List<RecipeHolder<?>>> ready = OUTPUT_RECIPES.get(level.getRecipeManager());
            if(ready != null) return ready.getOrDefault(output, List.of());
        }
        CompletableFuture<RecipeSources> res = prepareSources(level);
        if(!res.isDone()) throw new DeferredWorkScheduler.Pending();
        return res.join().outputs().getOrDefault(output, List.of());
    }

    @SuppressWarnings("rawtypes")
    private static List<WorkerResourceKey> recipeOutputs(Level level, RecipeHolder<?> holder){
        var recipes = WorkerModdedRecipes.recipes(level, holder);
        if(recipes != null) return recipes.isEmpty() ? WorkerModdedRecipes.declaredOutputs(level, holder)
                : recipes.stream().map(WorkerRecipeDefinition::result).distinct().toList();
        LinkedHashSet<WorkerResourceKey> outputs = new LinkedHashSet<>();
        ItemStack result = holder.value().getResultItem(level.registryAccess());
        if(!result.isEmpty()) outputs.add(new WorkerResourceKey(WorkerResourceType.ITEM, BuiltInRegistries.ITEM.getKey(result.getItem())));
        if(holder.value() instanceof ProcessingRecipe processing){
            for(Object val : processing.getRollableResultsAsItemStacks()){
                ItemStack stack = (ItemStack)val;
                if(!stack.isEmpty()) outputs.add(new WorkerResourceKey(WorkerResourceType.ITEM, BuiltInRegistries.ITEM.getKey(stack.getItem())));
            }
            for(Object val : processing.getFluidResults()){
                FluidStack stack = (FluidStack)val;
                if(!stack.isEmpty()) outputs.add(new WorkerResourceKey(WorkerResourceType.FLUID, BuiltInRegistries.FLUID.getKey(stack.getFluid())));
            }
        }
        return List.copyOf(outputs);
    }

    // Slice opaque ingredient probes instead of scanning the item registry in one server callback
    private static void prepareAlternatives(DeferredWorkScheduler scheduler, Ingredient ingredient,
                                             List<net.minecraft.world.item.Item> items){
        if(ingredient == Ingredient.EMPTY) return;
        boolean prepared = scheduler.onOwnerThread(() -> {
            if(ITEM_ALTERNATIVES.containsKey(ingredient)) return true;
            List<WorkerResourceKey> accepted = Arrays.stream(ingredient.getItems()).filter(stack -> !stack.isEmpty())
                    .map(stack -> new WorkerResourceKey(WorkerResourceType.ITEM, BuiltInRegistries.ITEM.getKey(stack.getItem())))
                    .distinct().toList();
            if(accepted.isEmpty()) return false;
            ITEM_ALTERNATIVES.put(ingredient, accepted);
            return true;
        });
        if(prepared) return;
        List<WorkerResourceKey> accepted = new ArrayList<>();
        for(int idx = 0; idx < items.size(); idx += 32){
            List<net.minecraft.world.item.Item> batch = items.subList(idx, Math.min(items.size(), idx + 32));
            accepted.addAll(scheduler.onOwnerThread(() -> batch.stream().filter(item -> {
                ItemStack sample = item.getDefaultInstance();
                return !sample.isEmpty() && ingredient.test(sample);
            }).map(item -> new WorkerResourceKey(WorkerResourceType.ITEM, BuiltInRegistries.ITEM.getKey(item))).toList()));
        }
        scheduler.onOwnerThread(() -> ITEM_ALTERNATIVES.put(ingredient, List.copyOf(accepted)));
    }

    // Resolve live recipes and tags once for each loaded recipe manager
    public static WorkerRecipeIndex index(Level level){
        RecipeManager manager = level.getRecipeManager();
        long version;
        synchronized(WorkerRecipeCatalog.class){
            WorkerRecipeIndex ready = INDEXES.get(manager);
            if(ready != null) return ready;
            version = revision;
        }
        List<WorkerRecipeDefinition> definitions = new ArrayList<>();
        Map<WorkerResourceKey, List<RecipeHolder<?>>> outputs = new LinkedHashMap<>();
        for(RecipeHolder<?> holder : manager.getRecipes()){
            ResourceLocation type = BuiltInRegistries.RECIPE_TYPE.getKey(holder.value().getType());
            Adapter adapter = ADAPTERS.get(type);
            definitions.addAll(adapter == null ? standard(level, holder, type) : adapter.recipes(level, holder));
            for(WorkerResourceKey output : recipeOutputs(level, holder))
                outputs.computeIfAbsent(output, key -> new ArrayList<>()).add(holder);
        }
        definitions.addAll(axeStrippingRecipes());
        WorkerRecipeIndex index = new WorkerRecipeIndex(definitions);
        Map<WorkerResourceKey, List<RecipeHolder<?>>> indexed = new LinkedHashMap<>();
        outputs.forEach((key, val) -> indexed.put(key, List.copyOf(val)));
        synchronized(WorkerRecipeCatalog.class){
            if(version != revision) return index;
            OUTPUT_RECIPES.put(manager, Map.copyOf(indexed));
            INDEXES.put(manager, index);
            return index;
        }
    }

    public static List<WorkerRecipeDefinition> recipes(Level level){
        return index(level).definitions();
    }

    @SuppressWarnings("rawtypes")
    private static List<WorkerRecipeDefinition> standard(Level level, RecipeHolder<?> holder, ResourceLocation type){
        var modded = WorkerModdedRecipes.recipes(level, holder);
        if(modded != null) return modded;
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
            ingredients.add(ITEM_INGREDIENTS.computeIfAbsent(ingredient,
                    key -> new WorkerRecipeDefinition.Ingredient(alternatives, 1L)));
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
        List<WorkerResourceKey> cached = ITEM_ALTERNATIVES.get(ingredient);
        if(cached != null) return cached;
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
        return axeStrippingRecipes(axes, BuiltInRegistries.BLOCK);
    }

    private static List<WorkerRecipeDefinition> axeStrippingRecipes(List<WorkerResourceKey> axes,
                                                                    Iterable<net.minecraft.world.level.block.Block> blocks){
        if(axes.isEmpty()) return List.of();
        List<WorkerRecipeDefinition> definitions = new ArrayList<>();
        for(var block : blocks){
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
        return checkSupport(recipe, supported, plan -> stages(level, plan));
    }

    // Test ingredient variants one live query at a time instead of expanding a whole tag on the server thread
    public static boolean supportedDeferred(DeferredWorkScheduler scheduler, Level level,
                                             WorkerRecipeDefinition recipe,
                                             java.util.function.Predicate<WorkerRecipePlan> supported){
        return checkSupport(recipe, plan -> scheduler.onOwnerThread(() -> supported.test(plan)),
                plan -> scheduler.onOwnerThread(() -> stages(level, plan)));
    }

    // Check machine types before choosing stocked inputs without probing ingredient variants
    public static boolean routableDeferred(DeferredWorkScheduler scheduler, Level level,
                                            WorkerRecipeDefinition recipe,
                                            java.util.function.Predicate<WorkerRecipePlan> routable){
        if(recipe.ingredients().stream().anyMatch(input -> input.alternatives().isEmpty())) return false;
        WorkerRecipePlan plan = new WorkerRecipePlan(recipe.recipeId(), recipe.processorType(), recipe.operation(),
                recipe.ingredients().stream().map(input -> new WorkerRecipePlan.Input(input.alternatives().getFirst(),
                        input.amount(), input.alternatives())).toList(), recipe.result(), recipe.resultAmount());
        if(!plan.requiresProcessor() || scheduler.onOwnerThread(() -> routable.test(plan))) return true;
        List<WorkerRecipePlan> stages = scheduler.onOwnerThread(() -> stages(level, plan));
        if(stages.isEmpty() || stages.size() == 1 && stages.getFirst().equals(plan)) return false;
        return stages.stream().allMatch(stage -> !stage.requiresProcessor()
                || scheduler.onOwnerThread(() -> routable.test(stage)));
    }

    // Reject unavailable processors before probing all of a recipe's ingredient variants
    public static boolean supportedDeferred(DeferredWorkScheduler scheduler, Level level,
                                             WorkerRecipeDefinition recipe,
                                             java.util.function.Predicate<WorkerRecipePlan> routable,
                                             java.util.function.Predicate<WorkerRecipePlan> supported){
        return checkSupport(recipe, plan -> scheduler.onOwnerThread(() -> supported.test(plan)),
                plan -> scheduler.onOwnerThread(() -> stages(level, plan)),
                plan -> !plan.requiresProcessor() || scheduler.onOwnerThread(() -> routable.test(plan)));
    }

    private static boolean checkSupport(WorkerRecipeDefinition recipe,
                                          java.util.function.Predicate<WorkerRecipePlan> supported,
                                          java.util.function.Function<WorkerRecipePlan, List<WorkerRecipePlan>> stages){
        return checkSupport(recipe, supported, stages, plan -> true);
    }

    private static boolean checkSupport(WorkerRecipeDefinition recipe,
                                          java.util.function.Predicate<WorkerRecipePlan> supported,
                                          java.util.function.Function<WorkerRecipePlan, List<WorkerRecipePlan>> stages,
                                          java.util.function.Predicate<WorkerRecipePlan> routable){
        if(recipe.ingredients().stream().anyMatch(input -> input.alternatives().isEmpty())) return false;
        WorkerRecipePlan plan = new WorkerRecipePlan(recipe.recipeId(), recipe.processorType(), recipe.operation(),
                recipe.ingredients().stream().map(input -> new WorkerRecipePlan.Input(input.alternatives().getFirst(),
                        input.amount(), input.alternatives())).toList(), recipe.result(), recipe.resultAmount());
        if(!routable.test(plan)){
            List<WorkerRecipePlan> resolved = stages.apply(plan);
            if(resolved.isEmpty() || resolved.size() == 1 && resolved.getFirst().equals(plan)) return false;
            return resolved.stream().allMatch(stage -> routable.test(stage) && supported.test(stage));
        }
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
        List<WorkerRecipePlan> resolved = stages.apply(plan);
        return !resolved.isEmpty() && resolved.stream().allMatch(supported);
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

        // Declare outputs cheaply when ingredient resolution would require registry probes
        default List<WorkerResourceKey> outputs(Level level, RecipeHolder<?> recipe){
            return recipes(level, recipe).stream().map(WorkerRecipeDefinition::result).distinct().toList();
        }
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
