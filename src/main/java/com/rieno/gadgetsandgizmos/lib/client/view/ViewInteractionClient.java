package com.rieno.gadgetsandgizmos.lib.client.view;

import com.rieno.gadgetsandgizmos.lib.view.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

// Deliver authenticated camera use through native client click, hold and release mechanics
@EventBusSubscriber(modid = "gadgetsngizmos", value = Dist.CLIENT)
public final class ViewInteractionClient{
    private static final List<InputHandler> HANDLERS = new ArrayList<>();
    private static ViewInteractionContext.Cursor cursor;
    private static boolean replaying;
    private static boolean requested;
    private static boolean pendingKey;
    private static boolean held;
    private static boolean latched;
    private static boolean ordinaryUse;
    private static int pulseTicks;
    private static long lastKeepAlive;
    private ViewInteractionClient(){}

    // Adapt a mod's raw-input handler without exposing its implementation through the library API
    public interface InputHandler{
        default boolean use(boolean down){ return false; }
        default boolean captured(){ return false; }
        default boolean scroll(double horizontal, double vertical){ return false; }
        default boolean key(int key, int scanCode, int action, int modifiers){ return false; }
        default boolean mouse(int button, int action, int modifiers){ return false; }
        default void release(){}
    }
    public static void register(InputHandler handler){ HANDLERS.add(java.util.Objects.requireNonNull(handler)); }
    public static void bootstrap(){
        ViewInteractionPayload.registerClientHandler(ViewInteractionClient::replay);
        ViewInteractionContext.registerClient(ViewInteractionClient::perspective);
    }
    public static boolean replaying(){ return replaying; }
    public static boolean held(){ return held; }
    public static boolean hasScreenContext(){ return cursor != null && cursor.source().equals(RemoteViewClient.reference()); }
    public static boolean scrolling(double horizontal, double vertical){
        if(ClientHooks.onMouseScroll(Minecraft.getInstance().mouseHandler, horizontal, vertical)) return true;
        for(var handler : HANDLERS) if(handler.scroll(horizontal, vertical)) return true;
        return false;
    }
    public static boolean key(int key, int scanCode, int action, int modifiers){
        var mc = Minecraft.getInstance();
        if(mc.options.keyUse.matches(key, scanCode) && ordinaryUse(action)) return true;
        ClientHooks.onKeyInput(key, scanCode, action, modifiers);
        for(var handler : HANDLERS) if(handler.key(key, scanCode, action, modifiers)) return true;
        return false;
    }
    public static boolean mouse(int button, int action, int modifiers){
        var mc = Minecraft.getInstance();
        if(latched && action == 1 && mc.options.keyUse.matchesMouse(button)){ release(); return true; }
        if(mc.options.keyUse.matchesMouse(button) && ordinaryUse(action)) return true;
        if(ClientHooks.onMouseButtonPre(button, action, modifiers)) return true;
        ClientHooks.onMouseButtonPost(button, action, modifiers);
        for(var handler : HANDLERS) if(handler.mouse(button, action, modifiers)) return true;
        return false;
    }
    private static boolean ordinaryUse(int action){
        if(action == 0 && ordinaryUse){ release(); return true; }
        if(HANDLERS.stream().anyMatch(InputHandler::captured)) return false;
        if(action == 1){ press(); ordinaryUse = true; }
        return true;
    }

    // Keep the configurable interaction key separate from the player's ordinary use binding
    public static void press(){
        if(latched){ release(); return; }
        requested = true;
        pendingKey = true;
        RemoteViewClient.flushInput();
        PacketDistributor.sendToServer(new RemoteViewActionPayload(RemoteViewActionPayload.Action.INTERACT));
    }
    public static void release(){
        boolean wasHeld = held;
        requested = held = latched = ordinaryUse = false;
        pulseTicks = 0;
        if(wasHeld) for(var handler : HANDLERS){ handler.use(false); handler.release(); }
        if(wasHeld) Minecraft.getInstance().options.keyUse.setDown(false);
    }
    public static void clear(){ release(); cursor = null; pendingKey = false; }

    // Return camera geometry only for the local player; nested feed captures keep their own context
    private static @Nullable ViewInteractionContext.Cursor perspective(Player player){
        var mc = Minecraft.getInstance();
        if(player != mc.player || mc.level == null || ViewSceneRenderer.isCapturing()) return null;
        if(replaying) return cursor;
        var ref = RemoteViewClient.reference();
        if(ref == null || !(ref.resolve(mc.level) instanceof RaycastViewSource source)) return null;
        var pose = RemoteViewClient.pose(1);
        if(pose == null) return null;
        var target = cursor != null && ref.equals(cursor.source()) ? cursor.target() : null;
        if(target == null){
            target = new ViewInteractionTargets.Target(ref, pose.position(), Direction.UP, 0, false);
        }
        return new ViewInteractionContext.Cursor(ref, target, pose, source.interactionRange());
    }

    // Preserve the native hit coordinates, including local coordinates on moving SubLevels
    private static void replay(ViewInteractionPayload msg){
        var mc = Minecraft.getInstance();
        if(mc.player == null || mc.level == null || mc.gameMode == null) return;
        ViewReference sourceRef = ViewReference.fromTag(msg.source());
        var tag = msg.target();
        ViewReference targetRef = ViewReference.fromTag(tag);
        Direction face = Direction.byName(tag.getString("Face"));
        if(sourceRef == null || targetRef == null || face == null || !(sourceRef.resolve(mc.level) instanceof RaycastViewSource source)) return;
        if(RemoteViewClient.isActive() && !sourceRef.equals(RemoteViewClient.reference())) return;
        var target = new ViewInteractionTargets.Target(targetRef, new Vec3(tag.getDouble("HitX"), tag.getDouble("HitY"), tag.getDouble("HitZ")),
                face, tag.getDouble("Distance"), msg.useBlock());
        cursor = new ViewInteractionContext.Cursor(sourceRef, target, source.viewPose(1), source.interactionRange());
        var prevScreen = mc.screen;
        var prevHit = mc.hitResult;
        held = true;
        boolean releasedEarly = pendingKey && !requested;
        pendingKey = false;
        pulseTicks = requested ? 0 : 2;
        replaying = true;
        mc.screen = null;
        mc.hitResult = target.localHit(mc.level);
        try{
            boolean consumed = false;
            for(var handler : HANDLERS) consumed |= handler.use(true);
            var event = ClientHooks.onClickInput(1, mc.options.keyUse, InteractionHand.MAIN_HAND);
            if(!consumed && !event.isCanceled() && msg.useBlock()) mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, (BlockHitResult) mc.hitResult);
            boolean captured = HANDLERS.stream().anyMatch(InputHandler::captured);
            if(captured && !requested && !releasedEarly){ latched = true; pulseTicks = 0; }
            if(releasedEarly) release();
            if(captured && !RemoteViewClient.isActive()){
                PacketDistributor.sendToServer(new ViewInteractionTakeoverPayload());
                if(mc.screen == null) mc.mouseHandler.grabMouse();
            }else if(mc.screen == null) mc.screen = prevScreen;
        }finally{
            replaying = false;
            if(!RemoteViewClient.isActive()) mc.hitResult = prevHit;
        }
    }

    // Native projections and grip handlers must see the lens hit every frame
    public static void updateHit(float partialTick){
        var mc = Minecraft.getInstance();
        var ref = RemoteViewClient.reference();
        if(ref == null || mc.level == null || mc.player == null || !(ref.resolve(mc.level) instanceof RaycastViewSource source)) return;
        var pose = RemoteViewClient.pose(partialTick);
        if(pose == null) return;
        var hit = ViewInteractionTargets.pick(mc.level, source, ref, pose, mc.player.getUUID());
        if(hit != null && hit.useBlock()) mc.hitResult = hit.localHit(mc.level);
        else{
            var ray = ViewRaycast.trace(mc.level, pose, source.interactionRange(), source.interactionFilter(), ref, 0, 1, mc.player.getUUID());
            mc.hitResult = BlockHitResult.miss(ray.position(), Direction.getNearest(pose.forward().x, pose.forward().y, pose.forward().z), net.minecraft.core.BlockPos.containing(ray.position()));
        }
        mc.crosshairPickEntity = null;
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        var mc = Minecraft.getInstance();
        if(mc.level == null || mc.player == null){ if(cursor != null || held) clear(); return; }
        if(cursor != null && cursor.source().resolve(mc.level) == null){ clear(); return; }
        if(pulseTicks > 0 && --pulseTicks == 0) release();
        if(held && (mc.screen != null || !mc.isWindowActive())) release();
        if(cursor != null && (RemoteViewClient.isActive() || mc.screen != null || held)
                && mc.level.getGameTime() - lastKeepAlive >= 10){
            lastKeepAlive = mc.level.getGameTime();
            PacketDistributor.sendToServer(new ViewInteractionKeepAlivePayload());
        }
    }
}
