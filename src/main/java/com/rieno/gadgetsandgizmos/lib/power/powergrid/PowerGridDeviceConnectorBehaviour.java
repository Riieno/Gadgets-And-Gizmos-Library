package com.rieno.gadgetsandgizmos.lib.power.powergrid;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.power.ElectricalStorage;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.patryk3211.powergrid.electricity.base.ElectricBehaviour;
import org.patryk3211.powergrid.electricity.base.IElectricEntity;
import org.patryk3211.powergrid.electricity.deviceconnector.BridgeElectricBehaviour;
import org.patryk3211.powergrid.electricity.deviceconnector.DeviceConnectorBlock;
import org.patryk3211.powergrid.electricity.deviceconnector.DeviceConnectorBlockEntity;
import org.patryk3211.powergrid.electricity.febridge.IFEBridgeHandler;
import org.patryk3211.powergrid.electricity.sim.ElectricWire;

import java.util.Optional;
import java.util.function.Supplier;

// Use shared storage only on opted-in hosts; ordinary Device Connectors retain their native adapter
public final class PowerGridDeviceConnectorBehaviour extends BridgeElectricBehaviour{
    private PowerGridStorageCircuit circuit;
    private boolean shared;
    private long legacyEnergy;

    public PowerGridDeviceConnectorBehaviour(DeviceConnectorBlockEntity host, Supplier<ElectricWire> wire){
        super(host, host.getBlockPos().relative(host.getBlockState().getValue(DeviceConnectorBlock.FACING)), wire);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public boolean shared(){ return shared; }
    public void buildCircuit(IElectricEntity.CircuitBuilder builder){ circuit.build(builder); }
    public double watts(){ return circuit == null ? 0 : circuit.watts(); }
    public double volts(){ return circuit == null ? 0 : circuit.volts(); }
    public double amps(){ return circuit == null ? 0 : circuit.amps(); }

    @Override public void initialize(){
        var storage = PowerGridDeviceConnectors.host(blockEntity);
        if(storage == null){ super.initialize(); return; }
        activate(storage);
        baseInitialize();
    }

    @Override public void tick(){
        var storage = PowerGridDeviceConnectors.host(blockEntity);
        if(storage != null){
            if(!shared) activate(storage);
            PowerGridDeviceConnectors.synchronize(storage);
            return;
        }
        if(shared){
            circuit.disable();
            var pos = getPos().relative(blockEntity.getBlockState().getValue(DeviceConnectorBlock.FACING));
            if(!getWorld().isLoaded(pos)) return;
            shared = false;
            rebuildCircuit(false);
        }
        super.tick();
    }

    // These paths keep the native proxy from creating or serializing an additional FE store
    @Override protected boolean isProxy(){ return !shared && super.isProxy(); }
    @Override public Optional<ElectricBehaviour> getMainBehaviour(){ return shared ? Optional.empty() : super.getMainBehaviour(); }
    @Override public IFEBridgeHandler getBridgeBehaviour(){ return shared ? null : super.getBridgeBehaviour(); }
    @Override public boolean isFE(){ return !shared && super.isFE(); }
    @Override public long getBufferedAmount(){ return shared ? 0 : super.getBufferedAmount(); }

    @Override public void remove(){
        var storage = PowerGridDeviceConnectors.host(blockEntity);
        if(storage != null){
            PowerGridDeviceConnectors.synchronize(storage);
            PowerGridDeviceConnectors.invalidate(storage);
        }
        super.remove();
    }

    @Override public void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket){
        super.read(tag, registries, clientPacket);
        legacyEnergy = Math.max(0, tag.getLong("GadgetsLegacyEnergy"));
    }

    @Override public void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket){
        super.write(tag, registries, clientPacket);
        if(legacyEnergy > 0) tag.putLong("GadgetsLegacyEnergy", legacyEnergy);
        else tag.remove("GadgetsLegacyEnergy");
    }

    void settle(ElectricalStorage storage, long tick){
        if(!shared || getWorld().isClientSide) return;
        if(legacyEnergy > 0) legacyEnergy -= storage.electricalStorage().receiveEnergy(legacyEnergy, false);
        circuit.settle(storage, tick, !getConnections().isEmpty());
    }

    void configure(long stored, long capacity, int connectors, double watts, double volts){
        if(shared) circuit.configure(stored, capacity, connectors, watts, volts, PowerGridWireLimits.amps(this));
    }

    private void activate(ElectricalStorage storage){
        var prev = super.getBridgeBehaviour();
        if(prev != null){
            legacyEnergy += Math.max(0, prev.getAmount());
            prev.setAmount(0);
        }
        circuit = new PowerGridStorageCircuit();
        shared = true;
        rebuildCircuit(false);
        PowerGridDeviceConnectors.invalidate(storage);
        PowerGridDeviceConnectors.refresh(storage);
    }
}
