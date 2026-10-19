package com.rieno.gadgetsandgizmos.lib.view;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
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
        if(src instanceof PredictedViewSource predicted) PacketDistributor.sendToPlayer(player,
                new RemoteViewAimPayload(ref.toTag(), 0, predicted.viewAim()));
        return true;
    }
    // Check whether movement packets belong to a locked session
    public static boolean isLocked(ServerPlayer player){ return SESSIONS.containsKey(player.getUUID()); }
    // Expose the acknowledged source without accepting a client-supplied action target
    public static @Nullable ViewReference source(ServerPlayer player){
        Session session = player == null ? null : SESSIONS.get(player.getUUID());
        return session == null ? null : session.ref;
    }
    // Toggle the current source's light once per server tick while retaining its other settings
    public static boolean toggleFlashlight(ServerPlayer player){
        Session session = active(player);
        if(session == null || !(session.source instanceof ControlledViewSource camera)) return false;
        long tick = player.level().getGameTime();
        if(session.lastFlashlight == tick) return false;
        session.lastFlashlight = tick;
        ViewControlState state = camera.viewControlState();
        camera.applyViewSettings(new ViewControlState(state.mode(), state.fov(), !state.flashlight(),
                state.autoTracking(), state.lockTarget()));
        return true;
    }
    // Sample the lens sensor once per tick and leave normal graph sampling under the source's control
    public static @Nullable ViewRaycast.Result raycast(ServerPlayer player){
        Session session = active(player);
        if(session == null || !(session.source instanceof RaycastViewSource camera)) return null;
        long tick = player.level().getGameTime();
        if(session.lastRaycast == tick) return null;
        session.lastRaycast = tick;
        return camera.castViewRay();
    }
    // Use the current server-acknowledged lens rather than a client-provided hit
    public static boolean interact(ServerPlayer player){
        Session session = active(player);
        return session != null && ViewBlockInteractions.interact(player, session.ref);
    }
    private static @Nullable Session active(ServerPlayer player){
        Session session = player == null ? null : SESSIONS.get(player.getUUID());
        if(session == null) return null;
        ViewSource resolved = session.ref.resolve(player.level());
        boolean same = resolved == session.source || resolved != null && session.source.viewEntity() != null
                && resolved.viewEntity() == session.source.viewEntity();
        if(!player.isAlive() || player.hasDisconnected() || !same){
            close(player);
            return null;
        }
        return session;
    }
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
        applyInput(session, src, msg.pan(), msg.tilt(), msg.zoom());
    }
    // Share a tick's motion allowance when an action flushes input before the next regular sample
    public static void input(ServerPlayer player, RemoteViewInputPayload msg){
        Session session = active(player);
        if(session == null || !(session.source instanceof PredictedViewSource predicted)
                || msg.sequence() <= session.inputSequence) return;
        session.inputSequence = msg.sequence();
        session.lastInput = player.level().getGameTime();
        if(Double.isFinite(msg.pan()) && Double.isFinite(msg.tilt()) && Double.isFinite(msg.zoom())){
            applyInput(session, predicted, msg.pan(), msg.tilt(), msg.zoom());
        }
        if(SESSIONS.get(player.getUUID()) == session) PacketDistributor.sendToPlayer(player,
                new RemoteViewAimPayload(session.ref.toTag(), msg.sequence(), predicted.viewAim()));
    }
    private static void applyInput(Session session, ViewSource source, double panDelta, double tiltDelta, double zoomSteps){
        long tick = session.player.level().getGameTime();
        if(session.predictedInputTick != tick){
            session.predictedInputTick = tick;
            session.inputPan = session.inputTilt = session.inputZoom = 0;
        }
        double pan = Math.clamp(session.inputPan + panDelta, -45, 45);
        double tilt = Math.clamp(session.inputTilt + tiltDelta, -45, 45);
        double zoom = Math.clamp(session.inputZoom + zoomSteps, -10, 10);
        source.controlView(pan - session.inputPan, tilt - session.inputTilt, zoom - session.inputZoom);
        session.inputPan = pan;
        session.inputTilt = tilt;
        session.inputZoom = zoom;
    }
    // Release all movement state and restore the saved rotation
    public static void close(ServerPlayer player){
        Session session = SESSIONS.remove(player.getUUID());
        if(session == null) return;
        var interaction = ViewInteractionContext.cursor(player);
        if(interaction != null && interaction.source().equals(session.ref)) ViewInteractionContext.end(player);
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
        private long inputSequence;
        private long predictedInputTick = Long.MIN_VALUE;
        private double inputPan;
        private double inputTilt;
        private double inputZoom;
        private long lastFlashlight = Long.MIN_VALUE;
        private long lastRaycast = Long.MIN_VALUE;
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
