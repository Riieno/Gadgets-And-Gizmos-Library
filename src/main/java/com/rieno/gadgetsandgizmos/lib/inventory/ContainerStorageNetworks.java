package com.rieno.gadgetsandgizmos.lib.inventory;

import com.rieno.gadgetsandgizmos.lib.access.WorldAccessPolicy;
import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerOrchestrator;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerContainerAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.UUID;

// Resolve full loaded worker storage networks from any member without forcing chunks to load
public final class ContainerStorageNetworks{
    private static final Map<BlockEntity, java.util.function.Function<BlockEntity, List<BlockEntity>>> SOURCES = new WeakHashMap<>();
    private static final Map<ServerLevel, Topology> TOPOLOGIES = new WeakHashMap<>();
    private static long revision;
    private ContainerStorageNetworks(){}

    // Call from the server tick of a block entity implementing WorkerOrchestrator
    public static void track(BlockEntity source){
        if(source instanceof WorkerOrchestrator) track(source, entity -> ((WorkerOrchestrator)entity).linkedStorage());
    }

    // Supply a non-capturing adapter for other storage-network controller implementations
    public static void track(BlockEntity source, java.util.function.Function<BlockEntity, List<BlockEntity>> storage){
        if(source != null && storage != null && source.getLevel() != null && !source.getLevel().isClientSide){
            if(!SOURCES.containsKey(source)) invalidate();
            SOURCES.put(source, storage);
        }
    }

    // Invalidate after changing explicit network links; normal loaded topology is refreshed every server tick
    public static void invalidate(){ TOPOLOGIES.clear(); revision++; }
    public static long revision(){ return revision; }

    // Release controllers and cached storage when a server stops
    public static void clear(MinecraftServer server){
        SOURCES.keySet().removeIf(source -> source.getLevel() == null || source.getLevel().getServer() == server);
        TOPOLOGIES.keySet().removeIf(level -> level.getServer() == server);
        revision++;
    }

    // Automation inherits the explicit network's reach without requiring an online player or loading chunks
    public static List<BlockEntity> connected(BlockEntity selected){
        if(!available(selected)) return List.of();
        ServerLevel level = SableLevelApi.serverLevel(selected.getLevel());
        if(level == null) return List.of();
        Topology topology = TOPOLOGIES.get(level);
        if(topology == null || topology.tick() != level.getGameTime()){
            topology = topology(level);
            TOPOLOGIES.put(level, topology);
        }
        return topology.components().getOrDefault(key(selected), List.of(root(selected))).stream()
                .filter(ContainerStorageNetworks::available).toList();
    }

    private static Topology topology(ServerLevel level){
        Map<Key, List<BlockEntity>> components = new LinkedHashMap<>();
        SOURCES.keySet().removeIf(source -> source == null || source.isRemoved());
        for(BlockEntity source : List.copyOf(SOURCES.keySet())){
            if(!available(source) || SableLevelApi.serverLevel(source.getLevel()) != level) continue;
            Map<Key, BlockEntity> merged = new LinkedHashMap<>();
            for(BlockEntity target : SOURCES.get(source).apply(source)){
                if(!available(target) || SableLevelApi.serverLevel(target.getLevel()) != level) continue;
                for(BlockEntity member : components.getOrDefault(key(target), List.of(root(target)))){
                    if(available(member)) merged.putIfAbsent(key(member), member);
                }
            }
            List<BlockEntity> network = List.copyOf(merged.values());
            for(Key member : merged.keySet()) components.put(member, network);
        }
        return new Topology(level.getGameTime(), components);
    }

    // Include overlapping linked networks while applying access checks to controllers and every container
    public static List<BlockEntity> connected(BlockEntity selected, ServerPlayer viewer){
        if(selected == null || selected.getLevel() == null || viewer == null) return List.of();
        List<List<BlockEntity>> networks = new ArrayList<>();
        SOURCES.keySet().removeIf(source -> source == null || source.isRemoved());
        for(BlockEntity source : List.copyOf(SOURCES.keySet())){
            if(SableLevelApi.serverLevel(source.getLevel()) != SableLevelApi.serverLevel(selected.getLevel())
                    || !accessible(source, viewer)) continue;
            networks.add(SOURCES.get(source).apply(source).stream()
                    .filter(target -> target != null && target.getLevel() != null && !target.isRemoved())
                    .filter(target -> accessible(target, viewer)).map(ContainerStorageNetworks::root).toList());
        }
        Map<Key, BlockEntity> connected = new LinkedHashMap<>();
        if(accessible(selected, viewer)) connected.put(key(selected), root(selected));
        boolean changed;
        do{
            changed = false;
            for(List<BlockEntity> network : networks){
                if(network.stream().noneMatch(target -> connected.containsKey(key(target)))) continue;
                for(BlockEntity target : network){
                    if(connected.putIfAbsent(key(target), target) == null) changed = true;
                }
            }
        }while(changed);
        return List.copyOf(connected.values());
    }

    private static boolean accessible(BlockEntity target, ServerPlayer viewer){
        return available(target)
                && WorldAccessPolicy.canAccessLocal(viewer, viewer.serverLevel(),
                SableLevelApi.containingId(target), target.getBlockPos());
    }

    private static Key key(BlockEntity target){
        return new Key(SableLevelApi.serverLevel(target.getLevel()), SableLevelApi.containingId(target),
                ContainerStorageIdentity.position(target.getLevel(), target.getBlockPos()));
    }

    private static BlockEntity root(BlockEntity target){
        var pos = ContainerStorageIdentity.position(target.getLevel(), target.getBlockPos());
        if(!WorkerContainerAccess.isLoaded(target.getLevel(), pos)) return target;
        var root = target.getLevel().getBlockEntity(pos);
        return root == null ? target : root;
    }

    private static boolean available(BlockEntity target){
        return target != null && !target.isRemoved() && target.getLevel() != null && !target.getLevel().isClientSide
                && WorkerContainerAccess.isLoaded(target.getLevel(), target.getBlockPos());
    }

    private record Key(ServerLevel level, UUID subLevelId, BlockPos pos){}
    private record Topology(long tick, Map<Key, List<BlockEntity>> components){}
}
