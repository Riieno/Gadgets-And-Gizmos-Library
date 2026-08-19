package com.rieno.gadgetsandgizmos.lib.tablet;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

// Persist paid tablet app ownership to the player across player owned tablets
public interface TabletAppEntitlementStore{
    boolean owns(TabletAppPurchaseScope scope, UUID playerId, UUID tabletId, ResourceLocation appId);
    boolean grant(TabletAppPurchaseScope scope, UUID playerId, UUID tabletId, ResourceLocation appId);
    boolean revoke(TabletAppPurchaseScope scope, UUID playerId, UUID tabletId, ResourceLocation appId);
}