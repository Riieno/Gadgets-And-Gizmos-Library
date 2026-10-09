package com.rieno.gadgetsandgizmos.lib.view;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

// Lock player controls while retaining server ownership of a loaded view source
@EventBusSubscriber(modid = "gadgetsngizmos")
public final class RemoteViewSessions{
/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                            CONSTANTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    // Retain sessions only until exit, source loss, disconnect or server shutdown
    private static final Map<UUID, Session> SESSIONS = new LinkedHashMap<>();

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                            FUNCTIONS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    // Prevent construction of the session facade
    private RemoteViewSessions(){}
    // Enter a loaded view without moving the player's body to the camera
    public static boolean open(ServerPlayer player, ViewReference ref){
        if(player == null || !player.isAlive() || player.isPassenger()) return false;
        ViewSource src = ref.resolve(player.level());
        if(src == null) return false;
        Session prev = SESSIONS.get(player.getUUID());
        if(prev != null && prev.ref.equals(ref)) return true;
        if(isViewing(src) || !ViewControlSessions.available(ref, player.getUUID())) return false;
        ViewControlSessions.release(ref);
        close(player);
        Session session = new Session(player, ref, src);
        SESSIONS.put(player.getUUID(), session);
        src.setViewControlled(true);
        player.setNoGravity(true);
        player.setDeltaMovement(Vec3.ZERO);
        player.connection.teleport(session.pos.x, session.pos.y, session.pos.z, session.yaw, session.pitch);
        PacketDistributor.sendToPlayer(player, new RemoteViewStatePayload(ref.toTag(), true));
        return true;
    }
    // Check whether movement packets belong to a locked session
    public static boolean isLocked(ServerPlayer player){ return SESSIONS.containsKey(player.getUUID()); }
    // Check whether a source currently has a remote controller
    public static boolean isViewing(ViewSource src){
        return SESSIONS.values().stream().anyMatch(session -> session.source == src);
    }
    // Apply at most one bounded control sample per game tick
    public static void input(ServerPlayer player, RemoteViewPayload msg){
        Session session = SESSIONS.get(player.getUUID());
        if(session == null) return;
        if(msg.exit()){
            close(player);
            return;
        }
        long tick = player.level().getGameTime();
        if(session.lastInput == tick) return;
        session.lastInput = tick;
        ViewSource src = session.ref.resolve(player.level());
        if(src == null){ close(player); return; }
        if(!Double.isFinite(msg.pan()) || !Double.isFinite(msg.tilt()) || !Double.isFinite(msg.zoom())) return;
        src.controlView(Math.clamp(msg.pan(), -45, 45), Math.clamp(msg.tilt(), -45, 45), Math.clamp(msg.zoom(), -10, 10));
    }
    // Release all movement state and restore the saved rotation
    public static void close(ServerPlayer player){
        Session session = SESSIONS.remove(player.getUUID());
        if(session == null) return;
        session.source.setViewControlled(false);
        player.setNoGravity(session.noGravity);
        player.setDeltaMovement(Vec3.ZERO);
        if(player.level().dimension().location().equals(session.ref.dimension()) && player.isAlive()){
            player.connection.teleport(session.pos.x, session.pos.y, session.pos.z, session.yaw, session.pitch);
        }
        if(!player.hasDisconnected()) PacketDistributor.sendToPlayer(player, new RemoteViewStatePayload(new CompoundTag(), false));
    }
    // Hold position and expire sessions whose camera or client is unavailable
    @SubscribeEvent
    public static void tick(ServerTickEvent.Post evt){
        for(Session session : new ArrayList<>(SESSIONS.values())){
            ServerPlayer player = session.player;
            if(!player.isAlive() || player.hasDisconnected() || session.ref.resolve(player.level()) == null
                    || player.level().getGameTime() - session.lastInput > 60){
                close(player);
                continue;
            }
            player.setPos(session.pos);
            player.setYRot(session.yaw);
            player.setXRot(session.pitch);
            player.setDeltaMovement(Vec3.ZERO);
            player.fallDistance = 0;
        }
    }
    // Release the saved state before the player leaves
    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent evt){
        if(evt.getEntity() instanceof ServerPlayer player) close(player);
    }
    // Release the saved state when the player changes dimensions
    @SubscribeEvent
    public static void changeDimension(PlayerEvent.PlayerChangedDimensionEvent evt){
        if(evt.getEntity() instanceof ServerPlayer player) close(player);
    }
    // Clear every session when its server stops
    @SubscribeEvent
    public static void stop(ServerStoppingEvent evt){
        for(Session session : new ArrayList<>(SESSIONS.values())) close(session.player);
    }
    // Retain the body state without altering game mode or inventory
    private static final class Session{
        private final ServerPlayer player;
        private final ViewReference ref;
        private final ViewSource source;
        private final Vec3 pos;
        private final float yaw;
        private final float pitch;
        private final boolean noGravity;
        private long lastInput;
        // Capture the exact state restored when the session ends
        private Session(ServerPlayer player, ViewReference ref, ViewSource source){
            this.player = player;
            this.ref = ref;
            this.source = source;
            pos = player.position();
            yaw = player.getYRot();
            pitch = player.getXRot();
            noGravity = player.isNoGravity();
            lastInput = player.level().getGameTime();
        }
    }
}
