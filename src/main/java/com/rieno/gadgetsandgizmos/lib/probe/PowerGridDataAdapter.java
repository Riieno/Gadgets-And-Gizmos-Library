package com.rieno.gadgetsandgizmos.lib.probe;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS                                                        
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import com.rieno.gadgetsandgizmos.lib.util.ReflectionMethods;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Read Power Grid circuits and control their existing settings without a mandatory dependency
public final class PowerGridDataAdapter implements BlockEntityDataAdapter<BlockEntity>{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           CONSTANTS                                                       
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final String PREFIX = "org.patryk3211.powergrid.";
    private static final Map<String, String> READERS = Map.ofEntries(
            Map.entry("energy", "getEnergy"), Map.entry("capacity", "getCapacity"),
            Map.entry("charge", "getCharge"), Map.entry("generated_speed", "getGeneratedSpeed"),
            Map.entry("torque", "torque"), Map.entry("field_strength", "getFieldStrength"),
            Map.entry("ratio", "getRatio"), Map.entry("signal", "getSignal"),
            Map.entry("redstone_output", "getRedstoneOutput"), Map.entry("load", "getLoad"),
            Map.entry("coil_count", "getCoilCount"), Map.entry("winding_current", "windingCurrent"),
            Map.entry("emf_voltage", "emfVoltage"), Map.entry("volume", "getVolume"), Map.entry("pitch", "getPitch"),
            Map.entry("power_level", "getPowerLevel"), Map.entry("pulses", "pulses"),
            Map.entry("sparking", "isSparking"), Map.entry("closed", "isClosed"),
            Map.entry("primary_connected", "hasPrimary"), Map.entry("secondary_connected", "hasSecondary"),
            Map.entry("coupling_factor", "couplingFactor"), Map.entry("core_al", "coreAl"),
            Map.entry("current", "getCurrent"), Map.entry("power", "getPower"),
            Map.entry("output_voltage", "outputVoltage"), Map.entry("output_resistance", "outputResistance"),
            Map.entry("input_throttle", "inputThrottling"), Map.entry("stored_fe", "storedEnergy"),
            Map.entry("speed", "getSpeed"), Map.entry("angular_speed", "getAngularSpeed"), Map.entry("temperature", "temperature"));
    private static final ClassValue<List<Field>> FIELDS = new ClassValue<>(){
        @Override protected List<Field> computeValue(Class<?> type){
            List<Field> fields = new ArrayList<>();
            for(Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()){
                for(Field field : current.getDeclaredFields()){
                    if(!Modifier.isStatic(field.getModifiers()) && field.trySetAccessible()) fields.add(field);
                }
            }
            fields.sort(java.util.Comparator.comparing(Field::getName));
            return List.copyOf(fields);
        }
    };

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           FUNCTIONS                                                       
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    @Override public Class<BlockEntity> targetType(){ return BlockEntity.class; }
    @Override public boolean isSupplemental(){ return true; }
    @Override public boolean supports(BlockEntity target){ return target != null && target.getClass().getName().startsWith(PREFIX); }

    @Override public long schemaRevision(BlockEntity target){ return ports(target).hashCode(); }

    @Override public List<BlockEntityDataPort> ports(BlockEntity target){
        Map<String, BlockEntityDataPort> ports = new LinkedHashMap<>();
        Object electric = part(target, "ElectricBehaviour");
        if(electric != null){
            add(ports, "terminal_count", "number", false); add(ports, "terminals", "list", false);
            add(ports, "voltage", "number", false); add(ports, "network_paused", "boolean", false);
            List<?> terminals = list(invoke(electric, "getExternalNodes"));
            for(int idx = 0; idx < terminals.size(); idx++){
                add(ports, "terminal_" + (idx + 1) + "_voltage", "number", false);
                add(ports, "terminal_" + (idx + 1) + "_current", "number", false);
            }
        }
        if(part(target, "ThermalBehaviour") != null){
            add(ports, "temperature", "number", false); add(ports, "overheated", "boolean", false);
        }
        wires(target).forEach((name, wire) -> {
            for(String field : List.of("voltage", "current", "power", "internal_power", "resistance"))
                add(ports, "wire_" + name + "_" + field, "number", false);
        });
        READERS.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            var method = ReflectionMethods.find(target.getClass(), entry.getValue(), 0);
            if(method != null) add(ports, entry.getKey(), method.getReturnType() == boolean.class ? "boolean" : "number", false);
        });
        if(part(target, "RotorBehaviour") != null){
            for(String id : List.of("rotor_speed", "rotor_angle", "rotor_inertia")) add(ports, id, "number", false);
        }
        if(part(target, "CustomDisplayBehaviour") != null){
            add(ports, "display_equation", "string", true); add(ports, "display_unit", "string", true);
            add(ports, "display_prefixes", "boolean", true);
        }
        if(type(target, "RedstoneConverterBlockEntity")){
            add(ports, "signal_override", "number", true); add(ports, "signal_override_enabled", "boolean", true);
        }
        if(type(target, "GaugeBlockEntity")){
            add(ports, "reading", "number", false); add(ports, "range", "number", false);
        }
        if(type(target, "ResistorBlockEntity")) add(ports, "resistance", "number", true);
        if(type(target, "CreativeSourceBlockEntity")){
            add(ports, "source_value", "number", true); add(ports, "current_source", "boolean", false);
            if(!Boolean.TRUE.equals(invoke(target, "isCurrentSource"))){
                for(String id : List.of("ac_amplitude", "ac_frequency", "dc_offset")) add(ports, id, "number", true);
            }
        }
        if(type(target, "SwitchBlockEntity")){
            add(ports, "switch_state", "boolean", true); add(ports, "normally_closed", "boolean", true);
        }
        if(type(target, "EnergyMeterBlockEntity")) add(ports, "precise", "boolean", true);
        if(type(target, "CarbonPileCoilBlockEntity")) add(ports, "trim", "number", true);
        if(type(target, "ModularDisplayBlockEntity")){
            for(int idx = 1; idx <= 4; idx++){
                add(ports, "slot_" + idx + "_index", "number", true);
                add(ports, "slot_" + idx + "_half_click", "boolean", true);
                ports.put("slot_" + idx + "_color", new BlockEntityDataPort("slot_" + idx + "_color", "string",
                        BlockEntityDataPort.Access.READ_WRITE, java.util.Arrays.stream(net.minecraft.world.item.DyeColor.values()).map(net.minecraft.world.item.DyeColor::getName).toList()));
            }
        }
        return List.copyOf(ports.values());
    }

    @Override public GraphValue read(BlockEntity target, String port){
        Object electric = part(target, "ElectricBehaviour");
        List<?> terminals = list(invoke(electric, "getExternalNodes"));
        if("terminal_count".equals(port)) return GraphValue.number(terminals.size());
        if("terminals".equals(port)) return GraphValue.list(terminals.stream().map(node -> Map.of(
                "voltage", number(invoke(node, "getVoltage")), "current", number(invoke(node, "getCurrent")))).toList());
        if("voltage".equals(port)) return GraphValue.number(terminals.isEmpty() ? 0 : number(invoke(terminals.getFirst(), "getVoltage"))
                - (terminals.size() > 1 ? number(invoke(terminals.get(1), "getVoltage")) : 0));
        if(port.startsWith("rotor_")){
            String method = switch(port){ case "rotor_speed" -> "getAngularVelocity"; case "rotor_angle" -> "getAngle"; default -> "getInertia"; };
            return GraphValue.number(number(invoke(part(target, "RotorBehaviour"), method)));
        }
        if("display_equation".equals(port)) return GraphValue.string(String.valueOf(field(part(target, "CustomDisplayBehaviour"), "equationStr")));
        if("display_prefixes".equals(port)) return GraphValue.bool(Boolean.TRUE.equals(field(part(target, "CustomDisplayBehaviour"), "prefixes")));
        if("display_unit".equals(port)){
            Object format = part(target, "CustomDisplayBehaviour"), unit = field(format, "unit");
            return GraphValue.string(unit instanceof Enum<?> val ? val.name().toLowerCase(java.util.Locale.ROOT) : String.valueOf(field(format, "unitStr")));
        }
        if("signal_override".equals(port)) return GraphValue.number(number(field(target, "signalOverride")));
        if("signal_override_enabled".equals(port)) return GraphValue.bool(field(target, "signalOverride") != null);
        if("network_paused".equals(port)) return GraphValue.bool(Boolean.TRUE.equals(invoke(electric, "isPaused")));
        if(port.startsWith("terminal_")){
            String[] parts = port.split("_");
            if(parts.length == 3){
                try{
                    int idx = Integer.parseInt(parts[1]) - 1;
                    return GraphValue.number(idx < 0 || idx >= terminals.size() ? 0 : number(invoke(terminals.get(idx), "current".equals(parts[2]) ? "getCurrent" : "getVoltage")));
                }catch(NumberFormatException ignored){}
            }
        }
        if("temperature".equals(port)){
            Object thermal = part(target, "ThermalBehaviour");
            return GraphValue.number(number(thermal == null ? invoke(target, "temperature") : invoke(thermal, "getTemperature")));
        }
        if("overheated".equals(port)) return GraphValue.bool(Boolean.TRUE.equals(invoke(part(target, "ThermalBehaviour"), "isOverheated")));
        for(var entry : wires(target).entrySet()){
            String prefix = "wire_" + entry.getKey() + "_";
            if(!port.startsWith(prefix)) continue;
            String method = switch(port.substring(prefix.length())){
                case "voltage" -> "potentialDifference"; case "current" -> "current"; case "power" -> "power";
                case "internal_power" -> "internalPower"; case "resistance" -> "getResistance"; default -> "";
            };
            if("getResistance".equals(method) && ReflectionMethods.find(entry.getValue().getClass(), method, 0) == null){
                double conductance = number(invoke(entry.getValue(), "conductance"));
                return GraphValue.number(conductance > 0 ? 1 / conductance : 0);
            }
            if(!method.isEmpty()) return GraphValue.number(number(invoke(entry.getValue(), method)));
        }
        String method = READERS.get(port);
        if(method != null){
            Object val = invoke(target, method); return val instanceof Boolean bool ? GraphValue.bool(bool) : GraphValue.number(number(val));
        }
        return switch(port){
            case "reading", "source_value", "resistance" -> GraphValue.number(number(invoke(target, "getValue")));
            case "range" -> GraphValue.number(number(invoke(target, "getMaxValue")));
            case "current_source" -> GraphValue.bool(Boolean.TRUE.equals(invoke(target, "isCurrentSource")));
            case "switch_state" -> GraphValue.bool(Boolean.TRUE.equals(field(target, "switchState")));
            case "normally_closed" -> GraphValue.bool(Boolean.TRUE.equals(invoke(target, "isNormallyClosed")));
            case "precise" -> GraphValue.bool(Boolean.TRUE.equals(field(target, "measurementPrecision")));
            case "trim" -> GraphValue.number(number(invoke(target, "getTrim")));
            case "ac_amplitude" -> GraphValue.number(number(field(target, "amplitude")));
            case "ac_frequency" -> GraphValue.number(number(field(target, "frequency")));
            case "dc_offset" -> GraphValue.number(number(field(target, "dc")));
            default -> slotValue(target, port);
        };
    }

    @Override public boolean write(BlockEntity target, String port, GraphValue value){
        if(!supports(target) || target.isRemoved() || target.getLevel() == null || target.getLevel().isClientSide || value == null) return false;
        var control = ports(target).stream().filter(entry -> entry.id().equals(port) && entry.access().canWrite()).findFirst().orElse(null);
        if(control == null) return false;
        double val = value.asNumber();
        if("number".equals(control.type()) && (!Double.isFinite(val) || Math.abs(val) > Float.MAX_VALUE)) return false;
        boolean changed = switch(port){
            case "display_equation", "display_unit", "display_prefixes" -> writeFormat(target, port, value);
            case "signal_override" -> setField(target, "signalOverride", (float)Math.clamp(val, 0, 1));
            case "signal_override_enabled" -> setField(target, "signalOverride", value.asBoolean()
                    ? (float)number(field(target, "signalOverride")) : null);
            case "resistance" -> setResistance(target, val);
            case "source_value" -> call(target, "setValue", new Class<?>[]{float.class}, (float)Math.clamp(val, -Float.MAX_VALUE, Float.MAX_VALUE));
            case "ac_amplitude", "ac_frequency", "dc_offset" -> call(target, "setValue", new Class<?>[]{float.class, float.class, float.class},
                    (float)("ac_amplitude".equals(port) ? Math.max(0, val) : number(field(target, "amplitude"))),
                    (float)("ac_frequency".equals(port) ? Math.max(0, val) : number(field(target, "frequency"))),
                    (float)("dc_offset".equals(port) ? val : number(field(target, "dc"))));
            case "switch_state" -> call(target, "setState", new Class<?>[]{boolean.class}, value.asBoolean());
            case "normally_closed" -> setNormallyClosed(target, value.asBoolean());
            case "precise" -> call(target, "setPrecise", new Class<?>[]{boolean.class}, value.asBoolean());
            case "trim" -> call(target, "setTrim", new Class<?>[]{float.class}, (float)Math.clamp(val, 0.5, 1.5));
            default -> writeSlot(target, port, value);
        };
        if(changed){
            target.setChanged();
            if(target instanceof SmartBlockEntity smart) smart.notifyUpdate();
        }
        return changed;
    }

    @Override public boolean writeAll(BlockEntity target, Map<String, GraphValue> values){
        boolean changed = false;
        if(values.containsKey("source_value")) changed |= write(target, "source_value", values.get("source_value"));
        for(var entry : values.entrySet()){
            if(!"source_value".equals(entry.getKey()) && !"signal_override_enabled".equals(entry.getKey()))
                changed |= write(target, entry.getKey(), entry.getValue());
        }
        if(values.containsKey("signal_override_enabled")) changed |= write(target, "signal_override_enabled", values.get("signal_override_enabled"));
        return changed;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            HELPERS                                                        
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static boolean writeFormat(BlockEntity target, String port, GraphValue value){
        Object format = part(target, "CustomDisplayBehaviour");
        if(format == null) return false;
        var method = ReflectionMethods.find(format.getClass(), "set", 4);
        if(method == null) return false;
        Object unit = field(format, "unit"), unitText = field(format, "unitStr");
        String equation = String.valueOf(field(format, "equationStr"));
        boolean prefixes = Boolean.TRUE.equals(field(format, "prefixes"));
        if("display_equation".equals(port)) equation = value.asString();
        if(equation.length() > 256) return false;
        if("display_prefixes".equals(port)) prefixes = value.asBoolean();
        if("display_unit".equals(port)){
            unitText = value.asString(); unit = null;
            if(value.asString().length() > 64) return false;
            for(Object option : method.getParameterTypes()[1].getEnumConstants()){
                if(((Enum<?>)option).name().equalsIgnoreCase(value.asString())){ unit = option; unitText = null; break; }
            }
        }
        try{
            Class<?> expression = Class.forName(PREFIX + "electricity.info.customdisplay.Expression", false, format.getClass().getClassLoader());
            var parser = ReflectionMethods.find(expression, "tryParse", String.class);
            if(parser == null || !(parser.invoke(null, equation) instanceof java.util.Optional<?> parsed) || parsed.isEmpty()) return false;
            method.invoke(format, equation, unit, unitText, prefixes);
            return true;
        }catch(ReflectiveOperationException | RuntimeException err){ return false; }
    }

    private static boolean setField(Object target, String name, Object value){
        for(Field field : FIELDS.get(target.getClass())){
            if(!field.getName().equals(name)) continue;
            try{ field.set(target, value); return true; }catch(IllegalAccessException | IllegalArgumentException err){ return false; }
        }
        return false;
    }

    private static boolean setNormallyClosed(BlockEntity target, boolean val){
        boolean state = Boolean.TRUE.equals(field(target, "switchState"));
        return call(target, "setNormallyClosed", new Class<?>[]{boolean.class}, val)
                && call(target, "setState", new Class<?>[]{boolean.class}, state);
    }

    private static boolean setResistance(BlockEntity target, double value){
        var behaviour = ScrollValueDataAdapter.fieldBehaviours(target).get("value");
        if(behaviour == null || !Double.isFinite(value) || value <= 0) return false;
        int min = (int)number(field(behaviour, "min")), max = (int)number(field(behaviour, "max"));
        if(max - (long)min > 10000) return false;
        int offset = (int)number(field(behaviour, "minOffset"));
        int best = min; double distance = Double.POSITIVE_INFINITY;
        for(int idx = min; idx <= max; idx++){
            double resistance = (idx % 9 + 1) * Math.pow(10, idx / 9 - offset);
            double next = Math.abs(Math.log(value / resistance));
            if(next < distance){ best = idx; distance = next; }
        }
        behaviour.setValue(best);
        return true;
    }

    private static GraphValue slotValue(BlockEntity target, String port){
        if(!port.matches("slot_[1-4]_(index|half_click|color)")) return null;
        Object slot = invoke(target, "getSlot", new Class<?>[]{int.class}, port.charAt(5) - '1');
        if(port.endsWith("index")) return GraphValue.number(number(invoke(slot, "getIndex")));
        if(port.endsWith("half_click")) return GraphValue.bool(Boolean.TRUE.equals(invoke(slot, "getHalfClick")));
        Object color = invoke(invoke(slot, "getModule"), "getColor");
        return GraphValue.string(color instanceof net.minecraft.world.item.DyeColor dye ? dye.getName() : "white");
    }

    private static boolean writeSlot(BlockEntity target, String port, GraphValue value){
        if(!port.matches("slot_[1-4]_(index|half_click|color)")) return false;
        int idx = port.charAt(5) - '1';
        if(port.endsWith("index")){
            Object slot = invoke(target, "getSlot", new Class<?>[]{int.class}, idx);
            int count = (int)number(invoke(invoke(slot, "getModule"), "getDisplayTextureCharacterCount"));
            if(count <= 0) return false;
            return call(target, "setIndex", new Class<?>[]{int.class, int.class}, idx, (int)Math.clamp(Math.round(value.asNumber()), 0, count - 1));
        }
        if(port.endsWith("half_click")) return call(target, "setHalfClick", new Class<?>[]{int.class, boolean.class}, idx, value.asBoolean());
        for(var color : net.minecraft.world.item.DyeColor.values()){
            if(color.getName().equalsIgnoreCase(value.asString())) return call(target, "setColor", new Class<?>[]{int.class, net.minecraft.world.item.DyeColor.class}, idx, color);
        }
        return false;
    }

    private static Object part(BlockEntity target, String type){
        for(Field field : FIELDS.get(target.getClass())){
            if(!isClass(field.getType(), type)) continue;
            Object value = get(field, target); if(value != null) return value;
        }
        return null;
    }

    private static Map<String, Object> wires(BlockEntity target){
        Map<String, Object> wires = new LinkedHashMap<>();
        for(Field field : FIELDS.get(target.getClass())){
            Class<?> type = field.getType();
            if(isClass(type, "AbstractElectricWire")){
                Object val = get(field, target); if(val != null) wires.put(snake(field.getName()), val);
            }else if(type.isArray() && isClass(type.getComponentType(), "AbstractElectricWire")){
                Object val = get(field, target); if(val == null) continue;
                for(int idx = 0; idx < Array.getLength(val); idx++){
                    Object wire = Array.get(val, idx); if(wire != null) wires.put(snake(field.getName()) + "_" + (idx + 1), wire);
                }
            }
        }
        return wires;
    }

    private static String snake(String name){ return name.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(java.util.Locale.ROOT); }
    private static boolean type(Object value, String name){ return value != null && isClass(value.getClass(), name); }
    private static boolean isClass(Class<?> type, String name){
        for(Class<?> current = type; current != null; current = current.getSuperclass()){
            if(current.getName().startsWith(PREFIX) && current.getSimpleName().equals(name)) return true;
        }
        return false;
    }
    private static List<?> list(Object value){ return value instanceof List<?> list ? list : List.of(); }
    private static double number(Object value){ return value instanceof Number val && Double.isFinite(val.doubleValue()) ? val.doubleValue() : 0; }
    private static void add(Map<String, BlockEntityDataPort> ports, String id, String type, boolean writable){
        ports.put(id, writable ? BlockEntityDataPort.readWrite(id, type) : BlockEntityDataPort.readable(id, type));
    }
    private static Object field(Object target, String name){
        if(target == null) return null;
        for(Field field : FIELDS.get(target.getClass())) if(field.getName().equals(name)) return get(field, target);
        return null;
    }
    private static Object get(Field field, Object target){
        try{ return field.get(target); }catch(IllegalAccessException err){ return null; }
    }
    private static Object invoke(Object target, String name){ return invoke(target, name, new Class<?>[0]); }
    private static Object invoke(Object target, String name, Class<?>[] types, Object... args){
        if(target == null) return null;
        var method = ReflectionMethods.find(target.getClass(), name, types);
        try{ return method == null ? null : method.invoke(target, args); }catch(ReflectiveOperationException | RuntimeException err){ return null; }
    }
    private static boolean call(Object target, String name, Class<?>[] types, Object... args){
        var method = ReflectionMethods.find(target.getClass(), name, types);
        if(method == null) return false;
        try{ method.invoke(target, args); return true; }catch(ReflectiveOperationException | RuntimeException err){ return false; }
    }
}
