package com.rieno.gadgetsandgizmos.lib.geometry;

import com.rieno.gadgetsandgizmos.lib.color.WeightedColorGradient;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class BandedFluidMeshTest{
    private static final AABB BOUNDS = new AABB(.4125D, .429029375D, .31875D, .5875D, .604029375D, .74375D);
    private static final WeightedColorGradient COLORS = new WeightedColorGradient(List.of(
            new WeightedColorGradient.Color(0xFF0000, 3), new WeightedColorGradient.Color(0x0000FF, 1)));

    @Test
    void tiltedLayersKeepTheirExactVolumeAndSolidColorsInsideTheBottle(){
        var directions = new java.util.ArrayList<Vector3f>(List.of(new Vector3f(0, 1, 0), new Vector3f(0, -1, 0),
                new Vector3f(1, 0, 0), new Vector3f(0, 0, 1), new Vector3f(1, 1, 1),
                new Vector3f(.00001F, 1, .00001F), new Vector3f(.0000001F, 1, .5F)));
        var random = new Random(57);
        for(int idx = 0; idx < 30; idx++) directions.add(new Vector3f(random.nextFloat() * 2 - 1,
                random.nextFloat() * 2 - 1, random.nextFloat() * 2 - 1));
        for(var up : directions){
            var normal = new Vec3(up.x, up.y, up.z).normalize();
            for(float fill : new float[]{.15F, .85F, 1}){
                var faces = BandedFluidMesh.build(BOUNDS, up, fill, COLORS);
                var volumes = new HashMap<Integer, Double>();
                double volume = 0;
                for(var face : faces){
                    assertTrue(face.color() == 0xFF0000 || face.color() == 0x0000FF);
                    var points = face.vertices();
                    for(var point : points){
                        assertTrue(point.x >= BOUNDS.minX - 1.0E-9D && point.x <= BOUNDS.maxX + 1.0E-9D);
                        assertTrue(point.y >= BOUNDS.minY - 1.0E-9D && point.y <= BOUNDS.maxY + 1.0E-9D);
                        assertTrue(point.z >= BOUNDS.minZ - 1.0E-9D && point.z <= BOUNDS.maxZ + 1.0E-9D);
                    }
                    for(int idx = 1; idx < points.size() - 1; idx++){
                        Vec3 a = points.getFirst(), b = points.get(idx), c = points.get(idx + 1);
                        Vec3 area = b.subtract(a).cross(c.subtract(a)).scale(.5D);
                        assertTrue(area.dot(face.normal()) >= -1.0E-10D);
                        volume += a.dot(b.cross(c)) / 6;
                        Vec3 center = a.add(b).add(c).scale(1D / 3);
                        Vec3 flux = center.subtract(normal.scale(center.dot(normal))).scale(.5D);
                        volumes.merge(face.color(), flux.dot(area), Double::sum);
                    }
                }
                double expected = BOUNDS.getXsize() * BOUNDS.getYsize() * BOUNDS.getZsize() * fill;
                assertEquals(expected, volume, expected * 1.0E-5D, "Total volume at " + up);
                assertEquals(expected * .75D, volumes.get(0xFF0000), expected * 1.0E-5D, "Red share at " + up);
                assertEquals(expected * .25D, volumes.get(0x0000FF), expected * 1.0E-5D, "Blue share at " + up);
            }
        }
    }

    @Test
    void emptyAndSingleColorStorageProduceNoUnexpectedLayers(){
        assertTrue(BandedFluidMesh.build(BOUNDS, new Vector3f(0, 1, 0), .85F, new WeightedColorGradient(List.of())).isEmpty());
        assertTrue(BandedFluidMesh.build(BOUNDS, new Vector3f(0, 1, 0), 0, COLORS).isEmpty());
        var single = new WeightedColorGradient(List.of(new WeightedColorGradient.Color(0xAABBCC, 128)));
        var faces = BandedFluidMesh.build(BOUNDS, new Vector3f(0, 1, 0), .85F, single);
        assertEquals(6, faces.size());
        assertTrue(faces.stream().allMatch(face -> face.color() == 0xAABBCC));
        var top = faces.getLast();
        assertEquals(new Vec3(0, 1, 0), top.normal());
        for(var point : top.vertices()) assertEquals(BOUNDS.minY + BOUNDS.getYsize() * .85F, point.y, 1.0E-9D);
    }
}
