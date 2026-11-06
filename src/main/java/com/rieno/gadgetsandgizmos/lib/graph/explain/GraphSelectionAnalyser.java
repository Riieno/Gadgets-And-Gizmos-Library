package com.rieno.gadgetsandgizmos.lib.graph.explain;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.graph.CompiledGraph;
import com.rieno.gadgetsandgizmos.lib.graph.GraphCompiler;
import com.rieno.gadgetsandgizmos.lib.graph.GraphModel;
import com.rieno.gadgetsandgizmos.lib.graph.GraphNodeDefinition;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.rieno.gadgetsandgizmos.lib.graph.explain.GraphNodeMeaning.Operation.*;
import static com.rieno.gadgetsandgizmos.lib.graph.explain.GraphSelectionAnalysis.*;

// Analyse configured topology without constructing an execution context or touching retained runtime state
public final class GraphSelectionAnalyser<N extends GraphModel.Node, E extends GraphModel.Edge>{
    /*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        CONSTANTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    public static final int MAX_NODES = 16384;
    public static final int MAX_EDGES = 65536;
    public static final int MAX_DEPTH = 32;
    public static final int MAX_PATHS = 16;
    private static final int MAX_WORK = 65536;

    /*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        DEFAULTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    private final CompiledGraph<N, E> index;
    private final GraphSemanticProvider<N> provider;
    private final Set<String> selected;
    private final Map<String, GraphNodeMeaning> meanings = new HashMap<>();
    private final Map<String, List<E>> incoming = new HashMap<>();
    private final Map<String, List<E>> outgoing = new HashMap<>();
    private final Map<CompiledGraph.Port, List<E>> inputEdges = new HashMap<>();
    private final Map<CompiledGraph.Port, Expression> expressions = new HashMap<>();
    private final Map<String, Notice> notices = new LinkedHashMap<>();
    private final List<Boundary> boundaries = new ArrayList<>();
    private int work;

    private GraphSelectionAnalyser(GraphModel<N, E> graph, Collection<String> selection,
                                   GraphSemanticProvider<N> provider){
        index = GraphCompiler.compile(graph);
        this.provider = provider;
        selected = new LinkedHashSet<>();
        selection.stream().filter(id -> id != null && index.node(id) != null).sorted().forEach(selected::add);
        if(selection.size() != selected.size()) notice("selection", "Some selected node IDs are absent or repeated.");
        index.diagnostics().forEach(detail -> notice("topology", detail));
        graph.edges().stream().filter(edge -> edge != null
                && (selected.contains(edge.fromNode()) || selected.contains(edge.toNode())))
                .sorted(Comparator.comparing(GraphModel.Edge::id, Comparator.nullsFirst(String::compareTo))).forEach(edge -> {
                    if(edge.fromNode() == null || edge.toNode() == null || edge.fromPort() == null || edge.toPort() == null){
                        notice("port", "An edge has an incomplete endpoint.");
                        return;
                    }
                    if(index.node(edge.fromNode()) == null || index.node(edge.toNode()) == null) return;
                    GraphNodeMeaning from = meaning(edge.fromNode());
                    GraphNodeMeaning to = meaning(edge.toNode());
                    String output = from.definition().outputs().get(edge.fromPort());
                    String input = to.definition().inputs().get(edge.toPort());
                    if(output == null || input == null || "exec".equals(output) != "exec".equals(input)){
                        notice("port", edge.id() + ": missing or incompatible ports");
                        return;
                    }
                    incoming.computeIfAbsent(edge.toNode(), ignored -> new ArrayList<>()).add(edge);
                    inputEdges.computeIfAbsent(new CompiledGraph.Port(edge.toNode(), edge.toPort()),
                            ignored -> new ArrayList<>()).add(edge);
                    outgoing.computeIfAbsent(edge.fromNode(), ignored -> new ArrayList<>()).add(edge);
                    if(selected.contains(edge.fromNode()) != selected.contains(edge.toNode())){
                        boundaries.add(new Boundary(edge.fromNode(), edge.fromPort(), edge.toNode(), edge.toPort(),
                                "exec".equals(output), selected.contains(edge.toNode())));
                    }
                });
    }

    /*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        FUNCTIONS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    // Compile a private topology index once, then traverse only selected nodes and their immediate boundaries
    public static <N extends GraphModel.Node, E extends GraphModel.Edge> GraphSelectionAnalysis analyse(
            GraphModel<N, E> graph, Collection<String> selection, GraphSemanticProvider<N> provider){
        if(graph == null || selection == null || selection.isEmpty())
            return new GraphSelectionAnalysis(List.of(), List.of(), List.of(), 0);
        if(graph.nodes().size() > MAX_NODES || graph.edges().size() > MAX_EDGES)
            return new GraphSelectionAnalysis(List.of(), List.of(),
                    List.of(new Notice("limit", "The graph exceeds the analysis size limit.")), selection.size());
        return new GraphSelectionAnalyser<>(graph, selection, provider).analyse();
    }

    private GraphSelectionAnalysis analyse(){
        List<Behaviour> behaviours = new ArrayList<>();
        List<Step> steps = new ArrayList<>();
        Set<String> anchors = new LinkedHashSet<>();
        for(String id : selected){
            GraphNodeMeaning node = meaning(id);
            boolean effect = switch(node.operation()){
                case ACTION, VARIABLE_WRITE, FUNCTION, UNKNOWN -> true;
                default -> false;
            };
            boolean terminal = outgoing.getOrDefault(id, List.of()).stream()
                    .noneMatch(edge -> selected.contains(edge.toNode()));
            if(effect || terminal) anchors.add(id);
        }
        // A closed component still needs a factual description even when every output has a consumer
        Set<String> visited = new HashSet<>();
        for(String id : selected){
            if(!visited.add(id)) continue;
            ArrayDeque<String> pending = new ArrayDeque<>();
            pending.add(id);
            boolean anchored = false;
            while(!pending.isEmpty()){
                String next = pending.removeFirst();
                anchored |= anchors.contains(next);
                for(E edge : incoming.getOrDefault(next, List.of())){
                    if(selected.contains(edge.fromNode()) && visited.add(edge.fromNode())) pending.add(edge.fromNode());
                }
                for(E edge : outgoing.getOrDefault(next, List.of())){
                    if(selected.contains(edge.toNode()) && visited.add(edge.toNode())) pending.add(edge.toNode());
                }
            }
            if(!anchored) anchors.add(id);
        }
        for(String id : selected){
            GraphNodeMeaning node = meaning(id);
            Map<String, Expression> inputs = inputs(id, new HashSet<>(), 0);
            List<ExecutionPath> paths = anchors.contains(id) && node.definition().inputs().containsValue("exec")
                    ? paths(id) : List.of();
            if(anchors.contains(id)) behaviours.add(new Behaviour(id, node, inputs, paths));
            steps.add(new Step(id, node, inputs, incoming.getOrDefault(id, List.of()).stream()
                    .map(GraphModel.Edge::fromNode).filter(selected::contains).distinct().sorted().toList(), paths));
        }
        // Inspect all outputs once so unused cyclic and unsupported dependencies are still reported
        for(String id : selected){
            GraphNodeMeaning node = meaning(id);
            for(var port : node.definition().outputs().entrySet()){
                if(!"exec".equals(port.getValue())) expression(id, port.getKey(), new HashSet<>(), 0);
            }
        }
        return new GraphSelectionAnalysis(behaviours, boundaries, new ArrayList<>(notices.values()), selected.size(), steps);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        HELPERS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    private GraphNodeMeaning meaning(String id){
        return meanings.computeIfAbsent(id, key -> {
            N node = index.node(key);
            GraphNodeMeaning res = provider.describe(node);
            if(res == null){
                res = new GraphNodeMeaning(new GraphNodeDefinition(node.type(), "unknown", Map.of(), Map.of(), false),
                        node.type(), UNKNOWN, Map.of(), Map.of(), Map.of(), List.of(), Map.of(), Set.of(), List.of());
            }
            if(res.operation() == UNKNOWN) notice("unsupported", res.label());
            for(var ref : res.references()){
                if(!ref.resolved()) notice("reference", ref.kind() + ": " + ref.name());
            }
            return res;
        });
    }

    private Map<String, Expression> inputs(String id, Set<CompiledGraph.Port> active, int depth){
        GraphNodeMeaning node = meaning(id);
        Map<String, Expression> res = new LinkedHashMap<>();
        for(var port : node.definition().inputs().entrySet()){
            if("exec".equals(port.getValue())) continue;
            List<E> edges = inputEdges.getOrDefault(new CompiledGraph.Port(id, port.getKey()), List.of());
            Expression val;
            if(!edges.isEmpty()){
                if(edges.size() > 1) notice("ambiguous", id + "." + port.getKey());
                E edge = edges.getFirst();
                val = expression(edge.fromNode(), edge.fromPort(), active, depth + 1);
            }else if(node.literals().containsKey(port.getKey())){
                val = new Expression("", port.getKey(), LITERAL, node.literals().get(port.getKey()), Map.of(), false);
            }else{
                val = new Expression("", port.getKey(), UNKNOWN, node.inputLabels().getOrDefault(port.getKey(), port.getKey()), Map.of(), false);
            }
            res.put(port.getKey(), val);
        }
        return res;
    }

    private Expression expression(String id, String port, Set<CompiledGraph.Port> active, int depth){
        CompiledGraph.Port key = new CompiledGraph.Port(id, port);
        Expression cached = expressions.get(key);
        if(cached != null) return cached;
        GraphNodeMeaning node = meaning(id);
        String label = node.outputLabels().getOrDefault(port, port + " of " + node.label());
        if(!selected.contains(id)) return new Expression(id, port, SOURCE,
                node.operation() == LITERAL ? node.settings().getOrDefault("value", label) : label, Map.of(), true);
        if(depth >= MAX_DEPTH || ++work > MAX_WORK){
            notice("limit", "Dependency traversal was bounded.");
            return new Expression(id, port, SOURCE, label, Map.of(), false);
        }
        if(!active.add(key)){
            notice("cycle", id + "." + port);
            return new Expression(id, port, SOURCE, "cyclic " + label, Map.of(), false);
        }
        Map<String, Expression> args = switch(node.operation()){
            case LITERAL, SOURCE, VARIABLE_READ, ACTION, FUNCTION, LOOP, FLOW -> Map.of();
            default -> inputs(id, active, depth);
        };
        if(node.operation() == SOURCE && inputEdges.containsKey(new CompiledGraph.Port(id, "target"))){
            Expression target = inputs(id, active, depth).get("target");
            if(target != null && target.operation() != UNKNOWN) args = Map.of("target", target);
        }
        GraphNodeMeaning.Operation operation = node.operation();
        if(operation == CONTROL && !port.equals(node.settings().getOrDefault("controlOutput", "value"))){
            operation = SOURCE;
            args = Map.of();
        }
        if(node.operation() == VARIABLE_WRITE) args = args.containsKey("value") ? Map.of("value", args.get("value")) : Map.of();
        active.remove(key);
        Expression res = new Expression(id, port, operation,
                node.operation() == LITERAL ? node.settings().getOrDefault("value", "unconfigured literal") : label, args, false);
        expressions.put(key, res);
        return res;
    }

    // Walk backwards along alternative execution routes; each route keeps its own conditions
    private List<ExecutionPath> paths(String id){
        List<ExecutionPath> res = new ArrayList<>();
        ArrayDeque<PathState> pending = new ArrayDeque<>();
        pending.add(new PathState(id, List.of(), List.of(), Set.of()));
        int visits = 0;
        while(!pending.isEmpty() && res.size() < MAX_PATHS && visits++ < MAX_WORK && ++work <= MAX_WORK){
            PathState state = pending.removeFirst();
            String next = state.node();
            if(state.visited().contains(next) || state.visited().size() >= MAX_DEPTH){
                notice("cycle", "Execution route is cyclic or exceeds the depth limit.");
                res.add(new ExecutionPath("", state.conditions(), state.steps(), false, false, Map.of()));
                continue;
            }
            GraphNodeMeaning node = meaning(next);
            if(node.operation() == EVENT){
                List<Condition> conditions = new ArrayList<>(state.conditions());
                if("compare".equals(node.settings().get("predicate"))){
                    Map<String, Expression> args = new LinkedHashMap<>(inputs(next, new HashSet<>(), 0));
                    args.put("operator", new Expression("", "operator", LITERAL,
                            node.literals().getOrDefault("operator", "=="), Map.of(), false));
                    conditions.add(new Condition(new Expression(next, "", COMPARE, node.label(), args, false), "true"));
                }
                res.add(new ExecutionPath(node.settings().getOrDefault("trigger", node.label()),
                        conditions, state.steps(), false, true,
                        node.settings().containsKey("predicate") ? Map.of() : inputs(next, new HashSet<>(), 0)));
                continue;
            }
            List<E> edges = incoming.getOrDefault(next, List.of()).stream().filter(this::execution).toList();
            if(edges.isEmpty()){
                res.add(new ExecutionPath("", state.conditions(), state.steps(), false, false, Map.of()));
                continue;
            }
            Set<String> visited = new HashSet<>(state.visited());
            visited.add(next);
            for(E edge : edges){
                if(pending.size() >= MAX_PATHS * MAX_DEPTH){
                    notice("limit", "Execution alternatives were bounded.");
                    break;
                }
                List<Condition> conditions = new ArrayList<>(state.conditions());
                List<String> steps = new ArrayList<>(state.steps());
                GraphNodeMeaning from = meaning(edge.fromNode());
                if(!selected.contains(edge.fromNode())){
                    res.add(new ExecutionPath(from.label(), conditions, steps, true, true, Map.of()));
                    if(res.size() >= MAX_PATHS) break;
                    continue;
                }
                if(from.operation() == BRANCH){
                    String port = from.definition().inputs().containsKey("condition") ? "condition" : "open";
                    conditions.add(new Condition(inputExpression(edge.fromNode(), port), edge.fromPort()));
                }else if(from.operation() == SWITCH){
                    conditions.add(new Condition(inputExpression(edge.fromNode(), "selector"), edge.fromPort()));
                }else if(from.operation() == LOOP || from.operation() == FLOW){
                    steps.add(from.label() + " [" + edge.fromPort() + "]" + configuredFlow(edge.fromNode(), from));
                }
                pending.addLast(new PathState(edge.fromNode(), conditions, steps, Set.copyOf(visited)));
            }
        }
        if(!pending.isEmpty()) notice("limit", "Execution alternatives were bounded.");
        return List.copyOf(res);
    }

    private String configuredFlow(String id, GraphNodeMeaning node){
        List<String> vals = new ArrayList<>();
        for(String port : List.of("iterations", "ticks", "count", "duration")){
            if(node.definition().inputs().containsKey(port)){
                Expression val = inputExpression(id, port);
                vals.add(port + "=" + val.label());
            }
        }
        return vals.isEmpty() ? "" : " (" + String.join(", ", vals) + ")";
    }

    private Expression inputExpression(String id, String port){
        return inputs(id, new HashSet<>(), 0).getOrDefault(port,
                new Expression("", port, UNKNOWN, port, Map.of(), false));
    }

    private boolean execution(E edge){
        return "exec".equals(meaning(edge.fromNode()).definition().outputs().get(edge.fromPort()));
    }

    private void notice(String code, String detail){
        notices.putIfAbsent(code + ":" + detail, new Notice(code, detail));
    }

    private record PathState(String node, List<Condition> conditions, List<String> steps, Set<String> visited){}
}

