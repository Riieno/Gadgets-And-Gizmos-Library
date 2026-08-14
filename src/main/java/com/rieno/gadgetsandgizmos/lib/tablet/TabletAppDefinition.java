package com.rieno.gadgetsandgizmos.lib.tablet;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;
import java.util.Set;

// Define one tablet app's presentation, tabs and shared data keys
public record TabletAppDefinition(
        ResourceLocation id,
        Component title,
        Component description,
        int accentColor,
        List<TabletTabDefinition> tabs,
        ResourceLocation icon,
        Set<String> sharedDataKeys
) {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the tablet app definition
    public TabletAppDefinition {
        Objects.requireNonNull(id, "id");
        title = title == null ? Component.literal(id.getPath()) : title.copy();
        description = description == null ? Component.empty() : description.copy();
        tabs = tabs == null ? List.of() : List.copyOf(tabs);
        icon = icon == null ? defaultIcon(id) : icon;
        sharedDataKeys = sharedDataKeys == null ? Set.of() : sharedDataKeys.stream()
                .filter(key -> key != null && !key.isBlank()).collect(
                        java.util.stream.Collectors.toUnmodifiableSet());
    }

    // Initialize the tablet app definition
    public TabletAppDefinition(ResourceLocation id, Component title, Component description,
                               int accentColor, List<TabletTabDefinition> tabs) {
        this(id, title, description, accentColor, tabs, null, Set.of());
    }

    // Initialize the tablet app definition
    public TabletAppDefinition(ResourceLocation id, Component title, Component description,
                               int accentColor, List<TabletTabDefinition> tabs,
                               ResourceLocation icon) {
        this(id, title, description, accentColor, tabs, icon, Set.of());
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Check if the tablet app shares the key
    public boolean shares(String key) {
        return key != null && sharedDataKeys.contains(key);
    }

    // Create the default icon
    public static ResourceLocation defaultIcon(ResourceLocation appId) {
        Objects.requireNonNull(appId, "appId");
        return ResourceLocation.fromNamespaceAndPath(appId.getNamespace(),
                "textures/gui/apps/" + appId.getPath() + ".png");
    }

    // Get the title
    @Override
    public Component title() {
        return title.copy();
    }

    // Get the description
    @Override
    public Component description() {
        return description.copy();
    }
}
