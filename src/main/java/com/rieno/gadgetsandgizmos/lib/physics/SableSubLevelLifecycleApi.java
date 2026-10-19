package com.rieno.gadgetsandgizmos.lib.physics;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.server.level.ServerLevel;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.IdentityHashMap;
import java.util.Map;

// Unload connected plots while their native bodies are still available to block entity callbacks
public final class SableSubLevelLifecycleApi{
    // Retain source plots through nested transfers on the owning game thread
    private static final ThreadLocal<Map<ServerSubLevel, Integer>> TRANSFERS = ThreadLocal.withInitial(IdentityHashMap::new);
    // Keep lifecycle operations on the owning level's static API
    private SableSubLevelLifecycleApi(){}

    // Delay empty-plot removal until every source block and tracking point has moved
    public static void transfer(ServerLevel level, ServerSubLevel body, Runnable action){
        if(!level.getServer().isSameThread()) throw new IllegalStateException("Sublevel transfer requires the server thread");
        if(body == null || body.getLevel() != level) throw new IllegalArgumentException("Sublevel belongs to another level");
        if(SubLevelPhysicsSystem.IN_PHYSICS_STEP) throw new IllegalStateException("Sublevels cannot transfer during a physics step");
        Map<ServerSubLevel, Integer> active = TRANSFERS.get();
        active.merge(body, 1, Integer::sum);
        try{ action.run(); }
        finally{
            int remaining = active.get(body) - 1;
            if(remaining > 0) active.put(body, remaining);
            else active.remove(body);
            if(active.isEmpty()) TRANSFERS.remove();
            if(remaining == 0) body.onPlotBoundsChanged();
        }
    }

    // Check whether a native removal callback is inside an unfinished transfer
    public static boolean isTransferring(ServerSubLevel body){
        Map<ServerSubLevel, Integer> active = TRANSFERS.get();
        boolean retained = active.containsKey(body);
        if(active.isEmpty()) TRANSFERS.remove();
        return retained;
    }

    // Remove an emptied plot after disassembly while retaining bodies with remaining blocks
    public static boolean removeEmpty(ServerLevel level, ServerSubLevel body){
        if(body == null || body.getLevel() != level) return false;
        var container = SubLevelContainer.getContainer(level);
        if(container == null || container.getSubLevel(body.getUniqueId()) != body) return false;
        for(var holder : body.getPlot().getLoadedChunks()){
            var chunk = holder.getChunk();
            if(chunk == null) return false;
            for(var section : chunk.getSections()) if(!section.hasOnlyAir()) return false;
        }
        remove(level, java.util.List.of(body));
        return true;
    }

    // Permanently remove loaded bodies on their server thread after every plot finishes unloading
    public static void remove(ServerLevel level, Collection<ServerSubLevel> bodies){
        if(!level.getServer().isSameThread()) throw new IllegalStateException("Sublevel removal requires the server thread");
        if(SubLevelPhysicsSystem.IN_PHYSICS_STEP) throw new IllegalStateException("Sublevels cannot be removed during a physics step");
        var container = SubLevelContainer.getContainer(level);
        var selected = new LinkedHashSet<>(bodies);
        for(ServerSubLevel body : selected){
            if(body == null || body.getLevel() != level || container.getSubLevel(body.getUniqueId()) != body){
                throw new IllegalArgumentException("Sublevel is unavailable in this level");
            }
        }
        for(ServerSubLevel body : selected) body.getPlot().kickAllEntities();
        // Unload the whole assembly before removing a body needed by another plot's callbacks
        for(ServerSubLevel body : selected) body.getPlot().onRemove();
        for(ServerSubLevel body : selected) container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
    }
}
