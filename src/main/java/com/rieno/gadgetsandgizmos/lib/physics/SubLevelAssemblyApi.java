package com.rieno.gadgetsandgizmos.lib.physics;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.util.SimAssemblyHelper;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.rieno.gadgetsandgizmos.lib.mixin.SimAssemblyHelperInvoker;
import com.rieno.gadgetsandgizmos.lib.geometry.CardinalRotation;
import com.simibubi.create.content.contraptions.StructureTransform;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

// Assemble and disassemble loaded SubLevels through the required APIs
public final class SubLevelAssemblyApi {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Scope custom placement transforms to one synchronous disassembly
    private static final ThreadLocal<SubLevelAssemblyHelper.AssemblyTransform> DISASSEMBLY = new ThreadLocal<>();
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the SubLevel assembly API
    private SubLevelAssemblyApi() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Assemble the supplied blocks into one SubLevel
    public static @Nullable ServerSubLevel assemble(ServerLevel level, BlockPos origin,
                                                     Iterable<BlockPos> blocks) {
        if (level == null || origin == null || blocks == null) {
            return null;
        }
        List<BlockPos> positions = new ArrayList<>();
        blocks.forEach(pos -> {
            if (pos != null) {
                positions.add(pos.immutable());
            }
        });
        if (positions.isEmpty()) {
            return null;
        }
        return SubLevelAssemblyHelper.assembleBlocks(level, origin, positions, BoundingBox3i.from(positions));
    }

    // Assemble one block into its own SubLevel
    public static @Nullable ServerSubLevel assembleSingle(ServerLevel level, BlockPos pos) {
        return assemble(level, pos, pos == null ? List.of() : List.of(pos));
    }

    // Assemble one block with Simulated placement rules
    public static @Nullable AssemblyResult assembleBlock(Level level, BlockPos pos, BlockPos anchor,
                                                          boolean moveEntities, boolean rotate) throws AssemblyException {
        if (level == null || pos == null || anchor == null) {
            return null;
        }
        SimAssemblyHelper.AssemblyResult res = SimAssemblyHelper.assembleFromSingleBlock(
                level, pos, anchor, moveEntities, rotate);
        return res == null ? null : new AssemblyResult(res.subLevel(), res.offset());
    }

    // Disassemble one SubLevel into the root level
    public static void disassemble(Level level, SubLevel subLevel, BlockPos origin,
                                   BlockPos anchor, Rotation rotation, boolean moveEntities) {
        if (level != null && subLevel != null && origin != null && anchor != null && rotation != null) {
            SimAssemblyHelper.disassembleSubLevel(level, subLevel, origin, anchor, rotation, moveEntities);
        }
    }

    // Restore an attachment on any block-grid axis without changing its saved plot contents
    public static void disassembleOriented(ServerLevel level, SubLevel subLevel, BlockPos origin,
            BlockPos anchor, CardinalRotation rotation, boolean playSound){
        if(level == null || subLevel == null || subLevel.isRemoved() || origin == null
                || anchor == null || rotation == null || subLevel.getLevel() != level) return;
        subLevel.getPlot().updateBoundingBox();
        var bounds = subLevel.getPlot().getBoundingBox();
        var transform = new OrientedTransform(origin, anchor, rotation, level);
        if(bounds.minX() > bounds.maxX() || bounds.minY() > bounds.maxY() || bounds.minZ() > bounds.maxZ()){
            // Transfer the removed head's tracking point without querying an empty plot's sentinel bounds
            var parent = SableLevelApi.containing(level, anchor);
            SubLevelAssemblyHelper.moveTrackingPoints(level,
                    new BoundingBox3i(origin.getX(), origin.getY(), origin.getZ(), origin.getX(), origin.getY(), origin.getZ()),
                    parent instanceof ServerSubLevel body ? body : null, transform);
            return;
        }
        var prev = DISASSEMBLY.get();
        DISASSEMBLY.set(transform);
        try{
            disassemble(level, subLevel, origin, anchor, Rotation.NONE, playSound);
        }finally{
            if(prev == null) DISASSEMBLY.remove();
            else DISASSEMBLY.set(prev);
        }
    }

    // Supply the scoped transform to Simulated's normal block and entity transfer
    public static SubLevelAssemblyHelper.AssemblyTransform disassemblyTransform(SubLevelAssemblyHelper.AssemblyTransform fallback){
        var transform = DISASSEMBLY.get();
        return transform == null ? fallback : transform;
    }

    // Rotate positions and block states through the same cardinal frame
    private static final class OrientedTransform extends SubLevelAssemblyHelper.AssemblyTransform{
        private final Vec3 origin;
        private final Vec3 anchor;
        private final org.joml.Quaterniond rotation;
        private final StructureTransform states;

        // Retain independent position and state transforms for one placement
        private OrientedTransform(BlockPos origin, BlockPos anchor, CardinalRotation rotation, ServerLevel level){
            super(origin, anchor, 0, Rotation.NONE, level);
            this.origin = origin.getCenter();
            this.anchor = anchor.getCenter();
            this.rotation = rotation.quaternion();
            states = new StructureTransform(BlockPos.ZERO,
                    rotation.axis() == net.minecraft.core.Direction.Axis.X ? rotation.degrees() : 0,
                    rotation.axis() == net.minecraft.core.Direction.Axis.Y ? rotation.degrees() : 0,
                    rotation.axis() == net.minecraft.core.Direction.Axis.Z ? rotation.degrees() : 0);
        }

        // Rotate entities around the original block centre
        @Override public Vec3 apply(Vec3 pos){
            var val = new org.joml.Vector3d(pos.x - origin.x, pos.y - origin.y, pos.z - origin.z);
            rotation.transform(val);
            return new Vec3(val.x + anchor.x, val.y + anchor.y, val.z + anchor.z);
        }

        // Keep destination block positions on the grid
        @Override public BlockPos apply(BlockPos pos){ return BlockPos.containing(apply(pos.getCenter())); }

        // Preserve Create's directional and kinetic state rotation rules
        @Override public BlockState apply(BlockState state){ return states.apply(state); }

        // Retain horizontal entity yaw changes in Simulated's entity transfer
        @Override public Rotation getRotation(){ return states.rotationAxis == net.minecraft.core.Direction.Axis.Y ? states.rotation : Rotation.NONE; }
    }

    // Prepare Create contraptions before one combined assembly
    public static void prepareCreateContraptions(Level level, Collection<BlockPos> blocks,
                                                  List<AABB> glues, boolean moveEntities) {
        if (level == null || blocks == null || blocks.isEmpty() || glues == null) {
            return;
        }
        SimAssemblyHelperInvoker.ct$prepareCreateContraptions(
                level, BoundingBox3i.from(blocks), blocks, moveEntities, glues);
    }

    // Store one assembled SubLevel and its moved block offset
    public record AssemblyResult(SubLevel subLevel, BlockPos offset) {
        // Initialize the SubLevel assembly result
        public AssemblyResult {
            if (subLevel == null || offset == null) {
                throw new IllegalArgumentException("SubLevel assembly result is incomplete");
            }
            offset = offset.immutable();
        }
    }
}
