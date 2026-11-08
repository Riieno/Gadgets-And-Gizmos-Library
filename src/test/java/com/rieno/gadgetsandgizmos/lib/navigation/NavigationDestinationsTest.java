package com.rieno.gadgetsandgizmos.lib.navigation;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NavigationDestinationsTest{
    private static final ResourceLocation OVERWORLD = ResourceLocation.withDefaultNamespace("overworld");
    private static final ResourceLocation NETHER = ResourceLocation.withDefaultNamespace("the_nether");

    @Test
    void namesCoordinatesAndDimensionsSurvivePersistence(){
        var saved = new NavigationDestinations(List.of()).save("Home | Dock", OVERWORLD, new Vec3(123.5, 80, -200));
        var entry = saved.entries().getFirst();
        var restored = NavigationDestinations.fromTag(saved.toTag().copy());
        assertEquals(saved, restored);
        assertEquals(entry.position(), NavigationDestinations.parseCoordinates(entry.coordinates()).orElseThrow());
        assertEquals(entry, restored.find(entry.id(), OVERWORLD).orElseThrow());
        assertTrue(restored.find(entry.id(), NETHER).isEmpty());
        assertTrue(restored.remove(entry.id()).entries().isEmpty());
    }

    @Test
    void resavingANameUpdatesItsCoordinatesWithoutCreatingDuplicates(){
        var saved = new NavigationDestinations(List.of()).save("Home", OVERWORLD, Vec3.ZERO);
        var id = saved.entries().getFirst().id();
        saved = saved.save(" home ", OVERWORLD, new Vec3(10, 20, 30));
        assertEquals(1, saved.entries().size());
        assertEquals(id, saved.entries().getFirst().id());
        assertEquals(new Vec3(10, 20, 30), saved.entries().getFirst().position());
        assertEquals(2, saved.save("home", NETHER, Vec3.ZERO).entries().size());
    }

    @Test
    void malformedCoordinatesCannotBecomeMovementCommands(){
        for(String input : List.of("NaN, 0, 0", "0 Infinity 0", "30000001 0 0", "1 2", "1 2 3 4", "", "one 2 3")){
            assertTrue(NavigationDestinations.parseCoordinates(input).isEmpty(), input);
        }
        assertEquals(new Vec3(-1.5, 64, 20), NavigationDestinations.parseCoordinates(" -1.5, 64, 20 ").orElseThrow());
        assertThrows(IllegalArgumentException.class, () -> new NavigationDestinations(List.of()).save(" ", OVERWORLD, Vec3.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new NavigationDestinations(List.of()).save("x".repeat(65), OVERWORLD, Vec3.ZERO));
        var corrupt = new CompoundTag();
        corrupt.put("Destinations", new NavigationDestinations(List.of()).save("Home", OVERWORLD, Vec3.ZERO).toTag().get("Destinations"));
        corrupt.getList("Destinations", 10).getCompound(0).putDouble("X", Double.NaN);
        assertTrue(NavigationDestinations.fromTag(corrupt).entries().isEmpty());
    }

    @Test
    void storageRemainsBoundedAndRetainsTheNewestDestinations(){
        var saved = new NavigationDestinations(List.of());
        for(int idx = 0; idx < 100; idx++) saved = saved.save("Destination " + idx, OVERWORLD, new Vec3(idx, 64, 0));
        assertEquals(NavigationDestinations.MAX_ENTRIES, saved.entries().size());
        assertEquals("Destination 99", saved.entries().getLast().name());
        assertEquals(saved, NavigationDestinations.fromTag(saved.toTag()));
    }
}
