package com.rieno.gadgetsandgizmos.lib.scm;

// Normalize unsigned SCM control strength without changing directional inputs
public final class ScmControlInputs{
    private ScmControlInputs(){}

    public static double strength(double val){
        return Double.isFinite(val) ? Math.clamp(val, 0.0D, 1.0D) : 0.0D;
    }
}
