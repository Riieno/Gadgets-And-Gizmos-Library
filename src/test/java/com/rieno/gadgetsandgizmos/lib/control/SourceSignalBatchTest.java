package com.rieno.gadgetsandgizmos.lib.control;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SourceSignalBatchTest{
    @Test
    void replacingAnActiveSourceNeverCreatesAnIntermediatePulse(){
        Map<String, Map<String, Integer>> signals = new HashMap<>();
        var batch = new SourceSignalBatch<String>(15);
        batch.set("face", "controller", 10);
        batch.applyTo(signals);
        batch.set("face", "controller", 0);
        batch.set("face", "controller", 10);
        assertTrue(batch.applyTo(signals).isEmpty());
        assertEquals(Map.of("controller", 10), signals.get("face"));
    }

    @Test
    void preservesOtherControllersAndRevealsTheirSignalOnRemoval(){
        Map<String, Map<String, Integer>> signals = new HashMap<>();
        var batch = new SourceSignalBatch<String>(15);
        batch.set("face", "first", 99);
        batch.set("face", "second", 7);
        assertEquals(List.of(new SourceSignalBatch.Change<>("face", 0, 15)), batch.applyTo(signals));
        batch.set("face", "second", 9);
        assertTrue(batch.applyTo(signals).isEmpty());
        batch.set("face", "first", -1);
        assertEquals(List.of(new SourceSignalBatch.Change<>("face", 15, 9)), batch.applyTo(signals));
        batch.set("face", "second", 0);
        assertEquals(List.of(new SourceSignalBatch.Change<>("face", 9, 0)), batch.applyTo(signals));
        assertTrue(signals.isEmpty());
        assertTrue(batch.applyTo(signals).isEmpty());
    }
}
