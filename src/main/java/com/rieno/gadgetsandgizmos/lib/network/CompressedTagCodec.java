package com.rieno.gadgetsandgizmos.lib.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.RegistryFriendlyByteBuf;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

// Bound compressed NBT and its expanded allocation independently
public final class CompressedTagCodec{
    public static final int MAX_COMPRESSED_BYTES = 16 * 1024 * 1024;
    public static final long MAX_EXPANDED_BYTES = 128L * 1024 * 1024;

    // Keep serialization shared by packet codecs and stored envelopes
    private CompressedTagCodec(){}

    // Encode one detached tag before splitting its bytes for transport
    public static byte[] encode(CompoundTag tag){
        try{
            var output = new ByteArrayOutputStream();
            NbtIo.writeCompressed(tag, output);
            byte[] bytes = output.toByteArray();
            if(bytes.length > MAX_COMPRESSED_BYTES) throw new IllegalArgumentException("Compressed NBT exceeds the payload limit");
            return bytes;
        }catch(IOException err){ throw new IllegalArgumentException("Cannot encode compressed NBT", err); }
    }

    // Reject oversized and incomplete data before exposing the decoded tag
    public static CompoundTag decode(byte[] bytes){
        if(bytes.length == 0 || bytes.length > MAX_COMPRESSED_BYTES) throw new IllegalArgumentException("Invalid compressed NBT size");
        try{
            return NbtIo.readCompressed(new ByteArrayInputStream(bytes), NbtAccounter.create(MAX_EXPANDED_BYTES));
        }catch(IOException err){ throw new IllegalArgumentException("Cannot decode compressed NBT", err); }
    }

    // Preserve the length-prefixed compressed packet format
    public static void write(RegistryFriendlyByteBuf buf, CompoundTag tag){ buf.writeByteArray(encode(tag)); }

    // Account for expanded NBT separately from the encoded packet length
    public static CompoundTag read(RegistryFriendlyByteBuf buf){ return decode(buf.readByteArray(MAX_COMPRESSED_BYTES)); }
}
