package com.rieno.gadgetsandgizmos.lib.physics;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

// Expose physical assembly links from blocks which cannot implement the actor API
public final class SableAssemblyConnectionRegistry{
    // Retain adapters in registration order and track changes for topology caches
    private static final Map<ResourceLocation, Entry> ENTRIES = new LinkedHashMap<>();
    private static long revision;

    // Prevent registry instances
    private SableAssemblyConnectionRegistry(){}

    // Register an adapter under its owning mod namespace
    public static synchronized void register(ResourceLocation id, Predicate<BlockEntity> filter, Adapter adapter){
        Objects.requireNonNull(id, "id");
        Entry entry = new Entry(Objects.requireNonNull(filter, "filter"), Objects.requireNonNull(adapter, "adapter"));
        if(ENTRIES.putIfAbsent(id, entry) != null) throw new IllegalStateException("Assembly adapter already registered: " + id);
        revision++;
    }

    // Remove an adapter and invalidate derived topology
    public static synchronized void unregister(ResourceLocation id){
        if(ENTRIES.remove(id) != null) revision++;
    }

    // Return the registration revision for topology caches
    public static synchronized long revision(){ return revision; }

    // Collect native links on the owning game thread without loading missing blocks
    public static List<SableAssemblyConnection> connections(ServerSubLevel owner){
        if(owner == null || owner.isRemoved()) return List.of();
        List<Entry> entries;
        synchronized(SableAssemblyConnectionRegistry.class){ entries = List.copyOf(ENTRIES.values()); }
        if(entries.isEmpty()) return List.of();
        List<SableAssemblyConnection> res = new ArrayList<>();
        for(BlockEntity blockEntity : SubLevelBlockEntityCollector.getBlockEntities(owner,
                val -> entries.stream().anyMatch(entry -> entry.filter().test(val)))){
            for(Entry entry : entries){
                if(!entry.filter().test(blockEntity)) continue;
                Collection<SableAssemblyConnection> links = entry.adapter().connections(owner, blockEntity);
                if(links != null) links.stream().filter(Objects::nonNull).forEach(res::add);
            }
        }
        return List.copyOf(res);
    }

    // Describe native physical links while leaving block identities in the consuming mod
    @FunctionalInterface
    public interface Adapter{
        // Include unavailable target ids so storage can reject incomplete assemblies
        Collection<SableAssemblyConnection> connections(ServerSubLevel owner, BlockEntity blockEntity);
    }

    // Retain each adapter with its block filter
    private record Entry(Predicate<BlockEntity> filter, Adapter adapter){}
}
