package com.rieno.gadgetsandgizmos.lib.worker;

import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkerModdedMachinesTest{
    @BeforeAll static void bootstrap(){
        SharedConstants.tryDetectVersion();
        try(var loader = mockStatic(LoadingModList.class)){
            var mods = mock(LoadingModList.class);
            when(mods.getModFiles()).thenReturn(List.of());
            loader.when(LoadingModList::get).thenReturn(mods);
            Bootstrap.bootStrap();
        }
    }
    @Test void readsRecipeProvidersWithoutGuessingBlockNames(){
        assertEquals(Set.of(ResourceLocation.withDefaultNamespace("smelting")), WorkerModdedMachines.recipeTypes(new Direct()));
        assertEquals(Set.of(ResourceLocation.withDefaultNamespace("smelting")), WorkerModdedMachines.recipeTypes(new Wrapped()));
        assertTrue(WorkerModdedMachines.recipeTypes(new Object()).isEmpty());
    }
    @Test void stopsProvidersThatReferBackToThemselves(){
        assertTrue(WorkerModdedMachines.recipeTypes(new Cycle()).isEmpty());
    }
    public static final class Direct{ public RecipeType<?> recipeType(){ return RecipeType.SMELTING; } }
    public static final class Wrapped{ public Direct getRecipeType(){ return new Direct(); } }
    public static final class Cycle{ public Cycle getRecipeType(){ return this; } }
}
