package com.rieno.gadgetsandgizmos.lib.graph.math;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.control.math.UnitConversions;
import com.rieno.gadgetsandgizmos.lib.control.math.ElectricalUnitConversions;
import com.rieno.gadgetsandgizmos.lib.power.powergrid.PowerGridUnits;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.rieno.gadgetsandgizmos.lib.control.math.UnitConversions.Category;
import com.rieno.gadgetsandgizmos.lib.graph.GraphNodeDefinition;
import com.rieno.gadgetsandgizmos.lib.graph.GraphPortOrder;
import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;

import java.util.List;
import java.util.Map;

// Share unit node execution and dependent editor choices between graph hosts
public final class UnitConversionNode{
    public static final String UNIT_TYPE = "unit_type";
    public static final String INPUT_UNIT = "input_unit";
    public static final String OUTPUT_UNIT = "output_unit";
    public static final String VOLTAGE = "voltage";
    public static final String DURATION = "duration";

    private UnitConversionNode(){}

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static MathGraphNodes.Node node(String namespace){
        if(namespace == null || !namespace.matches("[a-z0-9_.-]+")) throw new IllegalArgumentException("A mod namespace is required");
        return new MathGraphNodes.Node(new GraphNodeDefinition(namespace + ":convert_unit", "math",
                inputs(Category.POWER),
                GraphPortOrder.entries(Map.entry("value", "number"), Map.entry("valid", "boolean")), false),
                (ctx, in) -> execute(in), Map.of(UNIT_TYPE, GraphValue.string("time"), INPUT_UNIT, GraphValue.string("ticks"),
                        OUTPUT_UNIT, GraphValue.string("seconds"), "value", GraphValue.number(0),
                        VOLTAGE, GraphValue.number(1), DURATION, GraphValue.number(1)), "Convert Unit",
                "Convert Time, Distance, Speed, Weight or Fluid Volume using independent input and output unit selectors. "
                        + "Ticks use 20 ticks per second; blocks are 1 meter. Knots are speed, nautical miles are distance. "
                        + "Weight units measure mass. One bucket is 1000 mB or 1 liter; US and Imperial fluid units are separate. "
                        + "Power is available with PowerGrid. FE uses its configured bridge ratio; electrical conversions use Voltage (V) and Duration (s), both defaulting to 1. "
                        + "Invalid or incompatible units return Value = 0 and Valid = false.");
    }

    public static Map<String, GraphValue> execute(Map<String, GraphValue> in){
        boolean power = PowerGridUnits.available();
        boolean electrical = "power".equals(in.getOrDefault(UNIT_TYPE, GraphValue.string("time")).asString());
        return execute(in, power, power && electrical ? PowerGridUnits.fePerWatt() : Double.NaN);
    }

    public static Map<String, GraphValue> execute(Map<String, GraphValue> in, boolean powerAvailable, double fePerWatt){
        try{
            Category category = Category.fromId(in.getOrDefault(UNIT_TYPE, GraphValue.string("time")).asString());
            String inputId = in.getOrDefault(INPUT_UNIT, GraphValue.string(defaultInput(category))).asString();
            String outputId = in.getOrDefault(OUTPUT_UNIT, GraphValue.string(defaultOutput(category))).asString();
            var input = UnitConversions.unit(inputId);var output = UnitConversions.unit(outputId);
            GraphValue val = in.getOrDefault("value", GraphValue.number(0));
            if(input.category() != category || output.category() != category || !"number".equals(val.type())){
                throw new IllegalArgumentException("Unit inputs must match the selected category");
            }
            if(category == Category.POWER && !powerAvailable) throw new IllegalArgumentException("PowerGrid is unavailable");
            double res = category == Category.POWER ? ElectricalUnitConversions.convert(val.asNumber(), inputId, outputId, fePerWatt,
                    number(in, VOLTAGE), number(in, DURATION)) : UnitConversions.convert(val.asNumber(), input, output);
            return Map.of("value", GraphValue.number(res), "valid", GraphValue.bool(true));
        }catch(IllegalArgumentException err){
            return Map.of("value", GraphValue.number(0), "valid", GraphValue.bool(false));
        }
    }

    public static String defaultInput(Category category){ return UnitConversions.optionIds(category).getFirst(); }
    public static String defaultOutput(Category category){ return category == Category.POWER ? "watts" : UnitConversions.optionIds(category).get(1); }

    public static List<Category> categories(boolean powerAvailable){
        return java.util.Arrays.stream(Category.values()).filter(category -> category != Category.POWER || powerAvailable).toList();
    }

    public static Map<String, String> inputs(CompoundTag data){
        return inputs(category(data) == Category.POWER && PowerGridUnits.available() ? Category.POWER : Category.TIME);
    }

    public static Map<String, String> inputs(Category category){
        Map<String, String> res = new java.util.LinkedHashMap<>(GraphPortOrder.entries(Map.entry(UNIT_TYPE, "string"), Map.entry(INPUT_UNIT, "string"),
                Map.entry(OUTPUT_UNIT, "string"), Map.entry("value", "number")));
        if(category == Category.POWER){ res.put(VOLTAGE, "number"); res.put(DURATION, "number"); }
        return java.util.Collections.unmodifiableMap(res);
    }

    // Export the same dropdown choices and factors to browser graph hosts
    public static void addWebMetadata(JsonObject node){
        boolean power = PowerGridUnits.available();
        addWebMetadata(node, power, power ? PowerGridUnits.fePerWatt() : Double.NaN);
    }

    public static void addWebMetadata(JsonObject node, boolean powerAvailable, double fePerWatt){
        JsonObject categories = new JsonObject(), defaults = new JsonObject(), units = new JsonObject();
        for(Category category : categories(powerAvailable)){
            JsonArray choices = new JsonArray();UnitConversions.optionIds(category).forEach(choices::add);
            JsonObject options = new JsonObject();options.add(INPUT_UNIT, choices);options.add(OUTPUT_UNIT, choices.deepCopy());
            categories.add(category.id(), options);
            JsonObject fields = new JsonObject();
            fields.addProperty(INPUT_UNIT, defaultInput(category));fields.addProperty(OUTPUT_UNIT, defaultOutput(category));
            defaults.add(category.id(), fields);
            for(var unit : UnitConversions.units(category)){
                JsonObject value = new JsonObject();value.addProperty("category", category.id());value.addProperty("factor", unit.factor());
                if(category == Category.POWER){
                    double factor;
                    try{ factor = ElectricalUnitConversions.factor(unit.id(), fePerWatt); }
                    catch(IllegalArgumentException err){ factor = 0; }
                    value.addProperty("factor", factor);
                    var dimension = ElectricalUnitConversions.dimension(unit.id());
                    value.addProperty("voltageExponent", dimension.voltageExponent());
                    value.addProperty("durationExponent", dimension.durationExponent());
                }
                units.add(unit.id(), value);
            }
        }
        JsonObject groups = new JsonObject();groups.add(UNIT_TYPE, categories);node.add("dependentInputOptions", groups);
        JsonObject fallbacks = new JsonObject();fallbacks.add(UNIT_TYPE, defaults);node.add("dependentInputDefaults", fallbacks);
        node.add("unitConversions", units);
    }

    // Resolve choices from the current category even when saved dropdown metadata is absent
    public static List<String> inputOptions(CompoundTag data, String port){
        return inputOptions(data, port, PowerGridUnits.available());
    }

    public static List<String> inputOptions(CompoundTag data, String port, boolean powerAvailable){
        if(UNIT_TYPE.equals(port)) return categories(powerAvailable).stream().map(Category::id).toList();
        if(!INPUT_UNIT.equals(port) && !OUTPUT_UNIT.equals(port)) return List.of();
        if(category(data) == Category.POWER && !powerAvailable) return List.of();
        return UnitConversions.optionIds(category(data));
    }

    // Keep a valid selection when possible and replace incompatible choices after category changes
    public static void synchronize(CompoundTag data){
        synchronize(data, PowerGridUnits.available());
    }

    public static void synchronize(CompoundTag data, boolean powerAvailable){
        Category category = category(data);
        CompoundTag defaults = data.getCompound("Defaults");
        defaults.put(UNIT_TYPE, text(category.id()));
        List<String> choices = UnitConversions.optionIds(category);
        if(!choices.contains(selected(defaults, INPUT_UNIT))) defaults.put(INPUT_UNIT, text(defaultInput(category)));
        if(!choices.contains(selected(defaults, OUTPUT_UNIT))) defaults.put(OUTPUT_UNIT, text(defaultOutput(category)));
        for(String port : List.of(VOLTAGE, DURATION)) if(!defaults.contains(port)){
            CompoundTag entry = new CompoundTag(), payload = new CompoundTag();
            entry.putString("Type", "number");payload.putDouble("Value", 1);entry.put("Payload", payload);defaults.put(port, entry);
        }
        data.put("Defaults", defaults);
        CompoundTag options = data.getCompound("InputOptions");
        options.put(UNIT_TYPE, strings(inputOptions(data, UNIT_TYPE, powerAvailable)));
        List<String> available = category == Category.POWER && !powerAvailable ? List.of() : choices;
        options.put(INPUT_UNIT, strings(available));options.put(OUTPUT_UNIT, strings(available));
        data.put("InputOptions", options);
        CompoundTag labels = data.getCompound("InputOptionLabels");
        CompoundTag categories = new CompoundTag(), units = new CompoundTag();
        Category.optionIds().forEach(id -> categories.putString(id, UnitConversions.label(id)));
        for(Category type : Category.values()){
            UnitConversions.units(type).forEach(unit -> units.putString(unit.id(), unit.label()));
        }
        labels.put(UNIT_TYPE, categories);labels.put(INPUT_UNIT, units);labels.put(OUTPUT_UNIT, units.copy());
        data.put("InputOptionLabels", labels);
        CompoundTag inputs = data.getCompound("InputLabels");
        inputs.putString(UNIT_TYPE, "Unit Type");inputs.putString(INPUT_UNIT, "Input Unit");inputs.putString(OUTPUT_UNIT, "Output Unit");
        inputs.putString("value", "Value");
        inputs.putString(VOLTAGE, "Voltage (V)");inputs.putString(DURATION, "Duration (s)");
        data.put("InputLabels", inputs);
        CompoundTag outputs = data.getCompound("OutputLabels");
        outputs.putString("value", "Result");
        data.put("OutputLabels", outputs);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static Category category(CompoundTag data){
        try{ return Category.fromId(selected(data.getCompound("Defaults"), UNIT_TYPE)); }
        catch(IllegalArgumentException err){ return Category.TIME; }
    }

    private static double number(Map<String, GraphValue> in, String port){
        GraphValue val = in.getOrDefault(port, GraphValue.number(1));
        return "number".equals(val.type()) ? val.asNumber() : Double.NaN;
    }

    private static String selected(CompoundTag defaults, String port){
        return defaults.getCompound(port).getCompound("Payload").getString("Value");
    }

    private static CompoundTag text(String val){
        CompoundTag entry = new CompoundTag(), payload = new CompoundTag();
        entry.putString("Type", "string");payload.putString("Value", val);entry.put("Payload", payload);
        return entry;
    }

    private static ListTag strings(List<String> vals){
        ListTag res = new ListTag();vals.forEach(val -> res.add(StringTag.valueOf(val)));return res;
    }
}
