package com.rieno.gadgetsandgizmos.lib.scm;

import net.minecraft.world.phys.Vec3;

import java.util.Objects;

// Expose a gas-volume control as an absolute lift source for physical allocation
public final class ScmBuoyancyControl implements ScmControlProbe{
    private final String adapterId;
    private final Source source;

    public ScmBuoyancyControl(String adapterId, Source source){
        this.adapterId = Objects.requireNonNull(adapterId, "adapterId");
        this.source = Objects.requireNonNull(source, "source");
    }

    @Override public String adapterId(){ return adapterId; }
    @Override public String displayName(){ return "Balloon lift"; }
    @Override public boolean suppressesFallbackProbes(){ return true; }
    @Override public boolean hasForceModel(){ return true; }
    @Override public double minControl(){ return 0.0D; }
    @Override public double maxControl(){ return 1.0D; }
    @Override public boolean isAvailable(){ return source.isAvailable(); }
    @Override public double responseTimeSeconds(){ return positive(source.responseTimeSeconds()); }
    @Override public void restore(){ source.restore(); }

    @Override public double maximumForce(){
        Sample sample = source.sample();
        return sample.availableVolume() * sample.forcePerVolume();
    }

    @Override public void apply(double control){
        if(!source.isAvailable()) return;
        double level = Double.isFinite(control) ? Math.clamp(control, 0.0D, 1.0D) : 0.0D;
        source.applyVolume(level * source.sample().availableVolume());
    }

    @Override public Reading read(){
        Sample sample = source.sample();
        double lift = sample.currentVolume() * sample.forcePerVolume();
        return new Reading(0.0D, lift, lift > 0.0D);
    }

    @Override public Vec3 localEffectDirection(){ return source.sample().localDirection(); }
    @Override public Vec3 localEffectPosition(){ return source.sample().localPosition(); }

    // Share one enclosure's volume without counting its full capacity for every heater
    public static double volumeShare(double maximum, double capacity, double totalMaximum){
        maximum = positive(maximum);
        double total = Math.max(maximum, positive(totalMaximum));
        return total <= 0.0D ? 0.0D : maximum * Math.min(1.0D, positive(capacity) / total);
    }

    // Report current gas separately from the eventual full-output lift
    public record Sample(double availableVolume, double currentVolume, double forcePerVolume,
                         Vec3 localDirection, Vec3 localPosition){
        public Sample{
            availableVolume = positive(availableVolume);
            currentVolume = positive(currentVolume);
            forcePerVolume = positive(forcePerVolume);
            localDirection = finite(localDirection).normalize();
            localPosition = finite(localPosition);
        }
    }

    // Adapt native gas simulation while leaving filling and cooling to its owner
    public interface Source{
        Sample sample();
        void applyVolume(double volume);
        void restore();
        boolean isAvailable();
        default double responseTimeSeconds(){ return 0.0D; }
    }

    private static double positive(double val){ return Double.isFinite(val) ? Math.max(0.0D, val) : 0.0D; }
    private static Vec3 finite(Vec3 val){
        return val != null && Double.isFinite(val.x) && Double.isFinite(val.y) && Double.isFinite(val.z)
                ? val : Vec3.ZERO;
    }
}
