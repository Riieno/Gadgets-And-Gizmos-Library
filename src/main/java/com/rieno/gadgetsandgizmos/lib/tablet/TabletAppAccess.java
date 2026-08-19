package com.rieno.gadgetsandgizmos.lib.tablet;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import java.util.UUID;

public final class TabletAppAccess{
    private TabletAppAccess(){}

    public static boolean canUse(TabletAppDefinition app, TabletAppPurchaseScope scope, UUID playerId, UUID tabletId){
        return app != null && (app.builtIn() || TabletAppEntitlementApi.store().owns(scope, playerId, tabletId, app.id()));
    }
}