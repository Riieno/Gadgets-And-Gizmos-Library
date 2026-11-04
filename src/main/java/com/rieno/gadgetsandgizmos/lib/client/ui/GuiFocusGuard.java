package com.rieno.gadgetsandgizmos.lib.client.ui;

import net.minecraft.client.gui.components.events.GuiEventListener;

// Ignore repeated assignments so parent focus queries do not commit and reopen inline editors
public final class GuiFocusGuard{
    private GuiEventListener focused;

    public boolean update(GuiEventListener next){
        if(focused == next) return false;
        focused = next;
        return true;
    }
}
