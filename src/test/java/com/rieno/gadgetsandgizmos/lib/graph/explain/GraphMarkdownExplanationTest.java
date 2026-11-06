package com.rieno.gadgetsandgizmos.lib.graph.explain;

import com.rieno.gadgetsandgizmos.lib.documentation.MarkdownDocument;
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

// Check that the trace reads like instructions rather than a recursively expanded formula
class GraphMarkdownExplanationTest{
    @Test void formatsTheThrottleChainAsFiveReadableMarkdownBullets(){
        Graph graph = throttle("both", "0.25");
        String markdown = graph.markdown();
        var document = MarkdownDocument.parse(markdown);
        assertEquals(5, document.nodes().getFirst().children().stream().filter(node -> node.tag().equals("li")).count());
        assertEquals("ul", document.nodes().getFirst().tag());
        assertTrue(markdown.contains("**Configured Key Input** sends a pulse"));
        assertTrue(markdown.contains("pressed and again when it is released"));
        assertTrue(markdown.contains("0–1 to 0–15"));
        assertTrue(markdown.contains("**Ramp** receives the output from **Map Range**"));
        assertTrue(markdown.contains("falls by 0.25 per tick"));
        assertTrue(markdown.contains("**Round** rounds the output from **Ramp** to the nearest whole number"));
        assertTrue(markdown.contains("The output from **Round** is stored in the variable **throttle**"));
        assertFalse(markdown.contains("unconfigured"));
        assertFalse(markdown.contains("execution-dependent"));
        assertFalse(markdown.contains("saved initial value"));
    }

    @Test void stageOrderFollowsWiringRatherThanStorageOrder(){
        Graph graph = throttle("both", "0.25");
        String first = graph.markdown();
        Collections.reverse(graph.nodes);
        Collections.reverse(graph.edges);
        assertEquals(first, graph.markdown());
        assertTrue(first.indexOf("**Map Range**") < first.indexOf("**Ramp**"));
        assertTrue(first.indexOf("**Ramp** receives") < first.indexOf("**Round** rounds"));
        assertTrue(first.indexOf("**Round** rounds") < first.indexOf("is stored in"));
    }

    @Test void pulseDescriptionsRespectTheConfiguredEdge(){
        String rising = throttle("rising", "1").markdown();
        String falling = throttle("falling", "1").markdown();
        assertTrue(rising.contains("sends a pulse when **\\[W\\]** is pressed."));
        assertFalse(rising.contains("and again"));
        assertTrue(falling.contains("sends a pulse when **\\[W\\]** is released."));
        assertFalse(falling.contains("and again"));
    }

    @Test void zeroFallDoesNotClaimTheRampReturnsToZero(){
        String markdown = throttle("both", "0").markdown();
        assertTrue(markdown.contains("Fall is 0, so it will not decrease"));
        assertFalse(markdown.contains("It falls by 0 per tick"));
    }

    @Test void configuredNamesRemainTextRatherThanMarkdownInstructions(){
        Graph graph = throttle("both", "1");
        TestNode store = graph.nodes.getLast();
        var prev = store.meaning();
        graph.nodes.set(graph.nodes.size() - 1, new TestNode(store.id(), new GraphNodeMeaning(prev.definition(), prev.label(),
                prev.operation(), prev.literals(), prev.inputLabels(), prev.outputLabels(), prev.targets(),
                Map.of("variable", "[throttle](https://example.invalid)\n![image](https://example.invalid)"),
                prev.effectInputs(), prev.references())));
        MarkdownDocument document = MarkdownDocument.parse(graph.markdown());
        assertFalse(containsTag(document.nodes(), "img"));
        assertTrue(document.nodes().getFirst().plainText().contains("[throttle](https://example.invalid)"));
    }

    @Test void sharedInputsAreReferencedByNameWithoutReexpandingTheirHistory(){
        Graph graph = throttle("both", "0.25");
        for(int idx = 0; idx < 1500; idx++){
            String id = String.format(java.util.Locale.ROOT, "stage%04d", idx);
            graph.nodes.add(node(id, "Scale " + idx, MULTIPLY, Map.of("a", "number", "b", "number"),
                    Map.of("value", "number"), Map.of("b", "2"), Map.of()));
            graph.edge(idx == 0 ? "round" : String.format(java.util.Locale.ROOT, "stage%04d", idx - 1), "value", id, "a");
        }
        assertTimeoutPreemptively(Duration.ofSeconds(4), () -> {
            String markdown = graph.markdown();
            assertTrue(markdown.length() < 48000);
            assertTrue(markdown.contains("too large or complex"));
        });
    }

    @Test void partialSelectionsUsePlainBoundaryWording(){
        Graph graph = throttle("both", "0.25");
        var analysis = GraphSelectionAnalyser.analyse(graph, Set.of("round", "store"), TestNode::meaning);
        String markdown = new GraphExplanation().explainMarkdown(analysis);
        assertTrue(markdown.contains("outside this selection"));
        assertTrue(markdown.lines().filter(line -> !line.isBlank()).allMatch(line -> line.startsWith("- ")));
    }

    // Preserve the same display name in property labels and destination text
    @Test void actionLabelsKeepDisplayNameCharacters(){
        Graph graph = new Graph();
        var definition = new GraphNodeDefinition("example:write", "test", Map.of("power", "number"), Map.of(), false);
        graph.nodes.add(new TestNode("write", new GraphNodeMeaning(definition, "Set Data", ACTION,
                Map.of("power", "15"), Map.of("power", "signal strength [Lamp_West]"), Map.of(),
                List.of("opaque:target@123"), Map.of("targetLabel", "Lamp_West"), Set.of("power"), List.of())));
        String markdown = graph.markdown();
        assertTrue(markdown.contains("signal strength \\[Lamp\\_West\\]"));
        assertTrue(markdown.contains("on Lamp\\_West"));
        assertFalse(markdown.contains("opaque:target"));
    }

    // Keep graph bubbles aligned with the same meaningful stages shown in the modal
    @Test void annotatedStepsRetainTheirNodeIdsAndDescriptions(){
        Graph graph = throttle("both", "0.25");
        var analysis = GraphSelectionAnalyser.analyse(graph, graph.nodes.stream().map(TestNode::id).toList(), TestNode::meaning);
        var explanation = new GraphExplanation();
        var steps = explanation.explainSteps(analysis);
        assertEquals(List.of("key", "map", "ramp", "round", "store"), steps.stream().map(GraphExplanation.NodeStep::nodeId).toList());
        String markdown = explanation.explainMarkdown(analysis);
        for(var step : steps) assertTrue(markdown.contains("- " + step.markdown()));
    }

    // Describe the actual reading supplied by a source instead of its generic value port
    @Test void sourceDescriptionsUseTheHostSemanticSummary(){
        Graph graph = new Graph();
        graph.nodes.add(node("mspt", "MSPT", GENERIC, Map.of(), Map.of("value", "number"), Map.of(),
                Map.of("description", "Outputs the server's average milliseconds per tick.")));
        String markdown = graph.markdown();
        assertTrue(markdown.contains("**MSPT**: Outputs the server's average milliseconds per tick."));
        assertFalse(markdown.contains("configured output"));
        assertTrue(new GraphExplanation().explainSteps(GraphSelectionAnalyser.analyse(graph, Set.of("mspt"), TestNode::meaning))
                .getFirst().markdown().contains("milliseconds per tick"));
    }

    private static Graph throttle(String pulse, String fall){
        Graph graph = new Graph();
        graph.nodes.add(node("key", "Configured Key Input", EVENT, Map.of(), Map.of("exec", "exec", "value", "number"),
                Map.of(), Map.of("inputKind", "key", "binding", "W", "pulse", pulse, "trigger", "the key changes")));
        graph.nodes.add(node("map", "Map Range", MAP_RANGE,
                Map.of("value", "number", "in_min", "number", "in_max", "number", "out_min", "number", "out_max", "number"),
                Map.of("value", "number"), Map.of("in_min", "0", "in_max", "1", "out_min", "0", "out_max", "15"), Map.of()));
        graph.nodes.add(node("ramp", "Ramp", RAMP,
                Map.of("exec", "exec", "value", "number", "rise", "number", "fall", "number", "reset_delay", "number"),
                Map.of("exec", "exec", "value", "number"), Map.of("rise", "0.5", "fall", fall, "reset_delay", "0"), Map.of()));
        graph.nodes.add(node("round", "Round", ROUND, Map.of("value", "number"), Map.of("value", "number"), Map.of(), Map.of()));
        graph.nodes.add(node("store", "Set Variable", VARIABLE_WRITE, Map.of("exec", "exec", "value", "number"),
                Map.of("exec", "exec", "value", "number"), Map.of(), Map.of("variable", "throttle")));
        graph.edge("key", "value", "map", "value");
        graph.edge("key", "exec", "ramp", "exec");
        graph.edge("map", "value", "ramp", "value");
        graph.edge("ramp", "value", "round", "value");
        graph.edge("ramp", "exec", "store", "exec");
        graph.edge("round", "value", "store", "value");
        return graph;
    }

    private static boolean containsTag(List<MarkdownDocument.Node> nodes, String tag){
        for(var node : nodes) if(node.tag().equals(tag) || containsTag(node.children(), tag)) return true;
        return false;
    }

    private static TestNode node(String id, String label, GraphNodeMeaning.Operation operation,
                                 Map<String, String> inputs, Map<String, String> outputs,
                                 Map<String, String> literals, Map<String, String> settings){
        return new TestNode(id, new GraphNodeMeaning(new GraphNodeDefinition("example:" + id, "test", inputs, outputs, false),
                label, operation, literals, Map.of(), Map.of(), List.of(), settings, Set.of(), List.of()));
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
        private void edge(String from, String output, String to, String input){
            edges.add(new TestEdge("wire" + edges.size(), from, output, to, input));
        }
        private String markdown(){
            return new GraphExplanation().explainMarkdown(GraphSelectionAnalyser.analyse(this,
                    nodes.stream().map(TestNode::id).toList(), TestNode::meaning));
        }
    }
}

