package com.rieno.gadgetsandgizmos.lib.view;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

// Retain named sources across dimensions without exposing host storage internals
public record ViewSourceBindings(List<Entry> entries){
    public static final int MAX_SOURCES = 128;
    public ViewSourceBindings{
        entries = List.copyOf(entries);
        if(entries.size() > MAX_SOURCES) throw new IllegalArgumentException("The source list is full");
        if(entries.stream().map(Entry::source).distinct().count() != entries.size()){
            throw new IllegalArgumentException("Source bindings must be unique");
        }
    }
    // Replace the same physical source while retaining the rest of the bound list
    public ViewSourceBindings add(ViewReference ref, String label){
        List<Entry> next = new ArrayList<>(entries);
        next.removeIf(entry -> entry.source().equals(ref));
        if(next.size() >= MAX_SOURCES) throw new IllegalArgumentException("The source list is full");
        next.add(new Entry(ref, label));
        return new ViewSourceBindings(next);
    }
    // Remove only the explicitly selected source
    public ViewSourceBindings remove(String key){
        return new ViewSourceBindings(entries.stream().filter(entry -> !entry.key().equals(key)).toList());
    }
    // Keep source identity stable when its visible label changes
    public ViewSourceBindings rename(String key, String label){
        return new ViewSourceBindings(entries.stream().map(entry -> entry.key().equals(key)
                ? new Entry(entry.source(), label) : entry).toList());
    }
    // Save ordered bindings in the host's app or block data
    public ListTag toTag(){
        ListTag tags = new ListTag();
        for(Entry entry : entries){
            CompoundTag tag = entry.source().toTag();
            tag.putString("Name", entry.name());
            tags.add(tag);
        }
        return tags;
    }
    // Ignore unavailable saved references rather than inventing camera positions
    public static ViewSourceBindings fromTag(ListTag tags){
        List<Entry> entries = new ArrayList<>();
        for(Tag val : tags){
            if(!(val instanceof CompoundTag tag) || entries.size() >= MAX_SOURCES) continue;
            ViewReference ref = ViewReference.fromTag(tag);
            if(ref != null && entries.stream().noneMatch(entry -> entry.source().equals(ref))){
                entries.add(new Entry(ref, tag.getString("Name")));
            }
        }
        return new ViewSourceBindings(entries);
    }
    public record Entry(ViewReference source, String name){
        // Bound label size independently of packet and storage limits
        public Entry{
            java.util.Objects.requireNonNull(source, "source");
            name = name == null ? "" : name.strip().substring(0, Math.min(64, name.strip().length()));
        }
        // Distinguish sources with equal local positions in different worlds
        public String key(){ return source.dimension() + ";" + source.subLevelId() + ";" + source.blockPos().asLong(); }
    }
}
