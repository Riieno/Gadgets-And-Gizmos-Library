package com.rieno.gadgetsandgizmos.lib.physics;

import net.minecraft.world.phys.Vec3;

// Forward optional colored point lights to a client renderer
public final class ColoredLightBridge {
    private static final Light INACTIVE = new Light() {
        @Override
        public void update(Vec3 position, int color, float radius, float brightness) {
        }

        @Override
        public void close() {
        }
    };

    private static Factory factory;

    private ColoredLightBridge() {
    }

    // Install a client renderer without loading it on the server
    public static void install(Factory next) {
        factory = next;
    }

    // Create one caller-owned light
    public static Light create() {
        return factory == null ? INACTIVE : factory.create();
    }

    @FunctionalInterface
    public interface Factory {
        Light create();
    }

    public interface Light extends AutoCloseable {
        void update(Vec3 position, int color, float radius, float brightness);

        @Override
        void close();
    }
}
