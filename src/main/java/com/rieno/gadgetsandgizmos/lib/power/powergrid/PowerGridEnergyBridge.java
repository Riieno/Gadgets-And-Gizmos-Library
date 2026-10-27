package com.rieno.gadgetsandgizmos.lib.power.powergrid;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.power.EnergyUnitConversion;
import com.rieno.gadgetsandgizmos.lib.power.LongEnergyStorage;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import org.patryk3211.powergrid.electricity.base.ElectricBehaviour;
import org.patryk3211.powergrid.electricity.base.IElectric;
import org.patryk3211.powergrid.electricity.base.IElectricEntity;
import org.patryk3211.powergrid.electricity.base.ITerminalPlacement;
import org.patryk3211.powergrid.electricity.febridge.FEInverterBlockEntity;
import org.patryk3211.powergrid.electricity.febridge.IFEBridgeHandler;
import org.patryk3211.powergrid.electricity.sim.node.ProvidedVoltageSourceCoupling;

import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

// Bridge native two-terminal circuits directly to an existing shared FE storage
public final class PowerGridEnergyBridge{
    private final SmartBlockEntity host;
    private final Supplier<LongEnergyStorage> storage;
    private final Supplier<List<PowerGridEnergyPort>> ports;
    private final IntSupplier sharedPorts;
    private final ProvidedVoltageSourceCoupling[] sources;
    private final double[] voltages;
    private final float[] resistances;
    private ElectricBehaviour behaviour;
    private List<PowerGridEnergyPort> prevPorts;

    public PowerGridEnergyBridge(SmartBlockEntity host, Supplier<LongEnergyStorage> storage,
                                 Supplier<List<PowerGridEnergyPort>> ports, int portCount, IntSupplier sharedPorts){
        this.host = host;
        this.storage = storage;
        this.ports = ports;
        this.sharedPorts = sharedPorts;
        sources = new ProvidedVoltageSourceCoupling[portCount];
        voltages = new double[portCount];
        resistances = new float[portCount];
        java.util.Arrays.fill(resistances, 1000F);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Hosts implement IElectricEntity and delegate circuit construction before registering this behaviour
    public ElectricBehaviour behaviour(){
        if(behaviour == null) behaviour = new ElectricBehaviour((SmartBlockEntity & IElectricEntity)host);
        return behaviour;
    }

    public void buildCircuit(IElectricEntity.CircuitBuilder builder){
        builder.setTerminalCount(sources.length * 2);
        for(int idx = 0; idx < sources.length; idx++){
            int port = idx;
            var source = new ProvidedVoltageSourceCoupling(builder.terminalNode(idx * 2), builder.terminalNode(idx * 2 + 1), 1000F);
            source.setVoltageProvider(() -> voltages[port]);
            source.setResistanceProvider(() -> resistances[port]);
            sources[idx] = source;
            builder.add(source);
        }
    }

    // Use Power Grid's own terminal placement so cable endpoints match the rendered partial
    public ITerminalPlacement terminal(int idx){
        if(idx < 0 || idx >= sources.length * 2) return null;
        var port = port(idx / 2);
        if(port == null) return null;
        var state = port.connectorState();
        if(!(state.getBlock() instanceof IElectric electric)) return null;
        var terminal = electric.terminal(state, idx % 2);
        if(terminal == null) return null;
        return new ITerminalPlacement(){
            @Override public boolean check(Vec3 pos){ return terminal.check(port.unproject(pos.add(Vec3.atLowerCornerOf(host.getBlockPos())))); }
            @Override public Vec3 getOrigin(){ return port.project(terminal.getOrigin()).subtract(Vec3.atLowerCornerOf(host.getBlockPos())); }
        };
    }

    // Centered connectors may span two members; always save their cable endpoints on their owner
    public InteractionResult redirectWire(UseOnContext ctx){
        for(var port : ports.get()){
            if(port.owner().equals(host.getBlockPos()) || !port.bounds().inflate(.01).contains(ctx.getClickLocation())) continue;
            var electric = IElectric.getAt(ctx.getLevel(), port.owner());
            if(electric == null) continue;
            var hit = new BlockHitResult(ctx.getClickLocation(), ctx.getClickedFace(), port.owner(), ctx.isInside());
            return electric.onWire(ctx.getLevel().getBlockState(port.owner()), new UseOnContext(ctx.getPlayer(), ctx.getHand(), hit));
        }
        return InteractionResult.PASS;
    }

    // Apply each port separately so incoming and outgoing power never cancel in transfer telemetry
    public void tick(){
        if(host.getLevel() == null || host.getLevel().isClientSide) return;
        refreshConnections();
        var energy = storage.get();
        double wattsToFE = IFEBridgeHandler.wattToFE(), voltsToFE = IFEBridgeHandler.voltToFE();
        for(int idx = 0; idx < sources.length; idx++){
            var source = sources[idx];
            if(port(idx) == null || source == null || !Double.isFinite(wattsToFE) || wattsToFE <= 0
                    || !Double.isFinite(voltsToFE) || voltsToFE <= 0){
                voltages[idx] = 0;
                resistances[idx] = 1.0E6F;
                continue;
            }
            double voltage = source.getPositive().getVoltage() - source.getNegative().getVoltage();
            double watts = source.getCurrent() * voltage;
            if(connected(idx) && source.getNetwork() != null && source.isConverged() && Double.isFinite(voltage) && Math.abs(watts) > 1.0E-9){
                long limit = watts >= 0 ? (long)Math.min(Long.MAX_VALUE, Math.abs(voltage) * voltsToFE) : Long.MAX_VALUE;
                EnergyUnitConversion.transfer(energy, watts, wattsToFE, limit);
            }
            // Match the native inverter's voltage and resistance while sharing its budget across every possible port
            long available = energy.getEnergyStored() / Math.max(1, sharedPorts.getAsInt());
            double buffer = Math.min(available, FEInverterBlockEntity.energyBufferSize());
            voltages[idx] = buffer / voltsToFE;
            double maxWatts = buffer / wattsToFE;
            double resistance = maxWatts > 0 ? voltages[idx] * voltages[idx] / (2 * maxWatts) : 1000;
            // A full storage disconnects an incoming source while retaining its ability to supply a load
            if(watts > 0 && energy.getEnergyStored() >= energy.getMaxEnergyStored()) resistance = 1.0E6;
            // Keep resistance changes within the native solver's float precision
            resistances[idx] = (float)Math.clamp(resistance, 1.0E-6, 1.0E6);
        }
    }

    public double wattHours(){ return EnergyUnitConversion.wattHours(storage.get().getEnergyStored(), IFEBridgeHandler.wattToFE()); }

    // Remove cables from connectors whose physical owner or position changed during multiblock formation
    public void refreshConnections(){
        if(behaviour == null || host.getLevel() == null) return;
        var current = ports.get().stream().filter(port -> port.owner().equals(host.getBlockPos())).toList();
        if(current.equals(prevPorts)) return;
        if(prevPorts != null && !host.getLevel().isClientSide) behaviour.breakConnections();
        prevPorts = current;
        behaviour.refreshConnectionEntities();
    }

    private PowerGridEnergyPort port(int idx){
        for(var port : ports.get()) if(port.index() == idx && port.owner().equals(host.getBlockPos())) return port;
        return null;
    }

    private boolean connected(int idx){
        if(behaviour == null) return false;
        for(var endpoint : behaviour.getConnections().keySet()) if(endpoint.getTerminal() / 2 == idx) return true;
        return false;
    }
}
