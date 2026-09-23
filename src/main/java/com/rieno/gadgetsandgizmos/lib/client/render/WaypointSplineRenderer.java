package com.rieno.gadgetsandgizmos.lib.client.render;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rieno.gadgetsandgizmos.lib.navigation.WaypointSpline;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Collection;
import java.util.List;

// Render reusable waypoint splines and their authored control points in root-world coordinates
public final class WaypointSplineRenderer {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final double CURVE_SAMPLE_SPACING = 0.5D;
    private static final int MAX_CURVE_SAMPLES = 16_384;
    private static final double WAYPOINT_RADIUS = 0.16D;
    private static final double ENDPOINT_RADIUS = 0.22D;
    private static final double SELECTED_RADIUS = 0.28D;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the waypoint spline renderer
    private WaypointSplineRenderer() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Render detached route visuals relative to the active camera
    public static void render(PoseStack poseStack, Vec3 cameraPosition,
                              MultiBufferSource.BufferSource bufferSource,
                              Collection<RouteVisual> routes) {
        if (poseStack == null || cameraPosition == null || bufferSource == null
                || routes == null || routes.isEmpty()) return;
        VertexConsumer lines = bufferSource.getBuffer(RenderType.lines());
        poseStack.pushPose();
        poseStack.translate(-cameraPosition.x, -cameraPosition.y, -cameraPosition.z);
        for (RouteVisual route : routes) {
            if (route != null) renderRoute(poseStack, lines, route);
        }
        poseStack.popPose();
        bufferSource.endBatch(RenderType.lines());
    }

    // Render one sampled curve and its authored waypoint boxes
    private static void renderRoute(PoseStack poseStack, VertexConsumer lines,
                                    RouteVisual route) {
        List<Vec3> samples = route.spline().sample(
                CURVE_SAMPLE_SPACING, MAX_CURVE_SAMPLES);
        for (int idx = 1; idx < samples.size(); idx++) {
            line(lines, poseStack.last().pose(), samples.get(idx - 1), samples.get(idx),
                    route.lineColor());
        }
        List<Vec3> points = route.spline().waypoints();
        for (int idx = 0; idx < points.size(); idx++) {
            boolean endpoint = idx == 0 || idx == points.size() - 1;
            boolean selected = idx == route.selectedWaypoint();
            renderPoint(poseStack, lines, points.get(idx),
                    selected ? SELECTED_RADIUS : endpoint ? ENDPOINT_RADIUS : WAYPOINT_RADIUS,
                    selected ? route.selectedColor()
                            : endpoint ? route.endpointColor() : route.waypointColor());
        }
    }

    // Render one waypoint box
    private static void renderPoint(PoseStack poseStack, VertexConsumer lines,
                                    Vec3 point, double radius, int color) {
        float red = (color >> 16 & 0xFF) / 255.0F;
        float green = (color >> 8 & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;
        float alpha = (color >>> 24 & 0xFF) / 255.0F;
        LevelRenderer.renderLineBox(poseStack, lines,
                new AABB(point.x - radius, point.y - radius, point.z - radius,
                        point.x + radius, point.y + radius, point.z + radius),
                red, green, blue, alpha);
    }

    // Render one colored line
    private static void line(VertexConsumer lines, Matrix4f matrix,
                             Vec3 from, Vec3 to, int color) {
        Vector3f normal = new Vector3f((float) (to.x - from.x),
                (float) (to.y - from.y), (float) (to.z - from.z));
        if (normal.lengthSquared() < 1.0E-8F) return;
        normal.normalize();
        int alpha = color >>> 24 & 0xFF;
        lines.addVertex(matrix, (float) from.x, (float) from.y, (float) from.z)
                .setColor(color >> 16 & 0xFF, color >> 8 & 0xFF, color & 0xFF, alpha)
                .setNormal(normal.x, normal.y, normal.z);
        lines.addVertex(matrix, (float) to.x, (float) to.y, (float) to.z)
                .setColor(color >> 16 & 0xFF, color >> 8 & 0xFF, color & 0xFF, alpha)
                .setNormal(normal.x, normal.y, normal.z);
    }

    // Store one renderable spline and its colors
    public record RouteVisual(WaypointSpline spline, int lineColor,
                              int waypointColor, int endpointColor,
                              int selectedColor, int selectedWaypoint) {
        // Initialize one route visual
        public RouteVisual {
            spline = spline == null ? WaypointSpline.of(List.of()) : spline;
        }
    }
}
