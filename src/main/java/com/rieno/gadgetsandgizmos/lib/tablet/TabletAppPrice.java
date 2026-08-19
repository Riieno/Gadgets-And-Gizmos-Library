package com.rieno.gadgetsandgizmos.lib.tablet;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

public record TabletAppPrice(ResourceLocation itemId, int count){

    public static final TabletAppPrice FREE = new TabletAppPrice(ResourceLocation.withDefaultNamespace("emerald"), 0);

    public TabletAppPrice{
        Objects.requireNonNull(itemId, "itemId");
        if (count < 0){throw new IllegalStateException("Tablet app price cannot be negative");}
    }

    public boolean free(){
        return count == 0;
    }

}