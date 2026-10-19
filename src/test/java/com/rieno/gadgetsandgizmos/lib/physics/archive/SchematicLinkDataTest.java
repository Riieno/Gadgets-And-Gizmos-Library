package com.rieno.gadgetsandgizmos.lib.physics.archive;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/


import dev.ryanhcode.sable.api.schematic.SubLevelSchematicSerializationContext;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// Check declared link configuration through export, repeated sanitizing and fresh placement
class SchematicLinkDataTest{

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize vanilla registries before mocking server-level classes
    @BeforeAll static void prepareRegistries(){
        net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), java.util.Map.of());
        net.neoforged.fml.ModList.of(List.of(), List.of());
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    // Keep endpoint references portable without retaining unrelated stored contents
    @Test void linksSurviveSanitizingAndUseNewBodyIdentities(){
        BlockEntity be = mock(BlockEntity.class);
        when(be.getBlockPos()).thenReturn(new BlockPos(1000, 0, 2000));
        UUID source = UUID.randomUUID(), template = UUID.randomUUID(), placed = UUID.randomUUID();
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("test", "endpoints_" + UUID.randomUUID());
        SchematicLinkData.register(id, val -> val == be, new SchematicLinkData.Schema(Set.of("Goal", "GoalSubLevel"),
                List.of(new SchematicLinkData.Reference(List.of(), "Goal", "GoalSubLevel", SchematicLinkData.PositionFormat.LONG))));
        CompoundTag raw = new CompoundTag();
        raw.putLong("Goal", new BlockPos(1003, 2, 2005).asLong()); raw.putUUID("GoalSubLevel", source);
        raw.putString("Inventory", "stored contents");
        var frames = List.of(new SchematicConnectionData.BodyFrame(source, template, new BlockPos(1000, 0, 2000), new BlockPos(8, 8, 8)));
        CompoundTag safe = new CompoundTag();
        SchematicLinkData.capture(be, raw, safe, source, frames);
        assertFalse(safe.toString().contains("stored contents"));
        CompoundTag repeated = new CompoundTag();
        SchematicLinkData.capture(be, safe, repeated, template, frames);
        assertEquals(safe, repeated);
        var ctx = new SubLevelSchematicSerializationContext(SubLevelSchematicSerializationContext.Type.PLACE, null);
        ctx.getMappings().put(template, new SubLevelSchematicSerializationContext.SchematicMapping(null, null, placed,
                pos -> ((BlockPos)pos).offset(9000, 20, 12000)));
        ServerLevel level = mock(ServerLevel.class);
        BlockPos owner = new BlockPos(9000, 20, 12000);
        when(level.getBlockEntity(owner)).thenReturn(be);
        when(be.saveWithFullMetadata(any())).thenReturn(new CompoundTag());
        SchematicLinkData.restore(level, owner, safe, ctx, Vec3.ZERO, new Quaterniond());
        ArgumentCaptor<CompoundTag> captured = ArgumentCaptor.forClass(CompoundTag.class);
        verify(be).loadWithComponents(captured.capture(), any());
        assertEquals(placed, captured.getValue().getUUID("GoalSubLevel"));
        assertEquals(new BlockPos(9003, 22, 12005), BlockPos.of(captured.getValue().getLong("Goal")));
        assertNull(SubLevelSchematicSerializationContext.getCurrentContext());
    }

    // Allocate a new shared strand identity and move its world points with the chosen placement
    @Test void ropeGeometryAndPairedIdentitiesRemainConsistent(){
        BlockEntity be = mock(BlockEntity.class);
        when(be.getBlockPos()).thenReturn(BlockPos.ZERO);
        UUID source = UUID.randomUUID(), template = UUID.randomUUID(), strand = UUID.randomUUID();
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("test", "ropes_" + UUID.randomUUID());
        SchematicLinkData.register(id, val -> val == be, new SchematicLinkData.Schema(Set.of("HasRopeAttached", "Strand"),
                List.of(), Set.of("HasRopeAttached", "uuid"), List.of(List.of("Strand", "points")), Set.of("Strand")));
        CompoundTag raw = new CompoundTag(), rope = new CompoundTag();
        raw.putUUID("HasRopeAttached", strand); rope.putString("uuid", strand.toString());
        ListTag point = new ListTag();
        point.add(DoubleTag.valueOf(101)); point.add(DoubleTag.valueOf(20)); point.add(DoubleTag.valueOf(200));
        ListTag points = new ListTag(); points.add(point); rope.put("points", points); raw.put("Strand", rope);
        var frames = List.of(new SchematicConnectionData.BodyFrame(source, template, BlockPos.ZERO, new BlockPos(8, 8, 8), new Vec3(100, 20, 200)));
        CompoundTag safe = new CompoundTag();
        SchematicLinkData.capture(be, raw, safe, source, frames);
        assertFalse(SchematicLinkData.detached(be, raw).contains("Strand"));
        var ctx = new SubLevelSchematicSerializationContext(SubLevelSchematicSerializationContext.Type.PLACE, null);
        ctx.getMappings().put(template, new SubLevelSchematicSerializationContext.SchematicMapping(null, null,
                UUID.randomUUID(), pos -> (BlockPos)pos));
        ServerLevel level = mock(ServerLevel.class);
        when(level.getBlockEntity(BlockPos.ZERO)).thenReturn(be);
        when(be.saveWithFullMetadata(any())).thenReturn(new CompoundTag());
        SchematicLinkData.restore(level, BlockPos.ZERO, safe, ctx, new Vec3(50, 10, 60), new Quaterniond().rotateY(Math.PI / 2));
        ArgumentCaptor<CompoundTag> captured = ArgumentCaptor.forClass(CompoundTag.class);
        verify(be).loadWithComponents(captured.capture(), any());
        CompoundTag data = captured.getValue();
        UUID placedStrand = UUID.fromString(data.getCompound("Strand").getString("uuid"));
        assertNotEquals(strand, placedStrand);
        assertEquals(placedStrand, data.getUUID("HasRopeAttached"));
        ListTag placedPoint = (ListTag)data.getCompound("Strand").getList("points", 9).getFirst();
        assertEquals(50, placedPoint.getDouble(0), 1E-9);
        assertEquals(10, placedPoint.getDouble(1), 1E-9);
        assertEquals(59, placedPoint.getDouble(2), 1E-9);
    }

    // Keep fractional Bezier starts and node metadata when their plot origin changes
    @Test void inlineBezierOriginsKeepTheirCodecAndNodeFields(){
        BlockEntity be = mock(BlockEntity.class);
        BlockPos sourcePos = new BlockPos(1000, 0, 2000);
        when(be.getBlockPos()).thenReturn(sourcePos);
        UUID source = UUID.randomUUID(), template = UUID.randomUUID(), placed = UUID.randomUUID();
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("test", "bezier_" + UUID.randomUUID());
        SchematicLinkData.register(id, val -> val == be, new SchematicLinkData.Schema(Set.of("Starts", "PayloadBody"),
                List.of(new SchematicLinkData.Reference(List.of("Starts"), "", null, SchematicLinkData.PositionFormat.RELATIVE_VECTOR),
                        new SchematicLinkData.Reference(List.of(), "PayloadBody", null, SchematicLinkData.PositionFormat.BODY))));
        CompoundTag raw = new CompoundTag(), node = new CompoundTag();
        ListTag vector = new ListTag();
        vector.add(DoubleTag.valueOf(3.5)); vector.add(DoubleTag.valueOf(2.25)); vector.add(DoubleTag.valueOf(5.75));
        node.put("V", vector); node.putString("Type", "curve");
        ListTag nodes = new ListTag(); nodes.add(node); raw.put("Starts", nodes); raw.putUUID("PayloadBody", source);
        var frames = List.of(new SchematicConnectionData.BodyFrame(source, template, sourcePos, new BlockPos(8, 8, 8)));
        CompoundTag safe = new CompoundTag();
        SchematicLinkData.capture(be, raw, safe, source, frames);
        var ctx = new SubLevelSchematicSerializationContext(SubLevelSchematicSerializationContext.Type.PLACE, null);
        BlockPos destination = new BlockPos(9000, 20, 12000);
        ctx.getMappings().put(template, new SubLevelSchematicSerializationContext.SchematicMapping(null, null, placed,
                pos -> ((BlockPos)pos).offset(destination)));
        ServerLevel level = mock(ServerLevel.class);
        when(level.getBlockEntity(destination)).thenReturn(be);
        when(be.saveWithFullMetadata(any())).thenReturn(new CompoundTag());
        SchematicLinkData.restore(level, destination, safe, ctx, Vec3.ZERO, new Quaterniond());
        ArgumentCaptor<CompoundTag> captured = ArgumentCaptor.forClass(CompoundTag.class);
        verify(be).loadWithComponents(captured.capture(), any());
        CompoundTag restored = captured.getValue().getList("Starts", 10).getCompound(0);
        assertEquals("curve", restored.getString("Type"));
        assertEquals(vector, restored.getList("V", 6));
        assertEquals(placed, captured.getValue().getUUID("PayloadBody"));
    }

    // Overlay declared nested fields while retaining the destination's unrelated configuration
    @Test void nestedSchemasDropExternalLinksAndPreserveOtherFields(){
        BlockEntity be = mock(BlockEntity.class);
        when(be.getBlockPos()).thenReturn(BlockPos.ZERO);
        UUID source = UUID.randomUUID(), template = UUID.randomUUID(), placed = UUID.randomUUID();
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("test", "nested_" + UUID.randomUUID());
        SchematicLinkData.register(id, val -> val == be, new SchematicLinkData.Schema(Set.of("Links"),
                List.of(new SchematicLinkData.Reference(List.of("Links"), "Pos", null, SchematicLinkData.PositionFormat.LONG)),
                Set.of(), List.of(), Set.of(), List.of(List.of("State", "fields", "Direction"))));
        CompoundTag raw = new CompoundTag(), state = new CompoundTag(), fields = new CompoundTag();
        fields.putString("Direction", "east"); fields.putString("Inventory", "source contents");
        state.put("fields", fields); raw.put("State", state);
        CompoundTag inside = new CompoundTag(), outside = new CompoundTag();
        inside.putLong("Pos", new BlockPos(1, 2, 3).asLong()); outside.putLong("Pos", new BlockPos(99, 2, 3).asLong());
        ListTag links = new ListTag(); links.add(inside); links.add(outside); raw.put("Links", links);
        CompoundTag safe = new CompoundTag();
        SchematicLinkData.capture(be, raw, safe, source, List.of(new SchematicConnectionData.BodyFrame(
                source, template, BlockPos.ZERO, new BlockPos(8, 8, 8))));
        assertFalse(safe.toString().contains("source contents"));
        var ctx = new SubLevelSchematicSerializationContext(SubLevelSchematicSerializationContext.Type.PLACE, null);
        ctx.getMappings().put(template, new SubLevelSchematicSerializationContext.SchematicMapping(null, null, placed, pos -> (BlockPos)pos));
        CompoundTag current = new CompoundTag(), currentState = new CompoundTag(), currentFields = new CompoundTag();
        currentFields.putString("Inventory", "destination contents"); currentState.put("fields", currentFields); current.put("State", currentState);
        ServerLevel level = mock(ServerLevel.class);
        when(level.getBlockEntity(BlockPos.ZERO)).thenReturn(be);
        when(be.saveWithFullMetadata(any())).thenReturn(current);
        SchematicLinkData.restore(level, BlockPos.ZERO, safe, ctx, Vec3.ZERO, new Quaterniond());
        ArgumentCaptor<CompoundTag> captured = ArgumentCaptor.forClass(CompoundTag.class);
        verify(be).loadWithComponents(captured.capture(), any());
        CompoundTag restored = captured.getValue();
        assertEquals(1, restored.getList("Links", 10).size());
        assertEquals("east", restored.getCompound("State").getCompound("fields").getString("Direction"));
        assertEquals("destination contents", restored.getCompound("State").getCompound("fields").getString("Inventory"));
    }
}
