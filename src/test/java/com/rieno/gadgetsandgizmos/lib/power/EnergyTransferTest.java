package com.rieno.gadgetsandgizmos.lib.power;

import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EnergyTransferTest{
    @Test void suppliesTheCombinedDemandOfSeveralMachines(){
        var battery = new EnergyStorage(1_000_000, 1_000_000, 1_000_000, 1_000_000);
        var first = new EnergyStorage(50_000, 50_000, 0);
        var second = new EnergyStorage(75_000, 75_000, 0);
        assertEquals(125_000L, EnergyTransfer.transfer(List.of(battery), List.of(first, second)));
        assertEquals(875_000, battery.getEnergyStored());
        assertEquals(50_000, first.getEnergyStored());
        assertEquals(75_000, second.getEnergyStored());
    }

    @Test void neverRoutesEnergyBackIntoTheSameMultiblock(){
        var battery = new EnergyStorage(1_000_000, 1_000_000, 1_000_000, 500_000);
        assertEquals(0, EnergyTransfer.transfer(List.of(battery, battery), List.of(battery, battery)));
        assertEquals(500_000, battery.getEnergyStored());
    }

    @Test void repeatedFacesDoNotMultiplyAHandlersTransferLimit(){
        var source = new EnergyStorage(100_000, 100_000, 1_000, 100_000);
        var target = new EnergyStorage(100_000, 100_000, 0);
        assertEquals(1_000, EnergyTransfer.transfer(List.of(source, source), List.of(target, target)));
        assertEquals(99_000, source.getEnergyStored());
    }

    @Test void respectsReceiverAndSourceLimitsWithoutLosingEnergy(){
        var source = new EnergyStorage(100_000, 100_000, 4_000, 100_000);
        var target = new EnergyStorage(100_000, 3_000, 0);
        assertEquals(3_000, EnergyTransfer.transfer(List.of(source), List.of(target)));
        assertEquals(97_000, source.getEnergyStored());
        assertEquals(3_000, target.getEnergyStored());
    }

    @Test void feedsMachinesBeforeChargingAnotherBattery(){
        var source = new EnergyStorage(10_000, 10_000, 10_000, 10_000);
        var battery = new EnergyStorage(10_000, 10_000, 10_000);
        var machine = new EnergyStorage(8_000, 8_000, 0);
        assertEquals(10_000, EnergyTransfer.transfer(List.of(source), List.of(battery, machine)));
        assertEquals(8_000, machine.getEnergyStored());
        assertEquals(2_000, battery.getEnergyStored());
        assertEquals(0, source.getEnergyStored());
    }

    @Test void severalReceiversCanDrawMoreThanOneIntegerRequestInTotal(){
        var source = new LongSource(5_000_000_000L);
        var first = new EnergyStorage(Integer.MAX_VALUE, Integer.MAX_VALUE, 0);
        var second = new EnergyStorage(Integer.MAX_VALUE, Integer.MAX_VALUE, 0);
        assertEquals(2L * Integer.MAX_VALUE, EnergyTransfer.transfer(List.of(source), List.of(first, second)));
        assertEquals(5_000_000_000L - 2L * Integer.MAX_VALUE, source.energy);
    }

    private static final class LongSource implements IEnergyStorage{
        private long energy;
        private LongSource(long energy){ this.energy = energy; }
        @Override public int extractEnergy(int maximum, boolean simulate){
            int extracted = (int) Math.min(energy, maximum);
            if(!simulate) energy -= extracted;
            return extracted;
        }
        @Override public int receiveEnergy(int maximum, boolean simulate){ return 0; }
        @Override public int getEnergyStored(){ return (int) Math.min(Integer.MAX_VALUE, energy); }
        @Override public int getMaxEnergyStored(){ return Integer.MAX_VALUE; }
        @Override public boolean canExtract(){ return true; }
        @Override public boolean canReceive(){ return false; }
    }
}
