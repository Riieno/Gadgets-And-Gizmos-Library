package com.rieno.gadgetsandgizmos.lib.graph;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// Describe one port without evaluating its graph or retaining another port's values
public record GraphPortInspection(String label, String declaredType, boolean output, String source,
                                  GraphValue value, Preview preview){
    /*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        CONSTANTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    // Bound nested content before it reaches a tooltip or another presentation surface
    private static final int MAX_ROWS = 12;
    private static final int MAX_DEPTH = 3;
    private static final int MAX_TEXT = 240;

    /*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        FUNCTIONS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    // Infer rich content from its semantic type while allowing hosts to identify gradient maps
    public GraphPortInspection{
        label = label == null ? "" : label;
        declaredType = declaredType == null ? "any" : declaredType;
        source = source == null ? "" : source;
        if(preview == null || preview == Preview.NONE) preview = preview(declaredType, value);
        if(value == null) preview = Preview.NONE;
    }

    // Describe a port without requiring transport or sampling details in its presentation
    public GraphPortInspection(String label, String declaredType, boolean output, GraphValue value, Preview preview){
        this(label, declaredType, output, "", value, preview);
    }

    // Show the port's semantic type and resolve generic ports from their current value
    public String resolvedType(){
        if(!"any".equals(declaredType) && !declaredType.isBlank()) return declaredType;
        if(preview == Preview.COLOR) return "color";
        if(preview == Preview.IMAGE) return "image";
        if(preview == Preview.WIDGET) return GraphWidgetValue.TYPE;
        return value == null ? "any" : value.type();
    }

    // Prefer conventional result ports and otherwise retain the catalog's first data output
    public static String primaryOutput(Map<String, String> outputs){
        if(GraphWidgetValue.isGeneratedOutput(outputs, "widget")) return "widget";
        for(String name : List.of("value", "result", "output"))
            if(outputs.containsKey(name) && !"exec".equals(outputs.get(name))) return name;
        return outputs.entrySet().stream().filter(entry -> !"exec".equals(entry.getValue()))
                .map(Map.Entry::getKey).findFirst().orElse("");
    }

    // Format values using the declared port contract rather than its storage representation
    public GraphValue displayValue(){
        if(value == null) return null;
        return switch(resolvedType()){
            case "boolean" -> GraphValue.bool(value.value() instanceof Number num ? num.doubleValue() != 0 : value.asBoolean());
            case "string", "direction" -> new GraphValue(resolvedType(), value.asString());
            default -> value;
        };
    }

    // Identify media without mistaking ordinary numeric collections for gradients
    public static Preview preview(String type, GraphValue val){
        if(val == null) return Preview.NONE;
        if("color".equals(type) || "color".equals(val.type())) return Preview.COLOR;
        if("image".equals(type) || "image".equals(val.type())) return Preview.IMAGE;
        if(GraphWidgetValue.TYPE.equals(type) || GraphWidgetValue.TYPE.equals(val.type())) return Preview.WIDGET;
        if("gradient".equals(type) || "gradient".equals(val.type()) || isGradient(val)) return Preview.GRADIENT;
        return Preview.NONE;
    }

    // Recognize the library's gradient sample map after it passes through variables or generic ports
    private static boolean isGradient(GraphValue val){
        if(!(val.value() instanceof Map<?, ?> map) || map.size() != GradientGraphNode.RESOLUTION) return false;
        for(int idx = 0; idx < GradientGraphNode.RESOLUTION; idx++){
            Object entry = map.get(Integer.toString(idx));
            GraphValue sample = GraphValue.of(entry);
            if(!(sample.value() instanceof Number num)) return false;
            double col = num.doubleValue();
            if(col != Math.rint(col) || col < Integer.MIN_VALUE || col > 0xFFFFFFFFL || ((int)(long)col >>> 24) == 0) return false;
        }
        return true;
    }

    // Preserve full numeric precision and summarize structured values separately from their entries
    public String summary(){
        if(value == null) return "No value available";
        if(preview == Preview.COLOR){
            int col = color(value);
            return String.format(Locale.ROOT, "#%08X  R %d  G %d  B %d  A %d", col,
                    col >>> 16 & 255, col >>> 8 & 255, col & 255, col >>> 24);
        }
        if(preview == Preview.IMAGE) return shorten(GraphImageValue.source(value));
        if(preview == Preview.WIDGET) return GraphWidgetValue.summary(value);
        return text(displayValue());
    }

    // Return ordered collection rows with explicit depth and entry limits
    public List<Row> rows(){
        List<Row> rows = new ArrayList<>();
        if(value != null && preview != Preview.IMAGE) append(rows, value, "", 0);
        if(preview == Preview.GRADIENT) return rows.stream().map(row -> row.value() != null
                && row.value().value() instanceof Number ? new Row(row.path(), "color",
                String.format(Locale.ROOT, "#%08X", color(row.value())), row.value()) : row).toList();
        return List.copyOf(rows);
    }

    // Read signed ARGB numbers, unsigned ARGB numbers and common hex text without losing alpha
    public static int color(GraphValue val){
        if(val.value() instanceof Number num) return (int)num.longValue();
        String text = val.asString().strip();
        if(text.startsWith("#")) text = text.substring(1);
        else if(text.startsWith("0x") || text.startsWith("0X")) text = text.substring(2);
        try{
            long col = Long.parseUnsignedLong(text, 16);
            return (int)(text.length() <= 6 ? col | 0xFF000000L : col);
        }catch(NumberFormatException ignored){ return 0; }
    }

    // Read gradient samples in their authored order without copying image metadata into text rows
    public List<Integer> colors(){
        if(value == null || preview != Preview.GRADIENT) return List.of();
        if(value.value() instanceof Map<?, ?> map)
            return map.values().stream().limit(256).map(GraphValue::of).map(GraphPortInspection::color).toList();
        return value.entries().stream().limit(256).map(GraphPortInspection::color).toList();
    }

    // Flatten a bounded part of a collection while retaining each entry's path and type
    private static void append(List<Row> rows, GraphValue val, String path, int depth){
        if(depth >= MAX_DEPTH || rows.size() >= MAX_ROWS) return;
        if(val.value() instanceof Map<?, ?> map){
            int idx = 0;
            for(var entry : map.entrySet()){
                if(rows.size() >= MAX_ROWS - 1){ remaining(rows, map.size() - idx); break; }
                GraphValue child = GraphValue.of(entry.getValue());
                String key = path + shorten(String.valueOf(entry.getKey()));
                rows.add(new Row(key, child.type(), text(child), child));
                if(preview(child.type(), child) != Preview.IMAGE) append(rows, child, key + ".", depth + 1);
                idx++;
            }
        }else if(val.value() instanceof List<?> list){
            for(int idx = 0; idx < list.size(); idx++){
                if(rows.size() >= MAX_ROWS - 1){ remaining(rows, list.size() - idx); break; }
                GraphValue child = GraphValue.of(list.get(idx));
                String key = path + "[" + idx + "]";
                rows.add(new Row(key, child.type(), text(child), child));
                if(preview(child.type(), child) != Preview.IMAGE) append(rows, child, key + ".", depth + 1);
            }
        }
    }

    // Mark omitted entries instead of silently presenting a partial collection as complete
    private static void remaining(List<Row> rows, int count){
        if(count > 0 && rows.size() < MAX_ROWS) rows.add(new Row("…", "", count + " more entries", null));
    }

    // Format one value without serializing an entire nested payload
    private static String text(GraphValue val){
        if("image".equals(val.type())) return shorten(GraphImageValue.source(val));
        if(GraphWidgetValue.TYPE.equals(val.type())) return GraphWidgetValue.summary(val);
        if("color".equals(val.type())) return String.format(Locale.ROOT, "#%08X", color(val));
        if(val.value() instanceof Map<?, ?> map) return map.size() + " entries";
        if(val.value() instanceof List<?> list) return list.size() + " items";
        if(val.value() instanceof Number num) return num.toString();
        if("string".equals(val.type())) return '"' + shorten(val.asString()) + '"';
        return shorten(val.asString());
    }

    // Keep long strings and data URLs within a fixed inspection budget
    private static String shorten(String text){
        if(text.startsWith("data:image/")){
            int split = text.indexOf(',');
            return (split < 0 ? "Embedded image" : text.substring(0, Math.min(split, 80))) + " (embedded)";
        }
        return text.length() <= MAX_TEXT ? text : text.substring(0, MAX_TEXT) + "…";
    }

    // Select the visual preview independently of the serialized graph value type
    public enum Preview{ NONE, COLOR, IMAGE, GRADIENT, WIDGET }

    // Carry one bounded collection entry and its optional rich value
    public record Row(String path, String type, String text, GraphValue value){}
}
