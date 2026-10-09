package com.rieno.gadgetsandgizmos.lib.physics;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SubLevelAttachmentTest{
    @BeforeAll static void bootstrap(){
        net.minecraft.SharedConstants.tryDetectVersion();
        try(var loader = mockStatic(net.neoforged.fml.loading.LoadingModList.class)){
            var mods = mock(net.neoforged.fml.loading.LoadingModList.class);
            when(mods.getModFiles()).thenReturn(List.of());
            loader.when(net.neoforged.fml.loading.LoadingModList::get).thenReturn(mods);
            net.minecraft.server.Bootstrap.bootStrap();
        }
    }

    @Test void everyOutsideFaceTravelsWithItsSupport(){
        for(Direction dir : Direction.values()){
            ServerLevel level = level();
            BlockPos support = new BlockPos(10, 70, 10);
            BlockPos face = support.relative(dir);
            when(level.getBlockState(support)).thenReturn(Blocks.STONE.defaultBlockState());
            BlockState state = attachmentState(dir.getOpposite());
            when(level.getBlockState(face)).thenReturn(state);
            assertEquals(List.of(support, face), SubLevelAttachmentApi.includeAttachments(level, List.of(support)));
        }
    }

    @Test void neighborFacingAnotherSupportStaysBehind(){
        ServerLevel level = level();
        BlockPos support = new BlockPos(10, 70, 10);
        BlockState state = attachmentState(Direction.EAST);
        when(level.getBlockState(support.east())).thenReturn(state);
        assertEquals(List.of(support), SubLevelAttachmentApi.includeAttachments(level, List.of(support)));
    }

    @Test void trackingBoundsAndSupportProtectionSurviveRotatedTransfers(){
        ServerLevel level = level();
        BlockPos support = new BlockPos(10, 70, 10);
        BlockPos face = support.east();
        var transform = new SubLevelAssemblyHelper.AssemblyTransform(support, new BlockPos(1000, 70, 1000),
                1, Rotation.CLOCKWISE_90, level);
        SubLevelAttachmentApi.moveBlocks(level, transform, List.of(support, face), () -> {
            assertTrue(SubLevelAttachmentApi.isMoving(level, support));
            assertTrue(SubLevelAttachmentApi.isMoving(level, transform.apply(face)));
        });
        assertFalse(SubLevelAttachmentApi.isMoving(level, support));
        var bounds = SubLevelAttachmentApi.trackingBounds(transform, BoundingBox3i.from(List.of(support)));
        assertEquals(face.getX(), bounds.maxX());
        assertSame(bounds, SubLevelAttachmentApi.trackingBounds(transform, bounds));
    }

    @Test void failedTransferClearsOnlyItsOwnProtection(){
        ServerLevel level = level();
        BlockPos outerPos = new BlockPos(10, 70, 10);
        BlockPos innerPos = outerPos.east();
        var outer = new SubLevelAssemblyHelper.AssemblyTransform(outerPos, outerPos.above(), 0, Rotation.NONE, level);
        var inner = new SubLevelAssemblyHelper.AssemblyTransform(innerPos, innerPos.above(), 0, Rotation.NONE, level);
        SubLevelAttachmentApi.moveBlocks(level, outer, List.of(outerPos), () -> {
            assertThrows(IllegalStateException.class, () -> SubLevelAttachmentApi.moveBlocks(level, inner, List.of(innerPos),
                    () -> { throw new IllegalStateException("Transfer failed"); }));
            assertTrue(SubLevelAttachmentApi.isMoving(level, outerPos));
            assertFalse(SubLevelAttachmentApi.isMoving(level, innerPos));
        });
        assertFalse(SubLevelAttachmentApi.isMoving(level, outerPos));
    }

    private static ServerLevel level(){
        ServerLevel level = mock(ServerLevel.class);
        when(level.hasChunkAt(any())).thenReturn(true);
        when(level.getBlockState(any())).thenReturn(Blocks.AIR.defaultBlockState());
        return level;
    }

    private static final class Attachment extends Block implements SubLevelBlockAttachment{
        private final Direction supportDir;
        private Attachment(Direction supportDir){
            super(BlockBehaviour.Properties.of().noCollission().replaceable());
            this.supportDir = supportDir;
        }
        @Override public boolean isAttachedTo(BlockState state, Direction dir){ return dir == supportDir; }
    }

    private static BlockState attachmentState(Direction supportDir){
        BlockState state = mock(BlockState.class);
        Attachment attachment = mock(Attachment.class);
        when(state.getBlock()).thenReturn(attachment);
        when(attachment.isAttachedTo(state, supportDir)).thenReturn(true);
        return state;
    }
}
