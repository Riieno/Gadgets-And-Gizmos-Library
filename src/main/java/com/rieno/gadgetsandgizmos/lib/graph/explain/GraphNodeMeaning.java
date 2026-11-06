package com.rieno.gadgetsandgizmos.lib.graph.explain;

import com.rieno.gadgetsandgizmos.lib.graph.GraphNodeDefinition;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

// Borrow resolved node metadata and retain only immutable descriptions of its saved state
public record GraphNodeMeaning(GraphNodeDefinition definition, String label, Operation operation,
                               Map<String, String> literals, Map<String, String> inputLabels,
                               Map<String, String> outputLabels, List<String> targets,
                               Map<String, String> settings, Set<String> effectInputs,
                               List<Reference> references){
    public GraphNodeMeaning{
        Objects.requireNonNull(definition);
        Objects.requireNonNull(label);
        Objects.requireNonNull(operation);
        literals = Map.copyOf(literals);
        inputLabels = Map.copyOf(inputLabels);
        outputLabels = Map.copyOf(outputLabels);
        targets = List.copyOf(targets);
        settings = Map.copyOf(settings);
        effectInputs = Set.copyOf(effectInputs);
        references = List.copyOf(references);
    }

    public enum Operation{
        LITERAL, SOURCE, ROUTE, ADD, SUBTRACT, MULTIPLY, DIVIDE, COMPARE, AND, OR, NOT,
        CLAMP, MAP_RANGE, RAMP, ROUND, FLOOR, CEIL, SMOOTHING, SELECT, CONTROL, EVENT, BRANCH, SWITCH, LOOP, FLOW,
        ACTION, VARIABLE_READ, VARIABLE_WRITE, FUNCTION, GENERIC, UNKNOWN
    }

    // References identify possible relationships without claiming that a write or event has occurred
    public record Reference(String kind, String name, boolean resolved, List<String> nodeIds, String detail){
        public Reference{ nodeIds = List.copyOf(nodeIds); }
    }
}

