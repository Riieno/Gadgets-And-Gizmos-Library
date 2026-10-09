package com.rieno.gadgetsandgizmos.lib.compat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OptionalTypeMatcherTest{
    @Test
    void matchesInheritedClassesAndInterfacesAcrossDifferentInstances(){
        var base = new OptionalTypeMatcher(Base.class.getName());
        var iface = new OptionalTypeMatcher(Marker.class.getName());
        assertTrue(base.test(new Child()));
        assertTrue(base.test(new Child()));
        assertTrue(iface.test(new Child()));
        assertFalse(base.test(new Object()));
        assertFalse(iface.test(new Object()));
    }

    @Test
    void absentOptionalClassesAndNullTargetsAreUnavailable(){
        var absent = new OptionalTypeMatcher("missing.optional.BlockEntity");
        assertFalse(absent.test(new Child()));
        assertFalse(absent.test(new Object()));
        assertFalse(absent.test(null));
    }

    private interface Marker{}
    private static class Base implements Marker{}
    private static final class Child extends Base{}
}
