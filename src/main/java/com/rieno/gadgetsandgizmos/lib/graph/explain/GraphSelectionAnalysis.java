package com.rieno.gadgetsandgizmos.lib.graph.explain;

import java.util.List;
import java.util.Map;

// Retain verified operations, expression dependencies and alternative execution paths separately
public record GraphSelectionAnalysis(List<Behaviour> behaviours, List<Boundary> boundaries,
                                     List<Notice> notices, int selectedCount, List<Step> steps){
    public GraphSelectionAnalysis{
        behaviours = List.copyOf(behaviours);
        boundaries = List.copyOf(boundaries);
        notices = List.copyOf(notices);
        steps = List.copyOf(steps);
    }

    // Preserve callers that supply behaviour summaries without a full selection trace
    public GraphSelectionAnalysis(List<Behaviour> behaviours, List<Boundary> boundaries,
                                  List<Notice> notices, int selectedCount){
        this(behaviours, boundaries, notices, selectedCount, behaviours.stream().map(behaviour ->
                new Step(behaviour.nodeId(), behaviour.meaning(), behaviour.inputs(), List.of(), behaviour.paths())).toList());
    }

    // Retain immediate dependencies so a reader can follow each selected stage without nested formulas
    public record Step(String nodeId, GraphNodeMeaning meaning, Map<String, Expression> inputs,
                       List<String> predecessors, List<ExecutionPath> paths){
        public Step{ inputs = Map.copyOf(inputs); predecessors = List.copyOf(predecessors); paths = List.copyOf(paths); }
    }

    public record Behaviour(String nodeId, GraphNodeMeaning meaning, Map<String, Expression> inputs,
                            List<ExecutionPath> paths){
        public Behaviour{ inputs = Map.copyOf(inputs); paths = List.copyOf(paths); }
    }

    public record Expression(String nodeId, String port, GraphNodeMeaning.Operation operation,
                             String label, Map<String, Expression> inputs, boolean external){
        public Expression{ inputs = Map.copyOf(inputs); }
    }

    // Each path is an alternative; conditions within one path must hold together
    public record ExecutionPath(String trigger, List<Condition> conditions, List<String> steps,
                                boolean external, boolean connected, Map<String, Expression> inputs){
        public ExecutionPath{ conditions = List.copyOf(conditions); steps = List.copyOf(steps); inputs = Map.copyOf(inputs); }
    }

    public record Condition(Expression expression, String outcome){}
    public record Boundary(String fromNode, String fromPort, String toNode, String toPort,
                           boolean execution, boolean entering){}
    public record Notice(String code, String detail){}
}

