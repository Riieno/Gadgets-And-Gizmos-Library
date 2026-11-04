package com.rieno.gadgetsandgizmos.lib.util;

import org.junit.jupiter.api.Test;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

class CompatibleMethodsTest{
    @Test
    void inheritedPrivateMethodsAndNegativeLookupsAreRetained(){
        var method = CompatibleMethods.find(Child.class, "draw", "value", 1);
        assertEquals(Parent.class, method.getDeclaringClass());
        assertSame(method, CompatibleMethods.find(Child.class, "draw", "other", 3));
        assertNull(CompatibleMethods.find(Child.class, "missing", "value"));
        assertNull(CompatibleMethods.find(Child.class, "missing", "value"));
        assertNull(CompatibleMethods.find(Child.class, "draw", "value", 1L));
    }

    @Test
    void overloadsAndNullArgumentsKeepSeparateSignatures(){
        assertEquals(String.class, CompatibleMethods.find(Child.class, "draw", "text").getParameterTypes()[0]);
        assertEquals(Integer.class, CompatibleMethods.find(Child.class, "draw", 2).getParameterTypes()[0]);
        var nullMethod = CompatibleMethods.find(Child.class, "draw", new Object[]{null});
        assertSame(nullMethod, CompatibleMethods.find(Child.class, "draw", new Object[]{null}));
        assertNull(CompatibleMethods.find(Child.class, "draw", new Object()));
    }

    @Test
    void concurrentCallsDoNotMixArgumentTypes() throws Exception{
        try(var executor = Executors.newFixedThreadPool(4)){
            var jobs = IntStream.range(0, 1000).mapToObj(idx -> executor.submit(() -> {
                Object arg = idx % 2 == 0 ? "text" : Integer.valueOf(idx);
                assertEquals(arg.getClass(), CompatibleMethods.find(Child.class, "draw", arg).getParameterTypes()[0]);
            })).toList();
            for(var job : jobs) job.get();
        }
    }

    private static class Parent{
        private void draw(String text, int light){}
    }
    private static final class Child extends Parent{
        private void draw(String text){}
        private void draw(Integer val){}
    }
}
