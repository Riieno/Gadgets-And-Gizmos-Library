package com.rieno.gadgetsandgizmos.lib.power.alternator;

// Supply the speed, stress and output limits used by alternator calculations
public interface AlternatorTuning {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the minimum RPM
    double minRpm();

    // Get the rated RPM
    double ratedRpm();

    // Get the maximum FE per tick
    int maxFePerTick();

    // Get the maximum stress impact
    double maxStressImpact();
}
