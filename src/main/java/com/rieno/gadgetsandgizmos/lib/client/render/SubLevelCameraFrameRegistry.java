package com.rieno.gadgetsandgizmos.lib.client.render;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

// Supply a Sable camera frame for interactions which do not mount the camera entity
public final class SubLevelCameraFrameRegistry{
    private static final Map<ResourceLocation, Provider> PROVIDERS = new LinkedHashMap<>();
    private static volatile List<Provider> snapshot = List.of();

    private SubLevelCameraFrameRegistry(){}

    // Register a provider under the consuming mod's namespace
    public static synchronized void register(ResourceLocation id, Provider provider){
        Objects.requireNonNull(id);
        Objects.requireNonNull(provider);
        if(PROVIDERS.putIfAbsent(id, provider) != null) throw new IllegalArgumentException("Duplicate camera frame " + id);
        snapshot = List.copyOf(PROVIDERS.values());
    }

    // Release a provider without retaining a camera or world reference
    public static synchronized void unregister(ResourceLocation id){
        PROVIDERS.remove(id);
        snapshot = List.copyOf(PROVIDERS.values());
    }

    // Resolve a finite frame while leaving the provider's retained quaternion untouched
    public static Quaterniond orientation(Entity camera, float partialTick){
        if(camera == null || ClientViewOwnership.isReserved()) return null;
        for(Provider provider : snapshot){
            Quaterniondc val = provider.orientation(camera, partialTick);
            if(val == null) continue;
            Quaterniond res = new Quaterniond(val);
            if(Double.isFinite(res.x) && Double.isFinite(res.y) && Double.isFinite(res.z)
                    && Double.isFinite(res.w) && Double.isFinite(res.lengthSquared()) && res.lengthSquared() > 1.0E-18D){
                return res.normalize();
            }
        }
        return null;
    }

    // Return the world rotation of the camera's local body frame, or null outside the interaction
    @FunctionalInterface
    public interface Provider{
        Quaterniondc orientation(Entity camera, float partialTick);
    }
}
