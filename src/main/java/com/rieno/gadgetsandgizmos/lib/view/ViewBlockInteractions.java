package com.rieno.gadgetsandgizmos.lib.view;

import com.rieno.gadgetsandgizmos.lib.access.WorldAccessPolicy;
import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import com.rieno.gadgetsandgizmos.lib.physics.SableTransformApi;
import com.rieno.gadgetsandgizmos.lib.scm.ShipPermission;
import com.rieno.gadgetsandgizmos.lib.scm.ShipPermissionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// Use an authenticated lens hit through the player's normal block-use events and held item
@EventBusSubscriber(modid = "gadgetsngizmos")
public final class ViewBlockInteractions{
    private static final Map<UseKey, Long> LAST_USE = new HashMap<>();
    private static final Map<UUID, MenuAccess> MENUS = new HashMap<>();
    private static final ThreadLocal<Boolean> VERIFYING_MENU = ThreadLocal.withInitial(() -> false);

    private ViewBlockInteractions(){}

    // Recast on the server so callers never supply a fabricated hit or a stale block face
    public static boolean interact(ServerPlayer player, ViewReference ref){
        if(player == null || ref == null || !player.isAlive() || player.hasDisconnected()) return false;
        var source = ref.resolve(player.level());
        if(!(source instanceof RaycastViewSource camera) || !source.isViewAvailable() || !sourceAccess(player, ref)) return false;
        if(!ViewControlSessions.available(ref, player.getUUID())
                || RemoteViewSessions.isViewing(source) && !ref.equals(RemoteViewSessions.source(player))) return false;
        long tick = player.level().getGameTime();
        var key = new UseKey(player.getUUID(), ref);
        if(LAST_USE.getOrDefault(key, Long.MIN_VALUE) == tick) return false;
        LAST_USE.put(key, tick);
        var target = ViewInteractionTargets.pick(player.level(), camera, ref, 0, player.getUUID());
        if(target == null) return false;
        var level = player.serverLevel();
        var body = SableLevelApi.subLevel(level, target.reference().subLevelId());
        if(target.reference().subLevelId() != null && (body == null || body.isRemoved())) return false;
        BlockPos pos = target.reference().blockPos();
        Direction face = target.face();
        if(!level.isLoaded(pos) || !targetAccess(player, target.reference().subLevelId(), pos)) return false;
        Vec3 point = SableTransformApi.toLocalPosition(body, target.position());
        Vec3 offset = point.subtract(pos.getCenter());
        if(!Double.isFinite(offset.lengthSqr())) return false;
        var hit = new BlockHitResult(point, face, pos, false);
        var hand = InteractionHand.MAIN_HAND;
        var stack = player.getItemInHand(hand);
        if(!stack.isItemEnabled(level.enabledFeatures())) return false;
        AbstractContainerMenu before = player.containerMenu;
        player.resetLastActionTime();
        var cursor = new ViewInteractionContext.Cursor(ref, target, camera.viewPose(0), camera.interactionRange());
        ViewInteractionContext.begin(player, cursor);
        var result = target.useBlock() ? ViewInteractionContext.withPerspective(player, cursor,
                () -> player.gameMode.useItemOn(player, level, stack, hand, hit)) : net.minecraft.world.InteractionResult.SUCCESS;
        if(player.containerMenu != before && player.containerMenu != player.inventoryMenu){
            MENUS.put(player.getUUID(), new MenuAccess(player, player.containerMenu, ref, source, pos,
                    target.reference().subLevelId(), level.getBlockState(pos).getBlock(), level.getBlockEntity(pos), player.position()));
        }
        if(result == net.minecraft.world.InteractionResult.FAIL){ ViewInteractionContext.end(player); return false; }
        var tag = target.reference().toTag();
        tag.putDouble("HitX", target.position().x); tag.putDouble("HitY", target.position().y); tag.putDouble("HitZ", target.position().z);
        tag.putString("Face", face.getSerializedName()); tag.putDouble("Distance", target.distance());
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new ViewInteractionPayload(ref.toTag(), tag, target.useBlock()));
        if(result.shouldSwing()) player.swing(hand, true);
        return result.consumesAction();
    }

    // Extend only the target menu's reach; its block identity and other validity checks still run
    public static boolean menuReach(ServerPlayer player, BlockPos pos){
        if(VERIFYING_MENU.get()) return false;
        VERIFYING_MENU.set(true);
        try{ return authorizedMenuReach(player, pos); }
        finally{ VERIFYING_MENU.remove(); }
    }
    private static boolean authorizedMenuReach(ServerPlayer player, BlockPos pos){
        MenuAccess access = MENUS.get(player.getUUID());
        if(access == null || !valid(access)){ MENUS.remove(player.getUUID()); return false; }
        if(!pos.equals(access.pos)){
            var state = player.level().getBlockState(access.pos);
            if(!(access.block instanceof net.minecraft.world.level.block.ChestBlock)
                    || state.getValue(net.minecraft.world.level.block.ChestBlock.TYPE)
                    == net.minecraft.world.level.block.state.properties.ChestType.SINGLE
                    || !pos.equals(access.pos.relative(net.minecraft.world.level.block.ChestBlock.getConnectedDirection(state)))) return false;
        }
        return targetAccess(player, access.body, pos);
    }

    static boolean sourceAccess(ServerPlayer player, ViewReference ref){
        if(!player.level().dimension().location().equals(ref.dimension())) return false;
        return allowed(player, ref.subLevelId())
                && WorldAccessPolicy.canAccessLocal(player, player.serverLevel(), ref.subLevelId(), ref.blockPos());
    }
    static boolean targetAccess(ServerPlayer player, UUID body, BlockPos pos){
        var level = player.serverLevel();
        var space = SableLevelApi.subLevel(level, body);
        if(body != null && (space == null || space.isRemoved())) return false;
        BlockPos world = BlockPos.containing(SableTransformApi.toWorldPosition(space, pos.getCenter()));
        return level.getWorldBorder().isWithinBounds(world) && level.mayInteract(player, world)
                && allowed(player, body) && WorldAccessPolicy.canAccessLocal(player, level, body, pos);
    }
    private static boolean allowed(ServerPlayer player, UUID body){
        return body == null || ShipPermissionManager.get(player.server).allows(body, player.getUUID(), ShipPermission.INTERACT);
    }
    private static boolean valid(MenuAccess access){
        var player = access.player;
        var level = player.serverLevel();
        return player.isAlive() && !player.hasDisconnected() && player.containerMenu == access.menu
                && player.position().distanceToSqr(access.origin) <= 64 && sourceAccess(player, access.source)
                && access.source.resolve(level) == access.lens && level.isLoaded(access.pos)
                && level.getBlockState(access.pos).is(access.block)
                && (access.entity == null || level.getBlockEntity(access.pos) == access.entity)
                && targetAccess(player, access.body, access.pos);
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post evt){
        for(var entry : java.util.List.copyOf(MENUS.entrySet())){
            if(valid(entry.getValue())) continue;
            MENUS.remove(entry.getKey());
            var access = entry.getValue();
            if(access.player.containerMenu == access.menu) access.player.closeContainer();
        }
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent evt){
        LAST_USE.keySet().removeIf(key -> key.player.equals(evt.getEntity().getUUID())); MENUS.remove(evt.getEntity().getUUID());
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent evt){ LAST_USE.clear(); MENUS.clear(); }

    private record UseKey(UUID player, ViewReference source){}
    private record MenuAccess(ServerPlayer player, AbstractContainerMenu menu, ViewReference source, ViewSource lens,
                              BlockPos pos, UUID body, Block block, BlockEntity entity, Vec3 origin){}
}
