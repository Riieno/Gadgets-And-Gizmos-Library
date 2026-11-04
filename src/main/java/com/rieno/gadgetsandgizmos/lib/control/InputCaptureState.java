package com.rieno.gadgetsandgizmos.lib.control;

import net.minecraft.nbt.CompoundTag;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

// Share input capture between independent sources without releasing another source's request
public final class InputCaptureState{
    private Map<String, String> requests = Map.of();
    private Set<String> inputs = Set.of();

    public Map<String, String> requests(){ return requests; }
    public Set<String> inputs(){ return inputs; }
    public boolean captures(String input){ return inputs.contains(input); }
    public boolean requestedBy(String source){ return requests.containsKey(source); }

    // Replace the complete request snapshot on the owning game thread
    public boolean replace(Map<String, String> val){
        if(requests.equals(val)) return false;
        requests = Map.copyOf(val);
        inputs = Set.copyOf(requests.values());
        return true;
    }

    public CompoundTag write(){
        var tag = new CompoundTag();
        requests.forEach(tag::putString);
        return tag;
    }

    public boolean read(CompoundTag tag){
        Map<String, String> val = new LinkedHashMap<>();
        for(String key : tag.getAllKeys()){
            String input = tag.getString(key);
            if(!key.isBlank() && !input.isBlank()) val.put(key, input);
        }
        return replace(val);
    }
}
