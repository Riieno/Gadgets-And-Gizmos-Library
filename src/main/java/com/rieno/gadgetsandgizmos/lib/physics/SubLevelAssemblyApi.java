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
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

// Assemble and disassemble loaded SubLevels through the required APIs
public final class SubLevelAssemblyApi {
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
