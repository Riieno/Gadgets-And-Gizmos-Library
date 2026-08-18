package com.rieno.gadgetsandgizmos.lib.probe;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import net.minecraft.world.level.block.entity.BlockEntity;

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
                if (writable.containsKey(port) && values != null && !values.isEmpty()) {
                    options.put(port, List.copyOf(values));
                }
            });
        }

        BlockEntityDataAdapter<BlockEntity> adapter = findAdapter(target);
        if (adapter != null) {
            for (BlockEntityDataPort port : adapter.ports(target)) {
                if (port.access().canWrite() && !port.options().isEmpty()) {
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

        BlockEntityDataAdapter<BlockEntity> adapter = findAdapter(target);
        BlockEntityDataPort descriptor = findPort(adapter, target, port);
        return descriptor != null && descriptor.access().canRead()
                ? adapter.read(target, port) : null;
    }

    // Write one registered data value
    public static boolean write(BlockEntity target, String port, GraphValue value) {
        if (target == null || port == null || port.isBlank() || value == null) {
            return false;
        }
        if (target instanceof BlockEntityDataProvider provider
                && provider.graphWritableData().containsKey(port)) {
            return provider.writeGraphValue(port, value);
        }

        BlockEntityDataAdapter<BlockEntity> adapter = findAdapter(target);
        BlockEntityDataPort descriptor = findPort(adapter, target, port);
        return descriptor != null && descriptor.access().canWrite()
                && adapter.write(target, port, value);
    }

    // Build the readable or writable port map
    private static Map<String, String> dataPorts(BlockEntity target, boolean readable) {
        Map<String, String> ports = new LinkedHashMap<>();
        if (target == null) {
            return ports;
        }
        if (target instanceof BlockEntityDataProvider provider) {
            ports.putAll(readable ? provider.graphReadableData() : provider.graphWritableData());
        }

        BlockEntityDataAdapter<BlockEntity> adapter = findAdapter(target);
        if (adapter != null) {
            for (BlockEntityDataPort port : adapter.ports(target)) {
                if (readable ? port.access().canRead() : port.access().canWrite()) {
                    ports.putIfAbsent(port.id(), port.type());
                }
            }
        }
        return Collections.unmodifiableMap(ports);
    }

    // Find the first matching data adapter
    private static BlockEntityDataAdapter<BlockEntity> findAdapter(BlockEntity target) {
        if (target == null) {
            return null;
        }
        for (RegisteredAdapter entry : ADAPTERS) {
            BlockEntityDataAdapter<?> adapter = entry.adapter();
            if (!adapter.targetType().isInstance(target)) {
                continue;
            }
            BlockEntityDataAdapter<BlockEntity> cast = cast(adapter);
            if (cast.supports(target)) {
                return cast;
            }
        }
        return null;
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
