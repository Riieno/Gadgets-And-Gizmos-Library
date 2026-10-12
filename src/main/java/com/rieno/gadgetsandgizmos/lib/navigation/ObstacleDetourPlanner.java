package com.rieno.gadgetsandgizmos.lib.navigation;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;

// Prepare a committed bypass using the host's live swept-hull clearance policy
public final class ObstacleDetourPlanner{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final double EPSILON = 1.0E-6D;
    private static final Vec3 UP = new Vec3(0.0D, 1.0D, 0.0D);
    private static final double[] OFFSET_SCALES = {1.0D, 1.5D, 2.0D, 3.0D, 4.0D, 6.0D, 8.0D};

    private ObstacleDetourPlanner(){}

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Retain incremental candidate validation instead of changing escape sides every tick
    public static QueuedPlan queue(Request request){
        return new QueuedPlan(Objects.requireNonNull(request, "request"));
    }

    // Configure clearance offsets independently of a mod's vehicle implementation
    public record Request(Vec3 start, Vec3 rejoin, Vec3 travelDirection,
                          double horizontalClearance, double verticalClearance,
                          double maximumOffset, boolean verticalMovement,
                          boolean preferUp){
        public Request{
            start = finite(start);
            rejoin = finite(rejoin);
            travelDirection = normalize(travelDirection, rejoin.subtract(start));
            horizontalClearance = positive(horizontalClearance, 1.0D);
            verticalClearance = positive(verticalClearance, 1.0D);
            maximumOffset = Math.max(Math.max(horizontalClearance, verticalClearance),
                    positive(maximumOffset, 32.0D));
        }
    }

    // The host validates the complete translated hull and rejects unavailable geometry
    @FunctionalInterface
    public interface Probe{
        boolean clear(Vec3 start, Vec3 end);
    }

    public static final class QueuedPlan{
        private final Request request;
        private final List<Candidate> candidates;
        private int candidateIdx;
        private int legIdx;
        private boolean finished;
        private List<Vec3> waypoints = List.of();

        private QueuedPlan(Request request){
            this.request = request;
            candidates = candidates(request);
            finished = request.start().distanceToSqr(request.rejoin()) <= EPSILON;
        }

        // Bound both the number of world sweeps and the caller's measured tick allowance
        public void advance(Probe probe, int maximumProbes, BooleanSupplier workAvailable){
            Objects.requireNonNull(probe, "probe");
            Objects.requireNonNull(workAvailable, "workAvailable");
            for(int work = 0; !finished && work < maximumProbes && workAvailable.getAsBoolean(); work++){
                if(candidateIdx >= candidates.size()){
                    finished = true;
                    break;
                }
                Candidate candidate = candidates.get(candidateIdx);
                Vec3 start = legIdx == 0 ? request.start() : candidate.waypoints().get(legIdx - 1);
                Vec3 end = candidate.waypoints().get(legIdx);
                if(!probe.clear(start, end)){
                    candidateIdx++;
                    legIdx = 0;
                    continue;
                }
                if(++legIdx == candidate.waypoints().size()){
                    waypoints = candidate.waypoints();
                    finished = true;
                }
            }
            if(candidateIdx >= candidates.size()) finished = true;
        }

        // An accepted bypass includes every validated leg through the route rejoin
        public List<Vec3> waypoints(){
            return waypoints;
        }

        public boolean finished(){
            return finished;
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Opposing vehicles take their own right-hand side, with opposite vertical preferences
    private static List<Candidate> candidates(Request request){
        Vec3 right = normalize(request.travelDirection().cross(UP), new Vec3(1.0D, 0.0D, 0.0D));
        Vec3 vertical = request.preferUp() ? UP : UP.scale(-1.0D);
        List<Candidate> res = new ArrayList<>();
        for(double scale : OFFSET_SCALES){
            double horizontal = request.horizontalClearance() * scale;
            double height = request.verticalClearance() * scale;
            if(horizontal <= request.maximumOffset() + EPSILON){
                addCandidate(res, request, right.scale(horizontal), 0.0D);
                addCandidate(res, request, right.scale(-horizontal), 0.02D);
            }
            if(!request.verticalMovement() || height > request.maximumOffset() + EPSILON) continue;
            addCandidate(res, request, vertical.scale(height), 0.0D);
            addCandidate(res, request, vertical.scale(-height), 0.02D);
            if(horizontal > request.maximumOffset() + EPSILON) continue;
            addCandidate(res, request, right.scale(horizontal).add(vertical.scale(height)), 0.01D);
            addCandidate(res, request, right.scale(-horizontal).add(vertical.scale(height)), 0.03D);
            addCandidate(res, request, right.scale(horizontal).subtract(vertical.scale(height)), 0.03D);
            addCandidate(res, request, right.scale(-horizontal).subtract(vertical.scale(height)), 0.05D);
        }
        res.sort(Comparator.comparingDouble(Candidate::cost));
        return List.copyOf(res);
    }

    // A pure initial sidestep can leave a close obstruction which blocks every forward diagonal
    private static void addCandidate(List<Candidate> candidates, Request request, Vec3 offset, double bias){
        List<Vec3> points = List.of(request.start().add(offset), request.rejoin().add(offset), request.rejoin());
        double cost = request.start().distanceTo(points.getFirst())
                + points.getFirst().distanceTo(points.get(1)) + points.get(1).distanceTo(points.getLast());
        candidates.add(new Candidate(points, cost + offset.length() * bias));
    }

    private record Candidate(List<Vec3> waypoints, double cost){}

    private static Vec3 normalize(Vec3 val, Vec3 fallback){
        Vec3 res = finite(val);
        if(res.lengthSqr() > EPSILON) return res.normalize();
        res = finite(fallback);
        return res.lengthSqr() > EPSILON ? res.normalize() : new Vec3(0.0D, 0.0D, 1.0D);
    }

    private static Vec3 finite(Vec3 val){
        return val != null && Double.isFinite(val.x) && Double.isFinite(val.y) && Double.isFinite(val.z)
                ? val : Vec3.ZERO;
    }

    private static double positive(double val, double fallback){
        return Double.isFinite(val) && val > 0.0D ? val : fallback;
    }
}
