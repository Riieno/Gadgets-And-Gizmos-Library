package com.rieno.gadgetsandgizmos.lib.client.view;

import com.mojang.blaze3d.platform.InputConstants;
import com.rieno.gadgetsandgizmos.lib.client.input.ClientActionKey;
import com.rieno.gadgetsandgizmos.lib.config.ViewfinderConfig;
import com.rieno.gadgetsandgizmos.lib.view.RemoteViewActionPayload;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.List;

// Match configured physical inputs even while ordinary player key mappings are released
@EventBusSubscriber(modid = "gadgetsngizmos", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class RemoteViewKeys{
    private static final String CATEGORY = "key.categories.gadgetsngizmos.camera";
    public static final ClientActionKey EXIT = key("exit", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_CONTROL);
    public static final ClientActionKey FLASHLIGHT = key("flashlight", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN);
    public static final ClientActionKey RAYCAST = key("raycast", InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_LEFT);
    public static final ClientActionKey INTERACT = key("interact", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN);
    public static final ClientActionKey FRAME = key("frame", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN);
    public static final ClientActionKey GRID = key("grid", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN);
    private static final List<ClientActionKey> KEYS = List.of(EXIT, FLASHLIGHT, RAYCAST, INTERACT, FRAME, GRID);
    private static int heldKeyboard = GLFW.GLFW_KEY_UNKNOWN;
    private static int heldMouse = -1;

    private RemoteViewKeys(){}
    private static ClientActionKey key(String name, InputConstants.Type type, int code){
        return new ClientActionKey("key.gadgetsngizmos.camera." + name, CATEGORY, type, code);
    }
    @SubscribeEvent public static void register(RegisterKeyMappingsEvent evt){ KEYS.forEach(key -> key.register(evt::register)); }

    public static void keyboard(int key, int scanCode){
        keyboard(key, scanCode, GLFW.GLFW_PRESS);
    }
    public static boolean keyboard(int key, int scanCode, int state){
        if(state == GLFW.GLFW_RELEASE && key == heldKeyboard){
            heldKeyboard = GLFW.GLFW_KEY_UNKNOWN; ViewInteractionClient.release(); return true;
        }
        if(key == GLFW.GLFW_KEY_ESCAPE){ if(state == GLFW.GLFW_PRESS) RemoteViewClient.exit(); return true; }
        for(ClientActionKey action : KEYS) if(action.matchesKeyboard(key, scanCode)){
            if(action == INTERACT && state == GLFW.GLFW_PRESS) heldKeyboard = key;
            activate(action, state); return true;
        }
        return false;
    }
    public static void mouse(int button){
        mouse(button, GLFW.GLFW_PRESS);
    }
    public static boolean mouse(int button, int state){
        if(state == GLFW.GLFW_RELEASE && button == heldMouse){ heldMouse = -1; ViewInteractionClient.release(); return true; }
        for(ClientActionKey action : KEYS) if(action.matchesMouse(button)){
            if(action == INTERACT && state == GLFW.GLFW_PRESS) heldMouse = button;
            activate(action, state); return true;
        }
        return false;
    }
    private static void activate(ClientActionKey key, int state){
        if(!RemoteViewClient.isActive()) return;
        if(key == INTERACT){
            if(state == GLFW.GLFW_PRESS) ViewInteractionClient.press();
            else if(state == GLFW.GLFW_RELEASE) ViewInteractionClient.release();
            return;
        }
        if(state != GLFW.GLFW_PRESS) return;
        if(key == EXIT) RemoteViewClient.exit();
        else if(key == FRAME) ViewfinderConfig.toggleFrame(RemoteViewClient.reference());
        else if(key == GRID) ViewfinderConfig.toggleGrid(RemoteViewClient.reference());
        else{
            RemoteViewClient.flushInput();
            PacketDistributor.sendToServer(new RemoteViewActionPayload(key == FLASHLIGHT
                ? RemoteViewActionPayload.Action.FLASHLIGHT : key == INTERACT
                ? RemoteViewActionPayload.Action.INTERACT : RemoteViewActionPayload.Action.RAYCAST));
        }
    }
    public static Component label(ClientActionKey key){
        return key.mapping().isUnbound() ? Component.translatable("gadgetsngizmos.camera.unbound") : key.label();
    }
    public static Component exitLabel(){
        Component escape = InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_ESCAPE).getDisplayName();
        return EXIT.mapping().isUnbound() || EXIT.mapping().matches(GLFW.GLFW_KEY_ESCAPE, 0) ? escape
                : escape.copy().append(" / ").append(EXIT.label());
    }
}
