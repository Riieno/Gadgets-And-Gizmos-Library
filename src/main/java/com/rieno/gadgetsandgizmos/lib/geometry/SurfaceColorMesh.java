package com.rieno.gadgetsandgizmos.lib.geometry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

// Merge only occupied coplanar face cells; transparent gaps and silhouettes never expand
public final class SurfaceColorMesh{
    public record Cell(int face, int plane, int u, int v, int color){}
    public record Quad(int face, int plane, int u, int v, int width, int height, int color){}
    private record Plane(int face, int offset){}
    private record Point(int u, int v){}

    private SurfaceColorMesh(){}

    public static List<Quad> build(List<Cell> cells, int tile){
        if(tile < 1 || tile > 16) throw new IllegalArgumentException("Color tile must be between 1 and 16");
        var planes = new TreeMap<Plane, Map<Point, Integer>>(Comparator.comparingInt(Plane::face).thenComparingInt(Plane::offset));
        for(var cell : cells){
            if(cell.face < 0 || cell.face > 5) throw new IllegalArgumentException("Invalid face direction");
            planes.computeIfAbsent(new Plane(cell.face, cell.plane), ignored -> new HashMap<>()).put(new Point(cell.u, cell.v), cell.color);
        }
        var res = new ArrayList<Quad>();
        planes.forEach((plane, points) -> {
            if(tile > 1){
                var colors = new HashMap<Point, long[]>();
                points.forEach((point, color) -> {
                    var sum = colors.computeIfAbsent(new Point(Math.floorDiv(point.u, tile), Math.floorDiv(point.v, tile)), ignored -> new long[4]);
                    sum[0] += color >> 16 & 255; sum[1] += color >> 8 & 255; sum[2] += color & 255; sum[3]++;
                });
                points.replaceAll((point, color) -> {
                    var sum = colors.get(new Point(Math.floorDiv(point.u, tile), Math.floorDiv(point.v, tile)));
                    return (int)(sum[0] / sum[3]) << 16 | (int)(sum[1] / sum[3]) << 8 | (int)(sum[2] / sum[3]);
                });
            }
            var ordered = new ArrayList<>(points.keySet());
            ordered.sort(Comparator.comparingInt(Point::v).thenComparingInt(Point::u));
            for(var point : ordered){
                Integer color = points.get(point); if(color == null) continue;
                int width = 1, height = 1;
                while(color.equals(points.get(new Point(point.u + width, point.v)))) width++;
                boolean extend = true;
                while(extend){
                    for(int x = 0; x < width; x++) if(!color.equals(points.get(new Point(point.u + x, point.v + height)))){ extend = false; break; }
                    if(extend) height++;
                }
                for(int x = 0; x < width; x++) for(int y = 0; y < height; y++) points.remove(new Point(point.u + x, point.v + y));
                res.add(new Quad(plane.face, plane.offset, point.u, point.v, width, height, color));
            }
        });
        return List.copyOf(res);
    }
}
