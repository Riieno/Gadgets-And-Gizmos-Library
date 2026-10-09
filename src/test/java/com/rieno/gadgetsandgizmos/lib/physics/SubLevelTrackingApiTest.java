package com.rieno.gadgetsandgizmos.lib.physics;

import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SubLevelTrackingApiTest {
    @BeforeAll
    static void bootstrap(){ SableSplineConstraintTest.bootstrap(); }

    @Test
    void filtersLoadedBodiesByRangeAndScmNames(){
        ServerLevel level = mock(ServerLevel.class);
        SubLevel own = body(0), unnamed = body(2), named = body(5), far = body(50), removed = body(1);
        when(removed.isRemoved()).thenReturn(true);
        Map<SubLevel, String> names = Map.of(named, "Explorer", far, "Freighter");
        try(var levels = mockStatic(SableLevelApi.class)){
            levels.when(() -> SableLevelApi.subLevels(level)).thenReturn(List.of(far, named, own, removed, unnamed));
            var all = SubLevelTrackingApi.scan(level, Vec3.ZERO, new Vec3(0, 0, -1), new Vec3(0, 1, 0),
                    own.getUniqueId(), "All", 5, body -> names.getOrDefault(body, ""));
            assertEquals(List.of(unnamed.getUniqueId(), named.getUniqueId()), all.stream().map(SubLevelTrackingApi.Target::id).toList());
            var scmOnly = SubLevelTrackingApi.scan(level, Vec3.ZERO, new Vec3(0, 0, -1), new Vec3(0, 1, 0),
                    own.getUniqueId(), "All", 5, body -> "", body -> body == named);
            assertEquals(List.of(named.getUniqueId()), scmOnly.stream().map(SubLevelTrackingApi.Target::id).toList());
            assertEquals("", scmOnly.getFirst().name());
            assertTrue(SubLevelTrackingApi.scan(level, Vec3.ZERO, new Vec3(0, 0, -1), new Vec3(0, 1, 0),
                    own.getUniqueId(), "Explorer", 5, body -> names.getOrDefault(body, ""), body -> false).isEmpty());
            var selected = SubLevelTrackingApi.scan(level, Vec3.ZERO, new Vec3(0, 0, -1), new Vec3(0, 1, 0),
                    own.getUniqueId(), " Explorer, Freighter ", 5, body -> names.getOrDefault(body, ""));
            assertEquals(List.of(named.getUniqueId()), selected.stream().map(SubLevelTrackingApi.Target::id).toList());
            assertTrue(SubLevelTrackingApi.scan(level, Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, null,
                    "Missing", 100, body -> names.getOrDefault(body, "")).isEmpty());
            assertTrue(SubLevelTrackingApi.scan(level, Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, null,
                    "All", Double.NaN, null).isEmpty());
        }
    }

    @Test
    void bearingsFollowTheMountedFrame(){
        Vec3 forward = new Vec3(0, 0, -1), up = new Vec3(0, 1, 0);
        assertEquals(0, TrackingGeometry.relativeAngles(Vec3.ZERO, forward, forward, up).yaw(), 1.0E-9D);
        assertEquals(90, TrackingGeometry.relativeAngles(Vec3.ZERO, new Vec3(1, 0, 0), forward, up).yaw(), 1.0E-9D);
        assertEquals(-90, TrackingGeometry.relativeAngles(Vec3.ZERO, new Vec3(-1, 0, 0), forward, up).yaw(), 1.0E-9D);
        assertEquals(90, TrackingGeometry.relativeAngles(Vec3.ZERO, up, forward, up).pitch(), 1.0E-9D);
        assertEquals(90, TrackingGeometry.relativeAngles(Vec3.ZERO, new Vec3(0, 0, 1),
                new Vec3(1, 0, 0), up).yaw(), 1.0E-9D);
        assertEquals(new TrackingGeometry.Angles(0, 0), TrackingGeometry.relativeAngles(Vec3.ZERO, Vec3.ZERO, forward, up));
    }

    @Test
    void absentWearersKeepTheDeclaredPortTypes(){
        var values = EntityTelemetryApi.sample(null, Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, "", 8);
        EntityTelemetryApi.ports().forEach((port, type) -> assertEquals(type, values.get(port).type(), port));
        assertFalse(values.get("looking_at_block").asBoolean());
        assertFalse(values.get("same_dimension").asBoolean());
    }

    private SubLevel body(double x){
        SubLevel body = mock(SubLevel.class, RETURNS_DEEP_STUBS);
        when(body.getUniqueId()).thenReturn(UUID.randomUUID());
        when(body.logicalPose().position()).thenReturn(new org.joml.Vector3d(x, 0, 0));
        return body;
    }
}
