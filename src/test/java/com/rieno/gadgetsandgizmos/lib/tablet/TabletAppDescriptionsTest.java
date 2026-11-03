package com.rieno.gadgetsandgizmos.lib.tablet;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TabletAppDescriptionsTest{
    @Test
    void fallbackKeepsExistingDefinitionsCompatible(){
        var app = app("fallback");
        assertEquals("Summary", TabletAppDescriptions.details(app).getString());
        assertNotSame(app.description(), TabletAppDescriptions.details(app));
    }

    @Test
    void registeredTextIsCopiedAndCannotBeReplaced(){
        var app = app("registered");
        var text = Component.literal("Detailed listing");
        TabletAppDescriptions.register(app.id(), text);
        text.append(" changed");
        var published = TabletAppDescriptions.details(app);
        assertEquals("Detailed listing", published.getString());
        published.getSiblings().add(Component.literal(" changed"));
        assertEquals("Detailed listing", TabletAppDescriptions.details(app).getString());
        assertThrows(IllegalStateException.class, () -> TabletAppDescriptions.register(app.id(), Component.literal("Replacement")));
    }

    private static TabletAppDefinition app(String name){
        return new TabletAppDefinition(ResourceLocation.fromNamespaceAndPath("test", name + "_" + UUID.randomUUID()),
                Component.literal(name), Component.literal("Summary"), 0, List.of());
    }
}
