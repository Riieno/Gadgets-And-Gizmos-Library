package com.rieno.gadgetsandgizmos.lib.scm;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// Exercise ship access, ownership and persistence across server session changes
class ShipAccessModesRegressionTest{
    // Saved identities shared across root and connected-body checks
    private final UUID shipId = UUID.randomUUID();
    private final UUID bodyId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();
    private final UUID memberId = UUID.randomUUID();
    private final UUID visitorId = UUID.randomUUID();
    // Server session and saved-data factory used by the production lookup
    private MinecraftServer server;
    private DimensionDataStorage storage;
    private ShipPermissionManager manager;
    private SavedData.Factory<ShipPermissionManager> factory;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the vanilla registry context without loading addon content
    @BeforeAll
    static void prepareRegistries(){
        net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        net.neoforged.fml.ModList.of(List.of(), List.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    // Resolve a fresh manager through the same saved-data lookup as the addon
    @BeforeEach
    @SuppressWarnings("unchecked")
    void prepareServer(){
        server = mock(MinecraftServer.class);
        ServerLevel level = mock(ServerLevel.class);
        storage = mock(DimensionDataStorage.class);
        when(server.overworld()).thenReturn(level);
        when(level.getDataStorage()).thenReturn(storage);
        when(storage.computeIfAbsent(any(), anyString())).thenAnswer(invocation -> {
            factory = invocation.getArgument(0);
            if(manager == null) manager = factory.constructor().get();
            return manager;
        });
        manager = ShipPermissionManager.get(server);
    }

    // New and ownerless ships let every player use every ship action
    @Test
    void shipsDefaultToOpen(){
        assertTrue(manager.isOpen(shipId));
        assertTrue(manager.claim(shipId, ownerId));
        manager.bind(shipId, bodyId);
        for(ShipPermission permission : ShipPermission.values()){
            assertTrue(manager.allows(shipId, visitorId, permission));
            assertTrue(manager.allows(bodyId, visitorId, permission));
        }
        assertFalse(manager.setOpen(bodyId, visitorId, false));
        assertTrue(manager.isOpen(shipId));
    }

    // Closed ships enforce the stored rights on all connected bodies
    @Test
    void closingEnforcesMembersAndReopeningIgnoresThem(){
        assertTrue(manager.setOpen(shipId, ownerId, false));
        assertEquals(ownerId, manager.owner(shipId));
        manager.bind(shipId, bodyId);
        assertTrue(manager.set(bodyId, ownerId, memberId, "Member", ShipPermission.INTERACT, true));
        assertTrue(manager.allows(bodyId, memberId, ShipPermission.INTERACT));
        assertFalse(manager.allows(bodyId, memberId, ShipPermission.ACC_GRAPH));
        assertFalse(manager.allows(shipId, visitorId, ShipPermission.PLACE));
        assertTrue(manager.allows(bodyId, ownerId, ShipPermission.DESTROY));
        assertFalse(manager.set(bodyId, visitorId, visitorId, "Visitor", ShipPermission.PLACE, true));
        assertTrue(manager.setOpen(bodyId, ownerId, true));
        assertTrue(manager.allows(bodyId, visitorId, ShipPermission.ACC_GRAPH));
        assertTrue(manager.setOpen(shipId, ownerId, false));
        assertFalse(manager.allows(bodyId, visitorId, ShipPermission.ACC_GRAPH));
        assertTrue(manager.allows(bodyId, memberId, ShipPermission.INTERACT));
    }

    // Transferred ships retain access and bindings without retaining the former owner's management rights
    @Test
    void ownershipTransferSurvivesReload(){
        assertTrue(manager.setOpen(shipId, ownerId, false));
        manager.bind(shipId, bodyId);
        assertTrue(manager.set(shipId, ownerId, memberId, "Member", ShipPermission.INTERACT, true));
        assertFalse(manager.transferOwnership(bodyId, visitorId, memberId, "Owner"));
        assertFalse(manager.transferOwnership(bodyId, ownerId, ownerId, "Owner"));
        assertTrue(manager.transferOwnership(bodyId, ownerId, memberId, "Owner"));
        reload(manager.save(new CompoundTag(), null));
        assertEquals(memberId, manager.owner(bodyId));
        assertEquals(shipId, manager.root(bodyId));
        assertFalse(manager.isOpen(bodyId));
        assertTrue(manager.canManage(bodyId, memberId));
        assertFalse(manager.canManage(bodyId, ownerId));
        assertTrue(manager.allows(bodyId, memberId, ShipPermission.ACC_GRAPH));
        assertEquals(EnumSet.allOf(ShipPermission.class), manager.members(shipId).getFirst().permissions());
        assertEquals("Owner", manager.members(shipId).getFirst().name());
        assertFalse(manager.members(shipId).stream().anyMatch(member -> member.playerId().equals(memberId)));
        assertFalse(manager.claim(shipId, ownerId));
        manager.syncBindings(shipId, Set.of(bodyId));
        assertTrue(manager.setOpen(bodyId, memberId, true));
    }

    // Missing access-mode tags migrate existing owners, members and bindings to open
    @Test
    void legacyClaimsMigrateToOpen(){
        assertTrue(manager.setOpen(shipId, ownerId, false));
        manager.bind(shipId, bodyId);
        assertTrue(manager.set(shipId, ownerId, memberId, "Member", ShipPermission.STORE, true));
        CompoundTag saved = manager.save(new CompoundTag(), null);
        saved.getList("Claims", Tag.TAG_COMPOUND).getCompound(0).remove("Open");
        reload(saved);
        assertEquals(ownerId, manager.owner(bodyId));
        assertTrue(manager.isOpen(bodyId));
        assertTrue(manager.allows(bodyId, visitorId, ShipPermission.DESTROY));
        assertTrue(manager.setOpen(bodyId, ownerId, false));
        assertTrue(manager.allows(bodyId, memberId, ShipPermission.STORE));
        assertFalse(manager.allows(bodyId, visitorId, ShipPermission.STORE));
    }

    // Private worlds bypass saved restrictions and enable them immediately when opened to LAN
    @Test
    void privateSinglePlayerDisablesPermissionsUntilLanIsOpened(){
        assertTrue(manager.setOpen(shipId, ownerId, false));
        when(server.isSingleplayer()).thenReturn(true);
        assertFalse(ShipPermissionManager.isEnabled(server));
        for(ShipPermission permission : ShipPermission.values()) assertTrue(manager.allows(shipId, visitorId, permission));
        assertFalse(manager.canManage(shipId, ownerId));
        assertFalse(manager.claim(bodyId, visitorId));
        assertFalse(manager.setOpen(shipId, ownerId, true));
        assertFalse(manager.set(shipId, ownerId, visitorId, "Visitor", ShipPermission.PLACE, true));
        assertFalse(manager.transferOwnership(shipId, ownerId, visitorId, "Owner"));
        when(server.isPublished()).thenReturn(true);
        assertTrue(ShipPermissionManager.isEnabled(server));
        assertFalse(manager.allows(shipId, visitorId, ShipPermission.PLACE));
        assertTrue(manager.canManage(shipId, ownerId));
        assertEquals(ownerId, manager.owner(shipId));
    }

    // Reload through the factory supplied to Minecraft's saved-data storage
    private void reload(CompoundTag tag){
        manager = factory.deserializer().apply(tag, null);
        manager = ShipPermissionManager.get(server);
    }
}
