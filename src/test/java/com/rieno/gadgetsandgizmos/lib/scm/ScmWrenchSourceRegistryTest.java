package com.rieno.gadgetsandgizmos.lib.scm;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class ScmWrenchSourceRegistryTest{
    @Test
    void torqueMovesWithItsForceReferenceAndRoundTrips(){
        var val = new ScmWrenchSourceRegistry.Wrench(true, new Vec3(0, 10, 0), new Vec3(1, 2, 3),
                new Vec3(0, 4, 0), java.util.Set.of("ship_pitch"));
        Vec3 source = new Vec3(2, 0, 0);
        var shifted = val.aboutCenter(source, Vec3.ZERO);
        assertEquals(new Vec3(1, 2, 23), shifted.torque());
        assertEquals(val, shifted.aboutCenter(Vec3.ZERO, source));
        assertEquals(val.rotationActions(), ScmControlPriority.additionalWrench(val, new Vec3(0, 4, 0)).rotationActions());
        assertThrows(IllegalArgumentException.class, () -> val.aboutCenter(new Vec3(Double.NaN, 0, 0), Vec3.ZERO));
    }

    @Test
    void activeSourcesKeepTheirEnvironmentalSupportAndInactiveSourcesApplyNothing(){
        var request = new ScmWrenchSourceRegistry.Request(mock(BlockEntity.class), UUID.randomUUID(), Vec3.ZERO, 17);
        ScmWrenchSourceRegistry.register(ResourceLocation.fromNamespaceAndPath("test", "native_control"), ctx ->
                ctx == request ? new ScmWrenchSourceRegistry.Wrench(true, new Vec3(2, 20, 3),
                        new Vec3(4, 5, 6), new Vec3(0, 19, 0)) : ScmWrenchSourceRegistry.Wrench.NONE);
        ScmWrenchSourceRegistry.register(ResourceLocation.fromNamespaceAndPath("test", "idle_control"), ctx ->
                new ScmWrenchSourceRegistry.Wrench(false, new Vec3(0, 2000, 0), Vec3.ZERO));
        var res = ScmWrenchSourceRegistry.sample(request);
        assertTrue(res.active());
        assertEquals(new Vec3(2, 20, 3), res.force());
        assertEquals(new Vec3(4, 5, 6), res.torque());
        assertEquals(new Vec3(0, 19, 0), res.compensationForce());
        assertEquals(Vec3.ZERO, new ScmWrenchSourceRegistry.Wrench(true, Vec3.ZERO, Vec3.ZERO).compensationForce());
        assertThrows(IllegalArgumentException.class, () -> new ScmWrenchSourceRegistry.Wrench(true,
                Vec3.ZERO, Vec3.ZERO, new Vec3(Double.NaN, 0, 0)));
    }
}
