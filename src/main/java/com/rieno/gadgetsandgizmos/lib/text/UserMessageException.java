package com.rieno.gadgetsandgizmos.lib.text;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

// Carry a translated validation failure through existing argument checks
public final class UserMessageException extends IllegalArgumentException{
    private final Component message;

    public UserMessageException(Component message){
        super(message.getContents() instanceof TranslatableContents contents ? contents.getKey() : message.toString());
        this.message = message.copy();
    }

    // Keep the translation key and arguments for the receiving client
    public Component component(){
        return message.copy();
    }
}
