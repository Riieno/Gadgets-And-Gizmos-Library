package com.rieno.gadgetsandgizmos.lib.graph;

import com.rieno.gadgetsandgizmos.lib.display.WidgetCanvasProjection;
import com.rieno.gadgetsandgizmos.lib.graph.edit.GraphDataValue;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.function.UnaryOperator;
import java.util.Map;

// Carry a detached widget canvas and its resolved port values through graph wires
public final class GraphWidgetValue{
    public static final String TYPE = "widget";
    public static final String VALUES = "PortValues";
    public static final String APPEARANCE = "CanvasAppearance";

    private GraphWidgetValue(){}

    // Identify a generated canvas separately from controls that retain submitted values
    public static boolean isGeneratedOutput(Map<String, String> outputs, String port){
        return "widget".equals(port) && TYPE.equals(outputs.get(port));
    }

    // Retain native tag types and nested image assets without exposing mutable node data
    public static GraphValue of(CompoundTag canvas){
        return new GraphValue(TYPE, GraphDataNbt.fromCompound(canvas));
    }

    // Resolve authored elements once while retaining only their canvas and required host values
    public static GraphValue capture(CompoundTag data, CompoundTag values, UnaryOperator<CompoundTag> resolve){
        return capture(data, values, false, resolve);
    }

    // Match surfaces that constrain elements to the authored canvas
    public static GraphValue capture(CompoundTag data, CompoundTag values, boolean constrain, UnaryOperator<CompoundTag> resolve){
        CompoundTag canvas = new CompoundTag();
        canvas.putInt("WidgetWidth", WidgetCanvasProjection.canvasWidth(data));
        canvas.putInt("WidgetHeight", WidgetCanvasProjection.canvasHeight(data));
        canvas.put(VALUES, values.copy());
        canvas.putBoolean("Constrain", constrain);
        if(data.contains(APPEARANCE, Tag.TAG_COMPOUND)) canvas.put(APPEARANCE, data.getCompound(APPEARANCE).copy());
        ListTag elements = new ListTag();
        for(Tag raw : data.getList("WidgetElements", Tag.TAG_COMPOUND)){
            CompoundTag elm = resolve.apply(((CompoundTag)raw).copy());
            if(elm != null) elements.add(elm.copy());
        }
        canvas.put("WidgetElements", elements);
        return of(canvas);
    }

    // Return an independent canvas for a renderer or a host's native value transport
    public static CompoundTag payload(GraphValue val){
        return val != null && TYPE.equals(val.type()) && val.value() instanceof GraphDataValue.CompoundValue data
                ? GraphDataNbt.toCompound(data) : new CompoundTag();
    }

    // Describe a widget without printing its bindings or embedded image bytes
    public static String summary(GraphValue val){
        CompoundTag canvas = payload(val);
        int count = canvas.getList("WidgetElements", Tag.TAG_COMPOUND).size();
        return WidgetCanvasProjection.canvasWidth(canvas) + " x " + WidgetCanvasProjection.canvasHeight(canvas)
                + " - " + count + (count == 1 ? " element" : " elements");
    }
}
