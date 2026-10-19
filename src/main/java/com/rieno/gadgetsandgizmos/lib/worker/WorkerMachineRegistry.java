package com.rieno.gadgetsandgizmos.lib.worker;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

// Register machine integrations without depending on a consuming mod's controllers or workers
public final class WorkerMachineRegistry{
    private static final Map<ResourceLocation, Adapter> ADAPTERS = new LinkedHashMap<>();
    private static final Adapter VANILLA = new VanillaWorkerMachines();
    private static final Adapter CREATE = new com.rieno.gadgetsandgizmos.lib.create.worker.CreateWorkerMachines();
    private static final Adapter MODDED = new WorkerModdedMachines();

    private WorkerMachineRegistry(){}

    // Register during common setup on the owning game thread
    public static void register(ResourceLocation id, Adapter adapter){
        if(id == null || adapter == null) throw new IllegalArgumentException("A machine adapter needs an id and implementation");
        if(ADAPTERS.putIfAbsent(id, adapter) != null) throw new IllegalArgumentException("Duplicate worker machine adapter: " + id);
    }

    public static @Nullable WorkerMachine resolve(Level level, BlockPos pos, @Nullable Direction side){
        if(!WorkerContainerAccess.isLoaded(level, pos)) return null;
        Context ctx = new Context(level, pos.immutable(), side);
        if(level.getBlockEntity(pos) instanceof WorkerMachine machine) return machine;
        for(Adapter adapter : ADAPTERS.values()){
            WorkerMachine machine = adapter.resolve(ctx);
            if(machine != null) return machine;
        }
        WorkerMachine machine = VANILLA.resolve(ctx);
        if(machine != null) return machine;
        machine = CREATE.resolve(ctx);
        return machine == null ? MODDED.resolve(ctx) : machine;
    }

    // Resolve linked drivers to their actual receiving blocks without scanning unrelated nearby machines
    public static List<BlockPos> accessPositions(Level level, BlockPos pos){
        if(!WorkerContainerAccess.isLoaded(level, pos)) return List.of();
        Context ctx = new Context(level, pos.immutable(), null);
        LinkedHashSet<BlockPos> positions = new LinkedHashSet<>();
        positions.add(pos.immutable());
        for(Adapter adapter : ADAPTERS.values()) positions.addAll(adapter.accessPositions(ctx));
        positions.addAll(VANILLA.accessPositions(ctx));
        positions.addAll(CREATE.accessPositions(ctx));
        return List.copyOf(positions);
    }

    public record Context(Level level, BlockPos pos, @Nullable Direction side){}

    public interface Adapter{
        @Nullable WorkerMachine resolve(Context ctx);
        default List<BlockPos> accessPositions(Context ctx){ return List.of(); }
    }
}
