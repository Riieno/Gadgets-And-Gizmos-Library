package com.rieno.gadgetsandgizmos.lib.control;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;

// Carry one timestamped analogue channel sample without platform input objects
public record AnalogueSignalPacket(String channelId, float value, long gameTick, String sourceId, String sourceType) {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final StreamCodec<RegistryFriendlyByteBuf, AnalogueSignalPacket> STREAM_CODEC = StreamCodec.of(
            AnalogueSignalPacket::encode,
            AnalogueSignalPacket::decode);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the analogue signal
    public AnalogueSignalPacket {
        channelId = channelId == null ? "" : channelId;
        sourceId = sourceId == null ? "" : sourceId;
        sourceType = sourceType == null ? "" : sourceType;
        value = Mth.clamp(value, -1.0F, 1.0F);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Write the analogue signal data
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("ChannelId", channelId);
        tag.putFloat("Value", value);
        tag.putLong("GameTick", gameTick);
        tag.putString("SourceId", sourceId);
        tag.putString("SourceType", sourceType);
        return tag;
    }

    // Get the redstone strength
    public int redstoneStrength() {
        return AnalogueChannel.toRedstone(Math.abs(value));
    }

    // Read the analogue signal data
    public static AnalogueSignalPacket fromTag(CompoundTag tag) {
        return new AnalogueSignalPacket(
                tag.getString("ChannelId"),
                tag.getFloat("Value"),
                tag.getLong("GameTick"),
                tag.getString("SourceId"),
                tag.getString("SourceType"));
    }

    // Encode the analogue signal
    private static void encode(RegistryFriendlyByteBuf buffer, AnalogueSignalPacket packet) {
        buffer.writeUtf(packet.channelId);
        buffer.writeFloat(packet.value);
        buffer.writeLong(packet.gameTick);
        buffer.writeUtf(packet.sourceId);
        buffer.writeUtf(packet.sourceType);
    }

    // Decode the analogue signal
    private static AnalogueSignalPacket decode(RegistryFriendlyByteBuf buffer) {
        return new AnalogueSignalPacket(
                buffer.readUtf(),
                buffer.readFloat(),
                buffer.readLong(),
                buffer.readUtf(),
                buffer.readUtf());
    }
}
