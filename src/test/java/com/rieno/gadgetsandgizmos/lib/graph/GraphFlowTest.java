package com.rieno.gadgetsandgizmos.lib.graph;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GraphFlowTest{
    @Test
    void equalNumbersIgnoreTheirJavaRepresentation(){
        assertTrue(GraphComparison.numbers(0, 0.0, "=="));
        assertTrue(GraphComparison.numbers(42, 42.0f, "=="));
        assertTrue(GraphComparison.numbers(-0.0, 0, "=="));
        assertFalse(GraphComparison.numbers(42, 42.5, "=="));
        assertTrue(GraphComparison.numbers(42.5, 42, ">"));
        assertTrue(GraphComparison.numbers(42, 42.5, "<"));
        assertTrue(GraphComparison.numbers(42, 42.0, "<="));
        assertTrue(GraphComparison.numbers(42.0, 42, ">="));
    }

    @Test
    void specialNumbersHaveConsistentOrdering(){
        assertTrue(GraphComparison.numbers(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, "=="));
        assertTrue(GraphComparison.numbers(Double.NEGATIVE_INFINITY, 0, "<"));
        for(String operator : List.of("==", "<", "<=", ">", ">=")){
            assertFalse(GraphComparison.numbers(Double.NaN, 0, operator));
            assertFalse(GraphComparison.numbers(0, Double.NaN, operator));
        }
        assertTrue(GraphComparison.numbers(Double.NaN, Double.NaN, "!="));
    }

    @Test
    void savedOperatorAliasesRemainAccepted(){
        assertEquals("<=", GraphComparison.normalizeOperator("Less_Than_Or_Equal_To"));
        assertEquals(">=", GraphComparison.normalizeOperator(" greater-than-or-equal "));
        assertTrue(GraphComparison.numbers(0.0, 0, "equals"));
        assertTrue(GraphComparison.matches("not equal", false, 1));
        assertFalse(GraphComparison.numbers(1, 1, "unknown"));
    }

    @Test
    void wholeLimitsTruncateAndRejectInvalidValues(){
        assertEquals(3, GraphCounters.wholeCount(3.9));
        assertEquals(0, GraphCounters.wholeCount(-3));
        assertEquals(0, GraphCounters.wholeCount(Double.NaN));
        assertEquals(0, GraphCounters.wholeCount(Double.POSITIVE_INFINITY));
        assertEquals(Integer.MAX_VALUE, GraphCounters.wholeCount(Double.MAX_VALUE));
    }

    @Test
    void limitedCounterStopsAtItsLimitAndAllowsLargerLimits(){
        int count = 0;
        for(int pulse = 0; pulse < 300; pulse++){
            GraphCounters.LimitedPulse result = GraphCounters.limitedPulse(count, 300, false);
            assertTrue(result.passed());
            count = result.count();
        }
        assertEquals(new GraphCounters.LimitedPulse(300, false),
                GraphCounters.limitedPulse(count, 300, false));
        assertEquals(new GraphCounters.LimitedPulse(301, true),
                GraphCounters.limitedPulse(count, 301, false));
        assertEquals(new GraphCounters.LimitedPulse(300, false),
                GraphCounters.limitedPulse(count, 2, false));
    }

    @Test
    void resetRestartsBeforeProcessingTheCurrentPulse(){
        assertEquals(new GraphCounters.LimitedPulse(1, true), GraphCounters.limitedPulse(7, 3, true));
        assertEquals(new GraphCounters.LimitedPulse(0, false), GraphCounters.limitedPulse(7, 0, true));
        assertEquals(new GraphCounters.LimitedPulse(1, true), GraphCounters.limitedPulse(-7, 3, false));
        assertEquals(new GraphCounters.LimitedPulse(Integer.MAX_VALUE, false),
                GraphCounters.limitedPulse(Integer.MAX_VALUE, Integer.MAX_VALUE, false));
    }

    @Test
    void countAppliesOneStepInTheChosenDirection(){
        assertEquals(3, GraphCounters.step(0, 3, GraphCounters.Direction.INCREMENT));
        assertEquals(2.5, GraphCounters.step(3, 0.5, GraphCounters.Direction.DECREMENT));
        assertEquals(-1, GraphCounters.step(2, 3, GraphCounters.Direction.DECREMENT));
        assertEquals(5, GraphCounters.step(2, -3, GraphCounters.Direction.DECREMENT));
        assertEquals(GraphCounters.Direction.DECREMENT, GraphCounters.Direction.fromId("Decrement"));
        assertEquals(GraphCounters.Direction.INCREMENT, GraphCounters.Direction.fromId(null));
    }

    @Test
    void countResetReturnsZeroWithoutApplyingStep(){
        for(GraphCounters.Direction direction : GraphCounters.Direction.values()){
            assertEquals(0, GraphCounters.step(17, 3, direction, true));
            assertEquals(0, GraphCounters.step(0, 3, direction, true));
            assertEquals(0, GraphCounters.step(-17, Double.NaN, direction, true));
        }
        assertEquals(3, GraphCounters.step(0, 3, GraphCounters.Direction.INCREMENT, false));
        assertEquals(-3, GraphCounters.step(0, 3, GraphCounters.Direction.DECREMENT, false));
    }

    @Test
    void countKeepsNonFiniteStepsOutOfSavedState(){
        assertEquals(5, GraphCounters.step(5, Double.NaN, GraphCounters.Direction.INCREMENT));
        assertEquals(5, GraphCounters.step(5, Double.POSITIVE_INFINITY, GraphCounters.Direction.INCREMENT));
        assertEquals(Double.MAX_VALUE,
                GraphCounters.step(Double.MAX_VALUE, Double.MAX_VALUE, GraphCounters.Direction.INCREMENT));
        assertEquals(2, GraphCounters.step(Double.NaN, 2, GraphCounters.Direction.INCREMENT));
    }

    @Test
    void invalidationFollowsOnlyConsumersOfTheChangedDataPort(){
        TestGraph graph = new TestGraph(List.of(), List.of(
                edge("counter", "value", "math"),
                edge("math", "value", "display"),
                edge("counter", "other", "unrelated"),
                edge("counter", "exec", "action")));
        GraphDataDependencies dependencies = GraphDataDependencies.compile(graph, edge -> !edge.fromPort().equals("exec"));
        assertEquals(Set.of("math", "display"), dependencies.affectedNodes("counter", "value"));
        assertEquals(Set.of("unrelated"), dependencies.affectedNodes("counter", "other"));
        assertEquals(Set.of(), dependencies.affectedNodes("counter", "missing"));
        assertThrows(UnsupportedOperationException.class,
                () -> dependencies.affectedNodes("counter", "value").add("unexpected"));
    }

    @Test
    void invalidationTerminatesOnCyclesAndCapturesItsGraph(){
        TestGraph graph = new TestGraph(List.of(), new java.util.ArrayList<>(List.of(
                edge("counter", "value", "math"),
                edge("math", "value", "display"),
                edge("display", "value", "math"))));
        GraphDataDependencies dependencies = GraphDataDependencies.compile(graph, edge -> true);
        graph.edges().clear();
        assertEquals(Set.of("math", "display"), dependencies.affectedNodes("counter", "value"));
        assertEquals(Set.of(), GraphDataDependencies.empty().affectedNodes("counter", "value"));
    }

    private static TestEdge edge(String source, String port, String target){
        return new TestEdge(source + ":" + port + ":" + target, source, port, target, "input");
    }

    private record TestNode(String id, String type) implements GraphModel.Node{}
    private record TestEdge(String id, String fromNode, String fromPort, String toNode, String toPort)
            implements GraphModel.Edge{}
    private record TestGraph(List<TestNode> nodes, List<TestEdge> edges) implements GraphModel<TestNode, TestEdge>{}
}

