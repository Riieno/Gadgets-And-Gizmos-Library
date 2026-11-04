package com.rieno.gadgetsandgizmos.lib.power.powergrid;

import com.rieno.gadgetsandgizmos.lib.power.ElectricalStorage;
import com.rieno.gadgetsandgizmos.lib.power.EnergyUnitConversion;
import org.patryk3211.powergrid.electricity.base.IElectricEntity;
import org.patryk3211.powergrid.electricity.febridge.IFEBridgeHandler;

// Convert one native two-terminal circuit without creating a second energy buffer
final class PowerGridStorageCircuit{
    private PowerGridLimitedSource source;
    private long settledTick = Long.MIN_VALUE;

    void build(IElectricEntity.CircuitBuilder builder){
        builder.setTerminalCount(2);
        source = new PowerGridLimitedSource(builder.terminalNode(0), builder.terminalNode(1));
        builder.add(source);
    }

    double watts(){
        double val = amps() * volts();
        return Double.isFinite(val) ? val : 0;
    }

    double volts(){
        if(source == null || source.getNetwork() == null || !source.isConverged()) return 0;
        double val = source.getPositive().getVoltage() - source.getNegative().getVoltage();
        return Double.isFinite(val) ? val : 0;
    }

    double amps(){
        if(source == null || source.getNetwork() == null || !source.isConverged()) return 0;
        double val = source.getCurrent();
        return Double.isFinite(val) ? val : 0;
    }

    void settle(ElectricalStorage host, long tick, boolean connected){
        if(settledTick == tick || source == null) return;
        settledTick = tick;
        double watts = watts();
        if(!connected || Math.abs(watts) <= 1.0E-9) return;
        EnergyUnitConversion.transfer(host.electricalStorage(), watts, IFEBridgeHandler.wattToFE(), Long.MAX_VALUE);
    }

    void configure(long stored, long capacity, int connectors, double outputWatts, double outputVolts, double safeAmps){
        if(source == null) return;
        double wattsToFE = IFEBridgeHandler.wattToFE();
        if(!Double.isFinite(wattsToFE) || wattsToFE <= 0){ disable(); return; }
        long available = stored / Math.max(1, connectors);
        double maximum = Math.min(outputWatts, available / wattsToFE);
        if(maximum <= 0 || outputVolts == 0){ source.configure(0, 1000, safeAmps, stored < capacity, false); return; }
        double voltage = Math.copySign(Math.min(Math.abs(outputVolts), Math.sqrt(4 * maximum * 1.0E6)), outputVolts);
        // The source delivers at most V squared / (4 R) watts to its external load
        double target = Math.clamp(voltage * voltage / (4 * maximum), 1.0E-6, 1.0E6);
        source.configure(voltage, target, safeAmps, stored < capacity, true);
    }

    void disable(){ if(source != null) source.configure(0, 1000, 0, false, false); }
}
