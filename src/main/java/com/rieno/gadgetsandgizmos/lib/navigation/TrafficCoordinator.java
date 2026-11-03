package com.rieno.gadgetsandgizmos.lib.navigation;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.physics.SubLevelParticleOcclusion;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Share right-of-way and verified moves which clear a blocked vehicle's route
public final class TrafficCoordinator{
/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        DEFAULTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    public enum Action{ FOLLOW, WAIT, MOVE_ASIDE }
    public enum Clearance{ CLEAR, BLOCKED, PENDING }
    public interface EscapeProbe{
        Clearance test(UUID vehicleId, Vec3 start, Vec3 target);
    }
    public record Vehicle(RouteTrafficPriority.Participant participant, boolean movable, boolean blocked, boolean reroutable){
        public Vehicle(RouteTrafficPriority.Participant participant, boolean movable, boolean blocked){ this(participant, movable, blocked, true); }
    }
    public record Motion(Vec3 start, Vec3 target, UUID clearingFor, long startedTick){}
    public record Directive(RouteTrafficPriority.Decision traffic, Action action, Motion motion){
        public static final Directive CLEAR = new Directive(RouteTrafficPriority.Decision.clear(), Action.FOLLOW, null);
        public boolean managed(){ return action != Action.FOLLOW; }
    }

    private final Map<UUID, Progress> progress = new HashMap<>();
    private final Map<UUID, Motion> motions = new LinkedHashMap<>();
    private final Map<UUID, Attempt> attempts = new HashMap<>();
    private final Map<UUID, Long> leaderCooldown = new HashMap<>();
    private Map<UUID, Directive> directives = Map.of();
    private long frameTick = Long.MIN_VALUE;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        FUNCTIONS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    public long frameTick(){ return frameTick; }
    public Directive directive(UUID id){ return directives.getOrDefault(id, Directive.CLEAR); }

    // Probe at most one escape per frame and keep accepted targets through the passing manoeuvre
    public void update(long tick, Collection<Vehicle> vehicles, RouteTrafficPriority.Settings settings, EscapeProbe probe){
        if(frameTick == tick) return;
        frameTick = tick;
        Map<UUID, Vehicle> fleet = new LinkedHashMap<>();
        if(vehicles != null) for(Vehicle vehicle : vehicles){
            if(vehicle != null && vehicle.participant() != null) fleet.put(vehicle.participant().vehicleId(), vehicle);
        }
        progress.keySet().retainAll(fleet.keySet());
        attempts.keySet().retainAll(fleet.keySet());
        leaderCooldown.keySet().retainAll(fleet.keySet());
        for(var entry : fleet.entrySet()){
            Vec3 pos = entry.getValue().participant().position();
            Progress prev = progress.get(entry.getKey());
            if(prev == null || tick < prev.since || pos.distanceToSqr(prev.position) > 0.25D)
                progress.put(entry.getKey(), new Progress(pos, tick));
        }
        releasePassedMotions(fleet, tick);
        motions.entrySet().removeIf(entry -> !escapeClear(fleet.get(entry.getKey()).participant(), entry.getValue().target(),
                entry.getValue().clearingFor(), fleet));
        List<RouteTrafficPriority.Participant> traffic = new ArrayList<>();
        for(var entry : fleet.entrySet()){
            var participant = entry.getValue().participant();
            Motion motion = motions.get(entry.getKey());
            if(motion != null) participant = new RouteTrafficPriority.Participant(participant.vehicleId(), participant.position(),
                    participant.velocity(), List.of(participant.position(), motion.target()), participant.mode(),
                    participant.clearanceRadius(), participant.verticalClearance(), 2.0D, participant.hull());
            traffic.add(participant);
        }
        Map<UUID, RouteTrafficPriority.Decision> decisions = RouteTrafficPriority.resolveFleet(traffic, settings);
        Map<UUID, Directive> res = new HashMap<>();
        for(UUID id : fleet.keySet()) res.put(id, new Directive(decisions.get(id), Action.FOLLOW, null));
        for(var entry : motions.entrySet()) res.put(entry.getKey(), new Directive(decisions.get(entry.getKey()), Action.MOVE_ASIDE, entry.getValue()));

        List<Vehicle> stalled = fleet.values().stream().filter(vehicle -> stalled(vehicle, tick))
                .sorted(Comparator.<Vehicle>comparingInt(vehicle -> decisions.get(vehicle.participant().vehicleId()).yield() ? 1 : 0)
                        .thenComparing(vehicle -> vehicle.participant().vehicleId())).toList();
        boolean probed = false;
        for(Vehicle leader : stalled){
            UUID leaderId = leader.participant().vehicleId();
            if(motions.containsKey(leaderId) || leaderCooldown.getOrDefault(leaderId, Long.MIN_VALUE) > tick) continue;
            List<Vehicle> movers = fleet.values().stream().filter(other -> !other.participant().vehicleId().equals(leaderId)
                    && other.movable() && stalled(other, tick)
                    && blocks(leader.participant(), other.participant().position(), other.participant().hull())).toList();
            boolean passing = fleet.values().stream().anyMatch(other -> !other.participant().vehicleId().equals(leaderId)
                    && other.participant().velocity().length() > 0.25D
                    && blocks(leader.participant(), other.participant().position(), other.participant().hull()));
            if(movers.isEmpty() && leader.movable() && leader.reroutable() && !passing && !decisions.get(leaderId).yield()) movers = List.of(leader);
            for(Vehicle other : movers){
                UUID id = other.participant().vehicleId();
                if(motions.containsKey(id)) continue;
                boolean moving = motions.entrySet().stream().anyMatch(entry -> entry.getValue().clearingFor().equals(leaderId)
                        && fleet.get(entry.getKey()).participant().position().distanceTo(entry.getValue().target()) > 1.0D);
                if(moving) continue;
                Attempt attempt = attempts.get(id);
                if(attempt == null || !attempt.leader.equals(leaderId) || attempt.start.distanceTo(other.participant().position()) > 1.0D){
                    attempt = new Attempt(leaderId, other.participant().position());
                    attempts.put(id, attempt);
                }
                if(attempt.retryTick > tick) continue;
                List<Vec3> candidates = candidates(other.participant(), leader.participant(), attempt.start);
                while(attempt.idx < candidates.size() && !escapeClear(other.participant(), candidates.get(attempt.idx), leaderId, fleet)) attempt.idx++;
                if(attempt.idx >= candidates.size()){
                    attempt.idx = 0;
                    attempt.retryTick = tick + 20L;
                    continue;
                }
                if(probed) continue;
                probed = true;
                Vec3 target = candidates.get(attempt.idx);
                Clearance clearance = probe.test(id, attempt.start, target);
                if(clearance == Clearance.CLEAR){
                    Motion motion = new Motion(attempt.start, target, leaderId, tick);
                    motions.put(id, motion);
                    res.put(id, new Directive(decisions.get(id), Action.MOVE_ASIDE, motion));
                    attempts.remove(id);
                }else if(clearance == Clearance.BLOCKED){
                    attempt.idx++;
                }else res.put(id, new Directive(decisions.get(id), Action.WAIT, null));
            }
        }
        directives = Map.copyOf(res);
    }

    // Reopen local planning when a retained escape becomes obstructed
    public void invalidate(UUID id, long tick){
        Motion motion = motions.remove(id);
        if(motion != null){
            Attempt attempt = new Attempt(motion.clearingFor(), motion.start());
            attempt.idx = 1;
            attempt.retryTick = tick + 4L;
            attempts.put(id, attempt);
        }
    }

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        HELPERS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    private boolean stalled(Vehicle vehicle, long tick){
        Progress val = progress.get(vehicle.participant().vehicleId());
        return vehicle.blocked() && val != null && tick - val.since >= 20L;
    }

    private void releasePassedMotions(Map<UUID, Vehicle> fleet, long tick){
        motions.entrySet().removeIf(entry -> {
            Motion motion = entry.getValue();
            Vehicle vehicle = fleet.get(entry.getKey()), leader = fleet.get(motion.clearingFor());
            if(vehicle == null || leader == null || tick < motion.startedTick()) return true;
            Progress val = progress.get(entry.getKey());
            if(tick - motion.startedTick() > 400L || tick - val.since > 80L
                    && vehicle.participant().position().distanceTo(motion.target()) > 1.0D){
                leaderCooldown.put(motion.clearingFor(), tick + 80L);
                return true;
            }
            if(entry.getKey().equals(motion.clearingFor())) return vehicle.participant().position().distanceTo(motion.target()) <= 0.5D;
            if(tick - motion.startedTick() < 10L) return false;
            if(!blocks(leader.participant(), motion.start(), vehicle.participant().hull())) return true;
            if(vehicle.participant().position().distanceTo(motion.target()) <= 1.0D
                    && tick - progress.get(motion.clearingFor()).since > 100L){
                leaderCooldown.put(motion.clearingFor(), tick + 80L);
                return true;
            }
            return false;
        });
    }

    private static boolean blocks(RouteTrafficPriority.Participant leader, Vec3 pos, List<AABB> hull){
        double distance = Math.max(12.0D, span(leader.hull()) * 2);
        return DockTrafficGeometry.routesConflict(DockTrafficGeometry.routePrefix(leader.route(), distance),
                leader.hull(), List.of(pos), hull);
    }

    private boolean escapeClear(RouteTrafficPriority.Participant subject, Vec3 target, UUID leaderId, Map<UUID, Vehicle> fleet){
        Vec3 start = subject.position(), delta = target.subtract(start);
        double length = delta.length();
        if(length <= 0.25D) return true;
        Vec3 direction = delta.scale(1.0D / length);
        List<AABB> bounds = subject.hull().stream().map(box -> box.move(start)).toList();
        for(Vehicle vehicle : fleet.values()){
            var other = vehicle.participant();
            if(other.vehicleId().equals(subject.vehicleId())) continue;
            for(AABB box : other.hull()){
                AABB physical = box.move(other.position()), obstacle = physical.inflate(0.5D);
                if(SubLevelParticleOcclusion.allowsOverlapDeparture(bounds, obstacle, direction, length)
                        && retainsSeparatedFaces(bounds, physical, direction)) continue;
                if(DockTrafficGeometry.blocker(List.of(start, target), subject.hull(),
                        List.of(new DockTrafficGeometry.Obstacle(other.vehicleId(), List.of(obstacle)))) != null) return false;
            }
            Motion motion = motions.get(other.vehicleId());
            if(motion != null && DockTrafficGeometry.routesConflict(List.of(start, target), subject.hull(),
                    List.of(other.position(), motion.target()), other.hull())) return false;
        }
        Vehicle leader = fleet.get(leaderId);
        return leader != null && !blocks(leader.participant(), target, subject.hull());
    }

    // Leaving a padded envelope must not close the physical gap on another axis
    private static boolean retainsSeparatedFaces(List<AABB> bounds, AABB other, Vec3 direction){
        for(AABB hull : bounds){
            if(hull.maxX <= other.minX && direction.x > 1.0E-6D || hull.minX >= other.maxX && direction.x < -1.0E-6D
                    || hull.maxY <= other.minY && direction.y > 1.0E-6D || hull.minY >= other.maxY && direction.y < -1.0E-6D
                    || hull.maxZ <= other.minZ && direction.z > 1.0E-6D || hull.minZ >= other.maxZ && direction.z < -1.0E-6D) return false;
        }
        return true;
    }

    private static List<Vec3> candidates(RouteTrafficPriority.Participant subject, RouteTrafficPriority.Participant leader, Vec3 origin){
        Vec3 forward = leader.route().stream().skip(1).map(pos -> pos.subtract(leader.position()))
                .filter(val -> val.lengthSqr() > 1.0E-8D).findFirst().orElse(new Vec3(0, 0, 1)).normalize();
        Vec3 right = new Vec3(-forward.z, 0, forward.x).normalize();
        if(right.lengthSqr() < 1.0E-8D) right = new Vec3(1, 0, 0);
        if(!RouteTrafficPriority.prefersUpperPass(subject.vehicleId(), leader.vehicleId())) right = right.scale(-1);
        double distance = Math.min(48.0D, Math.max(4.0D, span(subject.hull()) + span(leader.hull()) + 2));
        List<Vec3> directions = subject.mode() == SablePathfinder.RouteMode.GROUND ? List.of(forward.scale(-1), forward)
                : List.of(right, new Vec3(0, 1, 0), right.scale(-1), forward.scale(-1), new Vec3(0, -1, 0), forward);
        return directions.stream().map(dir -> origin.add(dir.scale(distance))).toList();
    }

    private static double span(List<AABB> hull){
        double res = 1.0D;
        for(AABB box : hull) res = Math.max(res, Math.max(box.getXsize(), Math.max(box.getYsize(), box.getZsize())));
        return res;
    }

    private record Progress(Vec3 position, long since){}
    private static final class Attempt{
        private final UUID leader;
        private final Vec3 start;
        private int idx;
        private long retryTick = Long.MIN_VALUE;
        private Attempt(UUID leader, Vec3 start){ this.leader = leader; this.start = start; }
    }
}
