package com.rieno.gadgetsandgizmos.lib.control;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ControlDomainsTest{
    @Test
    void preservesTraversalOrderAcrossTransitiveOverlapsAndIndependentControls(){
        var domains = List.of(List.of("a"), List.of("b"), List.of("a", "c"),
                List.of("b", "c"), List.<String>of(), List.of("d"));
        var options = List.of(0, 1, 2, 3, 4, 5);
        assertEquals(List.of(List.of(0, 2, 3, 1), List.of(4), List.of(5)),
                ControlDomains.connected(options, domains::get));
    }
}
