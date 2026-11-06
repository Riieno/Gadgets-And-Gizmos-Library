package com.rieno.gadgetsandgizmos.lib.graph.explain;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.graph.GraphModel;
import com.rieno.gadgetsandgizmos.lib.graph.GraphNodeDefinition;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.rieno.gadgetsandgizmos.lib.graph.explain.GraphNodeMeaning.Operation.*;
import static org.junit.jupiter.api.Assertions.*;

// Verify behaviour inference independently of Minecraft, node executors and addon classes
class GraphSelectionAnalyserTest{
    @Test void foldsArithmeticIntoOneBehaviour(){
        Graph graph = new Graph();
        graph.literal("a", "7");
        graph.literal("b", "3");
        graph.node("sum", ADD, Map.of("a", "number", "b", "number"), Map.of("value", "number"));
        graph.node("scale", MULTIPLY, Map.of("a", "number", "b", "number"), Map.of("value", "number"), Map.of("b", "2"));
        graph.edge("a", "value", "sum", "a");
        graph.edge("b", "value", "sum", "b");
        graph.edge("sum", "value", "scale", "a");
        var analysis = graph.analyse();
        String explanation = render(analysis);
        assertEquals(1, analysis.behaviours().size());
        assertTrue(explanation.contains("sum of 7 and 3"));
        assertTrue(explanation.contains("scaled by 2"));
        assertFalse(explanation.contains("Node a"));
    }

    @Test void retainsTrueAndFalseExecutionAlternatives(){
        Graph graph = conditional();
        graph.action("off", "lamp", Map.of("value", "0"));
        graph.edge("branch", "false", "off", "exec");
        String explanation = render(graph.analyse());
        assertTrue(explanation.contains("ship altitude is greater than 12"));
        assertTrue(explanation.contains("not (ship altitude is greater than 12)"));
        assertTrue(explanation.contains("ACC updates"));
        assertEquals(2, graph.analyse().behaviours().size());
    }

    @Test void infersControlIntentFromActualControllerInputsAndOutputTarget(){
        Graph graph = control();
        String explanation = render(graph.analyse());
        assertTrue(explanation.contains("pitch"));
        assertTrue(explanation.contains("PID"));
        assertTrue(explanation.contains("port thrusters"));
        assertTrue(explanation.contains("towards 15"));
        assertTrue(explanation.contains("appears intended to regulate"));
        assertTrue(explanation.contains("does not verify that it succeeds"));
    }

    @Test void controllerIntentGeneralisesToStorageAndOtherMeasuredQuantities(){
        Graph graph = control();
        graph.nodes.set(1, meaning("telemetry", SOURCE, Map.of(), Map.of("pitch", "number"),
                Map.of(), Map.of("pitch", "tank fill level"), List.of(), Map.of(), List.of()));
        graph.replaceTargets("out", List.of("fluid pump"));
        String explanation = render(graph.analyse());
        assertTrue(explanation.contains("regulate tank fill level"));
        assertTrue(explanation.contains("fluid pump"));
        assertFalse(explanation.contains("ship"));
    }

    @Test void describesIndependentBehavioursSeparately(){
        Graph graph = control();
        graph.action("lamp", "warning lamp", Map.of("value", "15"));
        graph.edge("tick", "exec", "lamp", "exec");
        var analysis = graph.analyse();
        assertEquals(2, analysis.behaviours().size());
        String explanation = render(analysis);
        assertTrue(explanation.contains("port thrusters"));
        assertTrue(explanation.contains("warning lamp"));
    }

    @Test void partialSelectionStopsAtExternalDependencies(){
        Graph graph = control();
        var analysis = graph.analyse(Set.of("pid", "out"));
        String explanation = render(analysis);
        assertTrue(explanation.contains("external ship pitch"));
        assertTrue(explanation.contains("outside the selection"));
        assertEquals(2, analysis.boundaries().stream().filter(GraphSelectionAnalysis.Boundary::entering).count());
        assertTrue(analysis.behaviours().getFirst().paths().getFirst().external());
    }

    @Test void externalControllerDoesNotInventAnInternalControlPurpose(){
        Graph graph = control();
        String explanation = render(graph.analyse(Set.of("out")));
        assertTrue(explanation.contains("external PID"));
        assertFalse(explanation.contains("appears intended"));
    }

    @Test void anExternalBranchDoesNotContributeAnUnverifiedCondition(){
        Graph graph = conditional();
        var analysis = graph.analyse(Set.of("on"));
        var path = analysis.behaviours().getFirst().paths().getFirst();
        assertTrue(path.external());
        assertTrue(path.conditions().isEmpty());
        assertFalse(render(analysis).contains("unconfigured condition"));
    }

    @Test void storingACorrectionDoesNotByItselfImplyFeedbackControl(){
        Graph graph = control();
        TestNode output = graph.nodes.stream().filter(node -> node.id().equals("out")).findFirst().orElseThrow();
        var prev = output.meaning();
        graph.nodes.set(graph.nodes.indexOf(output), new TestNode("out", new GraphNodeMeaning(
                prev.definition(), "store", VARIABLE_WRITE, prev.literals(), prev.inputLabels(), prev.outputLabels(),
                List.of(), Map.of("action", "store the correction"), prev.effectInputs(), prev.references())));
        assertFalse(render(graph.analyse()).contains("appears intended"));
    }

    @Test void anActionStatusOutputDoesNotImplyThatItsConsumerRegulatesTheMeasuredInput(){
        Graph graph = control();
        graph.action("status", "status lamp", Map.of());
        TestNode output = graph.nodes.stream().filter(node -> node.id().equals("out")).findFirst().orElseThrow();
        var prev = output.meaning();
        graph.nodes.set(graph.nodes.indexOf(output), new TestNode("out", new GraphNodeMeaning(
                new GraphNodeDefinition(prev.definition().id(), "test", prev.definition().inputs(),
                        Map.of("exec", "exec", "success", "boolean"), false), prev.label(), ACTION,
                prev.literals(), prev.inputLabels(), Map.of("success", "retained write status"), prev.targets(),
                prev.settings(), prev.effectInputs(), prev.references())));
        graph.edge("out", "success", "status", "value");
        var analysis = graph.analyse();
        var status = analysis.behaviours().stream().filter(behaviour -> behaviour.nodeId().equals("status")).findFirst().orElseThrow();
        String explanation = render(new GraphSelectionAnalysis(List.of(status), List.of(), List.of(), 1));
        assertTrue(explanation.contains("retained write status"));
        assertFalse(explanation.contains("appears intended"));
    }

    @Test void mutuallyExclusiveRoutesRemainAlternativesAfterTheyMerge(){
        Graph graph = conditional();
        graph.edge("branch", "false", "on", "exec");
        var analysis = graph.analyse();
        var paths = analysis.behaviours().getFirst().paths();
        assertEquals(2, paths.size());
        assertTrue(paths.stream().allMatch(path -> path.conditions().size() == 1));
        assertEquals(Set.of("true", "false"), paths.stream().map(path -> path.conditions().getFirst().outcome())
                .collect(java.util.stream.Collectors.toSet()));
    }

    @Test void identifiesOutgoingConnectionsWithoutDescribingUnselectedActions(){
        Graph graph = control();
        var analysis = graph.analyse(Set.of("telemetry", "pid"));
        assertEquals(1, analysis.boundaries().stream().filter(boundary -> !boundary.entering()).count());
        assertTrue(render(analysis).contains("downstream behaviour is outside"));
        assertFalse(render(analysis).contains("port thrusters"));
    }

    @Test void disconnectedActionsDoNotClaimToRun(){
        Graph graph = new Graph();
        graph.action("out", "bearing", Map.of("value", "90"));
        String explanation = render(graph.analyse());
        assertTrue(explanation.contains("No complete execution route"));
        assertTrue(explanation.contains("90"));
        assertFalse(explanation.contains("When"));
    }

    @Test void loopsRetainConfiguredBoundsAndExecutionOutput(){
        Graph graph = new Graph();
        graph.event();
        graph.node("loop", LOOP, Map.of("exec", "exec", "iterations", "number"),
                Map.of("body", "exec", "complete", "exec"), Map.of("iterations", "4"));
        graph.action("out", "redstone", Map.of("value", "3"));
        graph.edge("tick", "exec", "loop", "exec");
        graph.edge("loop", "body", "out", "exec");
        String explanation = render(graph.analyse());
        assertTrue(explanation.contains("iterations=4"));
        assertTrue(explanation.contains("[body]"));
    }

    @Test void cyclesTerminateAndDoNotImplyConvergence(){
        Graph graph = new Graph();
        graph.node("a", ADD, Map.of("a", "number", "b", "number"), Map.of("value", "number"), Map.of("b", "1"));
        graph.node("b", ROUTE, Map.of("value", "number"), Map.of("value", "number"));
        graph.edge("a", "value", "b", "value");
        graph.edge("b", "value", "a", "a");
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            var analysis = graph.analyse();
            assertFalse(analysis.behaviours().isEmpty());
            assertTrue(analysis.notices().stream().anyMatch(notice -> notice.code().equals("cycle")));
            assertTrue(render(analysis).contains("convergence are not assumed"));
        });
    }

    @Test void executionStagesArePresentedInTriggerToEffectOrder(){
        Graph graph = new Graph();
        graph.event();
        graph.node("outer", LOOP, Map.of("exec", "exec", "iterations", "number"), Map.of("body", "exec"), Map.of("iterations", "3"));
        graph.node("inner", FLOW, Map.of("exec", "exec", "ticks", "number"), Map.of("exec", "exec"), Map.of("ticks", "5"));
        graph.action("out", "redstone", Map.of("value", "1"));
        graph.edge("tick", "exec", "outer", "exec");
        graph.edge("outer", "body", "inner", "exec");
        graph.edge("inner", "exec", "out", "exec");
        String explanation = render(graph.analyse());
        assertTrue(explanation.indexOf("outer") < explanation.indexOf("inner"));
    }

    @Test void missingReferencesAndUnsupportedNodesFallBackFactually(){
        Graph graph = new Graph();
        graph.nodes.add(meaning("custom", UNKNOWN, Map.of("a", "number"), Map.of("value", "number"),
                Map.of("a", "8"), Map.of(), List.of(), Map.of(),
                List.of(new GraphNodeMeaning.Reference("function", "missing", false, List.of(), ""))));
        String explanation = render(graph.analyse());
        assertTrue(explanation.contains("unsupported node custom"));
        assertTrue(explanation.contains("a = 8"));
        assertTrue(explanation.contains("cannot be resolved"));
        assertFalse(explanation.contains("appears intended"));
    }

    @Test void invalidWiresAreReportedWithoutSupplyingImaginaryData(){
        Graph graph = new Graph();
        graph.literal("a", "1");
        graph.action("out", "lamp", Map.of());
        graph.edge("a", "absent", "out", "value");
        graph.edges.add(new TestEdge("missing", "absent", "value", "out", "value"));
        var analysis = graph.analyse();
        assertTrue(analysis.notices().stream().anyMatch(notice -> notice.code().equals("port")));
        assertTrue(analysis.notices().stream().anyMatch(notice -> notice.code().equals("topology")));
        assertFalse(render(analysis).contains("value = 1"));
    }

    @Test void analysisIsDeterministicAndDoesNotChangeItsInputs(){
        Graph graph = conditional();
        List<TestNode> nodes = List.copyOf(graph.nodes);
        List<TestEdge> edges = List.copyOf(graph.edges);
        String first = render(graph.analyse());
        assertEquals(nodes, graph.nodes);
        assertEquals(edges, graph.edges);
        Collections.reverse(graph.nodes);
        Collections.reverse(graph.edges);
        assertEquals(first, render(graph.analyse()));
    }

    @Test void largeSharedDependenciesAreBounded(){
        Graph graph = new Graph();
        graph.literal("start", "1");
        String prev = "start";
        for(int idx = 0; idx < 2000; idx++){
            String id = String.format(java.util.Locale.ROOT, "n%04d", idx);
            graph.node(id, ADD, Map.of("a", "number", "b", "number"), Map.of("value", "number"));
            graph.edge(prev, "value", id, "a");
            graph.edge(prev, "value", id, "b");
            prev = id;
        }
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            String explanation = render(graph.analyse());
            assertTrue(explanation.length() < 50000);
        });
    }

    @Test void translationReceivesStableKeysAndSentenceArguments(){
        Graph graph = control();
        List<String> keys = new ArrayList<>();
        var renderer = new GraphExplanation((key, fallback, args) -> {
            keys.add(key);
            return String.format(java.util.Locale.ROOT, fallback, args);
        });
        assertFalse(renderer.explain(graph.analyse()).isBlank());
        assertTrue(keys.stream().allMatch(key -> key.startsWith("gadgetsngizmos.graph.explain.")));
        assertTrue(keys.contains("gadgetsngizmos.graph.explain.intent"));
    }

    /*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        HELPERS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    private static String render(GraphSelectionAnalysis analysis){ return new GraphExplanation().explain(analysis); }

    private static Graph control(){
        Graph graph = new Graph();
        graph.event();
        graph.nodes.add(meaning("telemetry", SOURCE, Map.of(), Map.of("pitch", "number"), Map.of(),
                Map.of("pitch", "ship pitch"), List.of(), Map.of(), List.of()));
        graph.nodes.add(meaning("pid", CONTROL, Map.of("actual", "number", "target", "number", "p", "number"),
                Map.of("value", "number"), Map.of("target", "15", "p", "2"), Map.of("value", "PID"), List.of(), Map.of(), List.of()));
        graph.action("out", "port thrusters", Map.of());
        graph.edge("telemetry", "pitch", "pid", "actual");
        graph.edge("pid", "value", "out", "value");
        graph.edge("tick", "exec", "out", "exec");
        return graph;
    }

    private static Graph conditional(){
        Graph graph = new Graph();
        graph.event();
        graph.nodes.add(meaning("telemetry", SOURCE, Map.of(), Map.of("altitude", "number"), Map.of(),
                Map.of("altitude", "ship altitude"), List.of(), Map.of(), List.of()));
        graph.node("compare", COMPARE, Map.of("a", "number", "b", "number", "operator", "string"),
                Map.of("value", "boolean"), Map.of("b", "12", "operator", ">"));
        graph.node("branch", BRANCH, Map.of("exec", "exec", "condition", "boolean"), Map.of("true", "exec", "false", "exec"));
        graph.action("on", "lamp", Map.of("value", "15"));
        graph.edge("telemetry", "altitude", "compare", "a");
        graph.edge("compare", "value", "branch", "condition");
        graph.edge("tick", "exec", "branch", "exec");
        graph.edge("branch", "true", "on", "exec");
        return graph;
    }

    private static TestNode meaning(String id, GraphNodeMeaning.Operation op, Map<String, String> inputs,
                                Map<String, String> outputs, Map<String, String> literals,
                                Map<String, String> outputLabels, List<String> targets,
                                Map<String, String> settings, List<GraphNodeMeaning.Reference> refs){
        return new TestNode(id, new GraphNodeMeaning(new GraphNodeDefinition("test:" + id, "test", inputs, outputs, false),
                id, op, literals, Map.of(), outputLabels, targets, settings, Set.of(), refs));
    }

    private record TestNode(String id, GraphNodeMeaning meaning) implements GraphModel.Node{
        @Override public String type(){ return meaning.definition().id(); }
    }

    private record TestEdge(String id, String fromNode, String fromPort, String toNode, String toPort) implements GraphModel.Edge{}

    private static final class Graph implements GraphModel<TestNode, TestEdge>{
        private final List<TestNode> nodes = new ArrayList<>();
        private final List<TestEdge> edges = new ArrayList<>();
        @Override public List<TestNode> nodes(){ return nodes; }
        @Override public List<TestEdge> edges(){ return edges; }
        private void literal(String id, String val){ nodes.add(meaning(id, LITERAL, Map.of(), Map.of("value", "number"),
                Map.of(), Map.of(), List.of(), Map.of("value", val), List.of())); }
        private void event(){ nodes.add(meaning("tick", EVENT, Map.of(), Map.of("exec", "exec"),
                Map.of(), Map.of(), List.of(), Map.of("trigger", "the ACC updates"), List.of())); }
        private void action(String id, String target, Map<String, String> vals){
            nodes.add(meaning(id, ACTION, Map.of("exec", "exec", "value", "number"), Map.of("exec", "exec"),
                    vals, Map.of(), List.of(target), Map.of("action", "set output", "controlDestination", "true"), List.of()));
        }
        private void node(String id, GraphNodeMeaning.Operation op, Map<String, String> inputs, Map<String, String> outputs){
            node(id, op, inputs, outputs, Map.of());
        }
        private void node(String id, GraphNodeMeaning.Operation op, Map<String, String> inputs, Map<String, String> outputs, Map<String, String> vals){
            nodes.add(meaning(id, op, inputs, outputs, vals, Map.of(), List.of(), Map.of(), List.of()));
        }
        private void replaceTargets(String id, List<String> targets){
            for(int idx = 0; idx < nodes.size(); idx++){
                TestNode node = nodes.get(idx);
                if(!node.id().equals(id)) continue;
                var val = node.meaning();
                nodes.set(idx, new TestNode(id, new GraphNodeMeaning(val.definition(), val.label(), val.operation(),
                        val.literals(), val.inputLabels(), val.outputLabels(), targets, val.settings(), val.effectInputs(), val.references())));
            }
        }
        private void edge(String from, String output, String to, String input){ edges.add(new TestEdge("e" + edges.size(), from, output, to, input)); }
        private GraphSelectionAnalysis analyse(){ return analyse(nodes.stream().map(TestNode::id).collect(java.util.stream.Collectors.toSet())); }
        private GraphSelectionAnalysis analyse(Set<String> selection){ return GraphSelectionAnalyser.analyse(this, selection, TestNode::meaning); }
    }
}

