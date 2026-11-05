package com.rieno.gadgetsandgizmos.lib.network;

import java.util.UUID;

// Assemble one ordered transfer without allocating from an unchecked length
public final class PayloadTransferBuffer{
    public static final int CHUNK_BYTES = 24 * 1024;
    public static final int MAX_BYTES = 16 * 1024 * 1024;
    public static final long TIMEOUT_NANOS = 60_000_000_000L;

    // Retain only the current stream until it completes or expires
    private UUID transferId;
    private byte[] bytes;
    private int offset;
    private long lastReceive;

    // Accept contiguous chunks and return bytes only after the entire transfer arrives
    public byte[] receive(UUID id, int total, int start, byte[] data, long now){
        if(total <= 0 || total > MAX_BYTES || start < 0 || data.length == 0
                || data.length > CHUNK_BYTES || start > total - data.length){
            clear();
            throw new IllegalArgumentException("Invalid payload chunk");
        }
        if(bytes != null && now - lastReceive > TIMEOUT_NANOS) clear();
        if(start == 0){
            if(bytes != null){ clear(); throw new IllegalArgumentException("Overlapping payload transfers"); }
            transferId = id;
            bytes = new byte[total];
        }
        if(bytes == null || !id.equals(transferId) || bytes.length != total || offset != start){
            clear();
            throw new IllegalArgumentException("Out of order payload chunk");
        }
        System.arraycopy(data, 0, bytes, start, data.length);
        offset += data.length;
        lastReceive = now;
        if(offset != total) return null;
        byte[] res = bytes;
        clear();
        return res;
    }

    // Release an abandoned stream without waiting for another packet
    public void clear(){ transferId = null; bytes = null; offset = 0; }
}
