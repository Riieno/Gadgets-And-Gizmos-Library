package com.rieno.gadgetsandgizmos.lib.navigation;

import net.minecraft.world.phys.Vec3;

import java.util.Optional;

// Project terrain, markers and route segments through one heading-up map frame
public record NavigationMapProjection(Vec3 center, double heading, int width, int height, double worldSpan){
    public NavigationMapProjection{
        if(!NavigationMapData.finite(center) || !Double.isFinite(heading) || width <= 0 || height <= 0
                || !Double.isFinite(worldSpan) || worldSpan <= 0) throw new IllegalArgumentException("Invalid navigation map frame");
    }

    public record Point(double x, double y){}
    public record Segment(Point from, Point to){}

    public double scale(){ return Math.max(width, height) / worldSpan; }

    public Point project(Vec3 pos){
        double angle = Math.toRadians(NavigationHeading.normalize(heading)), dx = pos.x - center.x, dz = pos.z - center.z;
        return new Point(width * .5 + (dx * Math.cos(angle) + dz * Math.sin(angle)) * scale(),
                height * .5 + (-dx * Math.sin(angle) + dz * Math.cos(angle)) * scale());
    }

    public Vec3 unproject(double x, double y){
        double angle = Math.toRadians(NavigationHeading.normalize(heading)), dx = (x - width * .5) / scale(), dz = (y - height * .5) / scale();
        return center.add(dx * Math.cos(angle) - dz * Math.sin(angle), 0, dx * Math.sin(angle) + dz * Math.cos(angle));
    }

    public boolean contains(Point pos){ return pos.x >= 0 && pos.x <= width && pos.y >= 0 && pos.y <= height; }

    // Clip distant destinations before converting coordinates to GUI floats
    public Optional<Segment> clip(Vec3 from, Vec3 to){
        if(!NavigationMapData.finite(from) || !NavigationMapData.finite(to)) return Optional.empty();
        Point a = project(from), b = project(to);
        double dx = b.x - a.x, dy = b.y - a.y, start = 0, end = 1;
        if(!Double.isFinite(a.x) || !Double.isFinite(a.y) || !Double.isFinite(dx) || !Double.isFinite(dy)) return Optional.empty();
        double[] p = {-dx, dx, -dy, dy}, q = {a.x, width - a.x, a.y, height - a.y};
        for(int idx = 0; idx < 4; idx++){
            if(Math.abs(p[idx]) < 1.0E-12){ if(q[idx] < 0) return Optional.empty(); continue; }
            double val = q[idx] / p[idx];
            if(p[idx] < 0) start = Math.max(start, val);
            else end = Math.min(end, val);
            if(start > end) return Optional.empty();
        }
        return Optional.of(new Segment(new Point(a.x + start * dx, a.y + start * dy), new Point(a.x + end * dx, a.y + end * dy)));
    }
}
