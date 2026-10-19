package com.rieno.gadgetsandgizmos.lib.graph;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;

import java.util.Map;

// Export editor schemas without depending on a host's node factory or transport
public final class GraphWebCatalog{
    private GraphWebCatalog(){}

    public static JsonObject node(GraphNodeDefinition definition, String name, String categoryName, int color,
                                  CompoundTag defaults, Map<String, String> inputs, Map<String, String> outputs){
        JsonObject node = new JsonObject();
        node.addProperty("id", definition.id());
        node.addProperty("name", name);
        node.addProperty("category", definition.category());
        node.addProperty("categoryName", categoryName);
        node.addProperty("color", String.format(java.util.Locale.ROOT, "#%06X", color & 0xFFFFFF));
        node.addProperty("stateful", definition.stateful());
        node.add("baseInputs", ports(definition.inputs()));
        node.add("baseOutputs", ports(definition.outputs()));
        node.add("inputs", ports(inputs));
        node.add("outputs", ports(outputs));
        CompoundTag data = defaults.copy();
        GraphPortOrder.write(data, GraphPortOrder.INPUT_ORDER, inputs.keySet());
        GraphPortOrder.write(data, GraphPortOrder.OUTPUT_ORDER, outputs.keySet());
        node.add("defaultData", NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, data));
        JsonObject properties = new JsonObject();
        for(String key : data.getAllKeys()){
            var tag = data.get(key);
            if(!(tag instanceof net.minecraft.nbt.StringTag) && !(tag instanceof net.minecraft.nbt.NumericTag)) continue;
            JsonObject property = new JsonObject();
            property.addProperty("type", tag instanceof net.minecraft.nbt.ByteTag ? "boolean"
                    : tag instanceof net.minecraft.nbt.NumericTag ? "number" : "string");
            properties.add(key, property);
        }
        node.add("properties", properties);
        return node;
    }

    public static JsonObject ports(Map<String, String> ports){
        JsonObject json = new JsonObject();
        ports.forEach(json::addProperty);
        return json;
    }
}
