package com.rieno.gadgetsandgizmos.lib.graph;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class GraphProfilerTest{
    @Test
    void nestedWorkDoesNotDoubleCountSelfTime(){
        var time = new AtomicLong();
        var profiler = new GraphProfiler(List.of("parent", "child"), time::get);
        profiler.beginTick();
        time.set(10); profiler.beginNode(0);
        time.set(20); profiler.beginNode(1);
        time.set(50); profiler.endNode();
        time.set(70); profiler.endNode();
        time.set(100); profiler.endTick();
        var profile = profiler.snapshot();
        assertEquals(100, profile.lastNanos());
        assertEquals(new GraphProfile.Node(1, 30, 60, 30), profile.nodes().get("parent"));
        assertEquals(new GraphProfile.Node(1, 30, 30, 30), profile.nodes().get("child"));
        assertEquals(60, profile.nodes().get("parent").averageInclusiveNanos());
        assertEquals(-1, profiler.currentNode());
    }

    @Test
    void functionInstancesAggregateAndIdleInstructionsStayVisible(){
        var time = new AtomicLong();
        var profiler = new GraphProfiler(List.of("function:f/n", "function:f/n", "idle"), time::get);
        profiler.beginTick();
        profiler.beginNode(0); time.set(20); profiler.endNode();
        profiler.beginNode(1); time.set(30); profiler.endNode();
        profiler.endTick();
        var profile = profiler.snapshot();
        assertEquals(new GraphProfile.Node(2, 30, 30, 20), profile.nodes().get("function:f/n"));
        assertEquals(15, profile.nodes().get("function:f/n").averageNanos());
        assertEquals(15, profile.nodes().get("function:f/n").averageInclusiveNanos());
        assertEquals(0, profile.nodes().get("idle").calls());
        assertEquals(0, profile.nodes().get("idle").averageInclusiveNanos());
        assertThrows(UnsupportedOperationException.class, () -> profile.nodes().clear());
    }

    @Test
    void scopesExpandAndAccumulateAcrossTicks(){
        var time = new AtomicLong();
        var profiler = new GraphProfiler(List.of("recursive"), time::get);
        profiler.beginTick();
        for(int idx = 0; idx < 100; idx++){ profiler.beginNode(0); time.incrementAndGet(); }
        for(int idx = 0; idx < 100; idx++){ time.incrementAndGet(); profiler.endNode(); }
        profiler.endTick();
        profiler.beginTick(); time.addAndGet(10); profiler.endTick();
        var profile = profiler.snapshot();
        assertEquals(2, profile.ticks());
        assertEquals(210, profile.totalNanos());
        assertEquals(200, profile.maxNanos());
        assertEquals(105, profile.averageNanos());
        assertEquals(100, profile.nodes().get("recursive").calls());
        assertEquals(200, profile.nodes().get("recursive").selfNanos());
    }

    @Test
    void unfinishedScopesCannotPublishMisleadingSamples(){
        var profiler = new GraphProfiler(List.of("node"));
        profiler.beginTick();
        assertThrows(IllegalStateException.class, profiler::snapshot);
        profiler.beginNode(0);
        assertThrows(IllegalStateException.class, profiler::endTick);
        profiler.endNode(); profiler.endTick();
        assertEquals(1, profiler.snapshot().ticks());
    }

    @Test
    void displayBandsAndUnitsAreStable(){
        assertEquals("1.25 \u03bcs", GraphProfileFormat.micros(1250));
        assertEquals(0xFF80E09A, GraphProfileFormat.color(9999));
        assertEquals(0xFFFFD166, GraphProfileFormat.color(10_000));
        assertEquals(0xFFFF7878, GraphProfileFormat.color(100_000));
    }

    @Test
    void snapshotsRoundTripWithoutChangingMetrics(){
        var time = new AtomicLong();
        var profiler = new GraphProfiler(List.of("node"), time::get);
        profiler.beginTick(); profiler.beginNode(0);
        time.set(37); profiler.endNode(); profiler.endTick();
        var profile = profiler.snapshot();
        assertEquals(profile, GraphProfileNbt.read(GraphProfileNbt.write(profile)));
    }
}
