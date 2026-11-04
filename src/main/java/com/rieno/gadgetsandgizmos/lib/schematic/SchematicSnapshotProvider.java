package com.rieno.gadgetsandgizmos.lib.schematic;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

// Supply portable block data from authoritative storage when a client exports a schematic
public interface SchematicSnapshotProvider{
    // Capture approved configuration on the owning server thread without sharing storage identities
    CompoundTag schematicSnapshot(HolderLookup.Provider provider);
}
