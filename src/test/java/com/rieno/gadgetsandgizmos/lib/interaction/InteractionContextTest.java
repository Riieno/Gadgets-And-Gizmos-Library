package com.rieno.gadgetsandgizmos.lib.interaction;

import org.junit.jupiter.api.Test;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import java.util.LinkedHashMap;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class InteractionContextTest{
    @Test void nestedFailuresRestoreThePreviousPlayer(){
        var first = origin();
        var second = origin();
        InteractionContext.run(first, () -> {
            assertThrows(IllegalStateException.class, () -> InteractionContext.run(second, () -> {
                assertEquals(second,InteractionContext.current());
                throw new IllegalStateException("test");
            }));
            assertEquals(first,InteractionContext.current());
        });
        assertNull(InteractionContext.current());
    }
    @Test void batchedBindingsRetainIndependentAndAnonymousWriters(){
        var first = origin();
        var second = origin();
        var writers = new LinkedHashMap<String,InteractionOrigin>();
        writers.put("first",first);
        writers.put("second",second);
        writers.put("anonymous",null);
        InteractionContext.run(first, () -> InteractionContext.runOutputs(writers, () -> {
            InteractionContext.runOutput("second", () -> assertEquals(second,InteractionContext.current()));
            InteractionContext.runOutput("first", () -> assertEquals(first,InteractionContext.current()));
            InteractionContext.runOutput("anonymous", () -> assertNull(InteractionContext.current()));
            InteractionContext.runOutput("unlisted", () -> assertEquals(first,InteractionContext.current()));
            assertEquals(first,InteractionContext.current());
        }));
        InteractionContext.runOutput("second", () -> assertNull(InteractionContext.current()));
    }
    private static InteractionOrigin origin(){
        return new InteractionOrigin(UUID.randomUUID(),"player",ResourceLocation.withDefaultNamespace("overworld"),Vec3.ZERO,0,0,100);
    }
}
