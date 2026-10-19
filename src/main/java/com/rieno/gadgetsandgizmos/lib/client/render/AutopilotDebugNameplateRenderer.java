package com.rieno.gadgetsandgizmos.lib.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rieno.gadgetsandgizmos.lib.scm.AutopilotDebugSnapshot;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/** Render detached autopilot diagnostics as camera-facing vehicle nameplates. */
public final class AutopilotDebugNameplateRenderer {
    private static final double MAX_RENDER_DISTANCE_SQR = 256.0D * 256.0D;
    private static final float TEXT_SCALE = 0.0125F;
    private static final float LINE_GAP = 1.0F;
    private static final int BACKGROUND_COLOR = 0xB8000000;

    private AutopilotDebugNameplateRenderer() {
    }

    /** Render every visible snapshot above its root-world anchor. */
    public static void render(
            PoseStack poseStack,
            Vec3 cameraPosition,
            Quaternionf cameraRotation,
            MultiBufferSource.BufferSource bufferSource,
            Font font,
            Collection<AutopilotDebugSnapshot> snapshots
    ) {
        if (poseStack == null || cameraPosition == null || cameraRotation == null
                || bufferSource == null || font == null
                || snapshots == null || snapshots.isEmpty()) {
            return;
        }
        List<AutopilotDebugSnapshot> visible = snapshots.stream()
                .filter(snapshot -> snapshot != null
                        && snapshot.anchor().distanceToSqr(cameraPosition)
                        <= MAX_RENDER_DISTANCE_SQR)
                .sorted(Comparator.comparingDouble(snapshot ->
                        -snapshot.anchor().distanceToSqr(cameraPosition)))
                .toList();
        for (AutopilotDebugSnapshot snapshot : visible) {
            renderSnapshot(poseStack, cameraPosition, cameraRotation,
                    bufferSource, font, snapshot);
        }
        bufferSource.endBatch();
    }

    // Render one complete nameplate from bottom to top above its vehicle.
    private static void renderSnapshot(
            PoseStack poseStack,
            Vec3 cameraPosition,
            Quaternionf cameraRotation,
            MultiBufferSource.BufferSource bufferSource,
            Font font,
            AutopilotDebugSnapshot snapshot
    ) {
        List<Line> lines = lines(snapshot);
        if (lines.isEmpty()) return;
        float lineHeight = font.lineHeight + LINE_GAP;
        float height = lines.size() * lineHeight;
        int width = lines.stream().mapToInt(line -> font.width(line.text())).max().orElse(0);
        poseStack.pushPose();
        Vec3 offset = snapshot.anchor().subtract(cameraPosition);
        poseStack.translate(offset.x, offset.y, offset.z);
        poseStack.mulPose(new Quaternionf(cameraRotation));
        poseStack.scale(TEXT_SCALE, -TEXT_SCALE, TEXT_SCALE);
        float y = -height;
        for (Line line : lines) {
            float x = -width * 0.5F;
            font.drawInBatch(line.text(), x, y, line.color(), false,
                    poseStack.last().pose(), bufferSource,
                    Font.DisplayMode.SEE_THROUGH, BACKGROUND_COLOR,
                    LightTexture.FULL_BRIGHT);
            y += lineHeight;
        }
        poseStack.popPose();
    }

    // Flatten structured sections into a compact diagnostic panel.
    private static List<Line> lines(AutopilotDebugSnapshot snapshot) {
        List<Line> lines = new ArrayList<>();
        lines.add(new Line(" " + snapshot.vehicleName() + "  ["
                + snapshot.state().name() + "] ", stateColor(snapshot.state())));
        lines.add(new Line(" vehicle " + shortId(snapshot.vehicleId())
                + "  tick " + snapshot.gameTime() + " ", 0xFF9AA4B2));
        for (AutopilotDebugSnapshot.Section section : snapshot.sections()) {
            lines.add(new Line(" [" + section.name().toUpperCase() + "] ", 0xFF65D9FF));
            for (AutopilotDebugSnapshot.Entry entry : section.entries()) {
                lines.add(new Line(" " + entry.label() + ": " + entry.value() + " ",
                        toneColor(entry.tone())));
            }
        }
        return List.copyOf(lines);
    }

    // Resolve the title color for the primary behavior.
    private static int stateColor(AutopilotDebugSnapshot.State state) {
        return switch (state) {
            case IDLE -> 0xFFB0B6C0;
            case NAVIGATING, FOLLOWING_ROUTE -> 0xFF7FDBFF;
            case PLANNING -> 0xFFC58CFF;
            case AVOIDING, RECOVERING -> 0xFFFFC45C;
            case YIELDING -> 0xFFFFE26A;
            case DOCKING -> 0xFF72F1B8;
            case BLOCKED -> 0xFFFF6868;
        };
    }

    // Resolve one diagnostic value color.
    private static int toneColor(AutopilotDebugSnapshot.Tone tone) {
        return switch (tone) {
            case NORMAL -> 0xFFF2F4F8;
            case MUTED -> 0xFFA4ACB8;
            case ACCENT -> 0xFF80DEFF;
            case POSITIVE -> 0xFF7CF29A;
            case WARNING -> 0xFFFFD166;
            case DANGER -> 0xFFFF6B6B;
        };
    }

    // Get a stable compact vehicle identifier.
    private static String shortId(java.util.UUID id) {
        String value = id == null ? "00000000" : id.toString();
        return value.substring(0, Math.min(8, value.length()));
    }

    private record Line(String text, int color) {
    }
}
