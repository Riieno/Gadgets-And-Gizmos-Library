package com.rieno.gadgetsandgizmos.lib.graph;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.function.Predicate;

// Queue immediate and delayed graph events while keeping both queues safely bounded
public final class GraphEventScheduler<E> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Maximum immediate
    private final int maximumImmediate;
    // Maximum scheduled
    private final int maximumScheduled;
    // Tracked immediate
    private final Queue<E> immediate = new ArrayDeque<>();
    // Tracked scheduled
    private final Queue<Scheduled<E>> scheduled = new PriorityQueue<>((a, b) -> Long.compare(a.tick(), b.tick()));

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the graph event scheduler
    public GraphEventScheduler(int maximumImmediate, int maximumScheduled) {
        if (maximumImmediate < 1 || maximumScheduled < 1) {
            throw new IllegalArgumentException("Graph scheduler limits must be positive");
        }
        this.maximumImmediate = maximumImmediate;
        this.maximumScheduled = maximumScheduled;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Queue the graph event
    public boolean enqueue(E event) {
        return event != null && immediate.size() < maximumImmediate && immediate.offer(event);
    }

    // Schedule the graph event scheduler
    public boolean schedule(long tick, E event) {
        return event != null && scheduled.size() < maximumScheduled
                && scheduled.offer(new Scheduled<>(tick, event));
    }

    // Release the graph event scheduler
    public void release(long tick) {
        while (!scheduled.isEmpty() && scheduled.peek().tick() <= tick
                && immediate.size() < maximumImmediate) {
            immediate.offer(scheduled.remove().event());
        }
    }

    // Poll the next scheduled graph event
    public E poll() {
        return immediate.poll();
    }

    // Check if this has immediate
    public boolean hasImmediate() {
        return !immediate.isEmpty();
    }

    // Check if this has work
    public boolean hasWork() {
        return !immediate.isEmpty() || !scheduled.isEmpty();
    }

    // Get the immediate size
    public int immediateSize() {
        return immediate.size();
    }

    // Get the scheduled size
    public int scheduledSize() {
        return scheduled.size();
    }

    // Remove immediate events matching the predicate
    public void removeImmediateIf(Predicate<E> predicate) {
        immediate.removeIf(predicate);
    }

    // Remove scheduled events matching the predicate
    public void removeScheduledIf(Predicate<E> predicate) {
        scheduled.removeIf(entry -> predicate.test(entry.event()));
    }

    // Get the immediate snapshot
    public List<E> immediateSnapshot() {
        return List.copyOf(immediate);
    }

    // Get the scheduled snapshot
    public List<Scheduled<E>> scheduledSnapshot() {
        return List.copyOf(new ArrayList<>(scheduled));
    }

    // Clear the graph event scheduler
    public void clear() {
        immediate.clear();
        scheduled.clear();
    }

    // Store the scheduled
    public record Scheduled<E>(long tick, E event) {
    }
}
