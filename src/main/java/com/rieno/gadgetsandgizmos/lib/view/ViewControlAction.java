package com.rieno.gadgetsandgizmos.lib.view;

import net.minecraft.server.level.ServerPlayer;

// Apply camera actions after the host has authorized its current source
public enum ViewControlAction{
    CONTROL,
    TAKE_CONTROL,
    INTERACT;

    public boolean apply(ServerPlayer player, ViewReference ref, ViewControlInput input){
        if(player == null || ref == null) return false;
        return switch(this){
            case CONTROL -> input != null && ViewControlSessions.apply(player, ref, input);
            case TAKE_CONTROL -> RemoteViewSessions.open(player, ref);
            case INTERACT -> ViewBlockInteractions.interact(player, ref);
        };
    }
}
