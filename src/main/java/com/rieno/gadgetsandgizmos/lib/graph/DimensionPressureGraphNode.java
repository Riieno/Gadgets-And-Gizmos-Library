package com.rieno.gadgetsandgizmos.lib.graph;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.physics.SablePhysicsTelemetry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

// Read a selected dimension's Sable base pressure without requiring a ship
public final class DimensionPressureGraphNode{
    public static final String CURRENT_DIMENSION = "Current Dimension";

    private DimensionPressureGraphNode(){}

    // Register the reusable node under its consuming mod's namespace
    public static GraphNodeDefinition definition(String namespace){
        return new GraphNodeDefinition(ResourceLocation.fromNamespaceAndPath(namespace, "dimension_pressure").toString(),
                "core", GraphPortOrder.ports("dimension", "string"),
                GraphPortOrder.ports("air_pressure", "number"), false);
    }

    // Default to the host dimension and let its editor provide the connected dimension choices
    public static GraphNodePresentationRegistry.Presentation presentation(){
        return new GraphNodePresentationRegistry.Presentation(Map.of("dimension", GraphValue.string(CURRENT_DIMENSION)),
                List.of(), Map.of("dimension", List.of(CURRENT_DIMENSION)));
    }

    // List every advertised dimension with stable registry identifiers
    public static List<String> dimensionOptions(Collection<ResourceKey<Level>> dimensions){
        var opts = new ArrayList<String>();
        opts.add(CURRENT_DIMENSION);
        dimensions.stream().map(key -> key.location().toString()).distinct().sorted().forEach(opts::add);
        return List.copyOf(opts);
    }

    // Express Sable's dimension base pressure as a percentage, including pressures above one atmosphere
    public static Map<String, GraphValue> execute(GraphExecutionContext ctx, Map<String, GraphValue> inputs){
        Level level = ctx.service(GraphHostServices.LEVEL).orElse(null);
        String dimension = inputs.getOrDefault("dimension", GraphValue.string(CURRENT_DIMENSION)).asString();
        if(level != null && !CURRENT_DIMENSION.equals(dimension)){
            ResourceLocation id = ResourceLocation.tryParse(dimension);
            if(id == null) level = null;
            else if(!level.dimension().location().equals(id)){
                level = level.getServer() == null ? null
                        : level.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, id));
            }
        }
        return Map.of("air_pressure", GraphValue.number(SablePhysicsTelemetry.dimensionPressure(level) * 100.0D));
    }
}
