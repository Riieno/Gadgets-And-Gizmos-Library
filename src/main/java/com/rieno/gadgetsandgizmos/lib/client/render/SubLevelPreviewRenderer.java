package com.rieno.gadgetsandgizmos.lib.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexSorting;
import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

/**
 * A reusable off-screen renderer and picker for loaded Sable sub-level blocks.
 *
 * <p>The renderer reads live loaded chunk data, renders it into a GUI texture,
 * and returns block/face picks in the sub-level's stored coordinate system. It
 * owns no gameplay state: callers supply grouping, highlighting, filters, and
 * any optional block-entity decoration needed by their own integrations.</p>
 */
public final class SubLevelPreviewRenderer implements AutoCloseable {
    public static final int DEFAULT_MAX_RENDERED_BLOCKS = 16_384;
    private static final AtomicInteger TEXTURE_IDS = new AtomicInteger();
    private static final float FIELD_OF_VIEW = (float) Math.toRadians(42.0D);
    private static final float MIN_DISTANCE = 3.0F;
    private static final float MAX_DISTANCE = 512.0F;

    /** A loaded non-air block in its owning sub-level's stored plot position. */
    public record PreviewBlock(UUID subLevelId, BlockPos position, BlockState state) {
        public PreviewBlock {
            position = position == null ? BlockPos.ZERO : position.immutable();
            state = state == null ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState() : state;
        }
    }

    /**
     * A server-synchronised block used only when the client is not currently
     * tracking the source Sable sub-level. The position is already expressed
     * in the root body's coordinate frame, so it remains renderable and
     * pickable without a client-side Sable pose.
     */
    public record SnapshotBlock(UUID subLevelId, BlockPos position, BlockState state, Vec3 rootPosition) {
        public SnapshotBlock {
            position = position == null ? BlockPos.ZERO : position.immutable();
            state = state == null ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState() : state;
            rootPosition = rootPosition == null ? Vec3.atLowerCornerOf(position) : rootPosition;
        }
    }

    /** A caller-defined target together with a face resolved by the picker. */
    public record PickTarget(String id, UUID subLevelId, BlockPos position, Direction face) {
        public PickTarget {
            id = id == null ? "" : id;
            position = position == null ? BlockPos.ZERO : position.immutable();
        }

        public PickTarget(String id, UUID subLevelId, BlockPos position) {
            this(id, subLevelId, position, null);
        }
    }

    /** An outline to render over a block or one of its faces. */
    public record Highlight(UUID subLevelId, BlockPos position, Direction face, int color) {
        public Highlight {
            position = position == null ? BlockPos.ZERO : position.immutable();
        }

        public Highlight(UUID subLevelId, BlockPos position, int color) {
            this(subLevelId, position, null, color);
        }
    }

    /** A direction marker rendered in a body's stored coordinate frame. */
    public record Marker(UUID subLevelId, Vec3 position, Vec3 direction, int color) {
        public Marker {
            position = position == null ? Vec3.ZERO : position;
            direction = direction == null ? Vec3.ZERO : direction;
        }
    }

    /**
     * Optional renderer for details owned by an optional mod. The normal
     * block-entity renderer is still called afterwards unless Create's
     * Flywheel-only kinetic fallback rendered the entity itself.
     */
    @FunctionalInterface
    public interface BlockEntityPreviewDecorator {
        void render(BlockEntity blockEntity, BlockState state, PoseStack pose,
                    MultiBufferSource buffers, int light, int overlay);
    }

    private record RayHit(float distance, Direction face) {
    }

    private record BlockKey(UUID subLevelId, BlockPos position) {
    }

    private final ResourceLocation textureBase;
    private final int maximumBlocks;
    private final List<BlockEntityPreviewDecorator> decorators;
    private final List<PreviewBlock> blocks = new ArrayList<>();
    private final Map<BlockKey, SnapshotBlock> fallbackBlocks = new LinkedHashMap<>();
    private final Set<BlockKey> fullyOccludedBlocks = new HashSet<>();
    private Predicate<PreviewBlock> visibility = ignored -> true;
    // In wireframe mode callers may nominate blocks which should stay as
    // ordinary models. Those are the only blocks returned for picking, letting
    // wireframe geometry remain a click-through visual aid.
    private Predicate<PreviewBlock> wireframeVisibility = ignored -> true;
    private RenderTarget target;
    private ResourceLocation texture;
    private UUID rootId;
    private List<UUID> bodyIds = List.of();
    private boolean contentsDirty = true;
    private boolean cameraNeedsFit = true;
    private boolean targetDirty = true;
    private boolean truncated;
    private boolean wireframe;
    private boolean usingFallbackScene;
    private int lastRebuildTick = Integer.MIN_VALUE;
    private float yaw = 35.0F;
    private float pitch = -26.0F;
    private float distance = 28.0F;
    private float panX;
    private float panY;
    private final Vector3f center = new Vector3f();
    private boolean dragging;
    private boolean panning;
    private int pressButton = -1;

    public SubLevelPreviewRenderer(ResourceLocation textureBase) {
        this(textureBase, DEFAULT_MAX_RENDERED_BLOCKS, List.of());
    }

    public SubLevelPreviewRenderer(ResourceLocation textureBase, int maximumBlocks,
                                   Collection<BlockEntityPreviewDecorator> decorators) {
        this.textureBase = Objects.requireNonNull(textureBase, "textureBase");
        this.maximumBlocks = Math.max(1, maximumBlocks);
        this.decorators = decorators == null ? List.of() : decorators.stream()
                .filter(Objects::nonNull).toList();
    }

    /** Set the root and all live sub-level bodies which form this preview. */
    public void setBodies(UUID rootSubLevelId, Collection<UUID> subLevelIds) {
        List<UUID> normalized = new ArrayList<>();
        if (rootSubLevelId != null) normalized.add(rootSubLevelId);
        if (subLevelIds != null) for (UUID id : subLevelIds) {
            if (id != null && !normalized.contains(id)) normalized.add(id);
        }
        if (!Objects.equals(rootId, rootSubLevelId) || !bodyIds.equals(normalized)) {
            rootId = rootSubLevelId;
            bodyIds = List.copyOf(normalized);
            contentsDirty = true;
            cameraNeedsFit = true;
            targetDirty = true;
        }
    }

    /**
     * Supply a detached server-side scene for use when the live source body is
     * outside the client's tracking range. A live client scene remains
     * preferred automatically whenever it is available.
     */
    public void setSnapshotBlocks(Collection<SnapshotBlock> snapshot) {
        Map<BlockKey, SnapshotBlock> normalized = new LinkedHashMap<>();
        if (snapshot != null) for (SnapshotBlock block : snapshot) {
            if (block == null || block.subLevelId() == null || block.state().isAir()
                    || block.state().getRenderShape() == RenderShape.INVISIBLE
                    || normalized.size() >= maximumBlocks) {
                continue;
            }
            normalized.putIfAbsent(new BlockKey(block.subLevelId(), block.position()), block);
        }
        if (fallbackBlocks.equals(normalized)) return;
        fallbackBlocks.clear();
        fallbackBlocks.putAll(normalized);
        contentsDirty = true;
        cameraNeedsFit = true;
        targetDirty = true;
    }

    /** Filter presentation and picking without altering the loaded scene. */
    public void setVisibilityPredicate(Predicate<PreviewBlock> predicate) {
        visibility = predicate == null ? ignored -> true : predicate;
        cameraNeedsFit = true;
        targetDirty = true;
    }

    /**
     * Select which visible blocks use the global wireframe presentation. A
     * false result keeps that block fully rendered and selectable while its
     * wireframe neighbours are visual-only and click through it.
     */
    public void setWireframePredicate(Predicate<PreviewBlock> predicate) {
        wireframeVisibility = predicate == null ? ignored -> true : predicate;
        targetDirty = true;
    }

    /** Render block bounds instead of full block and block-entity models. */
    public void setWireframe(boolean enabled) {
        if (wireframe == enabled) return;
        wireframe = enabled;
        targetDirty = true;
    }

    public boolean wireframe() {
        return wireframe;
    }

    /** Request a re-render after caller-owned highlight state changes. */
    public void invalidate() {
        targetDirty = true;
    }

    /** Render this live scene into the specified GUI rectangle. */
    public boolean render(GuiGraphics graphics, int x, int y, int width, int height, float partialTick,
                          Collection<Highlight> highlights) {
        return render(graphics, x, y, width, height, partialTick, highlights, List.of());
    }

    /** Render this live scene with caller-owned highlights and directional markers. */
    public boolean render(GuiGraphics graphics, int x, int y, int width, int height, float partialTick,
                          Collection<Highlight> highlights, Collection<Marker> markers) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || rootId == null || bodyIds.isEmpty()) return false;
        if (!refreshBlocks(minecraft.level, partialTick)) return false;
        ensureTarget(minecraft, width, height);
        if (target == null || texture == null) return false;
        if (targetDirty) renderFrame(minecraft, partialTick, highlights, markers);
        drawTexture(graphics, x, y, width, height);
        return true;
    }

    public int blockCount() {
        return blocks.size();
    }

    public int visibleBlockCount() {
        return visibleBlocks().size();
    }

    public boolean truncated() {
        return truncated;
    }

    /** A detached snapshot of all currently loaded blocks. */
    public List<PreviewBlock> blocks() {
        return List.copyOf(blocks);
    }

    /** A detached snapshot of visible loaded blocks. */
    public List<PreviewBlock> visibleBlocksSnapshot() {
        return List.copyOf(visibleBlocks());
    }

    public Optional<PreviewBlock> block(UUID subLevelId, BlockPos position) {
        return Optional.ofNullable(previewBlock(subLevelId, position));
    }

    /** Convert a stored body block position into the root preview coordinate frame. */
    public BlockPos relativePosition(UUID subLevelId, BlockPos position, float partialTick) {
        ClientLevel level = Minecraft.getInstance().level;
        ClientSubLevel root = resolve(level, rootId);
        Vector3f point = level == null || root == null ? null
                : rootPosition(level, root, subLevelId, position, partialTick);
        return point == null ? (position == null ? BlockPos.ZERO : position.immutable())
                : new BlockPos(gridCoordinate(point.x), gridCoordinate(point.y), gridCoordinate(point.z));
    }

    public String blockName(UUID subLevelId, BlockPos position) {
        return block(subLevelId, position).map(entry -> entry.state().getBlock().getName().getString())
                .filter(name -> name != null && !name.isBlank()).orElse("Unknown block");
    }

    public void mousePressed(double mouseX, double mouseY, int button) {
        pressButton = button;
        dragging = false;
        panning = button == GLFW.GLFW_MOUSE_BUTTON_RIGHT || button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE;
    }

    public void mouseDragged(double deltaX, double deltaY) {
        if (pressButton < 0) return;
        if (Math.abs(deltaX) > 0.01D || Math.abs(deltaY) > 0.01D) dragging = true;
        if (panning) {
            float units = worldUnitsPerPixel();
            panX += (float) deltaX * units;
            panY -= (float) deltaY * units;
        } else if (pressButton == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            yaw += (float) deltaX * 0.45F;
            pitch = Mth.clamp(pitch + (float) deltaY * 0.45F, -85.0F, 85.0F);
        }
        targetDirty = true;
    }

    public void mouseScrolled(double amount) {
        if (amount == 0.0D) return;
        distance = Mth.clamp(distance * (float) Math.pow(0.86D, amount), MIN_DISTANCE, MAX_DISTANCE);
        targetDirty = true;
    }

    /** Return each visible block as a default block-level target. */
    public List<PickTarget> pickTargets() {
        return visibleBlocks().stream().filter(block -> !rendersWireframe(block)).map(block -> new PickTarget(
                block.subLevelId() + ":" + block.position().asLong(), block.subLevelId(), block.position())).toList();
    }

    /** Resolve one click against caller-defined, visible block targets. */
    public PickTarget mouseReleased(double mouseX, double mouseY, int button, int viewX, int viewY,
                                    int viewWidth, int viewHeight, float partialTick,
                                    Collection<PickTarget> targets) {
        PickTarget result = null;
        if (button == pressButton && button == GLFW.GLFW_MOUSE_BUTTON_LEFT && !dragging) {
            result = pick(mouseX - viewX, mouseY - viewY, viewWidth, viewHeight, partialTick, targets);
        }
        pressButton = -1;
        panning = false;
        dragging = false;
        return result;
    }

    @Override
    public void close() {
        Minecraft minecraft = Minecraft.getInstance();
        if (texture != null) minecraft.getTextureManager().release(texture);
        if (target != null) target.destroyBuffers();
        texture = null;
        target = null;
        blocks.clear();
        fullyOccludedBlocks.clear();
        contentsDirty = true;
    }

    private boolean refreshBlocks(ClientLevel level, float partialTick) {
        ClientSubLevel root = resolve(level, rootId);
        if (root == null || root.isRemoved()) {
            return refreshFallbackBlocks(level, partialTick);
        }
        if (usingFallbackScene) {
            contentsDirty = true;
            cameraNeedsFit = true;
        }
        usingFallbackScene = false;
        int gameTick = (int) level.getGameTime();
        if (contentsDirty || gameTick - lastRebuildTick >= 20) {
            rebuildBlocks(level);
            lastRebuildTick = gameTick;
            contentsDirty = false;
            targetDirty = true;
        }
        if (cameraNeedsFit) fitCamera(level, partialTick);
        return true;
    }

    private boolean refreshFallbackBlocks(ClientLevel level, float partialTick) {
        if (fallbackBlocks.isEmpty()) return false;
        if (contentsDirty || !usingFallbackScene) {
            blocks.clear();
            fallbackBlocks.values().forEach(block -> blocks.add(new PreviewBlock(
                    block.subLevelId(), block.position(), block.state())));
            blocks.sort(Comparator.comparing((PreviewBlock block) -> block.subLevelId().toString())
                    .thenComparing(PreviewBlock::position));
            cullFullyOccludedBlocks();
            contentsDirty = false;
            targetDirty = true;
            cameraNeedsFit = true;
        }
        usingFallbackScene = true;
        if (cameraNeedsFit) fitCamera(level, partialTick);
        return !blocks.isEmpty();
    }

    private void rebuildBlocks(ClientLevel level) {
        blocks.clear();
        truncated = false;
        for (UUID bodyId : bodyIds) {
            ClientSubLevel body = resolve(level, bodyId);
            if (body == null || body.isRemoved()) continue;
            try {
                for (PlotChunkHolder holder : body.getPlot().getLoadedChunks()) {
                    if (blocks.size() >= maximumBlocks) {
                        truncated = true;
                        break;
                    }
                    LevelChunk chunk = holder.getChunk();
                    if (chunk == null || chunk.isEmpty()) continue;
                    chunk.findBlocks(state -> !state.isAir(), (position, state) -> {
                        if (blocks.size() >= maximumBlocks) {
                            truncated = true;
                        } else if (state.getRenderShape() != RenderShape.INVISIBLE) {
                            blocks.add(new PreviewBlock(bodyId, position.immutable(), state));
                        }
                    });
                    if (truncated) break;
                }
            } catch (RuntimeException | LinkageError ignored) {
                // A body can be replaced while its client chunk snapshot is rebuilt.
            }
            if (truncated) break;
        }
        blocks.sort(Comparator.comparing((PreviewBlock block) -> block.subLevelId().toString())
                .thenComparing(PreviewBlock::position));
        cullFullyOccludedBlocks();
    }

    /**
     * Cache blocks completely surrounded by opaque neighbours. They are skipped
     * for normal rendering, but retained for wireframe mode: wireframe is
     * explicitly an inspection view where users must be able to reveal and
     * select interior blocks through the surrounding line geometry.
     */
    private void cullFullyOccludedBlocks() {
        fullyOccludedBlocks.clear();
        if (blocks.size() < 7) return;
        Set<BlockKey> opaque = new HashSet<>();
        for (PreviewBlock block : blocks) {
            if (block.state().canOcclude()) {
                opaque.add(new BlockKey(block.subLevelId(), block.position()));
            }
        }
        blocks.stream().filter(block -> block.state().canOcclude()
                && opaque.contains(new BlockKey(block.subLevelId(), block.position().relative(Direction.DOWN)))
                && opaque.contains(new BlockKey(block.subLevelId(), block.position().relative(Direction.UP)))
                && opaque.contains(new BlockKey(block.subLevelId(), block.position().relative(Direction.NORTH)))
                && opaque.contains(new BlockKey(block.subLevelId(), block.position().relative(Direction.SOUTH)))
                && opaque.contains(new BlockKey(block.subLevelId(), block.position().relative(Direction.WEST)))
                && opaque.contains(new BlockKey(block.subLevelId(), block.position().relative(Direction.EAST))))
                .map(block -> new BlockKey(block.subLevelId(), block.position()))
                .forEach(fullyOccludedBlocks::add);
    }

    private void fitCamera(ClientLevel level, float partialTick) {
        ClientSubLevel root = resolve(level, rootId);
        List<PreviewBlock> visible = visibleBlocks();
        if ((!usingFallbackScene && root == null) || visible.isEmpty()) return;
        Vector3f min = new Vector3f(Float.MAX_VALUE);
        Vector3f max = new Vector3f(-Float.MAX_VALUE);
        for (PreviewBlock block : visible) {
            Vector3f point = rootPosition(level, root, block.subLevelId(), block.position(), partialTick);
            if (point == null) continue;
            min.min(point);
            max.max(point.add(1.0F, 1.0F, 1.0F));
        }
        if (min.x == Float.MAX_VALUE) return;
        center.set(min).add(max).mul(0.5F);
        float radius = Math.max(2.0F, min.distance(max) * 0.62F);
        distance = Mth.clamp(radius / (float) Math.tan(FIELD_OF_VIEW * 0.5F), MIN_DISTANCE, MAX_DISTANCE);
        panX = 0.0F;
        panY = 0.0F;
        cameraNeedsFit = false;
        targetDirty = true;
    }

    private void ensureTarget(Minecraft minecraft, int viewWidth, int viewHeight) {
        int scale = Math.max(1, (int) Math.round(minecraft.getWindow().getGuiScale()));
        int width = Math.max(1, viewWidth * scale);
        int height = Math.max(1, viewHeight * scale);
        if (target == null) {
            target = new TextureTarget(width, height, true, Minecraft.ON_OSX);
            texture = textureBase.withPath(textureBase.getPath() + "/" + TEXTURE_IDS.incrementAndGet());
            minecraft.getTextureManager().register(texture, new PreviewTexture(target));
        } else if (target.width != width || target.height != height) {
            target.resize(width, height, Minecraft.ON_OSX);
        } else {
            return;
        }
        targetDirty = true;
    }

    private void renderFrame(Minecraft minecraft, float partialTick, Collection<Highlight> highlights,
                             Collection<Marker> markers) {
        ClientLevel level = minecraft.level;
        ClientSubLevel root = resolve(level, rootId);
        if (level == null || target == null || (!usingFallbackScene && root == null)) return;
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        buffers.endBatch();
        RenderTarget main = minecraft.getMainRenderTarget();
        RenderSystem.backupProjectionMatrix();
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        try {
            target.setClearColor(0.025F, 0.045F, 0.065F, 1.0F);
            target.clear(Minecraft.ON_OSX);
            target.bindWrite(true);
            float aspect = Math.max(0.01F, (float) target.width / target.height);
            RenderSystem.setProjectionMatrix(new Matrix4f().perspective(FIELD_OF_VIEW, aspect, 0.05F, 1024.0F),
                    VertexSorting.DISTANCE_TO_ORIGIN);
            modelView.identity();
            modelView.translate(0.0F, 0.0F, -distance);
            modelView.translate(panX, panY, 0.0F);
            modelView.rotateX((float) Math.toRadians(pitch));
            modelView.rotateY((float) Math.toRadians(yaw));
            modelView.translate(-center.x, -center.y, -center.z);
            RenderSystem.applyModelViewMatrix();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            PoseStack pose = new PoseStack();
            if (usingFallbackScene) {
                renderScene(minecraft, level, root, partialTick, aspect, pose, buffers);
            } else {
                SubLevelClientRenderApi.withPoses(level, partialTick, () -> {
                    renderScene(minecraft, level, root, partialTick, aspect, pose, buffers);
                    return null;
                });
            }
            buffers.endBatch();
            RenderSystem.disableDepthTest();
            renderHighlights(level, root, partialTick, highlights, pose, buffers);
            renderMarkers(level, root, partialTick, markers, pose, buffers);
            buffers.endBatch();
            RenderSystem.enableDepthTest();
        } finally {
            target.unbindWrite();
            main.bindWrite(true);
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
            RenderSystem.enableDepthTest();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            targetDirty = false;
        }
    }

    private void renderScene(Minecraft minecraft, ClientLevel level, ClientSubLevel root,
                             float partialTick, float aspect, PoseStack pose,
                             MultiBufferSource.BufferSource buffers) {
        for (PreviewBlock block : visibleBlocks()) {
            if (!inCameraFrustum(level, root, block, partialTick, aspect)) continue;
            if (!wireframe && isFullyOccluded(block)) continue;
            if (rendersWireframe(block)) {
                renderWireframeBlock(level, root, block, partialTick, pose, buffers);
            } else {
                renderBlock(minecraft, level, root, block, partialTick, pose, buffers);
            }
        }
    }

    /** CPU frustum culling avoids model and block-entity work for off-screen blocks. */
    private boolean inCameraFrustum(ClientLevel level, ClientSubLevel root, PreviewBlock block,
                                    float partialTick, float aspect) {
        Vector3f position = rootPosition(level, root, block.subLevelId(), block.position(), partialTick);
        if (position == null) return false;
        Matrix4f inverse = new Matrix4f().rotateX((float) Math.toRadians(pitch))
                .rotateY((float) Math.toRadians(yaw)).invert();
        Vector3f offset = inverse.transformDirection(-panX, -panY, 0.0F, new Vector3f());
        Vector3f origin = inverse.transformPosition(0.0F, 0.0F, distance, new Vector3f()).add(center).add(offset);
        Vector3f delta = new Vector3f(position).add(0.5F, 0.5F, 0.5F).sub(origin);
        Vector3f forward = inverse.transformDirection(0.0F, 0.0F, -1.0F, new Vector3f());
        float depth = delta.dot(forward);
        float radius = 0.9F;
        if (depth < -radius || depth > 1024.0F + radius) return false;
        float tangent = (float) Math.tan(FIELD_OF_VIEW * 0.5F);
        Vector3f right = inverse.transformDirection(1.0F, 0.0F, 0.0F, new Vector3f());
        Vector3f up = inverse.transformDirection(0.0F, 1.0F, 0.0F, new Vector3f());
        return Math.abs(delta.dot(right)) <= Math.max(0.0F, depth) * tangent * aspect + radius
                && Math.abs(delta.dot(up)) <= Math.max(0.0F, depth) * tangent + radius;
    }

    private void renderHighlights(ClientLevel level, ClientSubLevel root, float partialTick,
                                  Collection<Highlight> highlights, PoseStack pose,
                                  MultiBufferSource.BufferSource buffers) {
        if (highlights == null || highlights.isEmpty()) return;
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        for (Highlight highlight : highlights) {
            if (highlight == null || highlight.subLevelId() == null || !isVisible(highlight.subLevelId(), highlight.position())) continue;
            PreviewBlock block = previewBlock(highlight.subLevelId(), highlight.position());
            if (block == null) continue;
            Vector3f position = rootPosition(level, root, block.subLevelId(), block.position(), partialTick);
            Quaternionf orientation = rootOrientation(level, root, block.subLevelId(), partialTick);
            if (position == null || orientation == null) continue;
            pose.pushPose();
            pose.translate(position.x, position.y, position.z);
            pose.translate(0.5F, 0.5F, 0.5F);
            pose.mulPose(orientation);
            pose.translate(-0.5F, -0.5F, -0.5F);
            int color = highlight.color();
            LevelRenderer.renderLineBox(pose, lines, highlight.face() == null
                            ? new AABB(-0.018D, -0.018D, -0.018D, 1.018D, 1.018D, 1.018D)
                            : faceOutline(highlight.face()),
                    ((color >> 16) & 0xFF) / 255.0F, ((color >> 8) & 0xFF) / 255.0F,
                    (color & 0xFF) / 255.0F, 1.0F);
            pose.popPose();
        }
        buffers.endBatch(RenderType.lines());
    }

    private void renderMarkers(ClientLevel level, ClientSubLevel root, float partialTick,
                               Collection<Marker> markers, PoseStack pose,
                               MultiBufferSource.BufferSource buffers) {
        if (usingFallbackScene || markers == null || markers.isEmpty()) return;
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        for (Marker marker : markers) {
            if (marker == null || marker.subLevelId() == null || marker.direction().lengthSqr() < 1.0E-6D) continue;
            Vector3f start = rootPosition(level, root, marker.subLevelId(), marker.position(), partialTick);
            Quaternionf orientation = rootOrientation(level, root, marker.subLevelId(), partialTick);
            if (start == null || orientation == null) continue;
            Vector3f direction = new Vector3f((float) marker.direction().x, (float) marker.direction().y,
                    (float) marker.direction().z).rotate(orientation);
            if (direction.lengthSquared() < 1.0E-6F) continue;
            direction.normalize();
            float length = 3.0F;
            Vector3f end = new Vector3f(start).add(direction.mul(length, new Vector3f()));
            Vector3f reference = Math.abs(direction.y) < 0.85F
                    ? new Vector3f(0.0F, 1.0F, 0.0F) : new Vector3f(1.0F, 0.0F, 0.0F);
            Vector3f side = direction.cross(reference, new Vector3f()).normalize().mul(0.55F);
            Vector3f back = new Vector3f(direction).mul(-0.8F);
            int color = marker.color();
            float red = ((color >> 16) & 0xFF) / 255.0F;
            float green = ((color >> 8) & 0xFF) / 255.0F;
            float blue = (color & 0xFF) / 255.0F;
            Matrix4f matrix = pose.last().pose();
            line(lines, matrix, start, end, red, green, blue);
            line(lines, matrix, end, new Vector3f(end).add(back).add(side), red, green, blue);
            line(lines, matrix, end, new Vector3f(end).add(back).sub(side), red, green, blue);
        }
        buffers.endBatch(RenderType.lines());
    }

    private static void line(VertexConsumer lines, Matrix4f matrix, Vector3f from, Vector3f to,
                             float red, float green, float blue) {
        Vector3f normal = new Vector3f(to).sub(from);
        if (normal.lengthSquared() < 1.0E-8F) return;
        normal.normalize();
        lines.addVertex(matrix, from.x, from.y, from.z).setColor(red, green, blue, 1.0F)
                .setNormal(normal.x, normal.y, normal.z);
        lines.addVertex(matrix, to.x, to.y, to.z).setColor(red, green, blue, 1.0F)
                .setNormal(normal.x, normal.y, normal.z);
    }

    private void renderWireframeBlock(ClientLevel level, ClientSubLevel root, PreviewBlock block,
                                      float partialTick, PoseStack pose,
                                      MultiBufferSource.BufferSource buffers) {
        Vector3f position = rootPosition(level, root, block.subLevelId(), block.position(), partialTick);
        Quaternionf orientation = rootOrientation(level, root, block.subLevelId(), partialTick);
        if (position == null || orientation == null) return;
        pose.pushPose();
        pose.translate(position.x, position.y, position.z);
        pose.translate(0.5F, 0.5F, 0.5F);
        pose.mulPose(orientation);
        pose.translate(-0.5F, -0.5F, -0.5F);
        LevelRenderer.renderLineBox(pose, buffers.getBuffer(RenderType.lines()),
                new AABB(0.01D, 0.01D, 0.01D, 0.99D, 0.99D, 0.99D),
                0.36F, 0.78F, 1.0F, 0.92F);
        pose.popPose();
    }

    private void renderBlock(Minecraft minecraft, ClientLevel level, ClientSubLevel root, PreviewBlock block,
                             float partialTick, PoseStack pose, MultiBufferSource.BufferSource buffers) {
        Vector3f position = rootPosition(level, root, block.subLevelId(), block.position(), partialTick);
        Quaternionf orientation = rootOrientation(level, root, block.subLevelId(), partialTick);
        if (usingFallbackScene) {
            renderFallbackBlock(minecraft, block, position, orientation, pose, buffers);
            return;
        }
        ClientSubLevel source = resolve(level, block.subLevelId());
        if (position == null || orientation == null || source == null || source.isRemoved()) return;
        BlockEntity entity = blockEntity(level, block);
        BakedModel model = minecraft.getBlockRenderer().getBlockModel(block.state());
        ModelData data = entity == null ? ModelData.EMPTY : entity.getModelData();
        try {
            data = model.getModelData(source.getLevel(), block.position(), block.state(), data);
        } catch (RuntimeException | LinkageError ignored) {
        }
        pose.pushPose();
        pose.translate(position.x, position.y, position.z);
        pose.translate(0.5F, 0.5F, 0.5F);
        pose.mulPose(orientation);
        pose.translate(-0.5F, -0.5F, -0.5F);
        if (block.state().getRenderShape() == RenderShape.MODEL) {
            ChunkRenderTypeSet types = model.getRenderTypes(block.state(), RandomSource.create(42L), data);
            for (RenderType layer : RenderType.chunkBufferLayers()) if (types.contains(layer)) {
                minecraft.getBlockRenderer().renderBatched(block.state(), block.position(), source.getLevel(), pose,
                        buffers.getBuffer(layer), false, RandomSource.create(42L), data, layer);
            }
        } else {
            minecraft.getBlockRenderer().renderSingleBlock(block.state(), pose, buffers, LightTexture.FULL_BRIGHT,
                    net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, data, null);
        }
        if (entity != null) try {
            if (!CreateKineticPreviewRenderer.render(entity, block.state(), pose, buffers, LightTexture.FULL_BRIGHT)) {
                for (BlockEntityPreviewDecorator decorator : decorators) {
                    decorator.render(entity, block.state(), pose, buffers, LightTexture.FULL_BRIGHT,
                            net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
                }
                minecraft.getBlockEntityRenderDispatcher().renderItem(entity, pose, buffers, LightTexture.FULL_BRIGHT,
                        net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
            }
        } catch (RuntimeException | LinkageError ignored) {
        }
        pose.popPose();
    }

    private static void renderFallbackBlock(Minecraft minecraft, PreviewBlock block,
                                            Vector3f position, Quaternionf orientation,
                                            PoseStack pose, MultiBufferSource.BufferSource buffers) {
        if (position == null || orientation == null) return;
        pose.pushPose();
        pose.translate(position.x, position.y, position.z);
        pose.translate(0.5F, 0.5F, 0.5F);
        pose.mulPose(orientation);
        pose.translate(-0.5F, -0.5F, -0.5F);
        minecraft.getBlockRenderer().renderSingleBlock(block.state(), pose, buffers, LightTexture.FULL_BRIGHT,
                net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
        pose.popPose();
    }

    private PickTarget pick(double mouseX, double mouseY, int width, int height, float partialTick,
                            Collection<PickTarget> targets) {
        ClientLevel level = Minecraft.getInstance().level;
        ClientSubLevel root = resolve(level, rootId);
        if (level == null || (!usingFallbackScene && root == null)
                || targets == null || targets.isEmpty() || width <= 0 || height <= 0) return null;
        Matrix4f inverse = new Matrix4f().rotateX((float) Math.toRadians(pitch)).rotateY((float) Math.toRadians(yaw)).invert();
        float tangent = (float) Math.tan(FIELD_OF_VIEW * 0.5F);
        float ndcX = (float) (mouseX * 2.0D / width - 1.0D);
        float ndcY = (float) (1.0D - mouseY * 2.0D / height);
        Vector3f offset = inverse.transformDirection(-panX, -panY, 0.0F, new Vector3f());
        Vector3f origin = inverse.transformPosition(0.0F, 0.0F, distance, new Vector3f()).add(center).add(offset);
        Vector3f direction = inverse.transformDirection(ndcX * ((float) width / height) * tangent, ndcY * tangent,
                -1.0F, new Vector3f()).normalize();
        PickTarget selected = null;
        float best = Float.POSITIVE_INFINITY;
        for (PickTarget target : targets) {
            if (target == null || target.subLevelId() == null || target.id().isBlank() || !isVisible(target.subLevelId(), target.position())) continue;
            PreviewBlock block = previewBlock(target.subLevelId(), target.position());
            if (block == null) continue;
            Vector3f position = rootPosition(level, root, block.subLevelId(), block.position(), partialTick);
            Quaternionf orientation = rootOrientation(level, root, block.subLevelId(), partialTick);
            if (position == null || orientation == null) continue;
            Quaternionf inverseOrientation = new Quaternionf(orientation).conjugate();
            Vector3f localOrigin = new Vector3f(origin).sub(position).sub(0.5F, 0.5F, 0.5F).rotate(inverseOrientation).add(0.5F, 0.5F, 0.5F);
            RayHit hit = rayBoxHit(localOrigin, new Vector3f(direction).rotate(inverseOrientation));
            if (hit != null && hit.distance() < best) {
                best = hit.distance();
                selected = new PickTarget(target.id(), target.subLevelId(), target.position(), hit.face());
            }
        }
        return selected;
    }

    private static BlockEntity blockEntity(ClientLevel level, PreviewBlock block) {
        ClientSubLevel source = resolve(level, block.subLevelId());
        return !block.state().hasBlockEntity() || source == null || source.isRemoved() ? null
                : source.getLevel().getBlockEntity(block.position());
    }

    private static RayHit rayBoxHit(Vector3f origin, Vector3f direction) {
        float min = 0.0F, max = Float.POSITIVE_INFINITY;
        float[] origins = {origin.x, origin.y, origin.z};
        float[] directions = {direction.x, direction.y, direction.z};
        Direction face = null;
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(directions[axis]) < 1.0E-6F) {
                if (origins[axis] < 0.0F || origins[axis] > 1.0F) return null;
                continue;
            }
            float inverse = 1.0F / directions[axis];
            float near = -origins[axis] * inverse, far = (1.0F - origins[axis]) * inverse;
            Direction nearFace = axis == 0 ? Direction.WEST : axis == 1 ? Direction.DOWN : Direction.NORTH;
            Direction farFace = axis == 0 ? Direction.EAST : axis == 1 ? Direction.UP : Direction.SOUTH;
            if (near > far) {
                float swap = near; near = far; far = swap;
                Direction faceSwap = nearFace; nearFace = farFace; farFace = faceSwap;
            }
            if (near > min) { min = near; face = nearFace; }
            max = Math.min(max, far);
            if (max < min) return null;
        }
        return face == null ? null : new RayHit(min, face);
    }

    private static AABB faceOutline(Direction face) {
        double inset = -0.018D, outset = 1.018D, depth = 0.025D;
        return switch (face) {
            case DOWN -> new AABB(inset, -depth, inset, outset, depth, outset);
            case UP -> new AABB(inset, 1.0D - depth, inset, outset, 1.0D + depth, outset);
            case NORTH -> new AABB(inset, inset, -depth, outset, outset, depth);
            case SOUTH -> new AABB(inset, inset, 1.0D - depth, outset, outset, 1.0D + depth);
            case WEST -> new AABB(-depth, inset, inset, depth, outset, outset);
            case EAST -> new AABB(1.0D - depth, inset, inset, 1.0D + depth, outset, outset);
        };
    }

    private static ClientSubLevel resolve(ClientLevel level, UUID id) {
        if (level == null || id == null) return null;
        try {
            Object value = ClientSubLevelContainer.getContainer(level).getSubLevel(id);
            return value instanceof ClientSubLevel subLevel ? subLevel : null;
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private Vector3f rootPosition(ClientLevel level, ClientSubLevel root, UUID sourceId,
                                  BlockPos position, float partialTick) {
        if (position == null) return null;
        if (usingFallbackScene) {
            SnapshotBlock fallback = fallbackBlocks.get(new BlockKey(sourceId, position));
            if (fallback == null) return null;
            Vec3 point = fallback.rootPosition();
            return !Double.isFinite(point.x) || !Double.isFinite(point.y) || !Double.isFinite(point.z)
                    ? null : new Vector3f((float) point.x, (float) point.y, (float) point.z);
        }
        return rootPosition(level, root, sourceId, Vec3.atLowerCornerOf(position), partialTick);
    }

    private Vector3f rootPosition(ClientLevel level, ClientSubLevel root, UUID sourceId,
                                  Vec3 position, float partialTick) {
        if (usingFallbackScene || root == null) return null;
        ClientSubLevel source = resolve(level, sourceId);
        if (source == null || source.isRemoved() || position == null) return null;
        Vector3d point = new Vector3d(position.x, position.y, position.z);
        source.renderPose(partialTick).transformPosition(point);
        root.renderPose(partialTick).transformPositionInverse(point);
        BlockPos rootCenter = root.getPlot().getCenterBlock();
        point.sub(rootCenter.getX(), rootCenter.getY(), rootCenter.getZ());
        return !Double.isFinite(point.x) || !Double.isFinite(point.y) || !Double.isFinite(point.z) ? null
                : new Vector3f((float) point.x, (float) point.y, (float) point.z);
    }

    private Quaternionf rootOrientation(ClientLevel level, ClientSubLevel root, UUID sourceId, float partialTick) {
        if (usingFallbackScene) return new Quaternionf();
        if (root == null) return null;
        ClientSubLevel source = resolve(level, sourceId);
        if (source == null || source.isRemoved()) return null;
        return new Quaternionf(new Quaterniond(root.renderPose(partialTick).orientation()).conjugate()
                .mul(source.renderPose(partialTick).orientation()).normalize());
    }

    private List<PreviewBlock> visibleBlocks() {
        return blocks.stream().filter(this::isVisible).toList();
    }

    private boolean isVisible(UUID subLevelId, BlockPos position) {
        PreviewBlock block = previewBlock(subLevelId, position);
        return block != null && isVisible(block);
    }

    private boolean isVisible(PreviewBlock block) {
        try {
            return visibility.test(block);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private boolean rendersWireframe(PreviewBlock block) {
        if (!wireframe) return false;
        try {
            return wireframeVisibility.test(block);
        } catch (RuntimeException ignored) {
            return true;
        }
    }

    private boolean isFullyOccluded(PreviewBlock block) {
        return block != null && fullyOccludedBlocks.contains(new BlockKey(block.subLevelId(), block.position()));
    }

    private PreviewBlock previewBlock(UUID subLevelId, BlockPos position) {
        if (subLevelId == null || position == null) return null;
        return blocks.stream().filter(block -> subLevelId.equals(block.subLevelId()) && position.equals(block.position()))
                .findFirst().orElse(null);
    }

    private float worldUnitsPerPixel() {
        return Math.max(0.0025F, distance * (float) Math.tan(FIELD_OF_VIEW * 0.5F) / 180.0F);
    }

    private static int gridCoordinate(float value) {
        int nearest = Math.round(value);
        return Math.abs(value - nearest) <= 1.0E-3F ? nearest : Mth.floor(value);
    }

    private void drawTexture(GuiGraphics graphics, int x, int y, int width, int height) {
        VertexConsumer vertices = graphics.bufferSource().getBuffer(RenderType.text(texture));
        Matrix4f matrix = graphics.pose().last().pose();
        vertices.addVertex(matrix, x, y, 0.0F).setColor(0xFFFFFFFF).setUv(0.0F, 1.0F).setLight(LightTexture.FULL_BRIGHT);
        vertices.addVertex(matrix, x, y + height, 0.0F).setColor(0xFFFFFFFF).setUv(0.0F, 0.0F).setLight(LightTexture.FULL_BRIGHT);
        vertices.addVertex(matrix, x + width, y + height, 0.0F).setColor(0xFFFFFFFF).setUv(1.0F, 0.0F).setLight(LightTexture.FULL_BRIGHT);
        vertices.addVertex(matrix, x + width, y, 0.0F).setColor(0xFFFFFFFF).setUv(1.0F, 1.0F).setLight(LightTexture.FULL_BRIGHT);
    }

    private static final class PreviewTexture extends AbstractTexture {
        private final RenderTarget target;

        private PreviewTexture(RenderTarget target) {
            this.target = target;
        }

        @Override public int getId() { return target.getColorTextureId(); }
        @Override public void releaseId() { }
        @Override public void load(ResourceManager resourceManager) throws IOException { }
        @Override public void close() { }
    }
}
