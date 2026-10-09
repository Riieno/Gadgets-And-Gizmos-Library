package com.rieno.gadgetsandgizmos.lib.view;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ViewSourceBindingsTest{
    @Test void equalPositionsInDifferentDimensionsRemainSeparateAfterSaving(){
        ViewReference first = new ViewReference(ResourceLocation.withDefaultNamespace("overworld"), null, BlockPos.ZERO);
        ViewReference second = new ViewReference(ResourceLocation.withDefaultNamespace("the_nether"), null, BlockPos.ZERO);
        ViewSourceBindings bindings = new ViewSourceBindings(List.of()).add(first, "Front door").add(second, "Portal");
        bindings = ViewSourceBindings.fromTag(bindings.toTag());
        assertEquals(2, bindings.entries().size());
        String key = bindings.entries().getFirst().key();
        bindings = bindings.rename(key, "Lobby");
        assertEquals("Lobby", bindings.entries().getFirst().name());
        assertEquals(first, bindings.entries().getFirst().source());
        bindings = bindings.remove(key);
        assertEquals(second, bindings.entries().getFirst().source());
    }
}
