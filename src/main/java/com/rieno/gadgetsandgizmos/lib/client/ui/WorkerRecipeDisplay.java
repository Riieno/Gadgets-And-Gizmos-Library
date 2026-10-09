package com.rieno.gadgetsandgizmos.lib.client.ui;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.worker.WorkerRecipeCatalog;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerRecipeDefinition;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerResourceType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// Resolve worker recipe names and previews without changing their saved identities
public final class WorkerRecipeDisplay{
    private WorkerRecipeDisplay(){}

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Keep one display entry for each persistent recipe id
    public record Entry(ResourceLocation recipeId, Component name, ItemStack icon){
        public Entry{
            name = name.copy();
            icon = icon.copy();
        }

        // Match either the translated result name or the original recipe id
        public boolean matches(String query){
            if(query == null || query.isBlank()) return true;
            String term = query.trim().toLowerCase(Locale.ROOT);
            return name.getString().toLowerCase(Locale.ROOT).contains(term)
                    || recipeId.toString().contains(term);
        }
    }

    // Collapse alternate machine routes and prefer an item preview for mixed outputs
    public static List<Entry> entries(Level level){
        if(level == null) return List.of();
        Map<ResourceLocation, WorkerRecipeDefinition> grouped = new LinkedHashMap<>();
        for(var def : WorkerRecipeCatalog.index(level).definitions()){
            var prev = grouped.get(def.recipeId());
            if(prev == null || prev.result().type() != WorkerResourceType.ITEM
                    && def.result().type() == WorkerResourceType.ITEM){
                grouped.put(def.recipeId(), def);
            }
        }
        return grouped.values().stream().map(def -> display(level, def))
                .sorted(Comparator.comparing((Entry entry) -> entry.name().getString(), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(entry -> entry.recipeId().toString())).toList();
    }

    // Read the real result stack so custom names and item components survive
    private static Entry display(Level level, WorkerRecipeDefinition def){
        var holder = level.getRecipeManager().byKey(def.recipeId()).orElse(null);
        if(holder != null){
            ItemStack result = holder.value().getResultItem(level.registryAccess());
            if(!result.isEmpty()) return new Entry(def.recipeId(), result.getHoverName(), result);
        }
        var output = def.result();
        if(output.type() == WorkerResourceType.FLUID){
            var fluid = BuiltInRegistries.FLUID.getOptional(output.id()).orElse(null);
            if(fluid != null){
                FluidStack stack = new FluidStack(fluid, 1000);
                if(!stack.isEmpty()){
                    ItemStack bucket = FluidUtil.getFilledBucket(stack);
                    return new Entry(def.recipeId(), stack.getHoverName(),
                            bucket.isEmpty() ? new ItemStack(Items.BUCKET) : bucket);
                }
            }
        }else{
            var item = BuiltInRegistries.ITEM.getOptional(output.id()).orElse(null);
            if(item != null){
                ItemStack stack = item.getDefaultInstance();
                if(!stack.isEmpty()) return new Entry(def.recipeId(), stack.getHoverName(), stack);
            }
        }
        return new Entry(def.recipeId(), Component.literal(plainName(output.id())), ItemStack.EMPTY);
    }

    // Give resources without a registered display name readable words
    private static String plainName(ResourceLocation id){
        String path = id.getPath();
        path = path.substring(path.lastIndexOf('/') + 1).replace('_', ' ').replace('-', ' ');
        StringBuilder name = new StringBuilder();
        for(String word : path.split("\\s+")){
            if(word.isEmpty()) continue;
            if(!name.isEmpty()) name.append(' ');
            name.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return name.toString();
    }
}
