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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// Discover exterior entrances and operate doors in their owning sublevel
public final class ShipDoorApi{
    private static final int MAX_ROOM_BLOCKS = 65_536;
    private static final List<DoorAdapter> ADAPTERS = new java.util.concurrent.CopyOnWriteArrayList<>();

    private ShipDoorApi(){}

    // Mods can retain their own multiblock or animated-door operation
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
            Map<BlockPos, Boolean> rooms = new HashMap<>();
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
                        Direction facing = exteriorFacing(body.getLevel(), pos, min, max, rooms);
                        if(facing == null) continue;
                        Vec3 outward = SableTransformApi.transformDirectionBetween(root.logicalPose(),
                                body.logicalPose(), Vec3.atLowerCornerOf(facing.getNormal()));
                        Side side = side(outward, forward, up);
                        if(side != null) res.add(new Door(body.getUniqueId(), pos, side));
                    }
                }
            }
        }
        return List.copyOf(res);
    }

    // Treat every door as closed when separating enclosed rooms from outside air
    public static @Nullable Direction exteriorFacing(BlockGetter view, BlockPos pos, BlockPos min, BlockPos max){
        return exteriorFacing(view, pos, min, max, new HashMap<>());
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
        if(state.getValue(property) != open){
            if(state.getBlock() instanceof DoorBlock doorBlock) doorBlock.setOpen(null, body.getLevel(), state, door.pos(), open);
            else body.getLevel().setBlock(door.pos(), state.setValue(property, open), 3);
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

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static @Nullable Direction exteriorFacing(BlockGetter view, BlockPos pos, BlockPos min,
                                                       BlockPos max, Map<BlockPos, Boolean> rooms){
        if(view instanceof Level level && !loaded(level, pos)) return null;
        BlockState state = view.getBlockState(pos);
        if(!isLowerDoor(state)) return null;
        Direction facing = facing(state);
        if(facing == null) return null;
        Boolean front = outside(view, pos.relative(facing), min, max, rooms);
        Boolean back = outside(view, pos.relative(facing.getOpposite()), min, max, rooms);
        if(front == null || back == null) return null;
        if(front && back){
            front = clearToOutside(view, pos.relative(facing), facing, min, max);
            back = clearToOutside(view, pos.relative(facing.getOpposite()), facing.getOpposite(), min, max);
        }
        if(front.equals(back)){
            if(state.getBlock() instanceof TrapDoorBlock && (pos.getY() == min.getY() || pos.getY() >= max.getY() - 1)) return facing;
            return null;
        }
        return front ? facing : facing.getOpposite();
    }

    // An open deck still has exterior doors when only one normal points clear of the hull
    private static boolean clearToOutside(BlockGetter view, BlockPos pos, Direction dir, BlockPos min, BlockPos max){
        for(int idx = 0; idx < MAX_ROOM_BLOCKS; idx++){
            if(!inside(pos, min, max)) return true;
            if(view instanceof Level level && !loaded(level, pos)) return false;
            if(!view.getBlockState(pos).isAir()) return false;
            pos = pos.relative(dir);
        }
        return false;
    }

    // Cache connected air spaces and leave oversized or unloaded rooms unclassified
    private static @Nullable Boolean outside(BlockGetter view, BlockPos start, BlockPos min,
                                             BlockPos max, Map<BlockPos, Boolean> rooms){
        if(!inside(start, min, max)) return true;
        if(rooms.containsKey(start)) return rooms.get(start);
        if(view instanceof Level level && !loaded(level, start)) return null;
        if(!view.getBlockState(start).isAir()) return false;
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        seen.add(start);
        queue.add(start);
        boolean exterior = false;
        while(!queue.isEmpty()){
            BlockPos pos = queue.removeFirst();
            for(Direction dir : Direction.values()){
                BlockPos next = pos.relative(dir);
                if(!inside(next, min, max) || Boolean.TRUE.equals(rooms.get(next))){
                    exterior = true;
                    break;
                }
                if(view instanceof Level level && !loaded(level, next)) return null;
                if(!rooms.containsKey(next) && view.getBlockState(next).isAir() && seen.add(next)){
                    if(seen.size() > MAX_ROOM_BLOCKS) return null;
                    queue.addLast(next);
                }
            }
            if(exterior) break;
        }
        for(BlockPos pos : seen) rooms.put(pos, exterior);
        return exterior;
    }

    private static boolean inside(BlockPos pos, BlockPos min, BlockPos max){
        return pos.getX() >= min.getX() && pos.getX() <= max.getX()
                && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }

    private static boolean isLowerDoor(BlockState state){
        DoorAdapter adapter = adapter(state);
        if(adapter != null) return adapter.root(state);
        var id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock());
        String name = id == null ? "" : id.getPath();
        if(!(state.getBlock() instanceof DoorBlock) && !(state.getBlock() instanceof TrapDoorBlock)
                && !(state.getBlock() instanceof FenceGateBlock) && !name.contains("door") && !name.contains("gate")
                && !name.contains("hatch") && !state.is(net.minecraft.tags.BlockTags.DOORS)
                && !state.is(net.minecraft.tags.BlockTags.TRAPDOORS) && !state.is(net.minecraft.tags.BlockTags.FENCE_GATES)) return false;
        if(openProperty(state) == null || facing(state) == null) return false;
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
