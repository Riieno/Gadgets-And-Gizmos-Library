package com.rieno.gadgetsandgizmos.lib.client.view;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.view.RemoteViewPayload;
import com.rieno.gadgetsandgizmos.lib.view.RemoteViewInputPayload;
import com.rieno.gadgetsandgizmos.lib.view.RemoteViewAimPayload;
import com.rieno.gadgetsandgizmos.lib.view.PredictedViewSource;
import com.rieno.gadgetsandgizmos.lib.view.ViewControlPrediction;
import com.rieno.gadgetsandgizmos.lib.client.render.SubLevelClientRenderApi;
import com.rieno.gadgetsandgizmos.lib.view.RemoteViewStatePayload;
import com.rieno.gadgetsandgizmos.lib.view.RemoteViewRaycastPayload;
import net.minecraft.Util;
import com.rieno.gadgetsandgizmos.lib.view.ViewPose;
import com.rieno.gadgetsandgizmos.lib.view.ViewReference;
import com.rieno.gadgetsandgizmos.lib.view.ViewSource;
import com.rieno.gadgetsandgizmos.lib.view.ViewTransforms;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import com.rieno.gadgetsandgizmos.lib.client.render.ClientViewOwnership;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

// Forward lens controls while preserving the local player's body orientation
@EventBusSubscriber(modid = "gadgetsngizmos", value = Dist.CLIENT)
public final class RemoteViewClient{
/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                            CONSTANTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    // Retain only the currently acknowledged source and input deltas
    private static @Nullable ViewReference source;
    private static @Nullable ViewControlPrediction prediction;
    private static double pan;
    private static double tilt;
    private static double zoom;
    private static float bodyYaw;
    private static float bodyPitch;
    private static CameraType prevCameraType;
    private static long startedAt;
    private static @Nullable RemoteViewRaycastPayload rayResult;
    private static long rayResultTime;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                            FUNCTIONS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    // Prevent construction of the client control facade
    private RemoteViewClient(){}
    // Install common-side callbacks during physical client setup
    public static void bootstrap(){
        ViewInteractionClient.bootstrap();
        RemoteViewStatePayload.registerClientHandler(RemoteViewClient::handle);
        RemoteViewAimPayload.registerClientHandler(msg -> {
            if(source == null || !source.equals(ViewReference.fromTag(msg.source()))) return;
            if(prediction == null) prediction = new ViewControlPrediction(msg.aim());
            prediction.acknowledge(msg.sequence(), msg.aim());
        });
        RemoteViewRaycastPayload.registerClientHandler(msg -> {
            if(source == null || !source.equals(ViewReference.fromTag(msg.source()))) return;
            rayResult = msg;
            rayResultTime = Util.getMillis();
        });
        ViewTransforms.registerClientPoses((body, tick) -> body instanceof ClientSubLevel client
                ? SubLevelClientRenderApi.interpolatedPose(client, tick) : body.logicalPose());
    }
    // Apply server acknowledgement and reset held player controls
    private static void handle(RemoteViewStatePayload msg){
        Minecraft mc = Minecraft.getInstance();
        if(!msg.active()){
            ViewInteractionClient.clear();
            clear();
            return;
        }
        clear();
        if(mc.player == null || mc.level == null) return;
        source = ViewReference.fromTag(msg.source());
        if(source == null) return;
        if(ClientViewOwnership.isReserved()){ exit(); return; }
        if(source.resolve(mc.level) instanceof PredictedViewSource predicted) prediction = new ViewControlPrediction(predicted.viewAim());
        startedAt = Util.getMillis();
        bodyYaw = mc.player.getYRot();
        bodyPitch = mc.player.getXRot();
        prevCameraType = mc.options.getCameraType();
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        mc.setScreen(null);
        mc.mouseHandler.grabMouse();
        KeyMapping.releaseAll();
    }
    // Check whether raw input belongs to an active camera session
    public static boolean isActive(){ return source != null && !ClientViewOwnership.isReserved(); }
    public static @Nullable ViewReference reference(){ return isActive() ? source : null; }
    // Resolve HUD capabilities and feedback against the currently acknowledged session
    public static @Nullable ViewSource source(){
        return source == null ? null : source.resolve(Minecraft.getInstance().level);
    }
    public static long startedAt(){ return startedAt; }
    public static @Nullable RemoteViewRaycastPayload rayResult(){ return rayResult; }
    public static long rayResultTime(){ return rayResultTime; }
    // Resolve the current lens without retaining unloaded block entities
    public static @Nullable ViewPose pose(float partialTick){
        if(!isActive()) return null;
        ViewSource src = source.resolve(Minecraft.getInstance().level);
        if(src instanceof PredictedViewSource predicted && prediction != null){
            return predicted.viewPose(partialTick, prediction.preview(pan, tilt, zoom));
        }
        return src == null ? null : src.viewPose(partialTick);
    }
    // Accumulate mouse motion using the game's sensitivity convention
    public static void turn(double dx, double dy){
        Minecraft mc = Minecraft.getInstance();
        double val = mc.options.sensitivity().get() * 0.6D + 0.2D;
        double scale = val * val * val * 8.0D * 0.15D;
        pan -= dx * scale;
        tilt -= dy * scale * (mc.options.invertYMouse().get() ? -1 : 1);
    }
    // Receive mouse deltas after vanilla sensitivity and inversion, before body rotation
    public static void turnScaled(double yaw, double pitch){ pan -= yaw * 0.15; tilt -= pitch * 0.15; }
    // Accumulate scroll steps without changing the hotbar
    public static void zoom(double steps){ zoom += steps; }
    // Put the visible aim ahead of a raycast or interaction on the server connection
    public static void flushInput(){
        if(!isActive() || prediction == null || pan == 0 && tilt == 0 && zoom == 0) return;
        sendInput();
    }
    private static void sendInput(){
        if(prediction != null){
            var sample = prediction.sample(pan, tilt, zoom);
            PacketDistributor.sendToServer(new RemoteViewInputPayload(sample.sequence(), sample.pan(), sample.tilt(), sample.zoom()));
        }else PacketDistributor.sendToServer(new RemoteViewPayload(pan, tilt, zoom, false));
        pan = tilt = zoom = 0;
    }
    // Exit immediately and let the server release the body lock
    public static void exit(){
        if(source == null) return;
        flushInput();
        ViewInteractionClient.clear();
        PacketDistributor.sendToServer(new RemoteViewPayload(0, 0, 0, true));
        clear();
    }
    // Restore camera preferences and discard retained input
    private static void clear(){
        Minecraft mc = Minecraft.getInstance();
        source = null;
        prediction = null;
        rayResult = null;
        rayResultTime = startedAt = 0;
        pan = tilt = zoom = 0;
        restoreCameraType(mc);
        KeyMapping.releaseAll();
        mc.mouseHandler.setIgnoreFirstMove();
    }
    // Restore the body preference after the external editor releases its camera
    private static void restoreCameraType(Minecraft mc){
        if(prevCameraType == null || ClientViewOwnership.isReserved()) return;
        mc.options.setCameraType(prevCameraType);
        prevCameraType = null;
    }
    // Send one sample and keep held movement keys released
    @SubscribeEvent
    public static void tick(ClientTickEvent.Pre evt){
        Minecraft mc = Minecraft.getInstance();
        if(source == null){ restoreCameraType(mc); return; }
        if(mc.level == null || mc.player == null){ clear(); return; }
        if(ClientViewOwnership.isReserved()){ exit(); return; }
        if(source.resolve(mc.level) == null || mc.screen != null
                && !(mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>)
                && !ViewInteractionClient.hasScreenContext()){ exit(); return; }
        if(mc.screen != null) pan = tilt = zoom = 0;
        KeyMapping.releaseAll();
        mc.player.setYRot(bodyYaw);
        mc.player.setXRot(bodyPitch);
        mc.player.setDeltaMovement(Vec3.ZERO);
        sendInput();
    }
}
