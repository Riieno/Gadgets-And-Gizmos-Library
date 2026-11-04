package com.rieno.gadgetsandgizmos.lib.graph;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.time.ClockTime;
import com.rieno.gadgetsandgizmos.lib.time.TimeSource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Expose world and real time through a reusable node owned by its consuming mod
public final class TimeGraphNode{
    public static final Set<String> RETIRED_INPUTS = Set.of("offset");
    private static final Map<String, String> INPUTS = GraphPortOrder.ports(
            "mode", "string", "timezone", "string");
    private static final List<String> ZONES = zones();

    // Keep node construction on the static API
    private TimeGraphNode(){}

    // Describe stable time outputs under the host's namespace
    public static GraphNodeDefinition definition(String namespace){
        return new GraphNodeDefinition(ResourceLocation.fromNamespaceAndPath(namespace, "time").toString(), "core", INPUTS,
                GraphPortOrder.ports("time", "string", "hour", "number", "minute", "number", "second", "number", "valid", "boolean"), false);
    }

    // Keep zone controls available only when the saved mode selects real time
    public static Map<String, String> inputs(CompoundTag data){
        String mode = data.getCompound("Defaults").getCompound("mode").getCompound("Payload").getString("Value");
        return "Real".equalsIgnoreCase(mode) ? INPUTS : GraphPortOrder.ports("mode", "string");
    }

    // Publish dropdowns and retain real-time settings when switching back to world time
    public static GraphNodePresentationRegistry.Presentation presentation(){
        return new GraphNodePresentationRegistry.Presentation(Map.of("mode", GraphValue.string("World"),
                "timezone", GraphValue.string("")),
                List.of(new GraphNodePresentationRegistry.Section("time", "Time", List.of("mode", "timezone"))),
                Map.of("mode", List.of("World", "Real"), "timezone", ZONES));
    }

    // Populate a new node from the authoritative server zone, including fixed-offset server zones
    public static void initializeTimezone(CompoundTag data, String timezone){
        ZoneId.of(timezone);
        CompoundTag defaults = data.getCompound("Defaults");
        CompoundTag val = new CompoundTag(); val.putString("Type", "string");
        CompoundTag payload = new CompoundTag(); payload.putString("Value", timezone); val.put("Payload", payload);
        defaults.put("timezone", val); data.put("Defaults", defaults);
        CompoundTag options = data.getCompound("InputOptions");
        var zones = options.getList("timezone", 8);
        if(zones.stream().noneMatch(zone -> timezone.equals(zone.getAsString()))) zones.addFirst(StringTag.valueOf(timezone));
        options.put("timezone", zones); data.put("InputOptions", options);
    }

    // Sample both clock modes without mutating the world or throwing on an invalid timezone
    public static Map<String, GraphValue> execute(GraphExecutionContext ctx, Map<String, GraphValue> inputs){
        TimeSource source = ctx.service(GraphHostServices.TIME).orElse(null);
        if(source == null) return unavailable();
        String mode = inputs.getOrDefault("mode", GraphValue.string("World")).asString();
        try{
            ClockTime time;
            if("World".equalsIgnoreCase(mode)) time = ClockTime.world(source.dayTime().getAsLong());
            else if("Real".equalsIgnoreCase(mode)){
                String zone = inputs.getOrDefault("timezone", GraphValue.string("")).asString().strip();
                if(zone.isEmpty()) zone = source.clock().getZone().getId();
                time = ClockTime.real(source.clock().instant(), zone);
            }else return unavailable();
            return Map.of("time", GraphValue.string(time.text()), "hour", GraphValue.number(time.hour()),
                    "minute", GraphValue.number(time.minute()), "second", GraphValue.number(time.second()), "valid", GraphValue.bool(true));
        }catch(DateTimeException | IllegalArgumentException err){ return unavailable(); }
    }

    // Preserve output types when the host or selected clock is unavailable
    private static Map<String, GraphValue> unavailable(){
        return Map.of("time", GraphValue.string(""), "hour", GraphValue.number(0), "minute", GraphValue.number(0),
                "second", GraphValue.number(0), "valid", GraphValue.bool(false));
    }

    // Offer explicit UTC and GMT choices alongside named regions with daylight-saving rules
    private static List<String> zones(){
        var zones = new ArrayList<String>();
        zones.add("UTC"); zones.add("GMT");
        ZoneId.getAvailableZoneIds().stream().sorted().filter(zone -> !zones.contains(zone)).forEach(zones::add);
        return List.copyOf(zones);
    }
}
