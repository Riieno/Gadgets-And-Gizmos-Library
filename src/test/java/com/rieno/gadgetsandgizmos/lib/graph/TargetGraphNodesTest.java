package com.rieno.gadgetsandgizmos.lib.graph;

import com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryKind;
import com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryNode;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

// Keep target selection, typed iteration and exact block identity independent of a graph host
class TargetGraphNodesTest{
    @Test void definitionsUseTheOwnerNamespaceAndNeedNoExecutionPulse(){
        var target = TargetGraphNodes.targetDefinition("example");
        var targets = TargetGraphNodes.getTargetsDefinition("example");
        assertEquals("example:target", target.id());
        assertEquals("example:get_targets", targets.id());
        assertTrue(target.inputs().isEmpty());
        assertTrue(targets.inputs().isEmpty());
        assertEquals(Map.of("target", "target"), target.outputs());
        assertEquals(Map.of("targets", "list"), targets.outputs());
        assertFalse(target.stateful());
        assertFalse(targets.stateful());
    }

    @Test void changingAndReloadingTheSelectionRetainsExactlyOneTarget() throws Exception{
        CompoundTag data = new CompoundTag();
        TargetGraphNodes.select(data, discovery("first"));
        TargetGraphNodes.select(data, discovery("second"));
        CompoundTag restored = TagParser.parseTag(data.toString());
        Ctx ctx = new Ctx();
        ctx.selected = TargetGraphNodes.reference(restored.getCompound(TargetGraphNodes.TARGET_DATA));
        assertEquals(discovery("second").toTag(), TargetGraphNodes.payload(
                TargetGraphNodes.target(ctx, Map.of()).get("target")));
        assertEquals("second", restored.getString("Target"));
        TargetGraphNodes.select(restored, null);
        assertFalse(restored.contains(TargetGraphNodes.TARGET_DATA));
        assertFalse(restored.contains("TargetLabel"));
        assertFalse(restored.contains("Target"));
    }

    @Test void referencesPreserveLargePositionsUuidsAndDetachedMetadata(){
        CompoundTag data = discovery("precise").toTag();
        data.putByteArray("Bytes", new byte[]{-1, 2});
        data.putIntArray("Ints", new int[]{Integer.MIN_VALUE, Integer.MAX_VALUE});
        data.putLongArray("Longs", new long[]{Long.MIN_VALUE, Long.MAX_VALUE});
        CompoundTag expected = data.copy();
        GraphValue reference = TargetGraphNodes.reference(data);
        data.putString("NodeId", "changed");
        CompoundTag payload = TargetGraphNodes.payload(reference);
        assertEquals(expected, payload);
        assertEquals(expected.getUUID("SubLevelId"), payload.getUUID("SubLevelId"));
        assertEquals(expected.getLong("BlockPos"), payload.getLong("BlockPos"));
        payload.remove("BlockPos");
        assertEquals(expected, TargetGraphNodes.payload(reference));
        assertEquals(expected, GraphDataNbt.toCompound(GraphDataNbt.fromCompound(expected)));
    }

    @Test void targetListsRefreshAndKeepTypedEntriesInHostOrder(){
        Ctx ctx = new Ctx();
        GraphValue first = TargetGraphNodes.reference(discovery("first").toTag());
        GraphValue second = TargetGraphNodes.reference(discovery("second").toTag());
        List<GraphValue> available = new ArrayList<>(List.of(first, second));
        ctx.source = () -> available;
        GraphValue snapshot = TargetGraphNodes.getTargets(ctx, Map.of()).get("targets");
        assertEquals("list", snapshot.type());
        assertEquals(List.of(first, second), snapshot.entries());
        assertEquals("target", snapshot.entries().getFirst().type());
        available.removeFirst();
        assertEquals(List.of(second), TargetGraphNodes.getTargets(ctx, Map.of()).get("targets").entries());
        assertEquals(List.of(first, second), snapshot.entries());
        available.clear();
        assertTrue(TargetGraphNodes.getTargets(ctx, Map.of()).get("targets").entries().isEmpty());
    }

    @Test void unavailableHostsAndUnsetSelectionsKeepTheirOutputTypes(){
        Ctx ctx = new Ctx();
        GraphValue selected = TargetGraphNodes.target(ctx, Map.of()).get("target");
        assertEquals("target", selected.type());
        assertTrue(TargetGraphNodes.payload(selected).isEmpty());
        GraphValue targets = TargetGraphNodes.getTargets(ctx, Map.of()).get("targets");
        assertEquals("list", targets.type());
        assertTrue(targets.entries().isEmpty());
    }

    private static ControllerDiscoveryNode discovery(String id){
        return new ControllerDiscoveryNode(id, ControllerDiscoveryKind.MACHINE, "group", "minecraft:chest",
                id, UUID.fromString("01234567-89ab-cdef-0123-456789abcdef"), new BlockPos(30_000_001, -19, -29_999_997));
    }

    private static final class Ctx implements GraphExecutionContext{
        private GraphValue selected;
        private GraphTargetSource source;
        @Override public long tick(){ return 0; }
        @Override public GraphValue state(String key){ return null; }
        @Override public void state(String key, GraphValue val){}
        @Override public GraphValue configuration(String key){ return selected; }
        @Override public <T> Optional<T> service(GraphServiceKey<T> key){
            return GraphHostServices.TARGETS.equals(key) && source != null
                    ? Optional.of(key.type().cast(source)) : Optional.empty();
        }
    }
}
