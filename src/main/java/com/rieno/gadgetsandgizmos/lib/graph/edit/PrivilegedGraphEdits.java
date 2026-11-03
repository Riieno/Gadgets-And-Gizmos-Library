package com.rieno.gadgetsandgizmos.lib.graph.edit;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

// Authorize protected graph changes only inside an authenticated operator edit
public final class PrivilegedGraphEdits{
    private static final ThreadLocal<ServerPlayer> ACTOR = new ThreadLocal<>();

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        FUNCTIONS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    // Keep authorization on the static facade
    private PrivilegedGraphEdits(){}

    // Check the server's current operator permissions
    public static boolean isOperator(ServerPlayer player){
        return player != null && player.hasPermissions(2);
    }

    // Scope authorization to one synchronous request and restore nested callers
    public static void run(ServerPlayer player, Runnable action){
        ServerPlayer prev = ACTOR.get();
        ACTOR.set(player);
        try{ action.run(); }
        finally{ if(prev == null) ACTOR.remove(); else ACTOR.set(prev); }
    }

    // Reject creation, movement, rewiring and removal of protected nodes and their data sources
    public static boolean canReplace(CompoundTag prev, CompoundTag next, Predicate<String> privileged){
        return isOperator(ACTOR.get()) || projection(prev, privileged).equals(projection(next, privileged));
    }

    // Find protected nodes in the root graph or nested function graphs
    public static boolean contains(CompoundTag graph, Predicate<String> privileged){
        for(Tag val : graph.getList("Nodes", Tag.TAG_COMPOUND)){
            if(privileged.test(((CompoundTag)val).getString("Type"))) return true;
        }
        for(Tag val : graph.getList("Functions", Tag.TAG_COMPOUND)){
            if(contains((CompoundTag)val, privileged)) return true;
        }
        return false;
    }

    // Retain protected configuration and its incoming dependency chain without locking unrelated nodes
    private static CompoundTag projection(CompoundTag graph, Predicate<String> privileged){
        var res = new CompoundTag();
        var nodes = graph.getList("Nodes", Tag.TAG_COMPOUND);
        var edges = graph.getList("Edges", Tag.TAG_COMPOUND);
        Set<String> ids = new HashSet<>();
        for(Tag val : nodes){
            var node = (CompoundTag)val;
            if(privileged.test(node.getString("Type"))) ids.add(node.getString("Id"));
        }
        boolean changed;
        do{
            changed = false;
            for(Tag val : edges){
                var edge = (CompoundTag)val;
                if(ids.contains(edge.getString("ToNode"))) changed |= ids.add(edge.getString("FromNode"));
            }
        }while(changed);
        for(Tag val : nodes){
            var node = (CompoundTag)val;
            if(!ids.contains(node.getString("Id"))) continue;
            String key = "node:" + node.getString("Id");
            if(res.contains(key)) key = "duplicate:" + key + ":" + res.size();
            res.put(key, node.copy());
        }
        for(Tag val : edges){
            var edge = (CompoundTag)val;
            if(!ids.contains(edge.getString("FromNode")) && !ids.contains(edge.getString("ToNode"))) continue;
            String key = "edge:" + edge.getString("Id");
            if(res.contains(key)) key = "duplicate:" + key + ":" + res.size();
            res.put(key, edge.copy());
        }
        // Function bindings and variable defaults can change a command without editing its own node
        if(!ids.isEmpty()){
            res.put("Variables", graph.getCompound("Variables").copy());
            res.put("Functions", graph.getList("Functions", Tag.TAG_COMPOUND).copy());
        }else{
            for(Tag val : graph.getList("Functions", Tag.TAG_COMPOUND)){
                var function = (CompoundTag)val;
                if(!contains(function, privileged)) continue;
                var retained = graph.copy();
                for(String key : Set.of("Revision", "ViewportX", "ViewportY", "ViewportZoom")) retained.remove(key);
                res.put("Graph", retained);
                break;
            }
        }
        return res;
    }
}
