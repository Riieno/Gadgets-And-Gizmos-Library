package com.rieno.gadgetsandgizmos.lib.graph;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import java.util.LinkedHashMap;
import java.util.Map;

// Keep each authored HUD control's execution and value outputs together
public final class HudInteractionPorts{
    private HudInteractionPorts(){}

    public static void synchronize(CompoundTag data){
        CompoundTag sides = data.getCompound("PortSections");
        CompoundTag sections = sides.getCompound("Outputs");
        for(String id : java.util.Set.copyOf(sections.getAllKeys())){
            if(id.startsWith("hud:")) sections.remove(id);
        }
        CompoundTag labels = data.getCompound("OutputLabels");
        ListTag order = new ListTag();
        for(Tag raw : data.getList("WidgetElements", Tag.TAG_COMPOUND)){
            CompoundTag elm = (CompoundTag)raw;
            String exec = elm.getString("ExecPort");
            String value = elm.getString("ValuePort");
            if(exec.isBlank() || value.isBlank()) continue;
            String id = "hud:" + elm.getString("InteractionId");
            String label = elm.getString("Text");
            if(label.isBlank()) label = elm.getString("Type");
            CompoundTag section = new CompoundTag();
            section.putString("Label", label);
            ListTag ports = new ListTag();
            ports.add(StringTag.valueOf(exec)); ports.add(StringTag.valueOf(value));
            section.put("Ports", ports); sections.put(id, section);
            order.add(StringTag.valueOf(exec)); order.add(StringTag.valueOf(value));
            labels.putString(exec, "Exec"); labels.putString(value, "Value");
        }
        sides.put("Outputs", sections); data.put("PortSections", sides);
        data.put("OutputLabels", labels); data.put("HudOutputOrder", order);
    }

    public static Map<String, String> ordered(CompoundTag data, Map<String, String> ports){
        Map<String, String> result = new LinkedHashMap<>();
        for(Tag raw : data.getList("HudOutputOrder", Tag.TAG_STRING)){
            String key = raw.getAsString();
            if(ports.containsKey(key)) result.put(key, ports.get(key));
        }
        ports.forEach(result::putIfAbsent);
        return result;
    }

    public static boolean changed(CompoundTag previous, CompoundTag next){
        return !java.util.Objects.equals(previous, next);
    }
}
