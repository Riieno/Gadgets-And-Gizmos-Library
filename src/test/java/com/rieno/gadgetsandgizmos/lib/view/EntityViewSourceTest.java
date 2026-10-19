package com.rieno.gadgetsandgizmos.lib.view;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class EntityViewSourceTest{
    @Test void savedBlockReferencesRemainCompatibleAndEntityReferencesKeepTheirIdentity(){
        var dimension = ResourceLocation.withDefaultNamespace("overworld");
        var block = new ViewReference(dimension, null, new BlockPos(1, 2, 3));
        assertEquals(block, ViewReference.fromTag(block.toTag()));
        assertNull(ViewReference.fromTag(block.toTag()).entityId());
        var entity = new ViewReference(dimension, null, BlockPos.ZERO, UUID.randomUUID());
        assertEquals(entity, ViewReference.fromTag(entity.toTag()));
        assertNotEquals(entity, new ViewReference(dimension, null, BlockPos.ZERO));
        var other = new ViewReference(dimension, null, BlockPos.ZERO, UUID.randomUUID());
        var bindings = new ViewSourceBindings(java.util.List.of()).add(entity, "First").add(other, "Second");
        assertNotEquals(bindings.entries().get(0).key(), bindings.entries().get(1).key());
        assertEquals(other, bindings.remove(bindings.entries().getFirst().key()).entries().getFirst().source());
    }
}
