package com.rieno.gadgetsandgizmos.lib.client.tablet;

import net.minecraft.resources.ResourceLocation;
import java.util.function.Consumer;

// Let app controls request host dialogs without depending on a particular tablet screen
public interface TabletAppClientUi{
    TabletAppClientUi NONE = new TabletAppClientUi(){};

    default boolean projected(){ return false; }
    default void editText(String prompt, String initial, int maximumLength, Consumer<String> accept){}
    default void pickItem(ResourceLocation selected, Consumer<ResourceLocation> accept){}
    default void pickFluidContainer(ResourceLocation selected, ResourceLocation fluid,
                                    int millibuckets, Consumer<ResourceLocation> accept){
        pickItem(selected, accept);
    }
}
