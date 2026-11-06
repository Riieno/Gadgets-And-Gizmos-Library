package com.rieno.gadgetsandgizmos.lib.text;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// Keep server snapshots translatable until the receiving client renders them
class LocalizedTextTest{
    @Test void earlierPlainTextSnapshotsRemainReadable(){
        CompoundTag data = new CompoundTag();
        data.putString("Error", "Earlier server message");
        assertEquals("Earlier server message", LocalizedText.read(data, "Error").getString());
        assertEquals(Component.empty(), LocalizedText.read(data, "Missing"));
    }

    @Test void invalidSnapshotsDoNotCrashTheScreen(){
        CompoundTag data = new CompoundTag();
        data.putInt("Error", 42);
        assertEquals(Component.empty(), LocalizedText.read(data, "Error"));
    }

    @Test void validationFailuresKeepComponentsThroughExceptionHandlers(){
        var msg = Component.translatable("example.validation", 3).withStyle(ChatFormatting.GOLD);
        var err = new UserMessageException(msg);
        assertInstanceOf(IllegalArgumentException.class, err);
        assertEquals(msg, LocalizedText.message(err));
        err.component().getSiblings().add(Component.literal("changed"));
        assertEquals(msg, LocalizedText.message(err));
        assertEquals("External API failure", LocalizedText.message(new IllegalArgumentException("External API failure")).getString());
    }
}
