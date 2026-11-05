package com.rieno.gadgetsandgizmos.lib.display;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

// Transfer presentation definitions without graph topology or executable state
public final class PresentationLayout{
    // Keep the layout envelope independent of a consuming graph implementation
    private PresentationLayout(){}

    // Encode only the elements needed by one presentation surface
    public static CompoundTag write(List<Element> elements){
        ListTag rows = new ListTag();
        for(Element element : elements){
            CompoundTag row = new CompoundTag();
            row.putString("Id", element.id); row.putString("Type", element.type);
            row.putString("Label", element.label); row.put("Data", element.data.copy());
            rows.add(row);
        }
        CompoundTag res = new CompoundTag(); res.put("Elements", rows);
        return res;
    }

    // Return detached definitions without constructing or compiling a graph document
    public static List<Element> read(CompoundTag tag){
        List<Element> res = new ArrayList<>();
        for(Tag raw : tag.getList("Elements", Tag.TAG_COMPOUND)){
            CompoundTag row = (CompoundTag) raw;
            res.add(new Element(row.getString("Id"), row.getString("Type"), row.getString("Label"), row.getCompound("Data")));
        }
        return List.copyOf(res);
    }

    // Own one element's authored layout and static defaults
    public record Element(String id, String type, String label, CompoundTag data){
        // Detach authored data from the runtime that published it
        public Element{ data = data.copy(); }
        // Keep callers from mutating a retained definition
        @Override public CompoundTag data(){ return data.copy(); }
    }
}
