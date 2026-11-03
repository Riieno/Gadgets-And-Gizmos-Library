package com.rieno.gadgetsandgizmos.lib.graph;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// Verify that disabling a type retains saved instances without allowing new ones
final class GraphNodeAvailabilityTest{
    // Permit retention and removal while rejecting replacement identifiers or duplicate copies
    @Test void disabledNodesCannotBeCreatedOrCopied(){
        var graph = graph("existing");
        assertTrue(allowed(graph, graph.copy()));
        assertTrue(allowed(graph, new CompoundTag()));
        assertFalse(allowed(new CompoundTag(), graph));
        assertFalse(allowed(graph, graph("replacement")));
        var duplicate = graph.copy();
        duplicate.getList("Nodes", Tag.TAG_COMPOUND).add(graph.getList("Nodes", Tag.TAG_COMPOUND).getCompound(0).copy());
        assertFalse(allowed(graph, duplicate));
    }

    // Treat moving a command into another function as creating a new scoped instance
    @Test void nestedFunctionsCannotBypassCreationPolicy(){
        var graph = new CompoundTag();
        var function = graph("command"); function.putString("Id", "first");
        var functions = new ListTag(); functions.add(function); graph.put("Functions", functions);
        var next = graph.copy(); next.getList("Functions", Tag.TAG_COMPOUND).getCompound(0).putString("Id", "second");
        assertFalse(allowed(graph, next));
        assertTrue(allowed(graph, graph.copy()));
    }

    // Build one disabled node with a stable identity
    private static CompoundTag graph(String id){
        var graph = new CompoundTag(); var node = new CompoundTag();
        node.putString("Id", id); node.putString("Type", "command");
        var nodes = new ListTag(); nodes.add(node); graph.put("Nodes", nodes);
        return graph;
    }

    // Apply the same disabled-type predicate to both documents
    private static boolean allowed(CompoundTag prev, CompoundTag next){
        return GraphNodeAvailability.canReplace(prev, next, "command"::equals);
    }
}
