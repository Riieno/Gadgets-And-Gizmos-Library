package com.rieno.gadgetsandgizmos.lib.nbt;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.Objects;

// Transfer changed compound fields and list entries without repeating unchanged payloads
public final class CompoundTagDelta{
/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        DEFAULTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                       FUNCTIONS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    private CompoundTagDelta(){}

    // The patch owns changed payloads independently of both source documents
    public static CompoundTag between(CompoundTag prev, CompoundTag next){
        return difference(prev, next);
    }

    // Report changes to a selected field, including replacement or removal of an ancestor
    public static boolean affectsPath(CompoundTag patch, String... path){
        CompoundTag current = patch;
        for(String key : path){
            if(current.getCompound("Values").contains(key)) return true;
            for(Tag removed : current.getList("Removed", Tag.TAG_STRING)) if(key.equals(removed.getAsString())) return true;
            CompoundTag children = current.getCompound("Children");
            if(!children.contains(key, Tag.TAG_COMPOUND)) return false;
            current = children.getCompound(key);
        }
        return !current.isEmpty();
    }

    // Apply to a caller-owned document after checking its expected revision
    public static void apply(CompoundTag target, CompoundTag patch){
        ListTag removed = patch.getList("Removed", Tag.TAG_STRING);
        for(Tag key : removed) target.remove(key.getAsString());
        CompoundTag changed = patch.getCompound("Values");
        for(String key : changed.getAllKeys()) target.put(key, changed.get(key).copy());
        CompoundTag children = patch.getCompound("Children");
        for(String key : children.getAllKeys()){
            Tag val = target.get(key);
            CompoundTag child = children.getCompound(key);
            if(val instanceof CompoundTag compound) apply(compound, child);
            else if(val instanceof ListTag list){
                if(child.contains("SpliceStart")){
                    int start = child.getInt("SpliceStart");
                    int count = child.getInt("SpliceDelete");
                    if(start < 0 || count < 0 || start > list.size() || count > list.size() - start){
                        throw new IllegalArgumentException("Invalid list delta");
                    }
                    for(int idx = 0; idx < count; idx++) list.remove(start);
                    Tag inserted = child.get("SpliceValues");
                    if(!(inserted instanceof ListTag entries)) throw new IllegalArgumentException("Missing list delta values");
                    for(int idx = 0; idx < entries.size(); idx++) list.add(start + idx, entries.get(idx).copy());
                    continue;
                }
                CompoundTag entries = child.getCompound("Entries");
                for(String idx : entries.getAllKeys()){
                    int index = Integer.parseInt(idx);
                    CompoundTag entry = entries.getCompound(idx);
                    if(entry.contains("Value")) list.set(index, entry.get("Value").copy());
                    else if(list.get(index) instanceof CompoundTag compound) apply(compound, entry);
                }
            }else throw new IllegalArgumentException("Delta source type changed");
        }
    }

    private static CompoundTag difference(CompoundTag prev, CompoundTag next){
        CompoundTag patch = new CompoundTag();
        CompoundTag changed = new CompoundTag();
        CompoundTag children = new CompoundTag();
        ListTag removed = new ListTag();
        for(String key : prev.getAllKeys()) if(!next.contains(key)) removed.add(StringTag.valueOf(key));
        for(String key : next.getAllKeys()){
            Tag before = prev.get(key);
            Tag after = next.get(key);
            if(Objects.equals(before, after)) continue;
            if(before instanceof CompoundTag first && after instanceof CompoundTag second){
                children.put(key, difference(first, second));
            }else if(before instanceof ListTag first && after instanceof ListTag second
                    && first.size() == second.size() && first.getElementType() == second.getElementType()){
                CompoundTag entries = new CompoundTag();
                for(int idx = 0; idx < second.size(); idx++){
                    Tag old = first.get(idx);
                    Tag val = second.get(idx);
                    if(Objects.equals(old, val)) continue;
                    CompoundTag entry;
                    if(old instanceof CompoundTag a && val instanceof CompoundTag b) entry = difference(a, b);
                    else{
                        entry = new CompoundTag();
                        entry.put("Value", val.copy());
                    }
                    entries.put(Integer.toString(idx), entry);
                }
                CompoundTag child = new CompoundTag();
                child.put("Entries", entries);
                children.put(key, child);
            }else if(before instanceof ListTag first && after instanceof ListTag second){
                int start = 0;
                int common = Math.min(first.size(), second.size());
                while(start < common && Objects.equals(first.get(start), second.get(start))) start++;
                int suffix = 0;
                while(suffix < common - start && Objects.equals(first.get(first.size() - suffix - 1),
                        second.get(second.size() - suffix - 1))) suffix++;
                CompoundTag child = new CompoundTag();
                child.putInt("SpliceStart", start);
                child.putInt("SpliceDelete", first.size() - start - suffix);
                ListTag inserted = new ListTag();
                for(int idx = start; idx < second.size() - suffix; idx++) inserted.add(second.get(idx).copy());
                child.put("SpliceValues", inserted);
                children.put(key, child);
            }else changed.put(key, after.copy());
        }
        if(!changed.isEmpty()) patch.put("Values", changed);
        if(!children.isEmpty()) patch.put("Children", children);
        if(!removed.isEmpty()) patch.put("Removed", removed);
        return patch;
    }
}
