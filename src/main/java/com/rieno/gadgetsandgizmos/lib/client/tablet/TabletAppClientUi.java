package com.rieno.gadgetsandgizmos.lib.client.tablet;

import net.minecraft.resources.ResourceLocation;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletInteractionMode;
import java.util.function.Consumer;

// Let app controls request host dialogs without depending on a particular tablet screen
public interface TabletAppClientUi{
    TabletAppClientUi NONE = new TabletAppClientUi(){};

    default boolean projected(){ return false; }
    // Full-screen menus and projected screens can both belong to a placed tablet
    default boolean placed(){ return projected(); }
    // Read and change the host's interaction mode without depending on its screen state
    default TabletInteractionMode interactionMode(){ return TabletInteractionMode.STANDARD; }
    default void setInteractionMode(TabletInteractionMode mode){}
    // Navigate through the host so saved tabs and active app snapshots stay in sync
    default void selectTab(String tabId){}
    default void openApp(ResourceLocation appId, String tabId){}
    // Native slots, cursor synchronization and item transfers remain owned by the host menu
    default TabletMenuInventory inventory(){ return null; }
    default void editText(String prompt, String initial, int maximumLength, Consumer<String> accept){}
    default void pickItem(ResourceLocation selected, Consumer<ResourceLocation> accept){}
    default void pickFluidContainer(ResourceLocation selected, ResourceLocation fluid,
                                    int millibuckets, Consumer<ResourceLocation> accept){
        pickItem(selected, accept);
    }
}
