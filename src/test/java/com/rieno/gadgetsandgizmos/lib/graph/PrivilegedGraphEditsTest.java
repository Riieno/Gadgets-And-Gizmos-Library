package com.rieno.gadgetsandgizmos.lib.graph;

import com.rieno.gadgetsandgizmos.lib.graph.edit.PrivilegedGraphEdits;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// Exercise protected edits without granting authority to an unauthenticated caller
final class PrivilegedGraphEditsTest{
    // Reject creation, movement, replacement and deletion while allowing unrelated edits
    @Test void protectedNodesCannotBeChangedWithoutAnOperator(){
        var graph = graph();
        assertFalse(allowed(new CompoundTag(), graph));
        assertFalse(allowed(graph, new CompoundTag()));
        var next = graph.copy();
        next.getList("Nodes", Tag.TAG_COMPOUND).getCompound(0).putDouble("X", 20);
        assertFalse(allowed(graph, next));
        next = graph.copy();
        next.getList("Nodes", Tag.TAG_COMPOUND).getCompound(1).putDouble("X", 20);
        assertTrue(allowed(graph, next));
        assertTrue(allowed(graph, graph.copy()));
    }

    // Protect input wiring and the nodes that supply executable command text
    @Test void wiringAndUpstreamConfigurationAreProtected(){
        var graph = graph();
        var edge = new CompoundTag();
        edge.putString("Id", "wire"); edge.putString("FromNode", "text"); edge.putString("ToNode", "command");
        var edges = new ListTag(); edges.add(edge); graph.put("Edges", edges);
        var next = graph.copy();
        next.getList("Nodes", Tag.TAG_COMPOUND).getCompound(1).putString("Value", "op someone");
        assertFalse(allowed(graph, next));
        next = graph.copy(); next.remove("Edges");
        assertFalse(allowed(graph, next));
    }

    // Reject duplicate identifiers that would otherwise hide a changed command in a keyed projection
    @Test void duplicateIdentifiersCannotShadowAProtectedNode(){
        var graph = graph();
        var next = graph.copy();
        var duplicate = next.getList("Nodes", Tag.TAG_COMPOUND).getCompound(0).copy();
        duplicate.putString("Value", "changed command");
        next.getList("Nodes", Tag.TAG_COMPOUND).addFirst(duplicate);
        assertFalse(allowed(graph, next));
    }

    // Protect nested commands when an entire function is replaced or removed
    @Test void nestedFunctionsCannotHideProtectedChanges(){
        var graph = new CompoundTag();
        var functions = new ListTag();
        var function = graph(); function.putString("Id", "function"); functions.add(function); graph.put("Functions", functions);
        assertTrue(PrivilegedGraphEdits.contains(graph, "command"::equals));
        var next = graph.copy(); next.remove("Functions");
        assertFalse(allowed(graph, next));
    }

    // Build one protected command and one ordinary string node
    private static CompoundTag graph(){
        var graph = new CompoundTag();
        var nodes = new ListTag();
        var command = new CompoundTag(); command.putString("Id", "command"); command.putString("Type", "command");
        var text = new CompoundTag(); text.putString("Id", "text"); text.putString("Type", "string");
        nodes.add(command); nodes.add(text); graph.put("Nodes", nodes);
        return graph;
    }

    // Evaluate the same server policy used by graph replacement entry points
    private static boolean allowed(CompoundTag prev, CompoundTag next){
        return PrivilegedGraphEdits.canReplace(prev, next, "command"::equals);
    }
}
