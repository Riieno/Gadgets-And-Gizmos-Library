package com.rieno.gadgetsandgizmos.lib.power.powergrid;

import org.patryk3211.powergrid.electricity.sim.node.IElectricNode;
import org.patryk3211.powergrid.electricity.sim.node.VoltageSourceCoupling;
import org.patryk3211.powergrid.electricity.sim.solver.IAdmittanceAdder;
import org.patryk3211.powergrid.electricity.sim.solver.IOuterHook;
import org.patryk3211.powergrid.electricity.sim.solver.IResidualAdder;
import org.patryk3211.powergrid.electricity.sim.solver.ISolverHook;

// Limit the solved circuit current before native cables accumulate any heat
final class PowerGridLimitedSource extends VoltageSourceCoupling implements IOuterHook, ISolverHook{
    private record Settings(double volts, double resistance, double amps, boolean input, boolean output){}
    private volatile Settings settings = new Settings(0, 1000, 0, false, false);
    private Settings active = settings;
    private double coefficient = 1;
    private double diagonal = -1000;
    private double residual;
    private int mode;

    PowerGridLimitedSource(IElectricNode positive, IElectricNode negative){ super(positive, negative, 1000F); }

    void configure(double volts, double resistance, double amps, boolean input, boolean output){
        settings = new Settings(volts, resistance, amps, input, output);
    }

    @Override public void preSolve(){ active = settings; super.setVoltage(active.volts()); }
    @Override public void addStaticResidual(IResidualAdder res){}
    @Override public void addResidual(IResidualAdder res){ res.add(getIndex(), residual); }

    @Override public void couple(IAdmittanceAdder matrix){
        int idx = getIndex(), positive = getPositive().getIndex(), negative = getNegative().getIndex();
        matrix.add(positive, idx, 1);
        matrix.add(negative, idx, -1);
        matrix.add(idx, positive, coefficient);
        matrix.add(idx, negative, -coefficient);
        matrix.add(idx, idx, diagonal);
    }

    @Override public void startIteration(int iteration){
        double volts = getPositive().getVoltage() - getNegative().getVoltage();
        double requested = (volts - active.volts()) / active.resistance();
        double positive = active.amps(), negative = -active.amps();
        double direction = volts == 0 ? active.volts() : volts;
        // Either voltage polarity can charge; the sign of V times I determines energy direction
        if(!active.input()){
            if(direction >= 0) positive = 0;
            if(direction <= 0) negative = 0;
        }
        if(!active.output()){
            if(direction > 0) negative = 0;
            if(direction < 0) positive = 0;
        }
        if(mode > 0 && requested < positive || mode < 0 && requested > negative) mode = 0;
        if(mode == 0){
            if(getCurrent() > positive && requested > positive) mode = 1;
            if(getCurrent() < negative && requested < negative) mode = -1;
        }
        double limited = mode > 0 ? positive : negative;
        boolean saturated = mode != 0;
        double nextCoefficient = saturated ? 0 : 1;
        double nextDiagonal = saturated ? -1 : -active.resistance();
        if(getNetwork() != null){
            getNetwork().alterConductanceMatrix(getIndex(), getPositive().getIndex(), nextCoefficient - coefficient);
            getNetwork().alterConductanceMatrix(getIndex(), getNegative().getIndex(), coefficient - nextCoefficient);
            getNetwork().alterConductanceMatrix(getIndex(), getIndex(), nextDiagonal - diagonal);
        }
        coefficient = nextCoefficient;
        diagonal = nextDiagonal;
        residual = saturated ? limited : -active.volts();
    }
}
