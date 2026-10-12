package com.rieno.gadgetsandgizmos.lib.shipping;

import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

// Coordinate a named dock request without exposing a mod's docks, controllers or connector implementations
public final class NamedDockingOperation<T>{
    public enum Phase{ IDLE, WAITING, NAVIGATING, DOCKING, COMPLETE, FAILED }
    public enum Reservation{ WAITING, GRANTED, INVALID }

    public interface Host<T>{
        Optional<T> resolve(String name);
        boolean valid(T target);
        Reservation reserve(T target, boolean committed);
        boolean navigate(T target);
        boolean approachReached(T target);
        boolean dock(T target);
        boolean locked(T target);
        void stopMovement();
        void release(T target, boolean disconnect);
    }

    private final Host<T> host;
    private T target;
    private String name = "";
    private Phase phase = Phase.IDLE;
    private long dockingStarted;
    private final long dockingTimeout;

    public NamedDockingOperation(Host<T> host, long dockingTimeout){
        if(host == null || dockingTimeout < 1) throw new IllegalArgumentException("Host and positive docking timeout are required");
        this.host = host;
        this.dockingTimeout = dockingTimeout;
    }

    // Reject unavailable destinations before disturbing an existing operation
    public boolean start(String requested){
        String next = requested == null ? "" : requested.trim();
        if(next.isEmpty()) return false;
        Optional<T> resolved = host.resolve(next);
        if(resolved.isEmpty() || !host.valid(resolved.get())) return false;
        cancel();
        target = resolved.get();
        name = next;
        phase = Phase.WAITING;
        return true;
    }

    public void tick(long tick){
        if(target == null || phase == Phase.IDLE || phase == Phase.FAILED) return;
        if(!host.valid(target)){ fail(); return; }
        boolean committed = phase == Phase.DOCKING || phase == Phase.COMPLETE;
        Reservation reservation = host.reserve(target, committed);
        if(reservation == Reservation.INVALID || committed && reservation != Reservation.GRANTED){ fail(); return; }
        if(phase == Phase.COMPLETE){
            if(!host.locked(target)) fail();
            return;
        }
        if(reservation == Reservation.WAITING){
            if(phase != Phase.WAITING) host.stopMovement();
            phase = Phase.WAITING;
            return;
        }
        if(host.locked(target)){
            host.stopMovement();
            phase = Phase.COMPLETE;
            return;
        }
        if(phase == Phase.WAITING) phase = Phase.NAVIGATING;
        if(phase == Phase.NAVIGATING){
            if(!host.navigate(target)){ fail(); return; }
            if(!host.approachReached(target)) return;
            dockingStarted = tick;
            phase = Phase.DOCKING;
        }
        if(phase == Phase.DOCKING){
            if(!host.dock(target)){ fail(); return; }
            if(host.locked(target)){
                host.stopMovement();
                phase = Phase.COMPLETE;
            }else if(tick - dockingStarted >= dockingTimeout){
                fail();
            }
        }
    }

    public void cancel(){
        if(target != null){
            host.stopMovement();
            host.release(target, phase != Phase.COMPLETE);
        }
        target = null;
        phase = Phase.IDLE;
        name = "";
    }

    private void fail(){
        host.stopMovement();
        host.release(target, true);
        target = null;
        phase = Phase.FAILED;
    }

    public Phase phase(){ return phase; }
    public String name(){ return name; }
    public boolean pending(){ return phase == Phase.WAITING || phase == Phase.NAVIGATING || phase == Phase.DOCKING; }
    public boolean complete(){ return phase == Phase.COMPLETE; }

    // Resolve an exact name, choosing the nearest valid match without interpreting it as a pattern
    public static <T> Optional<T> findNamed(Collection<T> candidates, String name, Function<T, String> label,
                                           Predicate<T> valid, ToDoubleFunction<T> distance){
        String requested = name == null ? "" : name.trim();
        if(requested.isEmpty()) return Optional.empty();
        return candidates.stream().filter(candidate -> {
                    String candidateName = label.apply(candidate);
                    return candidateName != null && requested.equalsIgnoreCase(candidateName.trim());
                })
                .filter(valid).min(Comparator.comparingDouble(candidate -> {
                    double value = distance.applyAsDouble(candidate);
                    return Double.isFinite(value) ? Math.max(0, value) : Double.POSITIVE_INFINITY;
                }));
    }
}
