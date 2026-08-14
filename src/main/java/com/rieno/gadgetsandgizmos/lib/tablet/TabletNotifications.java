package com.rieno.gadgetsandgizmos.lib.tablet;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// Keep app notifications in tablet storage without exposing another app's private data
public final class TabletNotifications {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final String NOTIFICATIONS_KEY = "Notifications";
    private static final int MAX_NOTIFICATIONS = 32;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the tablet notifications
    private TabletNotifications() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the list
    public static List<TabletNotification> list(UUID tabletId, ResourceLocation appId) {
        if (tabletId == null || appId == null) return List.of();
        List<TabletNotification> notifications = new ArrayList<>();
        ListTag tags = TabletStorageApi.storage().app(tabletId, appId)
                .getList(NOTIFICATIONS_KEY, Tag.TAG_COMPOUND);
        for (int idx = 0; idx < tags.size() && notifications.size() < MAX_NOTIFICATIONS; idx++) {
            TabletNotification notification = TabletNotification.fromTag(tags.getCompound(idx));
            if (notification != null) notifications.add(notification);
        }
        return List.copyOf(notifications);
    }

    // Count the tablet notifications
    public static int count(UUID tabletId, ResourceLocation appId) {
        return list(tabletId, appId).size();
    }

    // Post the tablet notifications
    public static void post(UUID tabletId, ResourceLocation appId, TabletNotification notification) {
        if (tabletId == null || appId == null || notification == null || notification.id().isBlank()) return;
        TabletStorageApi.storage().updateApp(tabletId, appId, data -> {
            ListTag next = new ListTag();
            for (TabletNotification existing : list(data)) {
                if (!notification.id().equals(existing.id())) next.add(existing.toTag());
            }
            next.add(notification.toTag());
            while (next.size() > MAX_NOTIFICATIONS) next.remove(0);
            data.put(NOTIFICATIONS_KEY, next);
            return data;
        });
    }

    // Dismiss a tablet notification
    public static void dismiss(UUID tabletId, ResourceLocation appId, String notificationId) {
        if (tabletId == null || appId == null || notificationId == null || notificationId.isBlank()) return;
        TabletStorageApi.storage().updateApp(tabletId, appId, data -> {
            ListTag next = new ListTag();
            for (TabletNotification notification : list(data)) {
                if (!notificationId.equals(notification.id())) next.add(notification.toTag());
            }
            if (next.isEmpty()) data.remove(NOTIFICATIONS_KEY);
            else data.put(NOTIFICATIONS_KEY, next);
            return data;
        });
    }

    // Clear the tablet notifications
    public static void clear(UUID tabletId, ResourceLocation appId) {
        if (tabletId == null || appId == null) return;
        TabletStorageApi.storage().updateApp(tabletId, appId, data -> {
            data.remove(NOTIFICATIONS_KEY);
            return data;
        });
    }

    // Get the list
    private static List<TabletNotification> list(CompoundTag data) {
        List<TabletNotification> notifications = new ArrayList<>();
        for (int idx = 0; idx < data.getList(NOTIFICATIONS_KEY, Tag.TAG_COMPOUND).size(); idx++) {
            TabletNotification notification = TabletNotification.fromTag(
                    data.getList(NOTIFICATIONS_KEY, Tag.TAG_COMPOUND).getCompound(idx));
            if (notification != null) notifications.add(notification);
        }
        return notifications;
    }
}
