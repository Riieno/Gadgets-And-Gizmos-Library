package com.rieno.gadgetsandgizmos.lib.worker;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

// Read exposed recipe-type getters without guessing a processor from its block name
public final class WorkerModdedMachines implements WorkerMachineRegistry.Adapter{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/
    private static final ClassValue<List<Method>> GETTERS = new ClassValue<>(){
        @Override protected List<Method> computeValue(Class<?> type){
            try{ return java.util.Arrays.stream(type.getMethods()).filter(method -> !Modifier.isStatic(method.getModifiers())
                    && method.getParameterCount() == 0 && Set.of("getRecipeType", "recipeType", "getRecipeTypes", "recipeTypes")
                    .contains(method.getName())).limit(8).toList(); }
            catch(LinkageError ex){ return List.of(); }
        }
    };
    private static final ClassValue<List<Field>> FIELDS = new ClassValue<>(){
        @Override protected List<Field> computeValue(Class<?> type){
            try{ return java.util.Arrays.stream(type.getFields()).filter(field -> !Modifier.isStatic(field.getModifiers())
                    && Set.of("recipeType", "recipeTypes").contains(field.getName())).limit(8).toList(); }
            catch(LinkageError ex){ return List.of(); }
        }
    };

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Inspect only public recipe declarations, including providers wrapping registered recipe types
    public static Set<ResourceLocation> recipeTypes(Object machine){
        Set<ResourceLocation> types = new LinkedHashSet<>();
        collect(machine, types, 0);
        return Set.copyOf(types);
    }

    private static void collect(Object value, Set<ResourceLocation> types, int depth){
        if(value == null || depth > 3) return;
        if(value instanceof RecipeType<?> recipeType){
            ResourceLocation id = BuiltInRegistries.RECIPE_TYPE.getKey(recipeType);
            if(id != null) types.add(id);
            return;
        }
        if(value instanceof Collection<?> collection){
            collection.stream().limit(64).forEach(entry -> collect(entry, types, depth + 1));
            return;
        }
        for(Method getter : GETTERS.get(value.getClass())){
            try{ collect(getter.invoke(value), types, depth + 1); }
            catch(ReflectiveOperationException | RuntimeException | LinkageError ignored){}
        }
        for(Field field : FIELDS.get(value.getClass())){
            try{ collect(field.get(value), types, depth + 1); }
            catch(ReflectiveOperationException | RuntimeException | LinkageError ignored){}
        }
    }

    @Override public WorkerMachine resolve(WorkerMachineRegistry.Context ctx){
        BlockEntity be = ctx.level().getBlockEntity(ctx.pos());
        if(be == null) return null;
        Set<ResourceLocation> types = recipeTypes(be);
        if(types.isEmpty()) return null;
        return new Processor(ctx, types, be);
    }

    private record Processor(WorkerMachineRegistry.Context ctx, Set<ResourceLocation> types,
                             BlockEntity be) implements WorkerMachine{
        @Override public boolean maySupportProcessor(ResourceLocation type){ return types.contains(type); }
        @Override public Set<ResourceLocation> supportedProcessorTypes(){ return types; }
        @Override public List<BlockPos> members(){ return List.of(ctx.pos()); }
        @Override public boolean supports(WorkerRecipePlan plan){
            if(plan == null || !types.contains(plan.processorType())) return false;
            var holder = ctx.level().getRecipeManager().byKey(plan.recipeId()).orElse(null);
            if(holder == null || !types.contains(BuiltInRegistries.RECIPE_TYPE.getKey(holder.value().getType()))) return false;
            List<String> diagnostics = WorkerModdedRecipes.diagnostics(ctx.level(), holder, be);
            if(!diagnostics.isEmpty()) return false;
            for(var input : plan.inputs()){
                if(input.resource().type() == WorkerResourceType.ITEM && itemInputs(plan).isEmpty()) return false;
                if(input.resource().type() == WorkerResourceType.FLUID && fluidInputs(plan).isEmpty()) return false;
            }
            return true;
        }
        @Override public List<String> routeDiagnostics(WorkerRecipePlan plan, WorkerMachineSite site){
            var holder = plan == null ? null : ctx.level().getRecipeManager().byKey(plan.recipeId()).orElse(null);
            return holder == null ? List.of("Machine recipe is unavailable at " + ctx.pos().toShortString())
                    : WorkerModdedRecipes.diagnostics(ctx.level(), holder, be);
        }
        @Override public List<IItemHandler> itemInputs(WorkerRecipePlan plan){
            return WorkerContainerAccess.itemHandlers(ctx.level(), ctx.pos(), ctx.side());
        }
        @Override public List<IItemHandler> itemOutputs(WorkerRecipePlan plan){ return itemInputs(plan); }
        @Override public List<IFluidHandler> fluidInputs(WorkerRecipePlan plan){
            return WorkerContainerAccess.fluidHandlers(ctx.level(), ctx.pos(), ctx.side());
        }
        @Override public List<IFluidHandler> fluidOutputs(WorkerRecipePlan plan){ return fluidInputs(plan); }
    }
}
