package com.rieno.gadgetsandgizmos.lib.control;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import java.util.Locale;
import java.util.function.ToDoubleFunction;

// Select one unsigned direction from a directional analogue snapshot
public enum DirectionalAnalogComponent {
    FORWARD("forward", DirectionalAnalogSnapshot::forward),
    BACKWARD("backward", DirectionalAnalogSnapshot::backward),
    LEFT("left", DirectionalAnalogSnapshot::left),
    RIGHT("right", DirectionalAnalogSnapshot::right);

    private final String id;
    private final ToDoubleFunction<DirectionalAnalogSnapshot> sampler;

    // Initialize the directional analogue component
    DirectionalAnalogComponent(String id, ToDoubleFunction<DirectionalAnalogSnapshot> sampler) {
        this.id = id;
        this.sampler = sampler;
    }

    // Get the serialized component id
    public String id() {
        return id;
    }

    // Sample the component
    public double sample(DirectionalAnalogSnapshot snapshot) {
        return sampler.applyAsDouble(snapshot == null ? DirectionalAnalogSnapshot.ZERO : snapshot);
    }

    // Resolve a serialized component id
    public static DirectionalAnalogComponent fromId(String id, DirectionalAnalogComponent fallback) {
        DirectionalAnalogComponent resolvedFallback = fallback == null ? FORWARD : fallback;
        if (id == null || id.isBlank()) {
            return resolvedFallback;
        }
        String normalized = id.strip().toLowerCase(Locale.ROOT);
        for (DirectionalAnalogComponent component : values()) {
            if (component.id.equals(normalized)) {
                return component;
            }
        }
        return resolvedFallback;
    }
}
