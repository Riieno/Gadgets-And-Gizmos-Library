package com.rieno.gadgetsandgizmos.lib.physics;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.server.level.ServerLevel;

import java.util.Collection;
import java.util.LinkedHashSet;

// Unload connected plots while their native bodies are still available to block entity callbacks
public final class SableSubLevelLifecycleApi{
    private SableSubLevelLifecycleApi(){}

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
