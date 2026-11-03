package com.rieno.gadgetsandgizmos.lib.geometry;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.color.WeightedColorGradient;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// Keep solid color layers inside a box while preserving their volume as the surface tilts
public final class BandedFluidMesh{
    public record Face(List<Vec3> vertices, Vec3 normal, Direction textureFace, int color){
        public Face{ vertices = List.copyOf(vertices); }
    }

    private BandedFluidMesh(){}

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Reserve headroom with fill below one; the supplied upward vector determines the free surface
    public static List<Face> build(AABB bounds, Vector3fc up, float fill, WeightedColorGradient colors){
        if(!Float.isFinite(fill) || fill < 0 || fill > 1) throw new IllegalArgumentException("Fluid fill must be between zero and one");
        if(!Double.isFinite(bounds.minX + bounds.minY + bounds.minZ + bounds.maxX + bounds.maxY + bounds.maxZ)
                || bounds.getXsize() <= 0 || bounds.getYsize() <= 0 || bounds.getZsize() <= 0){
            throw new IllegalArgumentException("Fluid bounds must be finite and have positive volume");
        }
        var normal = new Vec3(up.x(), up.y(), up.z());
        if(!Double.isFinite(normal.lengthSqr()) || normal.lengthSqr() < 1.0E-12D){
            throw new IllegalArgumentException("Fluid upward direction must be finite and non-zero");
        }
        if(fill == 0 || colors.colors().isEmpty()) return List.of();
        normal = normal.normalize();
        var box = box(bounds);
        var levels = new Projection(bounds, normal);
        var res = new ArrayList<Face>();
        double low = levels.height(0);
        var bands = colors.bands();
        for(var band : bands){
            double high = levels.height(fill * band.end());
            for(var face : box){
                var points = band.start() == 0 ? face.vertices : clip(face.vertices, normal, low, true);
                if(fill * band.end() < 1) points = clip(points, normal, high, false);
                if(points.size() >= 3) res.add(new Face(points, face.normal, face.textureFace, band.color()));
            }
            low = high;
        }
        if(fill < 1){
            var points = cap(box, normal, low);
            if(points.size() >= 3){
                res.add(new Face(points, normal, Direction.getNearest((float)normal.x, (float)normal.y, (float)normal.z), bands.getLast().color()));
            }
        }
        return List.copyOf(res);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static List<Face> box(AABB bounds){
        double x = bounds.minX, y = bounds.minY, z = bounds.minZ;
        double xx = bounds.maxX, yy = bounds.maxY, zz = bounds.maxZ;
        return List.of(
                face(Direction.DOWN, new Vec3(x, y, z), new Vec3(xx, y, z), new Vec3(xx, y, zz), new Vec3(x, y, zz)),
                face(Direction.UP, new Vec3(x, yy, z), new Vec3(x, yy, zz), new Vec3(xx, yy, zz), new Vec3(xx, yy, z)),
                face(Direction.NORTH, new Vec3(x, y, z), new Vec3(x, yy, z), new Vec3(xx, yy, z), new Vec3(xx, y, z)),
                face(Direction.SOUTH, new Vec3(x, y, zz), new Vec3(xx, y, zz), new Vec3(xx, yy, zz), new Vec3(x, yy, zz)),
                face(Direction.WEST, new Vec3(x, y, z), new Vec3(x, y, zz), new Vec3(x, yy, zz), new Vec3(x, yy, z)),
                face(Direction.EAST, new Vec3(xx, y, z), new Vec3(xx, yy, z), new Vec3(xx, yy, zz), new Vec3(xx, y, zz)));
    }

    private static Face face(Direction dir, Vec3... points){
        return new Face(List.of(points), new Vec3(dir.getStepX(), dir.getStepY(), dir.getStepZ()), dir, 0);
    }

    private static List<Vec3> clip(List<Vec3> points, Vec3 normal, double level, boolean above){
        if(points.isEmpty()) return points;
        var res = new ArrayList<Vec3>();
        Vec3 prev = points.getLast();
        double prevDist = prev.dot(normal) - level;
        boolean prevInside = above ? prevDist >= 0 : prevDist <= 0;
        for(var point : points){
            double dist = point.dot(normal) - level;
            boolean inside = above ? dist >= 0 : dist <= 0;
            if(inside != prevInside) res.add(prev.lerp(point, prevDist / (prevDist - dist)));
            if(inside) res.add(point);
            prev = point;
            prevDist = dist;
            prevInside = inside;
        }
        return res;
    }

    // Close the liquid with a polygon ordered around its outward surface normal
    private static List<Vec3> cap(List<Face> box, Vec3 normal, double level){
        var points = new ArrayList<Vec3>();
        for(var face : box){
            Vec3 prev = face.vertices.getLast();
            double prevDist = prev.dot(normal) - level;
            for(var point : face.vertices){
                double dist = point.dot(normal) - level;
                if(Math.abs(dist) < 1.0E-9D) addPoint(points, point);
                if(prevDist * dist < 0) addPoint(points, prev.lerp(point, prevDist / (prevDist - dist)));
                prev = point;
                prevDist = dist;
            }
        }
        if(points.size() < 3) return List.of();
        Vec3 center = Vec3.ZERO;
        for(var point : points) center = center.add(point);
        Vec3 origin = center.scale(1D / points.size());
        Vec3 u = (Math.abs(normal.y) < .9D ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0)).cross(normal).normalize();
        Vec3 v = normal.cross(u);
        points.sort(Comparator.comparingDouble(point -> Math.atan2(point.subtract(origin).dot(v), point.subtract(origin).dot(u))));
        return points;
    }

    private static void addPoint(List<Vec3> points, Vec3 point){
        for(var existing : points) if(existing.distanceToSqr(point) < 1.0E-16D) return;
        points.add(point);
    }

    // Invert the projected box volume so each layer retains its share at every orientation
    public static final class Projection{
        private final double min, span, denominator;
        private final double[] lengths;
        private final Vec3 normal;

        // Project box volume along a finite non-zero upward direction
        public Projection(AABB bounds, Vector3fc up){
            this(bounds, new Vec3(up.x(), up.y(), up.z()).normalize());
        }

        private Projection(AABB bounds, Vec3 normal){
            if(!Double.isFinite(normal.lengthSqr()) || normal.lengthSqr() < 1.0E-12D
                    || bounds.getXsize() <= 0 || bounds.getYsize() <= 0 || bounds.getZsize() <= 0){
                throw new IllegalArgumentException("Fluid projection requires positive bounds and a finite upward direction");
            }
            this.normal = normal;
            double[] values = {Math.abs(normal.x) * bounds.getXsize(), Math.abs(normal.y) * bounds.getYsize(), Math.abs(normal.z) * bounds.getZsize()};
            span = values[0] + values[1] + values[2];
            min = normal.dot(bounds.getCenter()) - span * .5D;
            var active = new ArrayList<Double>();
            for(double val : values) if(val > span * 1.0E-6D) active.add(val);
            lengths = active.stream().mapToDouble(Double::doubleValue).toArray();
            double product = lengths.length == 3 ? 6 : lengths.length;
            for(double val : lengths) product *= val;
            denominator = product;
        }

        public double height(double fraction){
            if(fraction <= 0) return min;
            if(fraction >= 1) return min + span;
            double low = 0, high = span;
            for(int idx = 0; idx < 40; idx++){
                double mid = (low + high) * .5D;
                if(fraction(mid) < fraction) low = mid;
                else high = mid;
            }
            return min + (low + high) * .5D;
        }

        // Return the share of box volume below a local point
        public double fractionAt(double x, double y, double z){
            return fraction(normal.x * x + normal.y * y + normal.z * z - min);
        }

        private double fraction(double level){
            double total = 0;
            for(double val : lengths) total += val;
            if(level > total * .5D) return 1 - fraction(total - level);
            double res = 0;
            for(int mask = 0; mask < 1 << lengths.length; mask++){
                double val = level;
                for(int idx = 0; idx < lengths.length; idx++) if((mask & 1 << idx) != 0) val -= lengths[idx];
                if(val > 0){
                    double power = lengths.length == 1 ? val : lengths.length == 2 ? val * val : val * val * val;
                    res += (Integer.bitCount(mask) % 2 == 0 ? 1 : -1) * power;
                }
            }
            return Math.clamp(res / denominator, 0, 1);
        }
    }
}
