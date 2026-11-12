package com.rieno.gadgetsandgizmos.lib.display;

import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

// Keep option labels separate from their original typed values
public final class DropdownOptions{
    public static final String PROPERTY = "Options";
    private DropdownOptions(){}

    public record Option(String key, String label, GraphValue value){}

    public static boolean accepts(String type){
        return "list".equals(type) || "map".equals(type);
    }

    // Preserve authored map order and zero-based list indices without flattening nested values
    public static List<Option> entries(GraphValue source){
        if(source == null || !accepts(source.type())) return List.of();
        List<Option> res = new ArrayList<>();
        if("map".equals(source.type()) && source.value() instanceof Map<?, ?> map){
            map.forEach((key, raw) -> res.add(new Option(String.valueOf(key), String.valueOf(key), GraphValue.of(raw))));
        }else if(source.value() instanceof List<?> list){
            for(int idx = 0; idx < list.size(); idx++){
                GraphValue val = GraphValue.of(list.get(idx));
                String key = Integer.toString(idx);
                res.add(new Option(key, accepts(val.type()) ? key : label(val), val));
            }
        }
        return List.copyOf(res);
    }

    public static Option find(List<Option> options, String key){
        return options.stream().filter(option -> Objects.equals(option.key(), key)).findFirst().orElse(null);
    }

    public static String selectedKey(List<Option> options, GraphValue value){
        return options.stream().filter(option -> Objects.equals(option.value(), value)).map(Option::key).findFirst().orElse(null);
    }

    public static String label(List<Option> options, GraphValue value, String fallback){
        String key = selectedKey(options, value);
        Option selected = key == null ? null : find(options, key);
        return selected == null ? fallback : selected.label();
    }

    private static String label(GraphValue val){
        if("number".equals(val.type())){
            double num = val.asNumber();
            return num == Math.rint(num) ? String.format(java.util.Locale.ROOT, "%.0f", num) : Double.toString(num);
        }
        return val.asString();
    }
}
