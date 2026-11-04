package com.rieno.gadgetsandgizmos.lib.shipping;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

// Keep resource destinations separate from movement and control bindings
public final class LogisticsBindings{
    public enum Group{
        ITEMS("items", "Items"), FLUIDS("fluids", "Fluids"), FUEL("fuel", "Fuel"),
        ENERGY("energy", "Energy"), FUEL_CONSUMER("fuel_consumer", "Fuel Consumer");

        private final String id;
        private final String label;
        Group(String id, String label){ this.id = id; this.label = label; }
        public String id(){ return id; }
        public String label(){ return label; }
        public static Group fromId(String id){
            for(Group group : values()) if(group.id.equals(id)) return group;
            return null;
        }
    }

    public record Endpoint(UUID subLevelId, BlockPos position, Direction face){
        public Endpoint{ position = position.immutable(); }
        public CompoundTag toTag(){
            CompoundTag tag = new CompoundTag();
            if(subLevelId != null) tag.putUUID("SubLevelId", subLevelId);
            tag.putLong("Position", position.asLong());
            if(face != null) tag.putString("Face", face.getName());
            return tag;
        }
        public static Endpoint fromTag(CompoundTag tag){
            return new Endpoint(tag.hasUUID("SubLevelId") ? tag.getUUID("SubLevelId") : null,
                    BlockPos.of(tag.getLong("Position")), tag.contains("Face")
                    ? Direction.byName(tag.getString("Face")) : null);
        }
    }

    private final EnumMap<Group, Set<Endpoint>> groups = new EnumMap<>(Group.class);
    private record BlockKey(UUID subLevelId, BlockPos position){}

    public Set<Endpoint> endpoints(Group group){ return Set.copyOf(groups.getOrDefault(group, Set.of())); }

    public void assign(Group group, List<Endpoint> endpoints){
        var selected = new LinkedHashMap<BlockKey, Endpoint>();
        endpoints(group).forEach(endpoint -> selected.put(new BlockKey(endpoint.subLevelId(), endpoint.position()), endpoint));
        for(Endpoint endpoint : endpoints){
            BlockKey key = new BlockKey(endpoint.subLevelId(), endpoint.position());
            if(selected.containsKey(key) || selected.size() < 4096) selected.put(key, endpoint);
        }
        groups.put(group, new LinkedHashSet<>(selected.values()));
    }

    public void remove(Group group, Predicate<Endpoint> predicate){
        Set<Endpoint> selected = new LinkedHashSet<>(endpoints(group));
        selected.removeIf(predicate);
        groups.put(group, selected);
    }

    public LogisticsBindings filtered(Predicate<Endpoint> predicate){
        LogisticsBindings res = new LogisticsBindings();
        for(Group group : Group.values()) res.assign(group, endpoints(group).stream().filter(predicate).toList());
        return res;
    }

    public boolean isEmpty(){ return groups.values().stream().allMatch(Set::isEmpty); }

    public CompoundTag toTag(){
        CompoundTag tag = new CompoundTag();
        for(Group group : Group.values()){
            ListTag entries = new ListTag();
            endpoints(group).stream().sorted(java.util.Comparator
                    .comparing(Endpoint::subLevelId, java.util.Comparator.nullsFirst(java.util.Comparator.naturalOrder()))
                    .thenComparing(Endpoint::position)
                    .thenComparing(endpoint -> endpoint.face() == null ? "" : endpoint.face().getName()))
                    .forEach(endpoint -> entries.add(endpoint.toTag()));
            tag.put(group.id(), entries);
        }
        return tag;
    }

    public static LogisticsBindings fromTag(CompoundTag tag){
        LogisticsBindings res = new LogisticsBindings();
        for(Group group : Group.values()){
            ListTag entries = tag.getList(group.id(), Tag.TAG_COMPOUND);
            List<Endpoint> endpoints = new java.util.ArrayList<>();
            for(int idx = 0; idx < Math.min(entries.size(), 4096); idx++){
                CompoundTag entry = entries.getCompound(idx);
                if(entry.contains("Position", Tag.TAG_LONG)) endpoints.add(Endpoint.fromTag(entry));
            }
            res.assign(group, endpoints);
        }
        return res;
    }
}
