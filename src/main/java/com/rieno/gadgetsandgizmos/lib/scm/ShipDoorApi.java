package com.rieno.gadgetsandgizmos.lib.scm;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import com.rieno.gadgetsandgizmos.lib.physics.SableTransformApi;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;

// Discover exterior entrances and operate doors in their owning sublevel
public final class ShipDoorApi{
    private static final int MAX_HULL_SCAN_BLOCKS = 65_536;
    private static final List<DoorAdapter> ADAPTERS = new java.util.concurrent.CopyOnWriteArrayList<>();

    private ShipDoorApi(){}

    // Mods can retain their own full-size multiblock or animated-door operation
    public interface DoorAdapter{
        boolean supports(BlockState state);
        boolean root(BlockState state);
        Direction facing(BlockState state);
        boolean setOpen(Level level, BlockPos pos, BlockState state, boolean open);
    }

    public static void register(DoorAdapter adapter){ ADAPTERS.add(java.util.Objects.requireNonNull(adapter)); }

    private static DoorAdapter adapter(BlockState state){
        return ADAPTERS.stream().filter(adapter -> adapter.supports(state)).findFirst().orElse(null);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Map loaded doors using the ship's forward and up vectors in the root frame
    public static List<Door> discover(SubLevel root, Collection<? extends SubLevel> bodies,
                                      Vec3 forward, Vec3 up){
        if(root == null || root.isRemoved() || bodies == null) return List.of();
        List<Door> res = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for(SubLevel body : bodies){
            if(body == null || body.isRemoved() || body.getPlot() == null
                    || !seen.add(body.getUniqueId())) continue;
            var bounds = body.getPlot().getBoundingBox();
            if(bounds == null || bounds.minX() > bounds.maxX()
                    || bounds.minY() > bounds.maxY() || bounds.minZ() > bounds.maxZ()) continue;
            BlockPos min = BlockPos.containing(bounds.minX(), bounds.minY(), bounds.minZ());
            BlockPos max = BlockPos.containing(bounds.maxX(), bounds.maxY(), bounds.maxZ());
            for(var holder : body.getPlot().getLoadedChunks()){
                var chunk = holder.getChunk();
                if(chunk == null) continue;
                for(int idx = 0; idx < chunk.getSectionsCount(); idx++){
                    var section = chunk.getSection(idx);
                    if(!section.maybeHas(ShipDoorApi::isLowerDoor)) continue;
                    int baseY = chunk.getSectionYFromSectionIndex(idx) << 4;
                    for(int y = 0; y < 16; y++) for(int z = 0; z < 16; z++) for(int x = 0; x < 16; x++){
                        if(!isLowerDoor(section.getBlockState(x, y, z))) continue;
                        BlockPos pos = new BlockPos(chunk.getPos().getMinBlockX() + x, baseY + y,
                                chunk.getPos().getMinBlockZ() + z);
                        Direction facing = exteriorFacing(body.getLevel(), pos, min, max);
                        if(facing == null) continue;
                        Vec3 outward = Vec3.atLowerCornerOf(facing.getNormal());
                        if(body != root) outward = SableTransformApi.transformDirectionBetween(root.logicalPose(),
                                body.logicalPose(), outward);
                        Side side = side(outward, forward, up);
                        if(side != null) res.add(new Door(body.getUniqueId(), pos, side));
                    }
                }
            }
        }
        return List.copyOf(res);
    }

    // Recognize selectable full-size doors without treating trapdoors as entrances
    public static boolean isFullSizeDoor(BlockState state){
        return state != null && isDoor(state);
    }

    // Map only player-selected doors without repeating automatic hull discovery
    public static List<Door> selected(SubLevel root, Collection<? extends SubLevel> bodies,
                                     Collection<Reference> selection, Vec3 origin, Vec3 forward, Vec3 up){
        if(root == null || root.isRemoved() || bodies == null || selection == null || !finite(origin)) return List.of();
        Map<UUID, SubLevel> indexed = new HashMap<>();
        for(SubLevel body : bodies) if(body != null && !body.isRemoved()) indexed.put(body.getUniqueId(), body);
        List<Door> res = new ArrayList<>();
        Set<Reference> seen = new HashSet<>();
        for(Reference ref : selection){
            SubLevel body = ref == null ? null : indexed.get(ref.subLevelId());
            if(body == null || !loaded(body.getLevel(), ref.pos())) continue;
            BlockPos pos = ref.pos();
            BlockState state = body.getLevel().getBlockState(pos);
            if(!isDoor(state)) continue;
            if(!isLowerDoor(state)){
                pos = pos.below();
                BlockState lower = body.getLevel().getBlockState(pos);
                if(!lower.is(state.getBlock()) || !isLowerDoor(lower)) continue;
                state = lower;
            }
            if(!seen.add(new Reference(ref.subLevelId(), pos))) continue;
            Direction facing = facing(state);
            if(facing == null || facing.getAxis() == Direction.Axis.Y) continue;
            Vec3 point = root.logicalPose().transformPositionInverse(body.logicalPose().transformPosition(pos.getCenter()));
            Vec3 normal = Vec3.atLowerCornerOf(facing.getNormal());
            if(body != root) normal = SableTransformApi.transformDirectionBetween(root.logicalPose(), body.logicalPose(), normal);
            Vec3 offset = point.subtract(origin);
            double distance = offset.dot(normal);
            Vec3 outward = Math.abs(distance) <= 1.0E-6D ? offset : normal.scale(distance < 0 ? -1 : 1);
            Side side = side(outward, forward, up);
            if(side != null) res.add(new Door(ref.subLevelId(), pos, side));
        }
        return List.copyOf(res);
    }

    // Find the exposed hull face through either half of a full-size doorway
    public static @Nullable Direction exteriorFacing(BlockGetter view, BlockPos pos, BlockPos min, BlockPos max){
        if(view == null || pos == null || min == null || max == null || !inside(pos, min, max)) return null;
        if(view instanceof Level level && !loaded(level, pos)) return null;
        BlockState state = view.getBlockState(pos);
        if(!isLowerDoor(state)) return null;
        Direction facing = facing(state);
        if(facing == null || facing.getAxis() == Direction.Axis.Y) return null;
        Direction opposite = facing.getOpposite();
        boolean front = clearToOutside(view, pos.relative(facing), facing, min, max)
                || clearToOutside(view, pos.above().relative(facing), facing, min, max);
        boolean back = clearToOutside(view, pos.relative(opposite), opposite, min, max)
                || clearToOutside(view, pos.above().relative(opposite), opposite, min, max);
        if(front != back) return front ? facing : opposite;
        if(!front) return null;

        // Resolve open decks against the hull centre without trusting the door's placement direction
        Direction.Axis axis = facing.getAxis();
        long offset = 2L * axis.choose(pos.getX(), pos.getY(), pos.getZ())
                - axis.choose(min.getX(), min.getY(), min.getZ()) - axis.choose(max.getX(), max.getY(), max.getZ());
        if(offset == 0L) return null;
        return (offset > 0L) == (facing.getAxisDirection() == Direction.AxisDirection.POSITIVE) ? facing : opposite;
    }

    // NORTH follows forward and EAST follows forward crossed with up
    public static @Nullable Side side(Vec3 outward, Vec3 forward, Vec3 up){
        if(!finite(outward) || !finite(forward) || !finite(up)) return null;
        Vec3 north = forward.normalize();
        Vec3 east = north.cross(up).normalize();
        if(north.lengthSqr() < 1.0E-12D || east.lengthSqr() < 1.0E-12D) return null;
        double n = outward.dot(north), e = outward.dot(east);
        if(Math.max(Math.abs(n), Math.abs(e)) < 1.0E-6D) return null;
        return Math.abs(n) >= Math.abs(e) ? n >= 0 ? Side.NORTH : Side.SOUTH
                : e >= 0 ? Side.EAST : Side.WEST;
    }

    // Use the block's native operation so both halves and Create door animations stay in sync
    public static boolean setOpen(Level level, Door door, boolean open){
        if(level == null || level.isClientSide || door == null) return false;
        Object resolved = SubLevelBlockEntityCollector.getSubLevel(level, door.subLevelId());
        if(!(resolved instanceof SubLevel body) || body.isRemoved()
                || !SubLevelBlockEntityCollector.isLoadedPlotChunk(body.getLevel(), door.pos())) return false;
        BlockState state = body.getLevel().getBlockState(door.pos());
        if(!isLowerDoor(state)) return true;
        DoorAdapter adapter = adapter(state);
        if(adapter != null) return adapter.setOpen(body.getLevel(), door.pos(), state, open);
        BooleanProperty property = openProperty(state);
        if(property == null) return false;
        if(state.getBlock() instanceof DoorBlock doorBlock){
            if(state.getValue(property) != open) doorBlock.setOpen(null, body.getLevel(), state, door.pos(), open);
        }else{
            if(state.getValue(property) != open) body.getLevel().setBlock(door.pos(), state.setValue(property, open), 3);
            BlockState upper = body.getLevel().getBlockState(door.pos().above());
            BooleanProperty upperProperty = openProperty(upper);
            if(upper.is(state.getBlock()) && upper.hasProperty(DoorBlock.HALF)
                    && upper.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER && upperProperty != null
                    && upper.getValue(upperProperty) != open)
                body.getLevel().setBlock(door.pos().above(), upper.setValue(upperProperty, open), 3);
        }
        BlockState updated = body.getLevel().getBlockState(door.pos());
        return !isLowerDoor(updated) || updated.getValue(property) == open;
    }

    public enum Side{
        NORTH(1), SOUTH(2), EAST(4), WEST(8);

        private final int mask;
        Side(int mask){ this.mask = mask; }
        public int mask(){ return mask; }
        public static int allMask(){ return NORTH.mask | SOUTH.mask | EAST.mask | WEST.mask; }
    }

    public record Door(UUID subLevelId, BlockPos pos, Side side){
        public Door{ pos = pos.immutable(); }
    }

    // Retain a selected block independently from its current cardinal classification
    public record Reference(UUID subLevelId, BlockPos pos){
        public Reference{ pos = java.util.Objects.requireNonNull(pos).immutable(); }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Trace the clear opening through collision shapes while treating other full-size doors as closed
    private static boolean clearToOutside(BlockGetter view, BlockPos pos, Direction dir, BlockPos min, BlockPos max){
        Vec3 step = Vec3.atLowerCornerOf(dir.getNormal()).scale(0.5D);
        for(int idx = 0; idx < MAX_HULL_SCAN_BLOCKS; idx++){
            if(!inside(pos, min, max)) return true;
            if(view instanceof Level level && !loaded(level, pos)) return false;
            BlockState state = view.getBlockState(pos);
            if(isDoor(state)) return false;
            var shape = state.getCollisionShape(view, pos);
            if(!shape.isEmpty() && shape.clip(pos.getCenter().subtract(step), pos.getCenter().add(step), pos) != null) return false;
            pos = pos.relative(dir);
        }
        return false;
    }

    // Keep hull rays inside the owning body's occupied bounds
    private static boolean inside(BlockPos pos, BlockPos min, BlockPos max){
        return pos.getX() >= min.getX() && pos.getX() <= max.getX()
                && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }

    // Recognize full-size doors across namespaces while excluding trapdoors and gates
    private static boolean isDoor(BlockState state){
        if(state.isAir()) return false;
        if(state.getBlock() instanceof TrapDoorBlock || state.getBlock() instanceof FenceGateBlock
                || state.is(net.minecraft.tags.BlockTags.TRAPDOORS) || state.is(net.minecraft.tags.BlockTags.FENCE_GATES)) return false;
        var id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock());
        String name = id == null ? "" : id.getPath();
        if(name.replace("_", "").contains("trapdoor")) return false;
        DoorAdapter adapter = adapter(state);
        if(adapter != null) return true;
        if(!(state.getBlock() instanceof DoorBlock) && !state.is(net.minecraft.tags.BlockTags.DOORS)
                && !name.contains("door")) return false;
        return openProperty(state) != null && facing(state) != null;
    }

    // Discover only the lower half or registered root of each full-size door
    private static boolean isLowerDoor(BlockState state){
        if(!isDoor(state)) return false;
        DoorAdapter adapter = adapter(state);
        if(adapter != null) return adapter.root(state);
        return !state.hasProperty(DoorBlock.HALF) || state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER;
    }

    private static boolean loaded(Level level, BlockPos pos){
        return level.hasChunkAt(pos) || SubLevelBlockEntityCollector.isLoadedPlotChunk(level, pos);
    }

    private static BooleanProperty openProperty(BlockState state){
        for(Property<?> property : state.getProperties()) if(property instanceof BooleanProperty bool
                && ("open".equals(property.getName()) || "opened".equals(property.getName()))) return bool;
        return null;
    }

    private static Direction facing(BlockState state){
        DoorAdapter adapter = adapter(state);
        if(adapter != null) return adapter.facing(state);
        for(Property<?> property : state.getProperties()) if(property instanceof DirectionProperty dir
                && "facing".equals(property.getName())) return state.getValue(dir);
        return null;
    }

    private static boolean finite(Vec3 val){
        return val != null && Double.isFinite(val.x) && Double.isFinite(val.y) && Double.isFinite(val.z);
    }
}
