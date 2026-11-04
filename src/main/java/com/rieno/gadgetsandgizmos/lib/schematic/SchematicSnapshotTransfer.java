package com.rieno.gadgetsandgizmos.lib.schematic;

import com.rieno.gadgetsandgizmos.lib.nbt.ChunkedTagPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// Validate a complete snapshot transfer independently of its client or exporter
public final class SchematicSnapshotTransfer{
    private final UUID id;
    private final Set<BlockPos> positions;
    private final Map<BlockPos, CompoundTag> envelopes = new LinkedHashMap<>();
    private final Map<BlockPos, CompoundTag> snapshots = new LinkedHashMap<>();
    private int bytes;
    private boolean complete;

    public SchematicSnapshotTransfer(UUID id, List<BlockPos> positions){
        this.id = id;
        this.positions = Set.copyOf(positions);
        if(this.positions.isEmpty() || this.positions.size() != positions.size()
                || positions.size() > SchematicSnapshotPackets.MAX_BLOCKS)
            throw new IllegalArgumentException("Invalid schematic snapshot selection");
    }

    // Accept ordered segments and require a successful terminator after every requested block
    public void accept(SchematicSnapshotPackets.Segment segment){
        if(complete || !id.equals(segment.id()) || !segment.success())
            throw new IllegalArgumentException("Invalid schematic snapshot response");
        if(segment.idx() == -1){
            if(segment.size() != 0 || segment.bytes().length != 0 || snapshots.size() != positions.size())
                throw new IllegalArgumentException("Incomplete schematic snapshots");
            complete = true;
            return;
        }
        if(!positions.contains(segment.pos()) || snapshots.containsKey(segment.pos()) || segment.idx() < 0
                || segment.size() <= 0 || segment.size() > ChunkedTagPayload.MAX_COMPRESSED_BYTES
                || segment.bytes().length == 0 || segment.bytes().length > ChunkedTagPayload.CHUNK_BYTES)
            throw new IllegalArgumentException("Invalid schematic snapshot segment");
        bytes += segment.bytes().length;
        if(bytes > SchematicSnapshotPackets.MAX_TRANSFER_BYTES) throw new IllegalArgumentException("Schematic transfer exceeds its limit");
        CompoundTag envelope = envelopes.computeIfAbsent(segment.pos(), ignored -> {
            CompoundTag res = new CompoundTag();
            res.putInt("Version", 1);
            res.putInt("CompressedBytes", segment.size());
            res.put("Chunks", new ListTag());
            return res;
        });
        ListTag chunks = (ListTag) envelope.get("Chunks");
        if(envelope.getInt("CompressedBytes") != segment.size() || chunks.size() != segment.idx())
            throw new IllegalArgumentException("Out-of-order schematic snapshot segment");
        CompoundTag chunk = new CompoundTag();
        chunk.putByteArray("Data", segment.bytes().clone());
        chunks.add(chunk);
        int count = (segment.size() + ChunkedTagPayload.CHUNK_BYTES - 1) / ChunkedTagPayload.CHUNK_BYTES;
        if(chunks.size() == count){
            snapshots.put(segment.pos(), ChunkedTagPayload.decode(envelope));
            envelopes.remove(segment.pos());
        }
    }

    public boolean isComplete(){ return complete; }

    // Borrow immutable values only after the complete selection has been validated
    public Map<BlockPos, CompoundTag> snapshots(){
        if(!complete) throw new IllegalStateException("Schematic snapshots are incomplete");
        return Map.copyOf(snapshots);
    }
}
