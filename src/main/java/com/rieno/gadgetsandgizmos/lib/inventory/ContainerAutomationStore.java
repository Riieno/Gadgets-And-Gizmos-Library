package com.rieno.gadgetsandgizmos.lib.inventory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Persist automation against a dimension, sublevel and block rather than a movable tablet
public final class ContainerAutomationStore extends SavedData{
    private static final Factory<ContainerAutomationStore> FACTORY = new Factory<>(ContainerAutomationStore::new, ContainerAutomationStore::load);
    private final Map<Key, ContainerAutomation> entries = new LinkedHashMap<>();

    public static ContainerAutomationStore get(ServerLevel level){
        return level.getDataStorage().computeIfAbsent(FACTORY, "gadgetsngizmos_container_automation");
    }

    public ContainerAutomation find(UUID subLevelId, BlockPos pos){ return entries.get(new Key(subLevelId, pos)); }

    // Bound the number of configured targets owned by any one player
    public ContainerAutomation configure(UUID owner, UUID subLevelId, BlockPos pos){
        Key key = new Key(subLevelId, pos);
        ContainerAutomation config = entries.get(key);
        if(config != null) return config;
        if(entries.values().stream().filter(val -> owner.equals(val.owner())).count() >= 128) throw new IllegalArgumentException("Container automation limit reached");
        config = new ContainerAutomation();
        config.claim(owner);
        entries.put(key, config);
        setDirty();
        return config;
    }

    public List<Map.Entry<Key, ContainerAutomation>> entries(){ return List.copyOf(entries.entrySet()); }

    // Remove stale configuration when its owning block is destroyed
    public void remove(UUID subLevelId, BlockPos pos){
        if(entries.remove(new Key(subLevelId, pos)) != null) setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider){
        ListTag rows = new ListTag();
        entries.forEach((key, val) -> {
            CompoundTag row = val.toTag(provider);
            row.putLong("Pos", key.pos().asLong());
            if(key.subLevelId() != null) row.putUUID("SubLevel", key.subLevelId());
            rows.add(row);
        });
        tag.put("Containers", rows);
        return tag;
    }

    private static ContainerAutomationStore load(CompoundTag tag, HolderLookup.Provider provider){
        ContainerAutomationStore store = new ContainerAutomationStore();
        ListTag rows = tag.getList("Containers", Tag.TAG_COMPOUND);
        for(int idx = 0; idx < rows.size(); idx++){
            CompoundTag row = rows.getCompound(idx);
            store.entries.put(new Key(row.hasUUID("SubLevel") ? row.getUUID("SubLevel") : null, BlockPos.of(row.getLong("Pos"))), ContainerAutomation.fromTag(row, provider));
        }
        return store;
    }

    public record Key(UUID subLevelId, BlockPos pos){ public Key{ pos = pos.immutable(); } }
}
