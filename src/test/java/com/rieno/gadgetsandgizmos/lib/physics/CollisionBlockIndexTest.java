package com.rieno.gadgetsandgizmos.lib.physics;

import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CollisionBlockIndexTest{
    @Test void cachedEmptyColumnsAreReusedAndEditsInvalidateThemImmediately(){
        var section = mock(LevelChunkSection.class,withSettings().extraInterfaces(CollisionBlockIndex.Revision.class));
        var air = mock(BlockState.class); when(air.isAir()).thenReturn(true);
        var solid = mock(BlockState.class);
        long[] revision = {0}; int[] occupied = {-1};
        when(((CollisionBlockIndex.Revision)section).collisionBlockRevision()).thenAnswer(call -> revision[0]);
        when(section.getBlockState(anyInt(),anyInt(),anyInt())).thenAnswer(call -> (int)call.getArgument(1) == occupied[0] ? solid : air);
        assertEquals(16,CollisionBlockIndex.nextOccupiedY(section,2,0,3));
        assertEquals(16,CollisionBlockIndex.nextOccupiedY(section,2,4,3));
        verify(section,times(16)).getBlockState(anyInt(),anyInt(),anyInt());
        occupied[0]=7;revision[0]++;
        assertEquals(7,CollisionBlockIndex.nextOccupiedY(section,2,0,3));
        assertEquals(16,CollisionBlockIndex.nextOccupiedY(section,2,8,3));
        verify(section,times(32)).getBlockState(anyInt(),anyInt(),anyInt());
        occupied[0]=-1;revision[0]++;
        assertEquals(16,CollisionBlockIndex.nextOccupiedY(section,2,0,3));
    }
}
