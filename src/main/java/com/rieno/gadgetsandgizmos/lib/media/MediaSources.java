package com.rieno.gadgetsandgizmos.lib.media;

import net.minecraft.world.phys.Vec3;

import java.util.UUID;

// Let common hosts publish sound sources without referencing client classes
public final class MediaSources{
    private static Observer observer = (id, state, pos, relative) -> {};
    private MediaSources(){}

    public static void setObserver(Observer val){ observer = java.util.Objects.requireNonNull(val); }

    public static void observe(UUID id, MediaPlaybackState state, Vec3 pos, boolean relative){
        if(id != null && pos != null && Double.isFinite(pos.lengthSqr())) observer.observe(id, state, pos, relative);
    }

    @FunctionalInterface
    public interface Observer{
        void observe(UUID id, MediaPlaybackState state, Vec3 pos, boolean relative);
    }
}
