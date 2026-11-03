package com.rieno.gadgetsandgizmos.lib.tablet;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

// Supply optional detailed listings without changing existing app definitions
public final class TabletAppDescriptions{
    private static final Map<ResourceLocation, Component> DETAILS = new ConcurrentHashMap<>();

    private TabletAppDescriptions(){}

    // Register listing text under the owning application's identifier
    public static void register(ResourceLocation appId, Component details){
        Objects.requireNonNull(appId, "appId");
        Objects.requireNonNull(details, "details");
        if(DETAILS.putIfAbsent(appId, details.copy()) != null)
            throw new IllegalStateException("Application details already registered: " + appId);
    }

    // Return an independent component and retain the existing description as a fallback
    public static Component details(TabletAppDefinition app){
        Objects.requireNonNull(app, "app");
        Component details = DETAILS.get(app.id());
        return details == null ? app.description() : details.copy();
    }
}
