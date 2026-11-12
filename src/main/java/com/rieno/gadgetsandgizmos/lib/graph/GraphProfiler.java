package com.rieno.gadgetsandgizmos.lib.graph;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.LongSupplier;

// Measure compiled instructions on their owning thread without allocating per call
public final class GraphProfiler{
/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        DEFAULTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    private final String[] ids;
    private final long[] calls, self, inclusive, peak;
    private final LongSupplier clock;
    private int[] stack = new int[32];
    private long[] starts = new long[32], children = new long[32];
    private int depth;
    private long ticks, total, last, maximum, tickStart;
    private boolean running;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                       FUNCTIONS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    public GraphProfiler(List<String> ids){ this(ids, System::nanoTime); }

    // Accept a monotonic clock for deterministic hosts and verification
    public GraphProfiler(List<String> ids, LongSupplier clock){
        this.ids = ids.toArray(String[]::new);
        this.clock = java.util.Objects.requireNonNull(clock);
        calls = new long[ids.size()];
        self = new long[ids.size()];
        inclusive = new long[ids.size()];
        peak = new long[ids.size()];
    }

    public void beginTick(){
        if(running) throw new IllegalStateException("A profiling tick is already running");
        running = true;
        tickStart = clock.getAsLong();
    }

    public void endTick(){
        if(!running || depth != 0) throw new IllegalStateException("Unbalanced profiling scopes");
        last = Math.max(0, clock.getAsLong() - tickStart);
        total += last;
        maximum = Math.max(maximum, last);
        ticks++;
        running = false;
    }

    public int currentNode(){ return depth == 0 ? -1 : stack[depth - 1]; }

    public void beginNode(int idx){
        if(!running || idx < 0 || idx >= ids.length) throw new IllegalStateException("Invalid profiling instruction");
        if(depth == stack.length){
            int size = depth * 2;
            stack = Arrays.copyOf(stack, size);
            starts = Arrays.copyOf(starts, size);
            children = Arrays.copyOf(children, size);
        }
        stack[depth] = idx;
        children[depth] = 0;
        starts[depth++] = clock.getAsLong();
    }

    public void endNode(){
        long end = clock.getAsLong();
        if(depth == 0) throw new IllegalStateException("No profiling instruction is running");
        int slot = --depth;
        int idx = stack[slot];
        long elapsed = Math.max(0, end - starts[slot]);
        long own = Math.max(0, elapsed - children[slot]);
        calls[idx]++;
        self[idx] += own;
        inclusive[idx] += elapsed;
        peak[idx] = Math.max(peak[idx], own);
        if(depth > 0) children[depth - 1] += elapsed;
    }

    // Merge function instances under their source node's stable presentation key
    public GraphProfile snapshot(){
        if(running) throw new IllegalStateException("Cannot publish an unfinished profiling tick");
        var nodes = new LinkedHashMap<String, GraphProfile.Node>();
        for(int idx = 0; idx < ids.length; idx++){
            var val = new GraphProfile.Node(calls[idx], self[idx], inclusive[idx], peak[idx]);
            nodes.merge(ids[idx], val, GraphProfile.Node::plus);
        }
        return new GraphProfile(ticks, total, last, maximum, nodes);
    }
}
