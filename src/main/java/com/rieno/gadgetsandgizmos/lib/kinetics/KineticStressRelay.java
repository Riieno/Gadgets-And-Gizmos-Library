package com.rieno.gadgetsandgizmos.lib.kinetics;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.simibubi.create.content.kinetics.KineticNetwork;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

// Relay one kinetic network into another without creating stress capacity
public final class KineticStressRelay {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Input kinetic node
    @Nullable
    private KineticBlockEntity input;
    // Generated output kinetic node
    @Nullable
    private GeneratingKineticBlockEntity output;
    // Consumer stress forwarded to the input network
    private float forwardedStressBase;
    // Input capacity made available to the output network
    private float suppliedCapacityBase;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Select the independently driven input and generated output
    public void configure(@Nullable KineticBlockEntity input,
                          @Nullable GeneratingKineticBlockEntity output) {
        if (this.input == input && this.output == output) return;
        clear();
        this.input = input;
        this.output = output;
    }

    // Clear the relay and its network entries
    public void clear() {
        KineticBlockEntity prevInput = input;
        GeneratingKineticBlockEntity prevOutput = output;
        input = null;
        output = null;
        forwardedStressBase = 0.0f;
        suppliedCapacityBase = 0.0f;
        updateStress(prevInput, 0.0f);
        updateCapacity(prevOutput, 0.0f);
    }

    // Refresh stress and capacity across the relay
    public void refresh() {
        if (!isActive()) {
            setValues(0.0f, 0.0f);
            return;
        }

        float speed = Math.abs(input.getTheoreticalSpeed());
        if (!Float.isFinite(speed) || Mth.equal(speed, 0.0f)) {
            setValues(0.0f, 0.0f);
            return;
        }

        KineticNetwork inputNetwork = input.getOrCreateNetwork();
        float inputCapacity = finitePositive(inputNetwork.calculateCapacity());
        float inputStress = finitePositive(inputNetwork.calculateStress());
        float forwardedStress = finitePositive(forwardedStressBase * speed);
        float localInputStress = Math.max(inputStress - forwardedStress, 0.0f);
        float availableCapacityBase = Math.max(inputCapacity - localInputStress, 0.0f) / speed;

        suppliedCapacityBase = finitePositive(availableCapacityBase);
        updateCapacity(output, suppliedCapacityBase);

        float outputStress = finitePositive(output.getOrCreateNetwork().calculateStress());
        forwardedStressBase = finitePositive(outputStress / speed);
        updateStress(input, forwardedStressBase);
    }

    // Get the stress applied by this endpoint
    public float stressAppliedBy(KineticBlockEntity endpoint) {
        return endpoint == input ? forwardedStressBase : 0.0f;
    }

    // Get the capacity provided by this endpoint
    public float capacityProvidedBy(KineticBlockEntity endpoint) {
        return endpoint == output ? suppliedCapacityBase : 0.0f;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Helpers
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Check if both relay networks are available
    private boolean isActive() {
        return input != null && output != null
                && input.getLevel() != null && !input.getLevel().isClientSide
                && input.hasNetwork() && output.hasNetwork() && output.isSource();
    }

    // Set both relay values
    private void setValues(float stressBase, float capacityBase) {
        forwardedStressBase = stressBase;
        suppliedCapacityBase = capacityBase;
        updateStress(input, stressBase);
        updateCapacity(output, capacityBase);
    }

    // Update one network stress entry
    private static void updateStress(@Nullable KineticBlockEntity endpoint, float stressBase) {
        if (endpoint == null || endpoint.getLevel() == null || endpoint.getLevel().isClientSide
                || !endpoint.hasNetwork()) return;
        endpoint.getOrCreateNetwork().updateStressFor(endpoint, stressBase);
    }

    // Update one network capacity entry
    private static void updateCapacity(@Nullable GeneratingKineticBlockEntity endpoint, float capacityBase) {
        if (endpoint == null || endpoint.getLevel() == null || endpoint.getLevel().isClientSide
                || !endpoint.hasNetwork() || !endpoint.isSource()) return;
        endpoint.getOrCreateNetwork().updateCapacityFor(endpoint, capacityBase);
    }

    // Get a finite positive value
    private static float finitePositive(float value) {
        return Float.isFinite(value) ? Math.max(value, 0.0f) : 0.0f;
    }
}
