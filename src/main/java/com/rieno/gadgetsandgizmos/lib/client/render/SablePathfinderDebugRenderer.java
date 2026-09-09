package com.rieno.gadgetsandgizmos.lib.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rieno.gadgetsandgizmos.lib.navigation.SablePathfinder;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Collection;

// Render detached Sable pathfinder debug snapshots in root-world coordinates.
public final class SablePathfinderDebugRenderer {
    private static final int MAX_TARGET_DASHES = 512;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the pathfinder debug renderer
    private SablePathfinderDebugRenderer() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Render the supplied root-world route snapshots relative to the active camera.
    public static void render(
            PoseStack poseStack,
            Vec3 cameraPosition,
            MultiBufferSource.BufferSource bufferSource,
            Collection<SablePathfinder.DebugRoute> routes
    ) {
        if (poseStack == null || cameraPosition == null || bufferSource == null
                || routes == null || routes.isEmpty()) {
            return;
        }

        VertexConsumer lines = bufferSource.getBuffer(RenderType.lines());
        poseStack.pushPose();
        poseStack.translate(-cameraPosition.x, -cameraPosition.y, -cameraPosition.z);
        for (SablePathfinder.DebugRoute route : routes) {
            if (route == null) {
                continue;
            }
            renderRoute(poseStack, lines, route);
        }
        poseStack.popPose();
        bufferSource.endBatch(RenderType.lines());
    }

    // Render one route with outcome-specific lines, green origin and red destination.
    private static void renderRoute(
            PoseStack poseStack,
            VertexConsumer lines,
            SablePathfinder.DebugRoute route
    ) {
        RouteColor color = route.style() == SablePathfinder.DebugRouteStyle.CACHED
                ? RouteColor.CACHED : RouteColor.forOutcome(route.outcome());
        for (SablePathfinder.DebugSegment checked : route.checkedSegments()) {
            if (checked == null) {
                continue;
            }
            RouteColor checkedColor = RouteColor.forTraversal(checked.result());
            line(lines, poseStack.last().pose(), checked.start(), checked.end(),
                    checkedColor.red(), checkedColor.green(), checkedColor.blue());
        }
        Vec3 previous = route.origin();
        for (SablePathfinder.Waypoint waypoint : route.waypoints()) {
            if (waypoint == null) {
                continue;
            }
            Vec3 current = waypoint.position();
            line(lines, poseStack.last().pose(), previous, current,
                    color.red(), color.green(), color.blue());
            renderPoint(poseStack, lines, current, 0.14D,
                    color.red(), color.green(), color.blue());
            previous = current;
        }
        if (previous.distanceToSqr(route.target()) > 1.0E-8D) {
            if (route.targetLegValidated()) {
                line(lines, poseStack.last().pose(), previous, route.target(),
                        color.red(), color.green(), color.blue());
            } else {
                dashedLine(lines, poseStack.last().pose(), previous, route.target(),
                        RouteColor.PENDING_TARGET.red(), RouteColor.PENDING_TARGET.green(),
                        RouteColor.PENDING_TARGET.blue());
            }
        }
        renderPoint(poseStack, lines, route.origin(), 0.20D, 0.20F, 1.0F, 0.30F);
        renderPoint(poseStack, lines, route.target(), 0.20D, 1.0F, 0.20F, 0.20F);
    }

    // Render one path point.
    private static void renderPoint(
            PoseStack poseStack,
            VertexConsumer lines,
            Vec3 point,
            double radius,
            float red,
            float green,
            float blue
    ) {
        LevelRenderer.renderLineBox(poseStack, lines,
                new AABB(point.x - radius, point.y - radius, point.z - radius,
                        point.x + radius, point.y + radius, point.z + radius),
                red, green, blue, 1.0F);
    }

    // Render one line segment.
    private static void line(
            VertexConsumer lines,
            Matrix4f matrix,
            Vec3 from,
            Vec3 to,
            float red,
            float green,
            float blue
    ) {
        Vector3f normal = new Vector3f(
                (float) (to.x - from.x),
                (float) (to.y - from.y),
                (float) (to.z - from.z));
        if (normal.lengthSquared() < 1.0E-8F) {
            return;
        }
        normal.normalize();
        lines.addVertex(matrix, (float) from.x, (float) from.y, (float) from.z)
                .setColor(red, green, blue, 1.0F)
                .setNormal(normal.x, normal.y, normal.z);
        lines.addVertex(matrix, (float) to.x, (float) to.y, (float) to.z)
                .setColor(red, green, blue, 1.0F)
                .setNormal(normal.x, normal.y, normal.z);
    }

    // Draw an unvalidated destination connection as a bounded dashed guide, never as an accepted route leg.
    private static void dashedLine(
            VertexConsumer lines,
            Matrix4f matrix,
            Vec3 from,
            Vec3 to,
            float red,
            float green,
            float blue
    ) {
        Vec3 delta = to.subtract(from);
        double length = delta.length();
        if (length <= 1.0E-8D) return;
        Vec3 direction = delta.scale(1.0D / length);
        double patternLength = Math.max(1.5D, length / MAX_TARGET_DASHES);
        double dashLength = patternLength * 0.6D;
        for (double distance = 0.0D; distance < length; distance += patternLength) {
            Vec3 start = from.add(direction.scale(distance));
            Vec3 end = from.add(direction.scale(
                    Math.min(length, distance + dashLength)));
            line(lines, matrix, start, end, red, green, blue);
        }
    }

    // Store the route color for an outcome.
    private record RouteColor(float red, float green, float blue) {
        // Keep completed retained routes visually distinct from live guidance and search probes.
        private static final RouteColor CACHED = new RouteColor(1.0F, 0.55F, 0.08F);
        // Distinguish a requested destination connection from collision-validated route geometry.
        private static final RouteColor PENDING_TARGET = new RouteColor(1.0F, 0.90F, 0.25F);

        // Resolve one route outcome color
        private static RouteColor forOutcome(SablePathfinder.Outcome outcome) {
            return switch (outcome == null ? SablePathfinder.Outcome.BLOCKED : outcome) {
                case COMPLETE -> new RouteColor(0.25F, 0.85F, 1.0F);
                case UNAVAILABLE -> new RouteColor(1.0F, 0.72F, 0.20F);
                case LIMIT_REACHED -> new RouteColor(0.85F, 0.35F, 1.0F);
                case BLOCKED -> new RouteColor(1.0F, 0.25F, 0.25F);
            };
        }

        // Keep live search checks visually separate from an accepted route.
        private static RouteColor forTraversal(SablePathfinder.TraversalResult result) {
            return switch (result == null ? SablePathfinder.TraversalResult.BLOCKED : result) {
                case CLEAR -> new RouteColor(0.30F, 0.95F, 0.35F);
                case BLOCKED -> new RouteColor(1.0F, 0.12F, 0.12F);
                case UNAVAILABLE -> new RouteColor(1.0F, 0.58F, 0.08F);
            };
        }
    }
}
