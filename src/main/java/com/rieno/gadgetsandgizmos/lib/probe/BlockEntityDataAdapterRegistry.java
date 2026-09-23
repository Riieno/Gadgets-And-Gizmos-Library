package com.rieno.gadgetsandgizmos.lib.probe;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

// Register typed block entity data adapters without reflective setter discovery
public final class BlockEntityDataAdapterRegistry {
    private static final List<RegisteredAdapter> ADAPTERS = new CopyOnWriteArrayList<>();

    // Initialize the block entity data adapter registry
    private BlockEntityDataAdapterRegistry() {
    }

    // Register a block entity data adapter
    public static synchronized void register(int priority, BlockEntityDataAdapter<?> adapter) {
        if (adapter == null || ADAPTERS.stream().anyMatch(entry -> entry.adapter() == adapter)) {
            return;
        }
        ADAPTERS.add(new RegisteredAdapter(priority, adapter));
        ADAPTERS.sort(Comparator.comparingInt(RegisteredAdapter::priority).reversed());
    }

    // Get the readable data ports
    public static Map<String, String> readableData(BlockEntity target) {
        return dataPorts(target, true);
    }

    // Get the writable data ports
    public static Map<String, String> writableData(BlockEntity target) {
        return dataPorts(target, false);
    }

    // Get explicitly grouped readable data ports
    public static Map<String, Map<String, String>> readableDataPortGroups(BlockEntity target) {
        return dataPortGroups(target, true);
    }

    // Get explicitly grouped writable data ports
    public static Map<String, Map<String, String>> writableDataPortGroups(BlockEntity target) {
        return dataPortGroups(target, false);
    }

    // Check whether dynamic data ports are ready to be refreshed
    public static boolean isDataSchemaReady(BlockEntity target) {
        return !(target instanceof BlockEntityDataProvider provider) || provider.isGraphDataSchemaReady();
    }

    // Get the revision for dynamic data ports
    public static long dataSchemaRevision(BlockEntity target) {
        return target instanceof BlockEntityDataProvider provider ? provider.graphDataSchemaRevision() : 0L;
    }

    // Get the writable data options
    public static Map<String, List<String>> writableOptions(BlockEntity target) {
        Map<String, List<String>> options = new LinkedHashMap<>();
        if (target instanceof BlockEntityDataProvider provider) {
            Map<String, String> writable = provider.graphWritableData();
            provider.graphWritableOptions().forEach((port, values) -> {
                if (writable.containsKey(port)
                        && !BlockEntityDataAccessPolicy.isItemContentMutation(
                        port, writable.get(port))
                        && values != null && !values.isEmpty()) {
                    options.put(port, List.copyOf(values));
                }
            });
        }

        for (BlockEntityDataAdapter<BlockEntity> adapter : findAdapters(target)) {
            for (BlockEntityDataPort port : adapter.ports(target)) {
                if (port.access().canWrite()
                        && !BlockEntityDataAccessPolicy.isItemContentMutation(
                        port.id(), port.type())
                        && !port.options().isEmpty()) {
                    options.putIfAbsent(port.id(), port.options());
                }
            }
        }
        return Collections.unmodifiableMap(options);
    }

    // Read one registered data value
    public static GraphValue read(BlockEntity target, String port) {
        if (target == null || port == null || port.isBlank()) {
            return null;
        }
        if (target instanceof BlockEntityDataProvider provider
                && provider.graphReadableData().containsKey(port)) {
            return provider.readGraphValue(port);
        }

        for (BlockEntityDataAdapter<BlockEntity> adapter : findAdapters(target)) {
            BlockEntityDataPort descriptor = findPort(adapter, target, port);
            if (descriptor != null && descriptor.access().canRead()) {
                return adapter.read(target, port);
            }
        }
        return null;
    }

    // Write one registered data value
    public static boolean write(BlockEntity target, String port, GraphValue value) {
        if (target == null || port == null || port.isBlank() || value == null) {
            return false;
        }
        if (target instanceof BlockEntityDataProvider provider) {
            Map<String, String> writable = provider.graphWritableData();
            if (writable.containsKey(port)) {
                return !BlockEntityDataAccessPolicy.isItemContentMutation(
                        port, writable.get(port))
                        && provider.writeGraphValue(port, value);
            }
        }

        for (BlockEntityDataAdapter<BlockEntity> adapter : findAdapters(target)) {
            BlockEntityDataPort descriptor = findPort(adapter, target, port);
            if (descriptor != null && descriptor.access().canWrite()
                    && !BlockEntityDataAccessPolicy.isItemContentMutation(
                    descriptor.id(), descriptor.type())) {
                return adapter.write(target, port, value);
            }
        }
        return false;
    }

    // Write registered data values as one batch
    public static boolean writeAll(BlockEntity target, Map<String, GraphValue> values) {
        if (target == null || values == null || values.isEmpty()) {
            return false;
        }
        Map<String, GraphValue> remaining = new LinkedHashMap<>();
        values.forEach((port, value) -> {
            if (port != null && !port.isBlank() && value != null) {
                remaining.put(port, value);
            }
        });
        if (remaining.isEmpty()) {
            return false;
        }

        boolean changed = false;
        if (target instanceof BlockEntityDataProvider provider) {
            Map<String, String> writable = provider.graphWritableData();
            Map<String, GraphValue> providerValues = new LinkedHashMap<>();
            for (String port : new ArrayList<>(remaining.keySet())) {
                String type = writable.get(port);
                if (type != null && !BlockEntityDataAccessPolicy.isItemContentMutation(port, type)) {
                    providerValues.put(port, remaining.remove(port));
                }
            }
            if (!providerValues.isEmpty()) {
                changed |= provider.writeGraphValues(Collections.unmodifiableMap(providerValues));
            }
        }
        for (Map.Entry<String, GraphValue> entry : remaining.entrySet()) {
            changed |= write(target, entry.getKey(), entry.getValue());
        }
        return changed;
    }

    // Build the readable or writable port map
    private static Map<String, String> dataPorts(BlockEntity target, boolean readable) {
        Map<String, String> ports = new LinkedHashMap<>();
        if (target == null) {
            return ports;
        }
        if (target instanceof BlockEntityDataProvider provider) {
            Map<String, String> declared = readable
                    ? provider.graphReadableData() : provider.graphWritableData();
            declared.forEach((port, type) -> {
                if (readable || !BlockEntityDataAccessPolicy.isItemContentMutation(port, type)) {
                    ports.put(port, type);
                }
            });
        }

        for (BlockEntityDataAdapter<BlockEntity> adapter : findAdapters(target)) {
            for (BlockEntityDataPort port : adapter.ports(target)) {
                if ((readable ? port.access().canRead() : port.access().canWrite())
                        && (readable || !BlockEntityDataAccessPolicy.isItemContentMutation(
                        port.id(), port.type()))) {
                    ports.putIfAbsent(port.id(), port.type());
                }
            }
        }
        return Collections.unmodifiableMap(ports);
    }

    // Get the provider ports assigned to explicit MAP groups
    private static Map<String, Map<String, String>> dataPortGroups(BlockEntity target, boolean readable) {
        if (!(target instanceof BlockEntityDataProvider provider)) {
            return Map.of();
        }
        Map<String, String> declared = readable ? provider.graphReadableData() : provider.graphWritableData();
        Map<String, Map<String, String>> configured = readable
                ? provider.graphReadableDataPortGroups() : provider.graphWritableDataPortGroups();
        if (configured == null || configured.isEmpty()) {
            return Map.of();
        }
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        Set<String> groupedPorts = new HashSet<>();
        configured.forEach((group, ports) -> {
            if (group == null || group.isBlank() || ports == null || ports.isEmpty()) {
                return;
            }
            Map<String, String> entries = new LinkedHashMap<>();
            ports.keySet().forEach(port -> {
                String type = declared.get(port);
                if (port != null && type != null && groupedPorts.add(port)) {
                    entries.put(port, type);
                }
            });
            if (!entries.isEmpty()) {
                result.put(group, Collections.unmodifiableMap(entries));
            }
        });
        return Collections.unmodifiableMap(result);
    }

    // Find every matching data adapter in priority order
    private static List<BlockEntityDataAdapter<BlockEntity>> findAdapters(BlockEntity target) {
        if (target == null) {
            return List.of();
        }
        List<BlockEntityDataAdapter<BlockEntity>> matches = new ArrayList<>();
        for (RegisteredAdapter entry : ADAPTERS) {
            BlockEntityDataAdapter<?> adapter = entry.adapter();
            if (!adapter.targetType().isInstance(target)) {
                continue;
            }
            BlockEntityDataAdapter<BlockEntity> cast = cast(adapter);
            if (cast.supports(target)) {
                matches.add(cast);
            }
        }
        return List.copyOf(matches);
    }

    // Find one declared port
    private static BlockEntityDataPort findPort(BlockEntityDataAdapter<BlockEntity> adapter,
                                                BlockEntity target,
                                                String port) {
        if (adapter == null) {
            return null;
        }
        for (BlockEntityDataPort descriptor : adapter.ports(target)) {
            if (descriptor.id().equals(port)) {
                return descriptor;
            }
        }
        return null;
    }

    // Cast an adapter after its target type was checked
    @SuppressWarnings("unchecked")
    private static BlockEntityDataAdapter<BlockEntity> cast(BlockEntityDataAdapter<?> adapter) {
        return (BlockEntityDataAdapter<BlockEntity>) adapter;
    }

    // Store one prioritized adapter
    private record RegisteredAdapter(int priority, BlockEntityDataAdapter<?> adapter) {
    }
}
