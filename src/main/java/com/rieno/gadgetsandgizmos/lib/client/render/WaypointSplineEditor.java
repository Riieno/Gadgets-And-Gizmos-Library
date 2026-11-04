package com.rieno.gadgetsandgizmos.lib.client.render;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.navigation.WaypointSpline;
import net.minecraft.client.Camera;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Share spline rendering, wrench picking and hold-interact editing between route owners
public final class WaypointSplineEditor{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final double PICK_DISTANCE = 128.0D;
    private static final double CURVE_PICK_RADIUS = 0.32D;
    private static final double WAYPOINT_PICK_RADIUS = 0.45D;
    private static final double PICK_SAMPLE_SPACING = 0.35D;
    private static final int CURVE_COLOR = 0xFF56DCEB;
    private static final int WAYPOINT_COLOR = 0xFFFFB347;
    private static final int ENDPOINT_COLOR = 0xFF4FE081;
    private static final int SELECTED_COLOR = 0xFFFFFFFF;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Whether the owning feature supplies editable routes
    private boolean visible;
    // Publish edits through the owning feature
    private final EditSink edits;
    // Latest server-authoritative route controls
    private List<Route> routes = List.of();
    // Immutable splines built once for the current server snapshot
    private Map<UUID, WaypointSpline> splines = Map.of();
    // Currently highlighted curve or waypoint
    private @Nullable Selection hovered;
    // Active hold-interact waypoint drag
    private @Nullable Drag drag;
    private List<Component> toolbar = List.of();
    private @Nullable WorldRenderer worldRenderer;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the reusable spline editor
    public WaypointSplineEditor(EditSink edits){
        this.edits = edits;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Replace the detached route controls while preserving an active drag
    public void setRoutes(List<Route> updated){
        visible = !updated.isEmpty();
        routes = List.copyOf(updated);
        Map<UUID, WaypointSpline> updatedSplines = new LinkedHashMap<>();
        for(Route route : routes) updatedSplines.put(route.id(), WaypointSpline.of(route.waypoints()));
        splines = Map.copyOf(updatedSplines);
        hovered = null;
        if(!visible || drag != null && routes.stream().noneMatch(route -> route.id().equals(drag.routeId()))) drag = null;
    }

    // Clear the client route state
    public void clear(){
        visible = false;
        routes = List.of();
        splines = Map.of();
        hovered = null;
        drag = null;
        toolbar = List.of();
    }

    // Keep owner-supplied shortcut help above the player's hotbar
    public void setToolbar(List<Component> lines){ toolbar = List.copyOf(lines); }

    // Let an owner replace the line overlay while retaining picking and drag previews
    public void setWorldRenderer(@Nullable WorldRenderer renderer){ worldRenderer = renderer; }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Update route picking and an active hold-interact drag
    public void onClientTick(ClientTickEvent.Pre evt){
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if(!visible || minecraft.level == null || player == null || minecraft.screen != null){
            hovered = null;
            drag = null;
            return;
        }
        if(drag != null){
            if(!hasWrench(player)){
                drag = null;
                hovered = null;
                return;
            }
            if(!minecraft.options.keyUse.isDown()){
                finishDrag();
                return;
            }
            Vec3 preview = player.getEyePosition().add(
                    player.getLookAngle().scale(drag.rayDistance()));
            Route route = route(drag.routeId());
            if(route == null){ drag = null; return; }
            boolean duplicating = drag.duplicating() || !drag.branching() && player.isShiftKeyDown()
                    && route.policy().canDuplicate(drag.waypointIndex(), route.waypoints().size());
            boolean branching = drag.branching() || Screen.hasAltDown()
                    && !duplicating
                    && route.policy().canBranch(drag.waypointIndex(), route.waypoints().size());
            preview = duplicating ? preview : branching ? route.policy().constrainBranch(drag.waypointIndex(), preview)
                    : route.policy().constrain(drag.waypointIndex(), preview);
            drag = new Drag(drag.routeId(), drag.waypointIndex(),
                    drag.rayDistance(), drag.original(), preview, branching, duplicating);
            hovered = Selection.waypoint(drag.routeId(), drag.waypointIndex(),
                    preview, drag.rayDistance());
            return;
        }
        hovered = hasWrench(player) ? pick(player) : null;
    }

    // Capture route edits before normal held-item interaction
    public void onInteractionKeyMappingTriggered(
            InputEvent.InteractionKeyMappingTriggered evt
    ){
        if(!evt.isUseItem() || !visible) return;
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if(minecraft.level == null || player == null || minecraft.screen != null
                || !hasWrench(player)) return;
        if(drag != null){
            consume(evt);
            return;
        }
        Selection selected = hovered == null ? pick(player) : hovered;
        if(selected == null) return;
        Route route = route(selected.routeId());
        if(route == null) return;
        boolean duplicate = player.isShiftKeyDown() && selected.waypointIndex() >= 0
                && route.policy().canDuplicate(selected.waypointIndex(), route.waypoints().size());
        boolean remove = route.policy().removeWithSprint() ? minecraft.options.keySprint.isDown() : player.isShiftKeyDown();
        if(remove && !duplicate && selected.waypointIndex() >= 0
                && route.policy().canRemove(selected.waypointIndex(), route.waypoints().size())){
            sendEdit(selected.routeId(), EditAction.REMOVE,
                    selected.waypointIndex(), selected.position());
            consume(evt);
            return;
        }
        if((!route.policy().addWithSprint() || minecraft.options.keySprint.isDown())
                && selected.segmentIndex() >= 0 && route.policy().canAdd()){
            sendEdit(selected.routeId(), EditAction.ADD,
                    selected.segmentIndex(), selected.position());
            consume(evt);
            return;
        }
        if(selected.waypointIndex() < 0) return;
        if(!route.policy().canMove(selected.waypointIndex(), route.waypoints().size())){
            if(route.policy().canSelect()){
                route.policy().onSelect(selected.waypointIndex());
                consume(evt);
            }
            return;
        }
        drag = new Drag(selected.routeId(), selected.waypointIndex(),
                selected.rayDistance(), selected.position(), selected.position(),
                !duplicate && Screen.hasAltDown() && route.policy().canBranch(selected.waypointIndex(), route.waypoints().size()), duplicate);
        consume(evt);
    }

    // Render the visible spline controls independently of the pathfinder debug overlay
    public void onRenderWorld(RenderLevelStageEvent evt){
        if(!visible || routes.isEmpty()
                || evt.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft minecraft = Minecraft.getInstance();
        if(minecraft.level == null || minecraft.player == null) return;
        if(worldRenderer != null){
            worldRenderer.render(evt, routes.stream().map(route -> new RenderedRoute(route.id(), renderedSpline(route),
                    hovered != null && route.id().equals(hovered.routeId()) ? hovered.waypointIndex() : -1)).toList());
            return;
        }
        List<WaypointSplineRenderer.RouteVisual> visuals = new ArrayList<>();
        for(Route route : routes){
            if(route.waypoints().size() < 2) continue;
            int selectedWaypoint = hovered != null && route.id().equals(hovered.routeId())
                    ? hovered.waypointIndex() : -1;
            List<Integer> colors = new ArrayList<>();
            for(int idx = 0; idx < route.waypoints().size(); idx++) colors.add(route.policy().pointColor(idx, route.waypoints().size()));
            visuals.add(new WaypointSplineRenderer.RouteVisual(
                    renderedSpline(route), CURVE_COLOR, WAYPOINT_COLOR,
                    ENDPOINT_COLOR, SELECTED_COLOR, selectedWaypoint, colors));
            if(drag != null && drag.branching() && route.id().equals(drag.routeId())){
                visuals.add(new WaypointSplineRenderer.RouteVisual(WaypointSpline.of(List.of(drag.original(), drag.preview())),
                        CURVE_COLOR, WAYPOINT_COLOR, ENDPOINT_COLOR, SELECTED_COLOR, 1,
                        List.of(WAYPOINT_COLOR, route.policy().branchColor())));
            }
            if(drag != null && drag.duplicating() && route.id().equals(drag.routeId())){
                Vec3 offset = drag.preview().subtract(drag.original());
                visuals.add(new WaypointSplineRenderer.RouteVisual(WaypointSpline.of(route.waypoints().stream()
                        .map(point -> point.add(offset)).toList()), CURVE_COLOR, WAYPOINT_COLOR,
                        ENDPOINT_COLOR, SELECTED_COLOR, drag.waypointIndex(), colors));
            }
        }
        Camera camera = evt.getCamera();
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        WaypointSplineRenderer.render(
                evt.getPoseStack(), camera.getPosition(), bufferSource, visuals);
    }

    // Let the owning feature consume endpoint scrolling before the hotbar changes
    public void onMouseScrolling(InputEvent.MouseScrollingEvent evt){
        Minecraft mc = Minecraft.getInstance();
        if(evt.isCanceled() || evt.getScrollDeltaY() == 0.0D || !visible || drag != null
                || mc.screen != null || mc.player == null || !hasWrench(mc.player)) return;
        Selection selected = hovered == null ? pick(mc.player) : hovered;
        if(selected == null) return;
        Route route = route(selected.routeId());
        if(route != null && route.policy().onRouteScroll(selected.waypointIndex(), selected.segmentIndex(), selected.position(), evt.getScrollDeltaY())) evt.setCanceled(true);
    }

    // Show client-only control help beside the crosshair while a wrench is held
    public void onRenderGui(RenderGuiEvent.Post evt){
        Minecraft mc = Minecraft.getInstance();
        if(!visible || mc.options.hideGui || mc.screen != null || mc.player == null || !hasWrench(mc.player)) return;
        var graphics = evt.getGuiGraphics();
        int bottom = graphics.guiHeight() - 64;
        for(int idx = toolbar.size() - 1; idx >= 0; idx--){
            List<net.minecraft.util.FormattedCharSequence> wrapped = mc.font.split(toolbar.get(idx), Math.max(80, graphics.guiWidth() - 24));
            for(int line = wrapped.size() - 1; line >= 0; line--){
                int width = mc.font.width(wrapped.get(line));
                int x = (graphics.guiWidth() - width) / 2;
                bottom -= mc.font.lineHeight + 3;
                graphics.fill(x - 4, bottom - 2, x + width + 4, bottom + mc.font.lineHeight + 1, 0xAA101820);
                graphics.drawString(mc.font, wrapped.get(line), x, bottom, 0xFFE0EDF4, false);
            }
        }
        if(hovered == null) return;
        Route route = route(hovered.routeId());
        if(route == null) return;
        List<Component> lines = route.policy().routeTooltip(hovered.waypointIndex(), route.waypoints().size(),
                hovered.position(), drag != null && drag.branching());
        if(lines.isEmpty()) return;
        graphics.renderComponentTooltip(mc.font, lines, graphics.guiWidth() / 2 + 10, graphics.guiHeight() / 2 + 8);
    }

    // Finish and publish one waypoint drag
    private void finishDrag(){
        Drag completed = drag;
        drag = null;
        if(completed == null) return;
        if(completed.original().distanceToSqr(completed.preview()) <= 1.0E-6D){
            Route route = route(completed.routeId());
            if(route != null) route.policy().onSelect(completed.waypointIndex());
            return;
        }
        sendEdit(completed.routeId(), completed.duplicating() ? EditAction.DUPLICATE : completed.branching() ? EditAction.BRANCH : EditAction.MOVE,
                completed.waypointIndex(), completed.preview());
    }

    // Pick the nearest curve and prefer an equally close authored waypoint
    private @Nullable Selection pick(LocalPlayer player){
        return pick(player.getEyePosition(), player.getLookAngle(), Screen.hasAltDown());
    }

    // Expose deterministic picking without requiring a client input event
    public @Nullable Selection pick(Vec3 origin, Vec3 direction, boolean branchModifier){
        Selection best = null;
        for(Route route : routes){
            WaypointSpline spline = renderedSpline(route);
            WaypointSpline.RayHit curve = spline.raycast(origin, direction,
                    PICK_DISTANCE, CURVE_PICK_RADIUS, PICK_SAMPLE_SPACING);
            if(curve.found() && (best == null || curve.rayDistance() < best.rayDistance()
                    - (best.waypointIndex() >= 0 ? WAYPOINT_PICK_RADIUS : 0.0D))){
                best = Selection.curve(route.id(), curve.segmentIndex(),
                        curve.position(), curve.rayDistance());
            }
            WaypointSpline.WaypointHit waypoint = spline.raycastWaypoint(
                    origin, direction, PICK_DISTANCE, WAYPOINT_PICK_RADIUS);
            if(waypoint.found()){
                int priority = route.policy().pickPriority(waypoint.waypointIndex(), route.waypoints().size(), branchModifier);
                Route previous = best == null ? null : route(best.routeId());
                int prevPriority = previous == null || best.waypointIndex() < 0 ? Integer.MIN_VALUE
                        : previous.policy().pickPriority(best.waypointIndex(), previous.waypoints().size(), branchModifier);
                if(best == null || waypoint.rayDistance() < best.rayDistance() - WAYPOINT_PICK_RADIUS
                        || waypoint.rayDistance() <= best.rayDistance() + WAYPOINT_PICK_RADIUS && priority >= prevPriority){
                    best = Selection.waypoint(route.id(), waypoint.waypointIndex(), waypoint.position(), waypoint.rayDistance());
                }
            }
        }
        return best;
    }

    // Get route controls with the active local drag preview applied
    private List<Vec3> renderedControls(Route route){
        if(drag == null || drag.branching() || drag.duplicating()) return route.waypoints();
        List<Vec3> controls = new ArrayList<>(route.waypoints());
        if(route.id().equals(drag.routeId()) && drag.waypointIndex() >= 0 && drag.waypointIndex() < controls.size())
            controls.set(drag.waypointIndex(), drag.preview());
        return route.policy().previewControls(drag.routeId(), drag.waypointIndex(), drag.preview(), controls);
    }

    // Apply connected control previews while retaining curves between drags
    private WaypointSpline renderedSpline(Route route){
        if(drag != null && !drag.branching() && !drag.duplicating()){
            return WaypointSpline.of(renderedControls(route));
        }
        WaypointSpline spline = splines.get(route.id());
        return spline == null ? WaypointSpline.of(route.waypoints()) : spline;
    }

    // Resolve one visible route by id
    private @Nullable Route route(UUID routeId){
        return routes.stream().filter(route -> route.id().equals(routeId)).findFirst().orElse(null);
    }

    // Send one route edit to the server
    private void sendEdit(UUID routeId, EditAction action,
                                 int index, Vec3 position){
        edits.edit(routeId, action, index, position);
    }

    // Suppress normal wrench interaction for one handled spline edit
    private static void consume(InputEvent.InteractionKeyMappingTriggered evt){
        evt.setSwingHand(false);
        evt.setCanceled(true);
    }

    // Check whether the player holds a wrench in either hand
    private static boolean hasWrench(LocalPlayer player){
        return player.getMainHandItem().is(net.neoforged.neoforge.common.Tags.Items.TOOLS_WRENCH)
                || player.getOffhandItem().is(net.neoforged.neoforge.common.Tags.Items.TOOLS_WRENCH);
    }

    // Store one current route pick
    public record Selection(UUID routeId, int segmentIndex, int waypointIndex,
                        Vec3 position, double rayDistance){
        // Create a curve pick
        private static Selection curve(UUID routeId, int segmentIndex,
                                  Vec3 position, double rayDistance){
            return new Selection(routeId, segmentIndex, -1, position, rayDistance);
        }

        // Create a waypoint pick
        private static Selection waypoint(UUID routeId, int waypointIndex,
                                     Vec3 position, double rayDistance){
            return new Selection(routeId, -1, waypointIndex, position, rayDistance);
        }
    }

    // Store one active hold-interact waypoint drag
    private record Drag(UUID routeId, int waypointIndex, double rayDistance,
                        Vec3 original, Vec3 preview, boolean branching, boolean duplicating){
    }
    public enum EditAction{ ADD, MOVE, REMOVE, BRANCH, DUPLICATE }

    public record RenderedRoute(UUID id, WaypointSpline spline, int selectedWaypoint){}

    @FunctionalInterface
    public interface WorldRenderer{
        void render(RenderLevelStageEvent evt, List<RenderedRoute> routes);
    }

    @FunctionalInterface
    public interface EditSink{
        void edit(UUID routeId, EditAction action, int idx, Vec3 position);
    }

    public interface EditPolicy{
        EditPolicy INTERIOR = new EditPolicy(){};
        default boolean canMove(int idx, int count){ return idx > 0 && idx < count - 1; }
        default boolean canRemove(int idx, int count){ return idx > 0 && idx < count - 1; }
        default boolean canAdd(){ return true; }
        default boolean addWithSprint(){ return true; }
        default boolean removeWithSprint(){ return false; }
        default boolean canDuplicate(int idx, int count){ return false; }
        default boolean canSelect(){ return false; }
        default void onSelect(int idx){}
        default Vec3 constrain(int idx, Vec3 position){ return position; }
        default boolean canBranch(int idx, int count){ return false; }
        default int pickPriority(int idx, int count, boolean branchModifier){ return 0; }
        default Vec3 constrainBranch(int idx, Vec3 position){ return constrain(idx, position); }
        default int branchColor(){ return ENDPOINT_COLOR; }
        default List<Vec3> previewControls(UUID activeRoute, int idx, Vec3 position, List<Vec3> controls){ return controls; }
        default Integer pointColor(int idx, int count){ return null; }
        default boolean onScroll(int idx, double delta){ return false; }
        default boolean onRouteScroll(int idx, int segment, Vec3 position, double delta){ return idx >= 0 && onScroll(idx, delta); }
        default List<Component> tooltip(int idx, int count, Vec3 position, boolean branching){ return List.of(); }
        default List<Component> routeTooltip(int idx, int count, Vec3 position, boolean branching){
            return idx < 0 ? List.of() : tooltip(idx, count, position, branching);
        }
    }

    public record Route(UUID id, List<Vec3> waypoints, EditPolicy policy){
        public Route{
            waypoints = List.copyOf(waypoints);
            policy = policy == null ? EditPolicy.INTERIOR : policy;
        }
    }

}
