package com.rieno.gadgetsandgizmos.lib.graph;

// Advance a retained bounded loop without depending on a scheduler or graph host
public final class GraphBoundedLoop{
    private GraphBoundedLoop(){}

    public record State(int iterations, int completed, boolean running){
        public State{
            iterations = Math.max(0, iterations);
            completed = Math.clamp(completed, 0, iterations);
        }

        public static State idle(){
            return new State(0, 0, false);
        }
    }

    public record Step(State state, int index, boolean iteration, boolean complete){}

    // An active run keeps its original limit when another pulse arrives
    public static State start(State previous, double iterations, int maximum){
        if(previous.running()) return previous;
        return new State(Math.min(GraphCounters.wholeCount(iterations), Math.max(0, maximum)), 0, true);
    }

    // Emit zero-based indices and complete immediately after the last iteration
    public static Step advance(State previous){
        int index = Math.max(0, previous.completed() - 1);
        if(!previous.running()) return new Step(previous, index, false, false);
        if(previous.completed() >= previous.iterations()){
            return new Step(new State(previous.iterations(), previous.completed(), false), index, false, true);
        }
        int completed = previous.completed() + 1;
        boolean complete = completed == previous.iterations();
        return new Step(new State(previous.iterations(), completed, !complete), previous.completed(), true, complete);
    }
}
