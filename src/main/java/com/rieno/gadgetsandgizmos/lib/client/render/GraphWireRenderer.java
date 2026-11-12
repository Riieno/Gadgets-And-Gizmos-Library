package com.rieno.gadgetsandgizmos.lib.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rieno.gadgetsandgizmos.lib.graph.render.GraphViewport;
import com.rieno.gadgetsandgizmos.lib.graph.render.GraphWireGeometry;
import org.joml.Matrix4f;

// Draw the routed geometry through the same graph viewport used by nodes and hit testing
public final class GraphWireRenderer{
    private GraphWireRenderer(){}

    // Keep other wires at forty percent opacity while the hovered wire stays fully opaque
    public static int hoverColor(int color, boolean hovered, boolean wireHovered){
        if(hovered) return 0xFFFFFFFF;
        if(!wireHovered) return color;
        return (color & 0x00FFFFFF) | (Math.round((color >>> 24) * 0.4F) << 24);
    }

    // Share each join between adjacent quads so translucent wires are not blended twice at their corners
    public static void add(VertexConsumer vertices, Matrix4f pose, GraphWireGeometry.Path path, GraphViewport viewport, double width, int color){
        if(width <= 0) return;
        var points = path.points();
        GraphWireGeometry.Point prev = null, point = points.getFirst();
        Join prevJoin = null;
        int idx = 0;
        while(true){
            int nextIdx = idx + 1;
            while(nextIdx < points.size() && Math.hypot(points.get(nextIdx).x() - point.x(), points.get(nextIdx).y() - point.y()) < 1.0E-8D) nextIdx++;
            var next = nextIdx < points.size() ? points.get(nextIdx) : null;
            if(prev == null && next == null) return;
            Join current = join(prev, point, next, viewport, width / 2);
            if(prevJoin != null){
                vertices.addVertex(pose, prevJoin.rightX(), prevJoin.rightY(), 0).setColor(color);
                vertices.addVertex(pose, prevJoin.leftX(), prevJoin.leftY(), 0).setColor(color);
                vertices.addVertex(pose, current.leftX(), current.leftY(), 0).setColor(color);
                vertices.addVertex(pose, current.rightX(), current.rightY(), 0).setColor(color);
            }
            if(next == null) return;
            prev = point; point = next; idx = nextIdx; prevJoin = current;
        }
    }

    private static Join join(GraphWireGeometry.Point prev, GraphWireGeometry.Point point, GraphWireGeometry.Point next, GraphViewport viewport, double half){
        double inX = prev == null ? next.x() - point.x() : point.x() - prev.x();
        double inY = prev == null ? next.y() - point.y() : point.y() - prev.y();
        double outX = next == null ? inX : next.x() - point.x(), outY = next == null ? inY : next.y() - point.y();
        double inLength = Math.hypot(inX, inY), outLength = Math.hypot(outX, outY);
        inX /= inLength; inY /= inLength; outX /= outLength; outY /= outLength;
        double nx = -inY - outY, ny = inX + outX, length = Math.hypot(nx, ny);
        if(length < 1.0E-8D){ nx = -outY; ny = outX; }
        else{ nx /= length; ny /= length; }
        double scale = half / Math.max(0.25, -nx * outY + ny * outX);
        double x = viewport.screenX(point.x()), y = viewport.screenY(point.y());
        if(prev == null){ x -= outX * half; y -= outY * half; }
        if(next == null){ x += inX * half; y += inY * half; }
        return new Join((float)(x + nx * scale), (float)(y + ny * scale), (float)(x - nx * scale), (float)(y - ny * scale));
    }

    private record Join(float leftX, float leftY, float rightX, float rightY){}
}
