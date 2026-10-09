package com.rieno.gadgetsandgizmos.lib.client.view;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.view.RemoteViewPayload;
import com.rieno.gadgetsandgizmos.lib.view.RemoteViewStatePayload;
import com.rieno.gadgetsandgizmos.lib.view.ViewPose;
import com.rieno.gadgetsandgizmos.lib.view.ViewReference;
import com.rieno.gadgetsandgizmos.lib.view.ViewSource;
import com.rieno.gadgetsandgizmos.lib.view.ViewTransforms;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
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
    private static double pan;
    private static double tilt;
    private static double zoom;
    private static float bodyYaw;
    private static float bodyPitch;
    private static CameraType prevCameraType;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                            FUNCTIONS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    // Prevent construction of the client control facade
    private RemoteViewClient(){}
    // Install common-side callbacks during physical client setup
    public static void bootstrap(){
        RemoteViewStatePayload.registerClientHandler(RemoteViewClient::handle);
        ViewTransforms.registerClientPoses((body, tick) -> body instanceof ClientSubLevel client
                ? client.renderPose(tick) : body.logicalPose());
    }
    // Apply server acknowledgement and reset held player controls
    private static void handle(RemoteViewStatePayload msg){
        Minecraft mc = Minecraft.getInstance();
        if(!msg.active()){
            clear();
            return;
        }
        clear();
        if(mc.player == null || mc.level == null) return;
        source = ViewReference.fromTag(msg.source());
        if(source == null) return;
        bodyYaw = mc.player.getYRot();
        bodyPitch = mc.player.getXRot();
        prevCameraType = mc.options.getCameraType();
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        mc.setScreen(null);
        mc.mouseHandler.grabMouse();
        KeyMapping.releaseAll();
    }
    // Check whether raw input belongs to an active camera session
    public static boolean isActive(){ return source != null; }
    // Resolve the current lens without retaining unloaded block entities
    public static @Nullable ViewPose pose(float partialTick){
        if(source == null) return null;
        ViewSource src = source.resolve(Minecraft.getInstance().level);
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
    // Accumulate scroll steps without changing the hotbar
    public static void zoom(double steps){ zoom += steps; }
    // Exit immediately and let the server release the body lock
    public static void exit(){
        if(source == null) return;
        PacketDistributor.sendToServer(new RemoteViewPayload(0, 0, 0, true));
        clear();
    }
    // Restore camera preferences and discard retained input
    private static void clear(){
        Minecraft mc = Minecraft.getInstance();
        source = null;
        pan = tilt = zoom = 0;
        if(prevCameraType != null) mc.options.setCameraType(prevCameraType);
        prevCameraType = null;
        KeyMapping.releaseAll();
        mc.mouseHandler.setIgnoreFirstMove();
    }
    // Send one sample and keep held movement keys released
    @SubscribeEvent
    public static void tick(ClientTickEvent.Pre evt){
        Minecraft mc = Minecraft.getInstance();
        if(source == null) return;
        if(mc.level == null || mc.player == null){ clear(); return; }
        if(mc.screen != null || source.resolve(mc.level) == null){ exit(); return; }
        KeyMapping.releaseAll();
        mc.player.setYRot(bodyYaw);
        mc.player.setXRot(bodyPitch);
        mc.player.setDeltaMovement(Vec3.ZERO);
        PacketDistributor.sendToServer(new RemoteViewPayload(pan, tilt, zoom, false));
        pan = tilt = zoom = 0;
    }
}
