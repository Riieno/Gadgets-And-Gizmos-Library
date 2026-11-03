package com.rieno.gadgetsandgizmos.lib.nbt;

import net.minecraft.nbt.CompoundTag;
import java.util.ArrayList;
import java.util.List;

// Retain one current document and reversible differences for older revisions
public final class CompoundTagHistory{
    private final int capacity;
    private CompoundTag newest;
    private final List<CompoundTag> older = new ArrayList<>();

    public CompoundTagHistory(int capacity){
        if(capacity < 1) throw new IllegalArgumentException("History capacity must be positive");
        this.capacity = capacity;
    }

    public int size(){ return newest == null ? 0 : older.size() + 1; }

    public void push(CompoundTag tag){
        if(newest != null) older.addFirst(CompoundTagDelta.between(tag, newest));
        newest = tag.copy();
        while(size() > capacity) older.removeLast();
    }

    // Restore a saved history collection in newest-to-oldest iteration order
    public void append(CompoundTag tag){
        if(newest == null) newest = tag.copy();
        else older.add(CompoundTagDelta.between(get(size() - 1), tag));
        while(size() > capacity) older.removeLast();
    }

    // Materialise only the selected revision and keep retained payloads private
    public CompoundTag get(int idx){
        if(idx < 0 || idx >= size()) throw new IndexOutOfBoundsException(idx);
        CompoundTag res = newest.copy();
        for(int step = 0; step < idx; step++) CompoundTagDelta.apply(res, older.get(step));
        return res;
    }

    public void remove(int idx){
        if(idx < 0 || idx >= size()) throw new IndexOutOfBoundsException(idx);
        if(idx == 0){
            newest = size() == 1 ? null : get(1);
            if(!older.isEmpty()) older.removeFirst();
        }else if(idx == size() - 1){
            older.removeLast();
        }else{
            CompoundTag patch = CompoundTagDelta.between(get(idx - 1), get(idx + 1));
            older.set(idx - 1, patch);
            older.remove(idx);
        }
    }

    public void clear(){ newest = null; older.clear(); }
}
