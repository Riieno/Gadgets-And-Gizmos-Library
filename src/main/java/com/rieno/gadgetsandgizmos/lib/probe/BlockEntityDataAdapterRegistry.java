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
