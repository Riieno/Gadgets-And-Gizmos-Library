package com.rieno.gadgetsandgizmos.lib.mixin;

import com.rieno.gadgetsandgizmos.lib.compat.simulated.SimulatedConnectorStorage;
import dev.simulated_team.simulated.multiloader.energy.SingleBattery;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = SingleBattery.class, remap = false)
public interface SimulatedBatteryBufferMixin extends SimulatedConnectorStorage.EnergyBuffer{
    @Override @Accessor("energy") int gadgetsngizmos$getBufferEnergy();
    @Override @Accessor("energy") void gadgetsngizmos$setBufferEnergy(int amount);
}
