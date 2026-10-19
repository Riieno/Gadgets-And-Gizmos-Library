package com.rieno.gadgetsandgizmos.lib.discovery;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NamedControllerTargetTest{
    @Test void fullNamesIgnoreCaseAndWhitespaceAndDuplicatesUseCallerOrder(){
        var first = target("first", "Engine 1");
        var second = target("second", "engine 1");
        assertSame(first, ControllerDiscoveryService.firstNamed(List.of(first, second), " ENGINE 1 "));
        assertSame(second, ControllerDiscoveryService.firstNamed(List.of(second, first), "Engine 1"));
        assertNull(ControllerDiscoveryService.firstNamed(List.of(first), "Engine"));
        assertNull(ControllerDiscoveryService.firstNamed(List.of(first), " "));
        assertNull(ControllerDiscoveryService.firstNamed(List.of(first), null));
        assertNull(ControllerDiscoveryService.firstNamed(null, "Engine 1"));
    }

    private static ControllerDiscoveryNode target(String id, String name){
        return new ControllerDiscoveryNode(id, ControllerDiscoveryKind.LINKER_FACE_OUTPUT,
                "world", "test:engine", name, null, BlockPos.ZERO);
    }
}
