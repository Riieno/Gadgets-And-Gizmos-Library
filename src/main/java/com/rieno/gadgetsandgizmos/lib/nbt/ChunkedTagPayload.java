package com.rieno.gadgetsandgizmos.lib.nbt;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

// Store bounded compressed NBT in independently transferable chunks
public final class ChunkedTagPayload{
    public static final int CHUNK_BYTES = 32 * 1024;
    public static final int MAX_COMPRESSED_BYTES = 16 * 1024 * 1024;
    private static final long MAX_DECOMPRESSED_BYTES = 128L * 1024 * 1024;

    private ChunkedTagPayload(){}

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Preserve the version-one envelope used by existing saved schematics
    public static CompoundTag encode(CompoundTag data){
        byte[] bytes;
        try{
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            NbtIo.writeCompressed(data, output);
            bytes = output.toByteArray();
        }catch(IOException err){ throw new IllegalArgumentException("Cannot encode chunked NBT", err); }
        if(bytes.length == 0 || bytes.length > MAX_COMPRESSED_BYTES)
            throw new IllegalArgumentException("Compressed NBT exceeds the payload limit");
        CompoundTag res = new CompoundTag();
        res.putInt("Version", 1);
        res.putInt("CompressedBytes", bytes.length);
        ListTag chunks = new ListTag();
        for(int offset = 0; offset < bytes.length; offset += CHUNK_BYTES){
            CompoundTag chunk = new CompoundTag();
            chunk.putByteArray("Data", Arrays.copyOfRange(bytes, offset, Math.min(bytes.length, offset + CHUNK_BYTES)));
            chunks.add(chunk);
        }
        res.put("Chunks", chunks);
        return res;
    }

    // Reject incomplete envelopes and bound decompression before reading their contents
    public static CompoundTag decode(CompoundTag envelope){
        if(envelope.getInt("Version") != 1) throw new IllegalArgumentException("Unsupported chunked NBT version");
        int size = envelope.getInt("CompressedBytes");
        ListTag chunks = envelope.getList("Chunks", Tag.TAG_COMPOUND);
        if(size <= 0 || size > MAX_COMPRESSED_BYTES || chunks.isEmpty()
                || chunks.size() > MAX_COMPRESSED_BYTES / CHUNK_BYTES)
            throw new IllegalArgumentException("Invalid chunked NBT size");
        ByteArrayOutputStream output = new ByteArrayOutputStream(size);
        for(Tag raw : chunks){
            byte[] bytes = ((CompoundTag) raw).getByteArray("Data");
            if(bytes.length == 0 || bytes.length > CHUNK_BYTES || bytes.length > size - output.size())
                throw new IllegalArgumentException("Invalid chunked NBT segment");
            output.writeBytes(bytes);
        }
        if(output.size() != size) throw new IllegalArgumentException("Incomplete chunked NBT");
        try{
            return NbtIo.readCompressed(new ByteArrayInputStream(output.toByteArray()),
                    NbtAccounter.create(MAX_DECOMPRESSED_BYTES));
        }catch(IOException err){ throw new IllegalArgumentException("Cannot decode chunked NBT", err); }
    }
}
