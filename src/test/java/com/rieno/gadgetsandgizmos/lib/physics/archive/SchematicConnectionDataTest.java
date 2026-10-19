package com.rieno.gadgetsandgizmos.lib.physics.archive;

import dev.ryanhcode.sable.api.schematic.SubLevelSchematicSerializationContext;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

// Check portable endpoints across separate source plots and newly allocated destination bodies
class SchematicConnectionDataTest{
    // Bind a cross-body endpoint to its destination template rather than the old plot address
    @Test void separateBodiesUseFreshPlacementCoordinates(){
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        UUID firstTemplate = UUID.randomUUID(), secondTemplate = UUID.randomUUID();
        var frames = List.of(new SchematicConnectionData.BodyFrame(first, firstTemplate, new BlockPos(1000, 0, 2000), new BlockPos(8, 8, 8)),
                new SchematicConnectionData.BodyFrame(second, secondTemplate, new BlockPos(3000, 0, 4000), new BlockPos(8, 8, 8)));
        CompoundTag endpoint = SchematicConnectionData.reference(first, new BlockPos(3003, 2, 4005), frames);
        assertNotNull(endpoint);
        assertEquals(secondTemplate, endpoint.getUUID("Body"));
        var ctx = new SubLevelSchematicSerializationContext(SubLevelSchematicSerializationContext.Type.PLACE, null);
        ctx.getMappings().put(secondTemplate, new SubLevelSchematicSerializationContext.SchematicMapping(null, null,
                UUID.randomUUID(), pos -> ((BlockPos)pos).offset(9000, 20, 12000)));
        assertEquals(new BlockPos(9003, 22, 12005), SchematicConnectionData.resolve(endpoint, ctx));
        assertEquals(new BlockPos(3, 2, 5), BlockPos.of(endpoint.getLong("Pos")));
    }

    // Keep imported local coordinates with the explicitly selected owning body
    @Test void overlappingTemplateBoundsPreferTheirOwner(){
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        var frames = List.of(new SchematicConnectionData.BodyFrame(first, first, BlockPos.ZERO, new BlockPos(8, 8, 8)),
                new SchematicConnectionData.BodyFrame(second, second, BlockPos.ZERO, new BlockPos(8, 8, 8)));
        assertEquals(second, SchematicConnectionData.reference(second, new BlockPos(2, 1, 3), frames).getUUID("Body"));
        assertNull(SchematicConnectionData.reference(new BlockPos(20, 0, 0), frames));
    }
}
