package com.rieno.gadgetsandgizmos.lib.tablet;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.resources.ResourceLocation;
import java.util.UUID;


public final class TabletAppEntitlementApi{
    private static final TabletAppEntitlementStore UNAVAILABLE = new TabletAppEntitlementStore(){
        @Override
        public boolean owns(TabletAppPurchaseScope scope, UUID playerId, UUID tabletId, ResourceLocation appId){return false;}
        @Override
        public boolean grant(TabletAppPurchaseScope scope, UUID playerId, UUID tabletId, ResourceLocation appId){return false;}
        @Override
        public boolean revoke(TabletAppPurchaseScope scope, UUID playerId, UUID tabletId, ResourceLocation appId){return false;}
    };

    private static volatile TabletAppEntitlementStore store = UNAVAILABLE;

    // Initialize the API
    private TabletAppEntitlementApi(){}

    public static TabletAppEntitlementStore store(){
        return store;
    }

    public static boolean available(){
        return store != UNAVAILABLE;
    }

    public static synchronized void install(TabletAppEntitlementStore next){
        if(next == null) throw new IllegalStateException("Entitlement store cannot be null");
        store = next;
    }

    public static synchronized void uninstall(TabletAppEntitlementStore expected){
        if(store == expected) store = UNAVAILABLE;
    }
}