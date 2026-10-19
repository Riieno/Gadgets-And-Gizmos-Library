package com.rieno.gadgetsandgizmos.lib.view;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

// Retain an authenticated target while its native client handler sends drag and GUI input
@EventBusSubscriber(modid = "gadgetsngizmos")
public final class ViewInteractionContext{
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final ThreadLocal<Scoped> SCOPE = new ThreadLocal<>();
    private static Function<Player, Cursor> client = player -> null;
    private ViewInteractionContext(){}

    public record Cursor(ViewReference source, ViewInteractionTargets.Target target, ViewPose pose, double range){}
    private record Scoped(Player player, Cursor cursor){}
    private record Session(ServerPlayer player, Cursor cursor, Object lens, Object block, BlockEntity entity, Vec3 origin, long expires){}

    // Install a physical-client provider without loading client classes on dedicated servers
    public static void registerClient(Function<Player, Cursor> provider){ client = java.util.Objects.requireNonNull(provider); }

    static void begin(ServerPlayer player, Cursor cursor){
        BlockPos pos = cursor.target.reference().blockPos();
        SESSIONS.put(player.getUUID(), new Session(player, cursor, cursor.source.resolve(player.level()),
                player.level().getBlockState(pos).getBlock(), player.level().getBlockEntity(pos), player.position(), player.level().getGameTime() + 200));
    }

    // Refresh only a previously server-selected target; packets cannot choose another block
    public static void keepAlive(ServerPlayer player){
        Session session = session(player);
        if(session == null) return;
        SESSIONS.put(player.getUUID(), new Session(player, session.cursor, session.lens, session.block,
                session.entity, session.origin, player.level().getGameTime() + 200));
    }
    public static void end(ServerPlayer player){ SESSIONS.remove(player.getUUID()); }

    public static @Nullable Cursor cursor(Player player){
        if(player == null) return null;
        if(player.level().isClientSide) return client.apply(player);
        if(!(player instanceof ServerPlayer server)) return null;
        Session session = session(server);
        if(session == null) return null;
        var source = (RaycastViewSource) session.lens;
        return new Cursor(session.cursor.source, session.cursor.target, source.viewPose(0), source.interactionRange());
    }

    // Virtualize eye and view getters only during native server use, and for the local camera client
    public static @Nullable Cursor perspective(Player player){
        if(player.level().isClientSide) return client.apply(player);
        Scoped scoped = SCOPE.get();
        return scoped != null && scoped.player == player ? scoped.cursor : null;
    }

    static <T> T withPerspective(Player player, Cursor cursor, Supplier<T> action){
        Scoped prev = SCOPE.get();
        SCOPE.set(new Scoped(player, cursor));
        try{ return action.get(); }
        finally{ if(prev == null) SCOPE.remove(); else SCOPE.set(prev); }
    }

    // Extend custom handler reach only to the authenticated block, including its owning SubLevel
    public static boolean canUse(Player player, BlockPos pos){
        Cursor cursor = cursor(player);
        return cursor != null && cursor.target.reference().blockPos().equals(pos);
    }
    public static boolean canUse(Player player, BlockEntity entity){
        Cursor cursor = cursor(player);
        return cursor != null && cursor.target.reference().equals(ViewReference.of(entity));
    }
    public static Vec3 eye(Player player){
        Cursor cursor = cursor(player);
        return cursor == null ? player.getEyePosition() : cursor.pose.position();
    }
    public static Vec3 direction(Player player){
        Cursor cursor = cursor(player);
        return cursor == null ? player.getViewVector(1) : cursor.pose.forward();
    }
    public static double range(Player player){
        Cursor cursor = cursor(player);
        return cursor == null ? player.blockInteractionRange() : cursor.range;
    }
    public static @Nullable ViewInteractionTargets.Target pick(Player player){
        Cursor cursor = cursor(player);
        if(cursor == null || !(cursor.source.resolve(player.level()) instanceof RaycastViewSource source)) return null;
        var target = ViewInteractionTargets.pick(player.level(), source, cursor.source, player.level().isClientSide ? 1 : 0, player.getUUID());
        if(target != null && player instanceof ServerPlayer server
                && !ViewBlockInteractions.targetAccess(server, target.reference().subLevelId(), target.reference().blockPos())) return null;
        return target;
    }

    private static @Nullable Session session(ServerPlayer player){
        Session session = SESSIONS.get(player.getUUID());
        if(session == null) return null;
        var target = session.cursor.target.reference();
        boolean valid = player.isAlive() && !player.hasDisconnected() && player.level().getGameTime() <= session.expires
                && player.position().distanceToSqr(session.origin) <= 64 && ViewBlockInteractions.sourceAccess(player, session.cursor.source)
                && session.cursor.source.resolve(player.level()) == session.lens
                && player.level().isLoaded(target.blockPos()) && player.level().getBlockState(target.blockPos()).is((net.minecraft.world.level.block.Block) session.block)
                && (session.entity == null || player.level().getBlockEntity(target.blockPos()) == session.entity)
                && ViewBlockInteractions.targetAccess(player, target.subLevelId(), target.blockPos())
                && ViewControlSessions.available(session.cursor.source, player.getUUID())
                && ((RaycastViewSource)session.lens).viewPose(0).position().distanceToSqr(
                        com.rieno.gadgetsandgizmos.lib.physics.SableTransformApi.toWorldPosition(
                                com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi.subLevel(player.level(), target.subLevelId()), target.blockPos().getCenter()))
                <= Math.pow(((RaycastViewSource)session.lens).interactionRange() + 2, 2)
                && (!RemoteViewSessions.isViewing((ViewSource) session.lens) || session.cursor.source.equals(RemoteViewSessions.source(player)));
        if(valid) return session;
        SESSIONS.remove(player.getUUID());
        return null;
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event){
        for(Session session : java.util.List.copyOf(SESSIONS.values())) session(session.player);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){ SESSIONS.remove(event.getEntity().getUUID()); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){ SESSIONS.clear(); }
}
