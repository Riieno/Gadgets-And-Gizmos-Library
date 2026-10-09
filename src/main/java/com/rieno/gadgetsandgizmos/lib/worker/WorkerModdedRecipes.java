package com.rieno.gadgetsandgizmos.lib.worker;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

// Normalize exposed item and fluid recipe formats while rejecting opaque requirements
public final class WorkerModdedRecipes{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/
    private static final Map<RecipeManager, Map<Recipe<?>, View>> VIEWS = new WeakHashMap<>();
    private static final List<String> INPUT_KEYS = List.of("item_inputs", "fluid_inputs", "input", "inputs", "ingredient", "ingredients");
    private static final List<String> OUTPUT_KEYS = List.of("item_outputs", "fluid_outputs", "output", "outputs", "result", "results");
    private static final java.util.Set<String> METADATA_KEYS = java.util.Set.of("type", "group", "category", "duration", "time",
            "eu", "energy", "energy_cost", "processingTime", "experience", "cookingtime", "conditions", "process_conditions");
    private record View(List<WorkerRecipeDefinition> recipes, JsonObject data, String failure){}

    private WorkerModdedRecipes(){}

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Adapt a detached serializer document without reading a level or block entity
    public static List<WorkerRecipeDefinition> readSerialized(ResourceLocation id, ResourceLocation type, JsonObject data){
        return parse(id, type, data).recipes();
    }

    // Read a mod's registered serializer instead of inferring recipes from a machine name
    public static List<WorkerRecipeDefinition> recipes(Level level, RecipeHolder<?> holder){
        var type = BuiltInRegistries.RECIPE_TYPE.getKey(holder.value().getType());
        if(type == null) return null;
        String namespace = type.getNamespace();
        if(namespace.equals("minecraft") || namespace.equals("create")) return null;
        return view(level, holder).recipes();
    }

    public static void invalidate(){ VIEWS.clear(); }

    private static View view(Level level, RecipeHolder<?> holder){
        Map<Recipe<?>, View> recipes = VIEWS.computeIfAbsent(level.getRecipeManager(), key -> new IdentityHashMap<>());
        return recipes.computeIfAbsent(holder.value(), recipe -> read(level, holder));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static View read(Level level, RecipeHolder<?> holder){
        try{
            var codec = (com.mojang.serialization.Codec)holder.value().getSerializer().codec().codec();
            var encoded = codec.encodeStart(RegistryOps.create(JsonOps.INSTANCE, level.registryAccess()), holder.value()).result();
            if(encoded.isEmpty() || !(encoded.get() instanceof JsonObject data)) return failed("Recipe serializer has no readable item/fluid format");
            View res = parse(holder.id(), BuiltInRegistries.RECIPE_TYPE.getKey(holder.value().getType()), data);
            JsonObject metadata = new JsonObject();
            if(data.has("eu")) metadata.add("eu", data.get("eu"));
            if(res.recipes().isEmpty()) for(String key : OUTPUT_KEYS){
                if(data.has(key)) metadata.add(key, data.get(key));
            }
            return new View(res.recipes(), metadata, res.failure());
        }catch(RuntimeException | LinkageError ex){
            return failed("Recipe serializer cannot expose its requirements");
        }
    }

    // Retain exact quantities and every required input, including catalysts with zero consumption chance
    private static View parse(ResourceLocation id, ResourceLocation type, JsonObject data){
        try{
            for(var entry : data.entrySet()){
                String key = entry.getKey().toLowerCase(java.util.Locale.ROOT);
                if(!INPUT_KEYS.contains(entry.getKey()) && !OUTPUT_KEYS.contains(entry.getKey())
                        && !METADATA_KEYS.contains(entry.getKey())) return failed("Recipe property requires a machine adapter: " + entry.getKey());
                if((key.contains("input") || key.contains("ingredient") || key.contains("catalyst"))
                        && !INPUT_KEYS.contains(entry.getKey())) return failed("Recipe requires unsupported input " + entry.getKey());
                if((key.equals("process_conditions") || key.equals("conditions")) && !empty(entry.getValue()))
                    return failed("Recipe has custom processing conditions requiring a machine adapter");
            }
            List<WorkerRecipeDefinition.Ingredient> inputs = new ArrayList<>();
            for(String key : INPUT_KEYS){
                if(!data.has(key)) continue;
                for(JsonElement value : entries(data.get(key))) inputs.add(input(value, key.equals("fluid_inputs")));
            }
            if(inputs.isEmpty() || inputs.size() > 64 || inputs.stream().anyMatch(input -> input == null))
                return failed("Recipe inputs cannot be resolved as items or fluids");
            Map<WorkerResourceKey, Long> outputs = new LinkedHashMap<>();
            for(String key : OUTPUT_KEYS){
                if(!data.has(key)) continue;
                for(JsonElement value : entries(data.get(key))){
                    boolean fluid = key.equals("fluid_outputs") || value.isJsonObject() && value.getAsJsonObject().has("fluid");
                    JsonObject output = value.isJsonPrimitive() ? named(value.getAsString(), fluid ? "fluid" : "item") : value.getAsJsonObject();
                    if(output.has("probability") && output.get("probability").getAsDouble() < 1D
                            || output.has("chance") && output.get("chance").getAsDouble() < 1D) continue;
                    String resource = name(output, fluid ? "fluid" : "item");
                    ResourceLocation resourceId = ResourceLocation.tryParse(resource);
                    if(resourceId == null || !(fluid ? BuiltInRegistries.FLUID.containsKey(resourceId) : BuiltInRegistries.ITEM.containsKey(resourceId)))
                        return failed("Recipe output is not a registered item or fluid");
                    long amount = amount(output);
                    outputs.merge(new WorkerResourceKey(fluid ? WorkerResourceType.FLUID : WorkerResourceType.ITEM, resourceId), amount, Math::addExact);
                }
            }
            if(outputs.isEmpty()) return failed("Recipe has no guaranteed item or fluid output");
            var definitions = outputs.entrySet().stream().map(output -> new WorkerRecipeDefinition(id, type,
                    WorkerRecipePlan.Operation.PROCESSING, inputs, output.getKey(), output.getValue())).toList();
            return new View(definitions, data, "");
        }catch(RuntimeException ex){
            return failed("Recipe contains an unsupported ingredient or output format");
        }
    }

    private static WorkerRecipeDefinition.Ingredient input(JsonElement value, boolean fluid){
        JsonObject data = value.isJsonPrimitive() ? named(value.getAsString(), fluid ? "fluid" : "item") : value.getAsJsonObject();
        fluid |= data.has("fluid");
        long amount = amount(data);
        JsonElement ingredient = data.has("ingredient") ? data.get("ingredient") : data;
        List<WorkerResourceKey> alternatives;
        if(fluid){
            JsonObject declared = ingredient.getAsJsonObject();
            if(declared.has("fluid") && declared.get("fluid").isJsonObject()) declared = declared.getAsJsonObject("fluid");
            if(declared.has("tag")){
                ResourceLocation tag = ResourceLocation.parse(declared.get("tag").getAsString());
                alternatives = BuiltInRegistries.FLUID.getTag(TagKey.create(Registries.FLUID, tag))
                        .map(values -> values.stream().map(holder -> new WorkerResourceKey(WorkerResourceType.FLUID,
                                BuiltInRegistries.FLUID.getKey(holder.value()))).toList()).orElse(List.of());
            }else{
                ResourceLocation id = ResourceLocation.parse(name(declared, "fluid"));
                alternatives = BuiltInRegistries.FLUID.containsKey(id) ? List.of(new WorkerResourceKey(WorkerResourceType.FLUID, id)) : List.of();
            }
        }else{
            var parsed = Ingredient.CODEC.parse(JsonOps.INSTANCE, ingredient).result().orElse(null);
            if(parsed == null) return null;
            alternatives = java.util.Arrays.stream(parsed.getItems()).filter(stack -> !stack.isEmpty())
                    .map(stack -> new WorkerResourceKey(WorkerResourceType.ITEM, BuiltInRegistries.ITEM.getKey(stack.getItem()))).distinct().toList();
        }
        return alternatives.isEmpty() ? null : new WorkerRecipeDefinition.Ingredient(alternatives, amount);
    }

    // Check exposed machine limits before delivering an unsupported recipe
    public static List<String> diagnostics(Level level, RecipeHolder<?> holder, Object machine){
        List<WorkerRecipeDefinition> recipes = recipes(level, holder);
        if(recipes == null) return List.of();
        View view = view(level, holder);
        if(!view.failure().isEmpty()) return List.of(view.failure());
        if(machine != null && view.data().has("eu")){
            try{
                var getter = machine.getClass().getMethod("getMaxRecipeEu");
                if(((Number)getter.invoke(machine)).longValue() < view.data().get("eu").getAsLong())
                    return List.of("Linked machine tier cannot supply " + view.data().get("eu") + " EU/t for " + holder.id());
            }catch(NoSuchMethodException ignored){}
            catch(ReflectiveOperationException | RuntimeException ex){ return List.of("Machine recipe power limit could not be read"); }
        }
        return List.of();
    }

    // Retain declared result names so unsupported requirements can be explained on a failed request
    public static List<WorkerResourceKey> declaredOutputs(Level level, RecipeHolder<?> holder){
        JsonObject data = view(level, holder).data();
        List<WorkerResourceKey> outputs = new ArrayList<>();
        try{
            for(String key : OUTPUT_KEYS){
                if(!data.has(key)) continue;
                for(var value : entries(data.get(key))){
                    boolean fluid = key.equals("fluid_outputs") || value.isJsonObject() && value.getAsJsonObject().has("fluid");
                    var output = value.isJsonPrimitive() ? named(value.getAsString(), fluid ? "fluid" : "item") : value.getAsJsonObject();
                    var id = ResourceLocation.parse(name(output, fluid ? "fluid" : "item"));
                    if(fluid ? BuiltInRegistries.FLUID.containsKey(id) : BuiltInRegistries.ITEM.containsKey(id))
                        outputs.add(new WorkerResourceKey(fluid ? WorkerResourceType.FLUID : WorkerResourceType.ITEM, id));
                }
            }
        }catch(RuntimeException ignored){}
        return outputs.stream().distinct().toList();
    }

    private static View failed(String reason){ return new View(List.of(), new JsonObject(), reason); }
    private static List<JsonElement> entries(JsonElement value){
        if(value.isJsonArray() && value.getAsJsonArray().size() > 64) throw new IllegalArgumentException("Recipe has too many declared slots");
        return value.isJsonArray() ? java.util.stream.StreamSupport.stream(value.getAsJsonArray().spliterator(), false).toList() : List.of(value);
    }
    private static boolean empty(JsonElement value){ return value.isJsonNull() || value.isJsonArray() && value.getAsJsonArray().isEmpty(); }
    private static JsonObject named(String value, String key){ var res = new JsonObject(); res.addProperty(key, value); return res; }
    private static long amount(JsonObject value){
        long amount = value.has("amount") ? value.get("amount").getAsLong() : value.has("count") ? value.get("count").getAsLong() : 1L;
        if(amount <= 0L) throw new IllegalArgumentException("Recipe amount must be positive");
        return amount;
    }
    private static String name(JsonObject value, String key){
        if(value.has(key)) return value.get(key).getAsString();
        if(value.has("id")) return value.get("id").getAsString();
        throw new IllegalArgumentException("Recipe resource is missing");
    }
}
