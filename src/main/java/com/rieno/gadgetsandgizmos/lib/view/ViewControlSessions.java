package com.rieno.gadgetsandgizmos.lib.view;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

// Arbitrate short control leases after the host has authorized its selected source
@EventBusSubscriber(modid = "gadgetsngizmos")
public final class ViewControlSessions{
    private static final Map<ViewReference, Lease> LEASES = new LinkedHashMap<>();
    private ViewControlSessions(){}

    // Apply one authorized command per player and source in a server tick
    public static boolean apply(ServerPlayer player, ViewReference ref, ViewControlInput input){
        if(player == null || !player.isAlive() || ref == null || input == null || !input.valid()) return false;
        ViewSource resolved = ref.resolve(player.level());
        if(!(resolved instanceof ControlledViewSource src) || RemoteViewSessions.isViewing(src)) return false;
        long tick = player.level().getGameTime();
        Lease prev = LEASES.get(ref);
        if(prev != null && (prev.source != src || tick - prev.lastUse > 20 || !prev.player.isAlive()
                || prev.player.hasDisconnected() || !prev.player.level().dimension().location().equals(ref.dimension()))){
            release(ref);
            prev = null;
        }
        if(prev != null && !prev.player.getUUID().equals(player.getUUID())) return false;
        if(src instanceof net.minecraft.world.level.block.entity.BlockEntity be){
            com.rieno.gadgetsandgizmos.lib.interaction.BlockInteractionTracker.record(be.getLevel(), be.getBlockPos(), player);
        }
        ViewControlState state = input.settings(src.viewControlState());
        src.applyViewSettings(state);
        boolean motion = input.pan() != 0 || input.tilt() != 0;
        if(motion && state.mode() != ViewRig.Mode.MANUAL){
            src.applyViewSettings(new ViewControlState(ViewRig.Mode.MANUAL, state.fov(), state.flashlight()));
        }
        if(prev == null){
            prev = new Lease(player, src);
            LEASES.put(ref, prev);
        }
        if(prev.lastUse != tick) src.controlView(input.pan(), input.tilt(), 0);
        prev.lastUse = tick;
        return true;
    }
    // Check ownership before transferring control to a body-locking remote session
    public static boolean available(ViewReference ref, UUID player){
        Lease lease = LEASES.get(ref);
        return lease == null || lease.player.getUUID().equals(player);
    }
    // Drop a lease without changing the source's chosen mode or aim
    public static void release(ViewReference ref){ LEASES.remove(ref); }
    // Expire abandoned controls without retaining disconnected players or unloaded sources
    @SubscribeEvent
    public static void tick(ServerTickEvent.Post evt){
        for(var entry : new ArrayList<>(LEASES.entrySet())){
            Lease lease = entry.getValue();
            if(lease.player.hasDisconnected() || !lease.player.isAlive() || !lease.source.isViewAvailable()
                    || entry.getKey().resolve(lease.player.level()) != lease.source
                    || lease.player.level().getGameTime() - lease.lastUse > 20) release(entry.getKey());
        }
    }
    // Release server ownership between worlds
    @SubscribeEvent
    public static void stopped(ServerStoppedEvent evt){ LEASES.clear(); }

    private static final class Lease{
        private final ServerPlayer player;
        private final ControlledViewSource source;
        private long lastUse = Long.MIN_VALUE;
        private Lease(ServerPlayer player, ControlledViewSource source){ this.player = player; this.source = source; }
    }
}
