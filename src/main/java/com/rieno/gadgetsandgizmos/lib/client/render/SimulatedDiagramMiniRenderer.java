package com.rieno.gadgetsandgizmos.lib.client.render;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.logging.LogUtils;
import dev.ryanhcode.sable.api.physics.force.ForceGroup;
import dev.ryanhcode.sable.api.physics.force.ForceGroups;
import dev.ryanhcode.sable.api.physics.force.QueuedForceGroup;
import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.content.entities.diagram.DiagramConfig;
import dev.simulated_team.simulated.content.entities.diagram.DiagramEntity;
import dev.simulated_team.simulated.content.entities.diagram.screen.DiagramScreen;
import dev.simulated_team.simulated.network.packets.contraption_diagram.DiagramDataPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.HangingEntity;
import org.joml.Vector3d;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

// Embed Simulated's contraption diagram with typed data, input and retry handling
public final class SimulatedDiagramMiniRenderer {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long RETRY_DELAY_MS = 250L;
    private static final long MAX_RETRY_DELAY_MS = 5000L;
    private static final int SOURCE_WIDTH = 512;
    private static final int SOURCE_HEIGHT = 320;
    private static final int MASS_BUTTON_OFFSET_X = 9;
    private static final int MASS_BUTTON_OFFSET_Y = 69;
    private static final int MASS_BUTTON_SIZE = 18;
    private static final ResourceLocation DIAGRAM_ENTITY_ID = ResourceLocation.fromNamespaceAndPath(
            "simulated", "contraption_diagram");

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Current sublevel ID
    private UUID subLevelId;

    // Sublevel currently allowed to retry
    private UUID retrySubLevelId;

    // Current hosted screen
    private HostedDiagramScreen screen;

    // Current client sublevel
    private ClientSubLevel clientSubLevel;

    // Shared diagram config
    private DiagramConfig sharedConfig;

    // Last hosted tick
    private long lastHostedTick = Long.MIN_VALUE;

    // Next retry time
    private long retryAtMs;

    // Consecutive failure count
    private int failureCount;

    // Current viewport X coordinate
    private final int viewportX;

    // Current viewport Y coordinate
    private final int viewportY;

    // Current viewport width
    private final int viewportWidth;

    // Current viewport height
    private final int viewportHeight;

    // Shared paper visibility
    private boolean sharedPaperVisible;

    // Current host-owned mass readout state
    private boolean massReadoutVisible;
    private double hostedMass;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the hosted diagram renderer
    public SimulatedDiagramMiniRenderer() {
        int textureWidth = DiagramScreen.DIAGRAM_TEXTURE.width;
        int textureHeight = DiagramScreen.DIAGRAM_TEXTURE.height;
        int diagramX = SOURCE_WIDTH / 2 - textureWidth / 2;
        int diagramY = SOURCE_HEIGHT / 2 - textureHeight / 2;
        viewportX = Math.max(0, diagramX - DiagramScreen.MAX_PAPER_OFFSET - 8);
        viewportY = Math.max(0, diagramY - 4);
        viewportWidth = Math.min(SOURCE_WIDTH - viewportX,
                textureWidth + DiagramScreen.MAX_PAPER_OFFSET + 18);
        viewportHeight = Math.min(SOURCE_HEIGHT - viewportY, textureHeight + 8);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the hosted diagram
    public boolean render(GuiGraphics graphics, int x, int y, int width, int height,
                          int mouseX, int mouseY, float partialTick,
                          UUID requestedSubLevel, DiagramDataSource data) {
        if (!prepare(requestedSubLevel, data)) {
            return false;
        }

        try {
            tickHostedScreen();
            float framePartialTick = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
            updateData(data);
            stabilizePaperAnimation();
            access().ct$setRenderTime(Float.MAX_VALUE);
            double scale = diagramScale(width, height);
            int localMouseX = (int) localMouseX(x, mouseX, scale);
            int localMouseY = (int) localMouseY(y, mouseY, scale);
            graphics.enableScissor(x, y, x + width, y + height);
            graphics.pose().pushPose();
            try {
                graphics.pose().translate(x, y, 0.0F);
                graphics.pose().scale((float) scale, (float) scale, 1.0F);
                graphics.pose().translate(-viewportX, -viewportY, 0.0F);
                screen.drawWindow(graphics, localMouseX, localMouseY, framePartialTick);
            } finally {
                graphics.pose().popPose();
                graphics.disableScissor();
            }

            graphics.pose().pushPose();
            try {
                graphics.pose().translate(x, y, 0.0F);
                graphics.pose().scale((float) scale, (float) scale, 1.0F);
                graphics.pose().translate(-viewportX, -viewportY, 0.0F);
                renderWidgets(graphics, localMouseX, localMouseY, partialTick);
                renderMassReadout(graphics);
            } finally {
                graphics.pose().popPose();
            }

            graphics.pose().pushPose();
            try {
                graphics.pose().translate(x, y, 0.0F);
                graphics.pose().scale((float) scale, (float) scale, 1.0F);
                graphics.pose().translate(-viewportX, -viewportY, 0.0F);
                screen.drawForeground(graphics, localMouseX, localMouseY, framePartialTick);
            } finally {
                graphics.pose().popPose();
            }
            markSuccess();
            return true;
        } catch (LinkageError | RuntimeException err) {
            fail("render", err);
            return false;
        }
    }

    // Handle a hosted diagram mouse click
    public boolean mouseClicked(int x, int y, int width, int height, double mouseX, double mouseY, int btn,
                                UUID requestedSubLevel, DiagramDataSource data) {
        if (!prepare(requestedSubLevel, data)) {
            return false;
        }
        double scale = diagramScale(width, height);
        double localX = localMouseX(x, mouseX, scale);
        double localY = localMouseY(y, mouseY, scale);
        try {
            if (btn == 0 && tryRotateButtonClick(localX, localY)) {
                captureSharedUiState();
                markSuccess();
                return true;
            }
            if (btn == 0 && tryMassButtonClick(localX, localY)) {
                markSuccess();
                return true;
            }
            boolean handled = screen.mouseClicked(localX, localY, btn);
            captureSharedUiState();
            markSuccess();
            return handled;
        } catch (LinkageError | RuntimeException err) {
            fail("mouse click", err);
            return false;
        }
    }

    // Handle a hosted diagram mouse release
    public boolean mouseReleased(int x, int y, int width, int height, double mouseX, double mouseY, int btn,
                                 UUID requestedSubLevel, DiagramDataSource data) {
        if (!prepare(requestedSubLevel, data)) {
            return false;
        }
        double scale = diagramScale(width, height);
        try {
            boolean handled = screen.mouseReleased(
                    localMouseX(x, mouseX, scale), localMouseY(y, mouseY, scale), btn);
            captureSharedUiState();
            markSuccess();
            return handled;
        } catch (LinkageError | RuntimeException err) {
            fail("mouse release", err);
            return false;
        }
    }

    // Handle a hosted diagram mouse drag
    public boolean mouseDragged(int x, int y, int width, int height, double mouseX, double mouseY, int btn,
                                double dragX, double dragY, UUID requestedSubLevel, DiagramDataSource data) {
        if (!prepare(requestedSubLevel, data)) {
            return false;
        }
        double scale = diagramScale(width, height);
        try {
            boolean handled = screen.mouseDragged(
                    localMouseX(x, mouseX, scale), localMouseY(y, mouseY, scale),
                    btn, dragX / scale, dragY / scale);
            captureSharedUiState();
            markSuccess();
            return handled;
        } catch (LinkageError | RuntimeException err) {
            fail("mouse drag", err);
            return false;
        }
    }

    // Handle a hosted diagram mouse scroll
    public boolean mouseScrolled(int x, int y, int width, int height, double mouseX, double mouseY,
                                 double scrollX, double scrollY, UUID requestedSubLevel, DiagramDataSource data) {
        if (!prepare(requestedSubLevel, data)) {
            return false;
        }
        double scale = diagramScale(width, height);
        try {
            boolean handled = screen.mouseScrolled(
                    localMouseX(x, mouseX, scale), localMouseY(y, mouseY, scale), scrollX, scrollY);
            captureSharedUiState();
            markSuccess();
            return handled;
        } catch (LinkageError | RuntimeException err) {
            fail("mouse scroll", err);
            return false;
        }
    }

    // Update the hosted diagram framebuffer
    public void tickHosted(UUID requestedSubLevel, DiagramDataSource data) {
        if (!prepare(requestedSubLevel, data)) {
            return;
        }
        try {
            tickHostedScreen();
            updateData(data);
            stabilizePaperAnimation();
            access().ct$setRenderTime(Float.MAX_VALUE);
            access().ct$renderContents(clientSubLevel, 0.0F);
            markSuccess();
        } catch (LinkageError | RuntimeException err) {
            fail("hosted tick", err);
        }
    }

    // Close the hosted diagram
    public void close() {
        releaseScreen();
        failureCount = 0;
        retryAtMs = 0L;
        retrySubLevelId = null;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Prepare the hosted diagram
    private boolean prepare(UUID requestedSubLevel, DiagramDataSource data) {
        if (requestedSubLevel == null || data == null) {
            return false;
        }
        if (!Objects.equals(retrySubLevelId, requestedSubLevel)) {
            failureCount = 0;
            retryAtMs = 0L;
            retrySubLevelId = requestedSubLevel;
        }
        if (System.currentTimeMillis() < retryAtMs) {
            return false;
        }

        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return false;
        }
        ClientSubLevelContainer container = SubLevelContainer.getContainer(level);
        SubLevel resolved = container == null ? null : container.getSubLevel(requestedSubLevel);
        if (!(resolved instanceof ClientSubLevel requested) || requested.isRemoved()) {
            releaseScreen();
            return false;
        }
        if (screen != null && requestedSubLevel.equals(subLevelId) && requested == clientSubLevel) {
            return true;
        }

        try {
            releaseScreen();
            subLevelId = requestedSubLevel;
            clientSubLevel = requested;
            @SuppressWarnings("unchecked")
            EntityType<? extends HangingEntity> entityType = (EntityType<? extends HangingEntity>)
                    (EntityType<?>) BuiltInRegistries.ENTITY_TYPE.get(DIAGRAM_ENTITY_ID);
            DiagramEntity entity = new DiagramEntity(entityType, level);
            if (sharedConfig == null) {
                sharedConfig = DiagramConfig.makeDefault(entity);
            }
            entity.setConfig(sharedConfig);
            screen = new HostedDiagramScreen(entity, requested);
            screen.useConfig(sharedConfig);
            access().ct$updateViewportOrientation();
            screen.init(minecraft, SOURCE_WIDTH, SOURCE_HEIGHT);
            applySharedUiState();
            updateData(data);
            return true;
        } catch (LinkageError | RuntimeException err) {
            fail("prepare", err);
            return false;
        }
    }

    // Update the hosted force data
    private void updateData(DiagramDataSource data) {
        hostedMass = Double.isFinite(data.mass()) ? Math.max(0.0D, data.mass()) : 0.0D;
        Map<ForceGroup, List<QueuedForceGroup.PointForce>> forcesByGroup = new HashMap<>();
        for (DiagramForceData force : data.forces()) {
            if (force == null || force.groupId() == null) {
                continue;
            }
            ForceGroup group = ForceGroups.REGISTRY.get(force.groupId());
            if (group == null) {
                continue;
            }
            QueuedForceGroup.PointForce pointForce = new QueuedForceGroup.PointForce(
                    new Vector3d(force.pointX(), force.pointY(), force.pointZ()),
                    new Vector3d(force.forceX(), force.forceY(), force.forceZ()));
            forcesByGroup.computeIfAbsent(group, ignored -> new ArrayList<>()).add(pointForce);
        }
        screen.updateData(new DiagramDataPacket(forcesByGroup, data.mass()));
    }

    // Draw the hosted widgets
    private void renderWidgets(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        for (Renderable renderable : screen.hostedRenderables()) {
            renderable.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    // Draw the selected sublevel mass beside Simulated's disabled mass button
    private void renderMassReadout(GuiGraphics graphics) {
        if (!massReadoutVisible) {
            return;
        }
        int windowX = SOURCE_WIDTH / 2 - DiagramScreen.DIAGRAM_TEXTURE.width / 2;
        int windowY = SOURCE_HEIGHT / 2 - DiagramScreen.DIAGRAM_TEXTURE.height / 2;
        String text = String.format(Locale.ROOT, "Mass: %.2f kg", hostedMass);
        int textX = windowX + MASS_BUTTON_OFFSET_X + MASS_BUTTON_SIZE + 4;
        int textY = windowY + MASS_BUTTON_OFFSET_Y + 5;
        int textWidth = Minecraft.getInstance().font.width(text);
        graphics.fill(textX - 3, textY - 3, textX + textWidth + 3, textY + 11, 0xD010151D);
        graphics.drawString(Minecraft.getInstance().font, text, textX, textY, 0xFFE8EEF6, false);
    }

    // Try one diagram rotation control
    private boolean tryRotateButtonClick(double mouseX, double mouseY) {
        int diagramX = SOURCE_WIDTH / 2 - DiagramScreen.DIAGRAM_TEXTURE.width / 2;
        int diagramY = SOURCE_HEIGHT / 2 - DiagramScreen.DIAGRAM_TEXTURE.height / 2;
        List<RotationHit> candidates = new ArrayList<>(4);
        if (sharedConfig.pitch() > -45.0D) {
            candidates.add(new RotationHit(diagramX + 236, diagramY + 8, 7, 7, 0, -1));
        }
        if (sharedConfig.pitch() < 45.0D) {
            candidates.add(new RotationHit(diagramX + 236, diagramY + 22, 7, 7, 0, 1));
        }
        candidates.add(new RotationHit(diagramX + 228, diagramY + 12, 8, 13, 1, 0));
        candidates.add(new RotationHit(diagramX + 243, diagramY + 12, 8, 13, -1, 0));

        RotationHit best = null;
        double bestDistance = Double.MAX_VALUE;
        for (RotationHit candidate : candidates) {
            if (!candidate.contains(mouseX, mouseY, 2)) {
                continue;
            }
            double dx = mouseX - candidate.centerX();
            double dy = mouseY - candidate.centerY();
            double distance = dx * dx + dy * dy;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        if (best == null) {
            return false;
        }
        access().ct$rotateDiagram(best.yawSteps(), best.pitchSteps());
        return true;
    }

    // Toggle the hosted readout for Simulated's otherwise inactive mass control
    private boolean tryMassButtonClick(double mouseX, double mouseY) {
        int windowX = SOURCE_WIDTH / 2 - DiagramScreen.DIAGRAM_TEXTURE.width / 2;
        int windowY = SOURCE_HEIGHT / 2 - DiagramScreen.DIAGRAM_TEXTURE.height / 2;
        int left = windowX + MASS_BUTTON_OFFSET_X;
        int top = windowY + MASS_BUTTON_OFFSET_Y;
        if (mouseX < left || mouseX >= left + MASS_BUTTON_SIZE
                || mouseY < top || mouseY >= top + MASS_BUTTON_SIZE) {
            return false;
        }
        massReadoutVisible = !massReadoutVisible;
        return true;
    }

    // Update the hosted screen once per game tick
    private void tickHostedScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        long gameTime = minecraft.level == null ? Long.MIN_VALUE : minecraft.level.getGameTime();
        if (gameTime == lastHostedTick) {
            return;
        }
        screen.tick();
        lastHostedTick = gameTime;
    }

    // Capture the shared UI state
    private void captureSharedUiState() {
        if (screen != null) {
            sharedPaperVisible = access().ct$isPaperVisible();
        }
    }

    // Apply the shared UI state
    private void applySharedUiState() {
        access().ct$setPaperVisible(sharedPaperVisible);
        stabilizePaperAnimation();
    }

    // Stabilize the paper animation
    private void stabilizePaperAnimation() {
        boolean visible = access().ct$isPaperVisible();
        float paper = visible ? DiagramScreen.MAX_PAPER_OFFSET : DiagramScreen.MIN_PAPER_OFFSET;
        float tab = visible ? 1.0F : 0.0F;
        access().ct$setLastPaperOffset(paper);
        access().ct$setPaperOffset(paper);
        access().ct$setLastTabOffset(tab);
        access().ct$setTabOffset(tab);
    }

    // Get the hosted diagram access
    private DiagramScreenAccess access() {
        return (DiagramScreenAccess) (Object) screen;
    }

    // Get the diagram scale
    private double diagramScale(int width, int height) {
        return Math.min(width / (double) viewportWidth, height / (double) viewportHeight);
    }

    // Get the local mouse X coordinate
    private double localMouseX(int x, double mouseX, double scale) {
        return viewportX + (mouseX - x) / scale;
    }

    // Get the local mouse Y coordinate
    private double localMouseY(int y, double mouseY, double scale) {
        return viewportY + (mouseY - y) / scale;
    }

    // Record one hosted diagram failure and retry delay
    private void fail(String operation, Throwable err) {
        failureCount++;
        long delay = Math.min(MAX_RETRY_DELAY_MS,
                RETRY_DELAY_MS << Math.min(4, Math.max(0, failureCount - 1)));
        retryAtMs = System.currentTimeMillis() + delay;
        LOGGER.warn("Hosted contraption diagram {} failed on attempt {}, retrying in {} ms",
                operation, failureCount, delay, err);
        releaseScreen();
    }

    // Clear the retry state after one successful operation
    private void markSuccess() {
        failureCount = 0;
        retryAtMs = 0L;
    }

    // Release the current hosted screen
    private void releaseScreen() {
        captureSharedUiState();
        if (screen != null) {
            try {
                access().ct$freeFramebuffers();
            } catch (LinkageError | RuntimeException err) {
                LOGGER.warn("Failed to release hosted contraption diagram framebuffers", err);
            }
        }
        screen = null;
        clientSubLevel = null;
        subLevelId = null;
        lastHostedTick = Long.MIN_VALUE;
        massReadoutVisible = false;
        hostedMass = 0.0D;
    }

    // Expose protected screen drawing to the hosted renderer
    private static final class HostedDiagramScreen extends DiagramScreen {
        // Initialize the hosted diagram screen
        private HostedDiagramScreen(DiagramEntity diagram, ClientSubLevel subLevel) {
            super(diagram, subLevel);
        }

        // Apply the shared diagram config
        private void useConfig(DiagramConfig config) {
            this.config = config;
        }

        // Draw the hosted window
        private void drawWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            renderWindow(graphics, mouseX, mouseY, partialTick);
        }

        // Draw the hosted foreground
        private void drawForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            renderWindowForeground(graphics, mouseX, mouseY, partialTick);
        }

        // Get the hosted renderables
        @SuppressWarnings("unchecked")
        private List<Renderable> hostedRenderables() {
            return (List<Renderable>) (List<?>) getRenderables();
        }
    }

    // Store one rotation control hit area
    private record RotationHit(int x, int y, int width, int height, int yawSteps, int pitchSteps) {
        // Check if the point is inside the expanded hit area
        private boolean contains(double px, double py, int padding) {
            return px >= x - padding && px < x + width + padding
                    && py >= y - padding && py < y + height + padding;
        }

        // Get the hit area center X coordinate
        private double centerX() {
            return x + width / 2.0D;
        }

        // Get the hit area center Y coordinate
        private double centerY() {
            return y + height / 2.0D;
        }
    }
}
