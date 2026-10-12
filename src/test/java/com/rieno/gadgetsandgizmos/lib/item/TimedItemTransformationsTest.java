package com.rieno.gadgetsandgizmos.lib.item;

import net.minecraft.world.Container;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class TimedItemTransformationsTest{
    @Test
    void ignoresTemporaryContainersWithoutALevel(){
        Container container = (Container)Proxy.newProxyInstance(Container.class.getClassLoader(),
                new Class<?>[]{Container.class}, (proxy, method, args) -> {
                    throw new AssertionError("Worldless tracking accessed the container: " + method.getName());
                });
        assertDoesNotThrow(() -> TimedItemTransformations.track(container, null));
    }
}
