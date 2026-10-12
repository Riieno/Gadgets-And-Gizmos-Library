package com.rieno.gadgetsandgizmos.lib.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import com.rieno.gadgetsandgizmos.lib.control.ActionInputState;
import com.rieno.gadgetsandgizmos.lib.control.ActionKeyLabels;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

// Expose configurable action keys without relying on the player's movement state
public final class ClientActionKey {
    private final KeyMapping mapping;
    private int previous;

    public ClientActionKey(String name, String category, InputConstants.Type type, int defaultKey) {
        mapping = new KeyMapping(name, KeyConflictContext.IN_GAME, type, defaultKey, category);
        ActionKeyLabels.register(name, mapping::getTranslatedKeyMessage);
    }

    public KeyMapping mapping() {
        return mapping;
    }

    public void register(Consumer<KeyMapping> register) {
        register.accept(mapping);
    }

    // Read the configured physical key, including mouse buttons and NeoForge modifiers
    public boolean isHeld() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null
                || minecraft.isPaused() || !minecraft.isWindowActive() || mapping.isUnbound()
                || !mapping.isConflictContextAndModifierActive()) return false;

        InputConstants.Key key = mapping.getKey();
        long window = minecraft.getWindow().getWindow();
        if (key.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
        }
        if (key.getType() == InputConstants.Type.KEYSYM) {
            return InputConstants.isKeyDown(window, key.getValue());
        }
        return mapping.isDown();
    }

    // Sample once per client tick and release actions when their context becomes unavailable
    public ActionInputState sample(boolean active) {
        int current = active && isHeld() ? 1 : 0;
        ActionInputState state = ActionInputState.sample(previous, current);
        previous = current;
        return state;
    }

    // Resolve the current assignment each time a tooltip or GUI renders
    public Component label() {
        return label(mapping);
    }

    public boolean matchesKeyboard(int key, int scanCode) {
        return !mapping.isUnbound() && mapping.isConflictContextAndModifierActive()
                && mapping.matches(key, scanCode);
    }

    public boolean matchesMouse(int button) {
        return !mapping.isUnbound() && mapping.isConflictContextAndModifierActive()
                && mapping.matchesMouse(button);
    }

    public static Component label(KeyMapping mapping) {
        return mapping.getTranslatedKeyMessage();
    }

    public Component hint(String translationKey) {
        return Component.translatable(translationKey, label());
    }
}
