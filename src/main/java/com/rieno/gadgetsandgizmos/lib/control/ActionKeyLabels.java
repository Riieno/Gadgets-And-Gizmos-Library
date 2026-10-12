package com.rieno.gadgetsandgizmos.lib.control;

import net.minecraft.network.chat.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

// Resolve client-provided key labels without loading client classes in common code
public final class ActionKeyLabels {
    private static final Map<String, Supplier<Component>> LABELS = new ConcurrentHashMap<>();

    private ActionKeyLabels() {}

    public static void register(String action, Supplier<Component> label) {
        if (LABELS.putIfAbsent(action, label) != null) {
            throw new IllegalArgumentException("Duplicate action key label: " + action);
        }
    }

    public static Component label(String action) {
        Supplier<Component> label = LABELS.get(action);
        return label == null ? Component.translatable("key.keyboard.unknown") : label.get();
    }

    public static Component hint(String translationKey, String... actions) {
        Object[] labels = new Object[actions.length];
        for (int i = 0; i < actions.length; i++) labels[i] = label(actions[i]);
        return Component.translatable(translationKey, labels);
    }
}
