package com.rieno.gadgetsandgizmos.lib.power.powergrid;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.patryk3211.powergrid.electricity.GlobalElectricNetworks;
import org.patryk3211.powergrid.electricity.sim.special.TransmissionLinePart;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.WeakHashMap;

// Share the weakest loaded cable's current rating across converters on the same wiring
final class PowerGridWireLimits{
    private static final Map<Level, Cache> CACHES = new WeakHashMap<>();
    private static final int MAX_PARTS = 4096;
    private static final class Cache{
        long tick = Long.MIN_VALUE;
        final Map<BlockPos, Double> amps = new HashMap<>();
    }

    static double amps(PowerGridDeviceConnectorBehaviour bridge){
        var level = bridge.getWorld();
        if(level == null || level.isClientSide) return 0;
        var cache = CACHES.computeIfAbsent(level, key -> new Cache());
        if(cache.tick != level.getGameTime()){
            cache.tick = level.getGameTime();
            cache.amps.clear();
        }
        if(cache.amps.containsKey(bridge.getPos())) return cache.amps.get(bridge.getPos());
        var networks = GlobalElectricNetworks.getWorldNetworks(level);
        var pending = new ArrayDeque<TransmissionLinePart>(bridge.wires());
        var visited = new HashSet<TransmissionLinePart>();
        var converters = new HashMap<BlockPos, PowerGridDeviceConnectorBehaviour>();
        converters.put(bridge.getPos(), bridge);
        double maximum = Double.POSITIVE_INFINITY;
        double measured = 0;
        while(!pending.isEmpty()){
            var part = pending.removeFirst();
            if(!visited.add(part)) continue;
            if(visited.size() > MAX_PARTS || part.owner == null || part.owner.isRemoved() || part.owner.getWireEntry() == null){ maximum = 0; break; }
            double rating = part.owner.getWireEntry().maximumCurrent();
            if(!Double.isFinite(rating) || rating <= 0){ maximum = 0; break; }
            maximum = Math.min(maximum, rating * .9);
            measured = Math.max(measured, Math.abs(part.current()));
            for(var endpoint : new org.patryk3211.powergrid.electricity.wire.IWireEndpoint[]{part.getEndpoint1(), part.getEndpoint2()}){
                if(endpoint instanceof BlockWireEndpoint block && block.getElectricBehaviour(level) instanceof PowerGridDeviceConnectorBehaviour other
                        && PowerGridDeviceConnectors.host(other.blockEntity) != null && converters.putIfAbsent(other.getPos(), other) == null){
                    pending.addAll(other.wires());
                }
                var adjacent = networks.findConnectedWires(endpoint);
                if(adjacent != null) pending.addAll(adjacent);
            }
        }
        double own = converters.values().stream().mapToDouble(val -> Math.abs(val.amps())).sum();
        double available = maximum - Math.max(0, measured - own);
        double res = visited.isEmpty() || !Double.isFinite(available) ? 0 : Math.max(0, available) / converters.size();
        converters.keySet().forEach(pos -> cache.amps.put(pos, res));
        return res;
    }
}
