package com.rieno.gadgetsandgizmos.lib.power.powergrid;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.power.ElectricalStorage;
import com.rieno.gadgetsandgizmos.lib.power.ConnectorOutputLimits;
import com.rieno.gadgetsandgizmos.lib.power.EnergyUnitConversion;
import com.rieno.gadgetsandgizmos.lib.power.LongEnergyStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.patryk3211.powergrid.electricity.base.ElectricBehaviour;
import org.patryk3211.powergrid.electricity.deviceconnector.DeviceConnectorBlock;
import org.patryk3211.powergrid.electricity.deviceconnector.DeviceConnectorBlockEntity;
import org.patryk3211.powergrid.electricity.febridge.IFEBridgeHandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Predicate;

// Bind actual placed connectors to an opted-in storage without linking addon implementation classes
public final class PowerGridDeviceConnectors{
    private static final Map<LongEnergyStorage, State> STATES = Collections.synchronizedMap(new WeakHashMap<>());
    private PowerGridDeviceConnectors(){}

    private static final class State{
        long discoveryTick = Long.MIN_VALUE;
        long settledTick = Long.MIN_VALUE;
        boolean settling;
        List<Connection> connections = List.of();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public record Connection(BlockPos member, Direction face, BlockPos connector){}

    public static ElectricalStorage host(BlockEntity connector){
        var level = connector.getLevel();
        if(level == null || !connector.getBlockState().hasProperty(DeviceConnectorBlock.FACING)) return null;
        var pos = connector.getBlockPos().relative(connector.getBlockState().getValue(DeviceConnectorBlock.FACING));
        return level.isLoaded(pos) && level.getBlockEntity(pos) instanceof ElectricalStorage storage ? storage : null;
    }

    public static List<Connection> connections(ElectricalStorage storage){
        var level = level(storage);
        if(level == null) return List.of();
        var state = state(storage);
        if(state.discoveryTick == level.getGameTime()) return state.connections;
        var members = storage.electricalMembers();
        List<Connection> res = new ArrayList<>();
        for(var member : members) for(var face : Direction.values()){
            var pos = member.getBlockPos().relative(face);
            if(!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof DeviceConnectorBlockEntity connector)
                    || connector.getBlockState().getValue(DeviceConnectorBlock.FACING) != face.getOpposite()) continue;
            res.add(new Connection(member.getBlockPos().immutable(), face, pos.immutable()));
        }
        res.sort(Comparator.comparingLong((Connection val) -> val.member().asLong()).thenComparing(val -> val.face().ordinal()));
        state.connections = List.copyOf(res);
        state.discoveryTick = level.getGameTime();
        return state.connections;
    }

    // Settle native power before an external FE mutation can spend the same stored energy
    public static void synchronize(ElectricalStorage storage){
        var level = level(storage);
        if(level == null || level.isClientSide) return;
        var state = state(storage);
        long tick = level.getGameTime();
        if(state.settling || state.settledTick == tick) return;
        state.settling = true;
        state.settledTick = tick;
        try{
            for(var connection : connections(storage)){
                var bridge = behaviour(level, connection);
                if(bridge != null) bridge.settle(storage, tick);
            }
        }finally{
            state.settling = false;
            refresh(storage);
        }
    }

    public static void refresh(ElectricalStorage storage){
        var level = level(storage);
        if(level == null || state(storage).settling) return;
        var connections = connections(storage);
        long energy = storage.electricalStorage().getEnergyStored();
        long capacity = storage.electricalStorage().getMaxEnergyStored();
        for(var connection : connections){
            if(!(level.getBlockEntity(connection.member()) instanceof ElectricalStorage member)) continue;
            var bridge = behaviour(level, connection);
            var limits = member.connectorOutputLimits();
            if(bridge != null) bridge.configure(energy, capacity, connections.size(), limits.watts(connection.face()), limits.signedVolts(connection.face()));
        }
    }

    public static void invalidate(ElectricalStorage storage){ state(storage).discoveryTick = Long.MIN_VALUE; }

    public static boolean setOutput(ElectricalStorage storage, BlockPos pos, Direction face, double watts){
        return setOutputSetting(storage, pos, face, limits -> limits.setWatts(face, watts));
    }

    public static boolean setVoltage(ElectricalStorage storage, BlockPos pos, Direction face, double volts){
        return setOutputSetting(storage, pos, face, limits -> limits.setVolts(face, volts));
    }

    public static boolean setPolarity(ElectricalStorage storage, BlockPos pos, Direction face, int sign){
        return setOutputSetting(storage, pos, face, limits -> limits.setPolarity(face, sign));
    }

    private static boolean setOutputSetting(ElectricalStorage storage, BlockPos pos, Direction face, Predicate<ConnectorOutputLimits> setting){
        var level = level(storage);
        if(level == null || level.isClientSide) return false;
        var connection = connections(storage).stream().filter(val -> val.member().equals(pos) && val.face() == face).findFirst().orElse(null);
        if(connection == null || !(level.getBlockEntity(connection.connector()) instanceof DeviceConnectorBlockEntity connector)
                || connector.getBlockState().getValue(DeviceConnectorBlock.FACING) != face.getOpposite()) return false;
        if(!(level.getBlockEntity(pos) instanceof ElectricalStorage member) || !setting.test(member.connectorOutputLimits())) return false;
        var target = level.getBlockEntity(pos);
        target.setChanged();
        if(target instanceof com.simibubi.create.foundation.blockEntity.SmartBlockEntity smart) smart.sendData();
        refresh(storage);
        return true;
    }

    public static void snapshot(ElectricalStorage storage, CompoundTag data){
        data.putBoolean("PowerGrid", true);
        double ratio = IFEBridgeHandler.wattToFE();
        data.putDouble("PowerGridWattHours", EnergyUnitConversion.wattHours(storage.electricalStorage().getEnergyStored(), ratio));
        data.putDouble("PowerGridCapacityWh", EnergyUnitConversion.wattHours(storage.electricalStorage().getMaxEnergyStored(), ratio));
        var level = level(storage);
        ListTag rows = new ListTag();
        if(level != null) for(var connection : connections(storage)){
            if(!(level.getBlockEntity(connection.member()) instanceof ElectricalStorage member)) continue;
            var row = new CompoundTag();
            row.putLong("Member", connection.member().asLong());
            row.putLong("Connector", connection.connector().asLong());
            row.putString("Face", connection.face().getName());
            row.putDouble("Watts", member.connectorOutputLimits().watts(connection.face()));
            row.putDouble("Volts", member.connectorOutputLimits().volts(connection.face()));
            row.putInt("Polarity", member.connectorOutputLimits().polarity(connection.face()));
            var bridge = behaviour(level, connection);
            row.putDouble("ActualWatts", bridge == null ? 0 : Math.max(0, -bridge.watts()));
            row.putDouble("ActualVolts", bridge == null ? 0 : bridge.volts());
            row.putDouble("ActualAmps", bridge == null ? 0 : bridge.amps());
            row.putDouble("TransferWatts", bridge == null ? 0 : bridge.watts());
            rows.add(row);
        }
        data.put("PowerGridConnectors", rows);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static State state(ElectricalStorage storage){ return STATES.computeIfAbsent(storage.electricalStorage(), key -> new State()); }
    private static Level level(ElectricalStorage storage){
        if(storage instanceof BlockEntity member) return member.getLevel();
        if(storage.electricalStorage() instanceof BlockEntity controller) return controller.getLevel();
        var members = storage.electricalMembers();
        return members.isEmpty() ? null : members.getFirst().getLevel();
    }

    private static PowerGridDeviceConnectorBehaviour behaviour(Level level, Connection connection){
        if(!level.isLoaded(connection.connector()) || !(level.getBlockEntity(connection.connector()) instanceof DeviceConnectorBlockEntity connector)) return null;
        return connector.getBehaviour(ElectricBehaviour.TYPE) instanceof PowerGridDeviceConnectorBehaviour bridge ? bridge : null;
    }
}
