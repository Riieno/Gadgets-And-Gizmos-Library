package com.rieno.gadgetsandgizmos.lib.navigation;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Select one deterministic stop from each ordered schedule layer. */
public final class ScheduleRouteLoop {
    private static final double EPSILON = 1.0E-9D;

    private ScheduleRouteLoop() {
    }

    /**
     * Select the shortest ordered chain, including the final-to-first edge for a cyclic schedule.
     * Candidate input order is the stable tie-breaker. This deliberately returns one physical stop
     * per schedule entry instead of a complete bipartite graph of every possible transition.
     */
    public static <T> List<Candidate<T>> select(
            List<? extends List<Candidate<T>>> layers,
            boolean cyclic
    ) {
        List<List<Candidate<T>>> normalized = normalize(layers);
        if (normalized.isEmpty()) return List.of();
        if (normalized.size() == 1) return List.of(normalized.getFirst().getFirst());

        Selection<T> best = null;
        int firstChoices = cyclic ? normalized.getFirst().size() : 1;
        for (int firstIndex = 0; firstIndex < firstChoices; firstIndex++) {
            Selection<T> candidate = solve(normalized, cyclic, firstIndex);
            if (candidate != null && (best == null
                    || candidate.cost() + EPSILON < best.cost())) {
                best = candidate;
            }
        }
        return best == null ? List.of() : best.stops();
    }

    private static <T> Selection<T> solve(
            List<List<Candidate<T>>> layers,
            boolean cyclic,
            int selectedFirst
    ) {
        int count = layers.size();
        List<double[]> costs = new ArrayList<>(count);
        List<int[]> parents = new ArrayList<>(count);
        double[] initial = new double[layers.getFirst().size()];
        java.util.Arrays.fill(initial, Double.POSITIVE_INFINITY);
        if (cyclic) {
            initial[selectedFirst] = 0.0D;
        } else {
            java.util.Arrays.fill(initial, 0.0D);
        }
        costs.add(initial);
        parents.add(new int[initial.length]);

        for (int layerIndex = 1; layerIndex < count; layerIndex++) {
            List<Candidate<T>> previous = layers.get(layerIndex - 1);
            List<Candidate<T>> current = layers.get(layerIndex);
            double[] layerCosts = new double[current.size()];
            int[] layerParents = new int[current.size()];
            java.util.Arrays.fill(layerCosts, Double.POSITIVE_INFINITY);
            java.util.Arrays.fill(layerParents, -1);
            for (int currentIndex = 0; currentIndex < current.size(); currentIndex++) {
                for (int previousIndex = 0; previousIndex < previous.size(); previousIndex++) {
                    double previousCost = costs.get(layerIndex - 1)[previousIndex];
                    if (!Double.isFinite(previousCost)) continue;
                    double candidateCost = previousCost + distance(
                            previous.get(previousIndex), current.get(currentIndex));
                    if (candidateCost + EPSILON < layerCosts[currentIndex]) {
                        layerCosts[currentIndex] = candidateCost;
                        layerParents[currentIndex] = previousIndex;
                    }
                }
            }
            costs.add(layerCosts);
            parents.add(layerParents);
        }

        int lastLayer = count - 1;
        int selectedLast = -1;
        double bestCost = Double.POSITIVE_INFINITY;
        for (int index = 0; index < layers.get(lastLayer).size(); index++) {
            double cost = costs.get(lastLayer)[index];
            if (cyclic && Double.isFinite(cost)) {
                cost += distance(layers.get(lastLayer).get(index),
                        layers.getFirst().get(selectedFirst));
            }
            if (cost + EPSILON < bestCost) {
                bestCost = cost;
                selectedLast = index;
            }
        }
        if (selectedLast < 0) return null;

        List<Candidate<T>> selected = new ArrayList<>(count);
        for (int index = 0; index < count; index++) selected.add(null);
        int cursor = selectedLast;
        for (int layerIndex = lastLayer; layerIndex >= 0; layerIndex--) {
            selected.set(layerIndex, layers.get(layerIndex).get(cursor));
            if (layerIndex > 0) cursor = parents.get(layerIndex)[cursor];
        }
        return new Selection<>(List.copyOf(selected), bestCost);
    }

    private static <T> List<List<Candidate<T>>> normalize(
            List<? extends List<Candidate<T>>> layers
    ) {
        if (layers == null || layers.isEmpty()) return List.of();
        List<List<Candidate<T>>> normalized = new ArrayList<>();
        for (List<Candidate<T>> layer : layers) {
            List<Candidate<T>> candidates = layer == null ? List.of() : layer.stream()
                    .filter(candidate -> candidate != null && candidate.position() != null)
                    .toList();
            if (!candidates.isEmpty()) normalized.add(candidates);
        }
        return List.copyOf(normalized);
    }

    private static double distance(Candidate<?> first, Candidate<?> second) {
        return first.position().distanceTo(second.position());
    }

    /** A caller-owned stop value and its route-space position. */
    public record Candidate<T>(T value, Vec3 position) {
        public Candidate {
            position = position == null ? Vec3.ZERO : position;
        }
    }

    private record Selection<T>(List<Candidate<T>> stops, double cost) {
    }
}
