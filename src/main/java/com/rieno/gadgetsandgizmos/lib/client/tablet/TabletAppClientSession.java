package com.rieno.gadgetsandgizmos.lib.client.tablet;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           IMPORTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.resources.ResourceLocation;
import java.util.Map;
import java.util.HashMap;
import java.util.Objects;


    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/


public final class TabletAppClientSession{
    private final Map<ResourceLocation, TabletAppClientState> states = new HashMap<>();

    public TabletAppClientState stateFor(ResourceLocation appId, TabletAppClientRenderer render){
        Objects.requireNonNull(appId, "appId");
        Objects.requireNonNull(render, "render");
        return states.computeIfAbsent(appId, ignored -> Objects.requireNonNull(render.createScreenState(), "render state"));
    }

    public void clear(){
        states.clear();
    }
}