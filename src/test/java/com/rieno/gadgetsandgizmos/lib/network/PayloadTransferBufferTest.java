package com.rieno.gadgetsandgizmos.lib.network;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

// Exercise network limits with data that cannot fit a vanilla NBT packet
class PayloadTransferBufferTest{
    // Preserve all bytes across many independently bounded packet slices
    @Test
    void incompressibleThreeMegabyteTagRoundTrips(){
        byte[] source = new byte[3 * 1024 * 1024];
        new Random(42).nextBytes(source);
        CompoundTag tag = new CompoundTag();
        tag.putByteArray("Schematic", source);
        byte[] encoded = CompressedTagCodec.encode(tag);
        assertTrue(encoded.length > 2 * 1024 * 1024);
        PayloadTransferBuffer buffer = new PayloadTransferBuffer();
        UUID id = UUID.randomUUID();
        byte[] res = null;
        for(int offset = 0; offset < encoded.length; offset += PayloadTransferBuffer.CHUNK_BYTES){
            res = buffer.receive(id, encoded.length, offset,
                    Arrays.copyOfRange(encoded, offset, Math.min(encoded.length, offset + PayloadTransferBuffer.CHUNK_BYTES)), offset);
            if(offset + PayloadTransferBuffer.CHUNK_BYTES < encoded.length) assertNull(res);
        }
        assertArrayEquals(source, CompressedTagCodec.decode(res).getByteArray("Schematic"));
    }

    // Reject attacker-controlled sizes before allocating a transfer buffer
    @Test
    void malformedLengthsAndOffsetsAreRejected(){
        PayloadTransferBuffer buffer = new PayloadTransferBuffer();
        UUID id = UUID.randomUUID();
        for(int total : new int[]{-1, 0, PayloadTransferBuffer.MAX_BYTES + 1, Integer.MAX_VALUE}){
            assertThrows(IllegalArgumentException.class, () -> buffer.receive(id, total, 0, new byte[1], 0));
        }
        assertThrows(IllegalArgumentException.class, () -> buffer.receive(id, 10, -1, new byte[1], 0));
        assertThrows(IllegalArgumentException.class, () -> buffer.receive(id, 10, Integer.MAX_VALUE, new byte[1], 0));
        assertThrows(IllegalArgumentException.class, () -> buffer.receive(id, 10, 0, new byte[0], 0));
        assertThrows(IllegalArgumentException.class, () -> buffer.receive(id, PayloadTransferBuffer.MAX_BYTES, 0,
                new byte[PayloadTransferBuffer.CHUNK_BYTES + 1], 0));
    }

    // Never publish duplicated, missing or mixed stream data
    @Test
    void incompleteAndInterleavedTransfersCannotApply(){
        PayloadTransferBuffer buffer = new PayloadTransferBuffer();
        UUID id = UUID.randomUUID();
        assertNull(buffer.receive(id, 4, 0, new byte[]{1, 2}, 0));
        assertThrows(IllegalArgumentException.class, () -> buffer.receive(id, 4, 0, new byte[]{1, 2}, 1));
        assertNull(buffer.receive(id, 4, 0, new byte[]{1, 2}, 2));
        assertThrows(IllegalArgumentException.class, () -> buffer.receive(UUID.randomUUID(), 4, 2, new byte[]{3, 4}, 3));
        assertThrows(IllegalArgumentException.class, () -> buffer.receive(id, 4, 3, new byte[]{4}, 4));
        assertArrayEquals(new byte[]{5, 6}, buffer.receive(UUID.randomUUID(), 2, 0, new byte[]{5, 6}, 5));
    }

    // Release timed-out data and allow a fresh transfer on the same connection
    @Test
    void expiredStreamsRequireAFreshBaseline(){
        PayloadTransferBuffer buffer = new PayloadTransferBuffer();
        UUID id = UUID.randomUUID();
        buffer.receive(id, 4, 0, new byte[]{1, 2}, 0);
        assertThrows(IllegalArgumentException.class, () -> buffer.receive(id, 4, 2, new byte[]{3, 4},
                PayloadTransferBuffer.TIMEOUT_NANOS + 1));
        assertArrayEquals(new byte[]{7}, buffer.receive(UUID.randomUUID(), 1, 0, new byte[]{7},
                PayloadTransferBuffer.TIMEOUT_NANOS + 2));
        assertThrows(IllegalArgumentException.class, () -> CompressedTagCodec.decode(new byte[]{1, 2, 3}));
    }
}
