package com.rieno.gadgetsandgizmos.lib.physics.archive;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RemoteArchiveAccessTest{
    @Test void creativeOrOperatorAccessIsRecheckedForEveryRemoteRequest(){
        var player = mock(ServerPlayer.class);var level = mock(ServerLevel.class);
        var limits = new SubLevelArchiveApi.Limits(16,32,32768,64,2048);
        assertFalse(SubLevelArchiveApi.canStoreRemotely(null));
        for(boolean creative : new boolean[]{false,true}){
            for(boolean operator : new boolean[]{false,true}){
                when(player.isCreative()).thenReturn(creative);when(player.hasPermissions(2)).thenReturn(operator);
                assertEquals(creative || operator,SubLevelArchiveApi.canStoreRemotely(player));
            }
        }
        when(player.isCreative()).thenReturn(false);when(player.hasPermissions(2)).thenReturn(false);
        var error = assertThrows(IllegalArgumentException.class,
                () -> SubLevelArchiveApi.storeRemote(player,level,UUID.randomUUID(),limits));
        assertTrue(error.getMessage().contains("creative mode or operator"));
        verifyNoInteractions(level);
    }
}
