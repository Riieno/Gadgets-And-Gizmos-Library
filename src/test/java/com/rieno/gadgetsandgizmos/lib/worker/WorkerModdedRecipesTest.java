package com.rieno.gadgetsandgizmos.lib.worker;

import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkerModdedRecipesTest{
    @BeforeAll static void bootstrap(){
        SharedConstants.tryDetectVersion();
        try(var loader = mockStatic(LoadingModList.class)){
            var mods = mock(LoadingModList.class);
            when(mods.getModFiles()).thenReturn(List.of());
            loader.when(LoadingModList::get).thenReturn(mods);
            Bootstrap.bootStrap();
        }
    }
    @Test void retainsMachineQuantitiesFluidsAndCatalysts(){
        var recipes = read("""
                {"item_inputs":[{"item":"minecraft:iron_ingot","amount":9},{"item":"minecraft:stick","amount":1,"probability":0}],
                 "fluid_inputs":[{"fluid":"minecraft:water","amount":1000}],
                 "item_outputs":[{"item":"minecraft:iron_block","amount":2},{"item":"minecraft:gold_ingot","probability":0.5}]}
                """);
        assertEquals(1, recipes.size());
        assertEquals(List.of(9L, 1L, 1000L), recipes.getFirst().ingredients().stream().map(WorkerRecipeDefinition.Ingredient::amount).toList());
        assertEquals(2L, recipes.getFirst().resultAmount());
        assertEquals(WorkerResourceType.FLUID, recipes.getFirst().ingredients().getLast().alternatives().getFirst().type());
    }
    @Test void rejectsOpaqueRequirementsInsteadOfOmittingThem(){
        assertTrue(read("""
                {"input":{"ingredient":{"item":"minecraft:iron_ingot"},"count":2},
                 "chemical_input":{"chemical":"test:gas","amount":100},"output":{"id":"minecraft:iron_block"}}
                """).isEmpty());
        assertTrue(read("""
                {"input":{"item":"minecraft:iron_ingot"},"output":{"id":"minecraft:iron_block"},"temperature":1000}
                """).isEmpty());
    }
    @Test void readsSizedSingleInputsAndGuaranteedOutputs(){
        var recipes = read("""
                {"input":{"ingredient":{"item":"minecraft:iron_ingot"},"count":3},"output":{"id":"minecraft:iron_block","count":2}}
                """);
        assertEquals(3L, recipes.getFirst().ingredients().getFirst().amount());
        assertEquals(2L, recipes.getFirst().resultAmount());
    }
    @Test void rejectsChanceOnlyOutputsAndCustomConditions(){
        assertTrue(read("""
                {"input":{"item":"minecraft:iron_ingot"},"item_outputs":[{"item":"minecraft:iron_block","probability":0.1}]}
                """).isEmpty());
        assertTrue(read("""
                {"input":{"item":"minecraft:iron_ingot"},"output":{"id":"minecraft:iron_block"},"process_conditions":[{"type":"test:custom"}]}
                """).isEmpty());
    }
    private static List<WorkerRecipeDefinition> read(String data){
        return WorkerModdedRecipes.readSerialized(ResourceLocation.parse("test:recipe"), ResourceLocation.parse("test:machine"),
                JsonParser.parseString(data).getAsJsonObject());
    }
}
