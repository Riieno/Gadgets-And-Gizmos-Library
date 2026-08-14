package com.rieno.gadgetsandgizmos.lib.tablet;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.nbt.CompoundTag;

// Store one app-owned notification shown on a tablet home screen and status bar
public record TabletNotification(String id, String title, String message, int accentColor) {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the tablet notification
    public TabletNotification {
        id = id == null ? "" : id.strip();
        title = title == null ? "" : title.strip();
        message = message == null ? "" : message.strip();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Write the tablet notification data
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Id", id);
        tag.putString("Title", title);
        tag.putString("Message", message);
        tag.putInt("Accent", accentColor);
        return tag;
    }

    // Read the tablet notification data
    public static TabletNotification fromTag(CompoundTag tag) {
        if (tag == null) return null;
        TabletNotification notification = new TabletNotification(tag.getString("Id"),
                tag.getString("Title"), tag.getString("Message"), tag.getInt("Accent"));
        return notification.id().isBlank() ? null : notification;
    }
}
