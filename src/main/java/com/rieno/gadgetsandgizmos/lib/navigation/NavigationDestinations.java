package com.rieno.gadgetsandgizmos.lib.navigation;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// Store named world coordinates without depending on a tablet host or its database
public record NavigationDestinations(List<Entry> entries){
    public static final int MAX_ENTRIES = 64;
    public static final int MAX_NAME_LENGTH = 64;

    public NavigationDestinations{ entries = entries == null ? List.of() : List.copyOf(entries.subList(0, Math.min(MAX_ENTRIES, entries.size()))); }

    public record Entry(UUID id, String name, ResourceLocation dimension, Vec3 position){
        public Entry{
            name = name == null ? "" : name.strip();
            if(id == null || name.isBlank() || name.length() > MAX_NAME_LENGTH || dimension == null || !valid(position)){
                throw new IllegalArgumentException("Invalid navigation destination");
            }
        }

        public String coordinates(){ return position.x + ", " + position.y + ", " + position.z; }
    }

    // Update a matching name in this dimension without changing its saved identity
    public NavigationDestinations save(String name, ResourceLocation dimension, Vec3 position){
        String label = name == null ? "" : name.strip();
        UUID id = entries.stream().filter(val -> val.dimension().equals(dimension) && val.name().equalsIgnoreCase(label))
                .map(Entry::id).findFirst().orElseGet(UUID::randomUUID);
        var entry = new Entry(id, label, dimension, position);
        var next = new ArrayList<>(entries.stream().filter(val -> !val.id().equals(id)).toList());
        next.add(entry);
        while(next.size() > MAX_ENTRIES) next.removeFirst();
        return new NavigationDestinations(next);
    }

    public NavigationDestinations remove(UUID id){
        return new NavigationDestinations(entries.stream().filter(val -> !val.id().equals(id)).toList());
    }

    public Optional<Entry> find(UUID id, ResourceLocation dimension){
        return entries.stream().filter(val -> val.id().equals(id) && val.dimension().equals(dimension)).findFirst();
    }

    // Reject malformed and nonfinite input before it can reach a movement command
    public static Optional<Vec3> parseCoordinates(String text){
        if(text == null) return Optional.empty();
        String[] parts = text.strip().split("[,\\s]+");
        if(parts.length != 3) return Optional.empty();
        try{
            var pos = new Vec3(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]), Double.parseDouble(parts[2]));
            return valid(pos) ? Optional.of(pos) : Optional.empty();
        }catch(NumberFormatException err){ return Optional.empty(); }
    }

    public CompoundTag toTag(){
        var tag = new CompoundTag();
        var rows = new ListTag();
        for(var val : entries){
            var row = new CompoundTag();
            row.putUUID("Id", val.id());
            row.putString("Name", val.name());
            row.putString("Dimension", val.dimension().toString());
            row.putDouble("X", val.position().x); row.putDouble("Y", val.position().y); row.putDouble("Z", val.position().z);
            rows.add(row);
        }
        tag.put("Destinations", rows);
        return tag;
    }

    // Ignore corrupt entries while keeping the remainder of a saved list usable
    public static NavigationDestinations fromTag(CompoundTag tag){
        var entries = new ArrayList<Entry>();
        var rows = tag.getList("Destinations", Tag.TAG_COMPOUND);
        for(int idx = 0; idx < rows.size() && entries.size() < MAX_ENTRIES; idx++){
            var row = rows.getCompound(idx);
            if(!row.hasUUID("Id") || !row.contains("X", Tag.TAG_ANY_NUMERIC)
                    || !row.contains("Y", Tag.TAG_ANY_NUMERIC) || !row.contains("Z", Tag.TAG_ANY_NUMERIC)) continue;
            try{
                entries.add(new Entry(row.getUUID("Id"), row.getString("Name"), ResourceLocation.tryParse(row.getString("Dimension")),
                        new Vec3(row.getDouble("X"), row.getDouble("Y"), row.getDouble("Z"))));
            }catch(IllegalArgumentException err){ continue; }
        }
        return new NavigationDestinations(entries);
    }

    private static boolean valid(Vec3 pos){
        return pos != null && Double.isFinite(pos.x) && Double.isFinite(pos.y) && Double.isFinite(pos.z)
                && Math.abs(pos.x) <= 30_000_000 && Math.abs(pos.y) <= 30_000_000 && Math.abs(pos.z) <= 30_000_000;
    }
}
