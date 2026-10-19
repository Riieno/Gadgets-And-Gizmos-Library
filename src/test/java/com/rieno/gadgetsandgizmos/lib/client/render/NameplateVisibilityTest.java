package com.rieno.gadgetsandgizmos.lib.client.render;

import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.common.util.TriState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NameplateVisibilityTest{
    @BeforeAll static void bootstrap(){
        if(net.neoforged.fml.loading.LoadingModList.get() == null){
            net.neoforged.fml.loading.LoadingModList.of(java.util.List.of(), java.util.List.of(),
                    java.util.List.of(), java.util.List.of(), java.util.Map.of());
        }
        if(net.neoforged.fml.ModList.get() == null){
            net.neoforged.fml.ModList.of(java.util.List.of(), java.util.List.of());
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test void runtimeVisibilityOverridesPlayerNameRenderingAndCanBeEnabledAgain(){
        Entity entity = mock(Entity.class, withSettings().extraInterfaces(NameplateVisibility.class));
        when(((NameplateVisibility)entity).isNameplateVisible()).thenReturn(false, true, false);
        for(boolean visible : new boolean[]{false, true, false}){
            var event = event(entity, TriState.TRUE);
            NameplateVisibilityEvents.renderNameplate(event);
            assertEquals(visible ? TriState.TRUE : TriState.FALSE, event.canRender());
            assertEquals("PlayerName", event.getContent().getString());
        }
    }

    @Test void enabledNameplatesRetainRendererAndTeamRestrictions(){
        Entity entity = mock(Entity.class, withSettings().extraInterfaces(NameplateVisibility.class));
        when(((NameplateVisibility)entity).isNameplateVisible()).thenReturn(true);
        for(TriState state : TriState.values()){
            var event = event(entity, state);
            NameplateVisibilityEvents.renderNameplate(event);
            assertEquals(state, event.canRender());
        }
    }

    @Test void ordinaryEntitiesKeepTheirExistingNameplatePolicy(){
        Entity entity = mock(Entity.class);
        var event = event(entity, TriState.TRUE);
        NameplateVisibilityEvents.renderNameplate(event);
        assertEquals(TriState.TRUE, event.canRender());
    }

    private static RenderNameTagEvent event(Entity entity, TriState state){
        var event = new RenderNameTagEvent(entity, Component.literal("PlayerName"), null, null, null, 0, 0);
        event.setCanRender(state);
        return event;
    }
}
