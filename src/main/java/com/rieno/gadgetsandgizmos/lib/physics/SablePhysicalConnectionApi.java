package com.rieno.gadgetsandgizmos.lib.physics;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.GadgetsNGizmosLibrary;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

// Discover real attachments which do not publish Sable actor dependencies
@EventBusSubscriber(modid = GadgetsNGizmosLibrary.MOD_ID)
public final class SablePhysicalConnectionApi{
    private static final Map<ServerLevel, State> STATES = new WeakHashMap<>();

    private SablePhysicalConnectionApi(){}

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    ========================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Retain only live constraints between two sublevels in the same dimension
    public static synchronized void track(ServerSubLevel first, ServerSubLevel second, PhysicsConstraintHandle handle){
        if(first == null || second == null || first == second || handle == null
                || first.getLevel() != second.getLevel()) return;
        State state = STATES.computeIfAbsent(first.getLevel(), ignored -> new State());
        if(state.links.containsKey(handle)) return;
        state.links.put(handle, new Link(first, second, handle));
        state.validatedTick = Long.MIN_VALUE;
        state.revision++;
    }

    // Native removals invalidate ownership immediately without polling every handle
    public static synchronized void untrack(PhysicsConstraintHandle handle){
        for(State state : STATES.values()) if(state.links.remove(handle) != null) state.revision++;
    }

    // Remove all attachment edges when their native body leaves the pipeline
    public static synchronized void untrack(ServerSubLevel body){
        State state = body == null ? null : STATES.get(body.getLevel());
        if(state != null && state.links.values().removeIf(link -> link.first() == body || link.second() == body)) state.revision++;
    }

    // Recheck native breakage after physics publishes its next frame
    public static synchronized void invalidate(ServerLevel level){
        State state = STATES.get(level);
        if(state != null) state.validatedTick = Long.MIN_VALUE;
    }

    // Rebuild collision membership after an attachment is added or removed
    public static synchronized long revision(ServerLevel level){
        State state = state(level);
        return state == null ? 0L : state.revision;
    }

    // Expand owned bodies through real constraints without changing control-map partitions
    public static synchronized Set<UUID> connectedBodyIds(ServerLevel level, Collection<UUID> roots){
        Set<UUID> ids = new LinkedHashSet<>(roots);
        State state = state(level);
        if(state == null) return Set.copyOf(ids);
        state.updateComponents();
        for(UUID root : roots) ids.addAll(state.components.getOrDefault(root, Set.of()));
        return Set.copyOf(ids);
    }

    // Release native handles when their dimension unloads
    @SubscribeEvent public static synchronized void onLevelUnload(LevelEvent.Unload evt){
        if(evt.getLevel() instanceof ServerLevel level) STATES.remove(level);
    }

    // Drop broken handles before returning a membership snapshot
    private static State state(ServerLevel level){
        State state = STATES.get(level);
        if(state != null && state.validatedTick != level.getGameTime()){
            state.validatedTick = level.getGameTime();
            if(state.links.values().removeIf(link -> link.first().isRemoved()
                    || link.second().isRemoved() || !link.handle().isValid())) state.revision++;
        }
        return state;
    }

    private record Link(ServerSubLevel first, ServerSubLevel second, PhysicsConstraintHandle handle){}

    private static final class State{
        private final Map<PhysicsConstraintHandle, Link> links = new IdentityHashMap<>();
        private long revision;
        private long validatedTick = Long.MIN_VALUE;
        private long componentsRevision = Long.MIN_VALUE;
        private final Map<UUID, Set<UUID>> components = new HashMap<>();

        // Build each attachment component once per membership revision
        private void updateComponents(){
            if(componentsRevision == revision) return;
            componentsRevision = revision;
            components.clear();
            Map<UUID, Set<UUID>> adjacent = new HashMap<>();
            for(Link link : links.values()){
                UUID first = link.first().getUniqueId(), second = link.second().getUniqueId();
                adjacent.computeIfAbsent(first, ignored -> new HashSet<>()).add(second);
                adjacent.computeIfAbsent(second, ignored -> new HashSet<>()).add(first);
            }
            for(UUID root : adjacent.keySet()){
                if(components.containsKey(root)) continue;
                Set<UUID> ids = new HashSet<>();
                ArrayDeque<UUID> pending = new ArrayDeque<>();
                ids.add(root); pending.add(root);
                while(!pending.isEmpty()){
                    for(UUID next : adjacent.get(pending.removeFirst())) if(ids.add(next)) pending.addLast(next);
                }
                Set<UUID> component = Set.copyOf(ids);
                for(UUID id : component) components.put(id, component);
            }
        }
    }
}
