package com.rieno.gadgetsandgizmos.lib.inventory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// Retain stock ownership while ignoring retired container settings in existing saves
@SuppressWarnings("deprecation")
class ContainerAutomationRetirementTest{
    // Old saves must not reactivate container restrictions or adjacent push/pull transfers
    @Test void legacyControlsAreIgnoredAndNotSavedAgain(){
        UUID owner = UUID.randomUUID();
        UUID body = UUID.randomUUID();
        BlockPos pos = new BlockPos(3, 4, 5);
        CompoundTag saved = new CompoundTag();
        saved.putUUID("Owner", owner);
        saved.putUUID("ControllerSubLevel", body);
        saved.putLong("ControllerPos", pos.asLong());
        saved.putBoolean("Locked", true);
        saved.putBoolean("Push", true);
        saved.putBoolean("Pull", true);
        saved.put("Filter", new CompoundTag());
        HolderLookup.Provider registries = mock(HolderLookup.Provider.class);
        ContainerAutomation config = ContainerAutomation.fromTag(saved, registries);
        config.setLocked(true);
        config.setPush(true);
        config.setPull(true);
        assertFalse(config.locked());
        assertFalse(config.push());
        assertFalse(config.pull());
        assertTrue(config.filter().isEmpty());
        CompoundTag written = config.toTag(registries);
        assertEquals(owner, written.getUUID("Owner"));
        assertEquals(body, written.getUUID("ControllerSubLevel"));
        assertEquals(pos.asLong(), written.getLong("ControllerPos"));
        assertTrue(written.contains("Stock"));
        for(String key : new String[]{"Locked", "Push", "Pull", "Filter"}) assertFalse(written.contains(key));
        verifyNoInteractions(registries);
    }
}
