package com.rieno.gadgetsandgizmos.lib.graph;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

// Describe ordered, collapsible node sections independently of any editor implementation
public final class GraphNodePresentationRegistry{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Map<String, Presentation> ENTRIES = new LinkedHashMap<>();

    private GraphNodePresentationRegistry(){}

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Register a presentation without replacing another integration
    public static synchronized void register(String type, Presentation presentation){
        if(type == null || !type.contains(":") || net.minecraft.resources.ResourceLocation.tryParse(type) == null){
            throw new IllegalArgumentException("A node presentation requires an owning namespace");
        }
        if(ENTRIES.putIfAbsent(Objects.requireNonNull(type), Objects.requireNonNull(presentation)) != null){
            throw new IllegalStateException("Duplicate node presentation: " + type);
        }
    }

    // Initialize saved defaults and section metadata for a newly created node
    public static synchronized void initialize(String type, CompoundTag data){
        Presentation presentation = ENTRIES.get(type);
        if(presentation == null) return;
        CompoundTag defaults = data.getCompound("Defaults");
        presentation.defaults().forEach((port, val) -> defaults.put(port, encode(val)));
        data.put("Defaults", defaults);
        CompoundTag sides = new CompoundTag();
        CompoundTag inputs = new CompoundTag();
        ListTag order = new ListTag();
        for(Section section : presentation.sections()){
            CompoundTag encoded = new CompoundTag();
            encoded.putString("Label", section.label());
            ListTag ports = new ListTag();
            section.ports().forEach(port -> { ports.add(StringTag.valueOf(port)); order.add(StringTag.valueOf(port)); });
            encoded.put("Ports", ports);
            inputs.put(section.id(), encoded);
        }
        sides.put("Inputs", inputs);
        data.put("PortSections", sides);
        data.put("InputOrder", order);
        CompoundTag options = data.getCompound("InputOptions");
        presentation.options().forEach((port, vals) -> {
            ListTag values = new ListTag();
            vals.forEach(val -> values.add(StringTag.valueOf(val)));
            options.put(port, values);
        });
        if(!options.isEmpty()) data.put("InputOptions", options);
    }

    // Remove retired input metadata without changing remaining settings or section state
    public static void removeInputs(CompoundTag data, Set<String> ports){
        for(String key : List.of("Defaults", "DynamicInputs", "InputOptions")){
            CompoundTag vals = data.getCompound(key);
            ports.forEach(vals::remove);
        }
        data.getList("InputOrder", 8).removeIf(val -> ports.contains(val.getAsString()));
        CompoundTag sections = data.getCompound("PortSections").getCompound("Inputs");
        for(String key : Set.copyOf(sections.getAllKeys())){
            ListTag vals = sections.getCompound(key).getList("Ports", 8);
            vals.removeIf(val -> ports.contains(val.getAsString()));
            if(vals.isEmpty()) sections.remove(key);
        }
    }

    // Encode the primitive default format shared by graph editors
    private static CompoundTag encode(GraphValue val){
        CompoundTag encoded = new CompoundTag();
        encoded.putString("Type", val.type());
        CompoundTag payload = new CompoundTag();
        switch(val.type()){
            case "boolean" -> payload.putBoolean("Value", val.asBoolean());
            case "number" -> payload.putDouble("Value", val.asNumber());
            case "string" -> payload.putString("Value", val.asString());
            case "list" -> {
                if(val.value() instanceof List<?> list){
                    for(int idx = 0; idx < list.size(); idx++) payload.put(Integer.toString(idx), encode(GraphValue.of(list.get(idx))));
                }
            }
            case "map" -> {
                if(val.value() instanceof Map<?, ?> map) map.forEach((key, entry) -> payload.put(key.toString(), encode(GraphValue.of(entry))));
            }
            default -> { }
        }
        encoded.put("Payload", payload);
        return encoded;
    }

    // Keep related ports adjacent so each collapsible section has one heading
    public static synchronized Map<String, String> orderedInputs(String type, Map<String, String> ports){
        Presentation presentation = ENTRIES.get(type);
        if(presentation == null) return ports;
        Map<String, String> ordered = new LinkedHashMap<>();
        for(Section section : presentation.sections()){
            for(String port : section.ports()){
                if(ports.containsKey(port)) ordered.put(port, ports.get(port));
            }
        }
        ports.forEach(ordered::putIfAbsent);
        return java.util.Collections.unmodifiableMap(ordered);
    }

    public record Presentation(Map<String, GraphValue> defaults, List<Section> sections, Map<String, List<String>> options){
        public Presentation{
            defaults = Map.copyOf(defaults);
            sections = List.copyOf(sections);
            Map<String, List<String>> copy = new LinkedHashMap<>();
            options.forEach((port, vals) -> copy.put(port, List.copyOf(vals)));
            options = Map.copyOf(copy);
        }
        public Presentation(Map<String, GraphValue> defaults, List<Section> sections){ this(defaults, sections, Map.of()); }
    }

    public record Section(String id, String label, List<String> ports){
        public Section{ Objects.requireNonNull(id); Objects.requireNonNull(label); ports = List.copyOf(ports); }
    }
}
