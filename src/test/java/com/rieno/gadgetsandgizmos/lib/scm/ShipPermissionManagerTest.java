package com.rieno.gadgetsandgizmos.lib.scm;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ShipPermissionManagerTest {
    @Test
    void worldAndUnclaimedShipsAllowEveryPlayerAction(){
        ShipPermissionManager manager = new ShipPermissionManager();
        UUID ship = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        UUID guest = UUID.randomUUID();

        assertFalse(manager.isClaimed(null));
        assertFalse(manager.isClaimed(ship));
        for(ShipPermission permission : ShipPermission.values()){
            assertTrue(manager.allows(null, guest, permission));
            assertTrue(manager.allows(ship, guest, permission));
        }

        assertTrue(manager.claim(ship, owner));
        assertTrue(manager.isClaimed(ship));
        for(ShipPermission permission : ShipPermission.values()){
            assertFalse(manager.allows(ship, guest, permission));
            assertTrue(manager.allows(null, guest, permission));
        }

        manager.unclaim(ship);
        assertFalse(manager.isClaimed(ship));
        for(ShipPermission permission : ShipPermission.values()){
            assertTrue(manager.allows(ship, guest, permission));
        }
    }

    @Test
    void detachedBodiesStopInheritingTheOldShipClaim(){
        ShipPermissionManager manager = new ShipPermissionManager();
        UUID ship = UUID.randomUUID();
        UUID attached = UUID.randomUUID();
        UUID detached = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        UUID guest = UUID.randomUUID();

        assertTrue(manager.claim(ship, owner));
        manager.syncBindings(ship, Set.of(ship, attached, detached));
        assertFalse(manager.allows(detached, guest, ShipPermission.INTERACT));

        manager.syncBindings(ship, Set.of(ship, attached));
        assertFalse(manager.isClaimed(detached));
        for(ShipPermission permission : ShipPermission.values()){
            assertTrue(manager.allows(detached, guest, permission));
            assertFalse(manager.allows(attached, guest, permission));
        }
    }

    @Test
    void ownerAndMemberRightsFollowTheShipAndClearOnUnclaim(){
        ShipPermissionManager manager = new ShipPermissionManager();
        UUID ship = UUID.randomUUID();
        UUID child = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        UUID guest = UUID.randomUUID();

        assertTrue(manager.claim(ship, owner));
        manager.bind(ship, child);
        assertTrue(manager.allows(child, owner, ShipPermission.DESTROY));
        assertFalse(manager.allows(child, guest, ShipPermission.INTERACT));
        assertFalse(manager.set(child, guest, owner, "owner", ShipPermission.INTERACT, true));
        assertFalse(manager.set(child, guest, guest, "guest", ShipPermission.INTERACT, true));
        assertTrue(manager.set(child, owner, guest, "guest", ShipPermission.INTERACT, true));
        assertTrue(manager.allows(child, guest, ShipPermission.INTERACT));
        assertFalse(manager.allows(child, guest, ShipPermission.DESTROY));
        assertFalse(manager.claim(ship, guest));

        manager.unclaim(child);
        assertNull(manager.owner(ship));
        assertTrue(manager.allows(child, guest, ShipPermission.DESTROY));
    }
}
