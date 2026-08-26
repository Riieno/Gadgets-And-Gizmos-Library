package com.rieno.gadgetsandgizmos.lib.probe;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// Expose block state properties as reusable typed data ports
public final class BlockStateDataAccess {
    public static final String PORT_PREFIX = "state_";

    // Initialize the block state data access
    private BlockStateDataAccess() {
    }

    // Get the readable or writable state data
    public static Map<String, String> data(@Nullable BlockState state, boolean writable) {
        if (state == null) {
            return Map.of();
        }
        Map<String, String> ports = new LinkedHashMap<>();
        for (Property<?> property : state.getProperties()) {
            if (!writable || isWritable(property)) {
                ports.put(PORT_PREFIX + property.getName(), graphType(state, property));
            }
        }
        return Collections.unmodifiableMap(ports);
    }

    // Get the selectable state values
    public static Map<String, List<String>> options(@Nullable BlockState state, boolean writable) {
        if (state == null) {
            return Map.of();
        }
        Map<String, List<String>> options = new LinkedHashMap<>();
        for (Property<?> property : state.getProperties()) {
            if (writable && !isWritable(property)) {
                continue;
            }
            List<String> values = new ArrayList<>();
            addOptions(values, property);
            options.put(PORT_PREFIX + property.getName(), List.copyOf(values));
        }
        return Collections.unmodifiableMap(options);
    }

    // Read one state data value
    public static @Nullable GraphValue read(@Nullable BlockState state, String port) {
        Property<?> property = findProperty(state, port);
        return property == null ? null : readProperty(state, property);
    }

    // Write one state data value
    public static boolean write(Level level, BlockPos pos, String port, GraphValue value) {
        if (port == null || value == null) {
            return false;
        }
        return write(level, pos, Map.of(port, value));
    }

    // Write a complete state data update
    public static boolean write(Level level, BlockPos pos, Map<String, GraphValue> values) {
        if (level == null || pos == null || values == null || values.isEmpty()
                || !level.isLoaded(pos)) {
            return false;
        }
        BlockState current = level.getBlockState(pos);
        BlockState updated = current;
        for (Map.Entry<String, GraphValue> entry : values.entrySet()) {
            Property<?> property = findProperty(updated, entry.getKey());
            if (property == null || !isWritable(property) || entry.getValue() == null) {
                continue;
            }
            BlockState candidate = withValue(updated, property, entry.getValue());
            if (candidate != null) {
                updated = candidate;
            }
        }
        if (updated.equals(current) || !level.setBlock(pos, updated, 3)) {
            return false;
        }
        level.updateNeighborsAt(pos, updated.getBlock());
        for (Direction direction : Direction.values()) {
            level.updateNeighborsAt(pos.relative(direction), updated.getBlock());
        }
        return true;
    }

    // Find one state property from its port id
    private static @Nullable Property<?> findProperty(@Nullable BlockState state, String port) {
        if (state == null || port == null || !port.startsWith(PORT_PREFIX)) {
            return null;
        }
        String name = port.substring(PORT_PREFIX.length());
        for (Property<?> property : state.getProperties()) {
            if (property.getName().equals(name)) {
                return property;
            }
        }
        return null;
    }

    // Check if a state property can be changed through data access
    private static boolean isWritable(Property<?> property) {
        return property != null && !"waterlogged".equals(property.getName());
    }

    // Get the graph type for one state property
    private static String graphType(BlockState state, Property<?> property) {
        if (property instanceof BooleanProperty) {
            return "boolean";
        }
        if (property instanceof IntegerProperty) {
            return "number";
        }
        return propertyValue(state, property) instanceof Direction ? "direction" : "string";
    }

    // Read one property as a graph value
    private static <T extends Comparable<T>> GraphValue readProperty(BlockState state, Property<T> property) {
        T current = state.getValue(property);
        if (property instanceof BooleanProperty) {
            return GraphValue.bool(Boolean.parseBoolean(property.getName(current)));
        }
        if (property instanceof IntegerProperty) {
            try {
                return GraphValue.number(Integer.parseInt(property.getName(current)));
            } catch (NumberFormatException ignored) {
                return GraphValue.number(0.0D);
            }
        }
        String value = property.getName(current);
        return current instanceof Direction
                ? new GraphValue("direction", value)
                : GraphValue.string(value);
    }

    // Apply one graph value to a property
    private static <T extends Comparable<T>> @Nullable BlockState withValue(
            BlockState state, Property<T> property, GraphValue value) {
        if (property instanceof BooleanProperty booleanProperty) {
            Boolean requested = graphBoolean(value);
            if (requested == null) {
                return null;
            }
            return state.getValue(booleanProperty) == requested
                    ? state : state.setValue(booleanProperty, requested);
        }
        if (property instanceof IntegerProperty integerProperty) {
            Double requested = graphNumber(value);
            if (requested == null) {
                return null;
            }
            int minimum = integerProperty.getPossibleValues().stream()
                    .min(Integer::compareTo).orElse(0);
            int maximum = integerProperty.getPossibleValues().stream()
                    .max(Integer::compareTo).orElse(minimum);
            int rounded = (int) Math.round(Mth.clamp(requested, minimum, maximum));
            return state.getValue(integerProperty) == rounded
                    ? state : state.setValue(integerProperty, rounded);
        }
        String requested = value.asString().trim();
        if (requested.isEmpty()) {
            return null;
        }
        for (T candidate : property.getPossibleValues()) {
            if (property.getName(candidate).equalsIgnoreCase(requested)) {
                return state.getValue(property).equals(candidate)
                        ? state : state.setValue(property, candidate);
            }
        }
        return null;
    }

    // Read a graph value as a Boolean
    private static @Nullable Boolean graphBoolean(GraphValue value) {
        if ("boolean".equals(value.type())) {
            return value.asBoolean();
        }
        Double numeric = graphNumber(value);
        if (numeric != null) {
            return numeric != 0.0D;
        }
        return switch (value.asString().trim().toLowerCase(Locale.ROOT)) {
            case "true", "on", "yes" -> true;
            case "false", "off", "no" -> false;
            default -> null;
        };
    }

    // Read a graph value as a finite Number
    private static @Nullable Double graphNumber(GraphValue value) {
        if ("boolean".equals(value.type())) {
            return value.asBoolean() ? 1.0D : 0.0D;
        }
        if ("number".equals(value.type())) {
            double numeric = value.asNumber();
            return Double.isFinite(numeric) ? numeric : null;
        }
        try {
            double numeric = Double.parseDouble(value.asString().trim());
            return Double.isFinite(numeric) ? numeric : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    // Get the current property value
    private static <T extends Comparable<T>> T propertyValue(BlockState state, Property<T> property) {
        return state.getValue(property);
    }

    // Add the possible property values
    private static <T extends Comparable<T>> void addOptions(List<String> target, Property<T> property) {
        for (T value : property.getPossibleValues()) {
            target.add(property.getName(value));
        }
    }
}
