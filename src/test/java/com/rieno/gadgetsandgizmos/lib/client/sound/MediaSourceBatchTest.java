package com.rieno.gadgetsandgizmos.lib.client.sound;

import com.rieno.gadgetsandgizmos.lib.media.MediaPlaybackState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MediaSourceBatchTest{
    private static final UUID ID = UUID.randomUUID();

    @Test void handheldAndPlacedCopiesProduceOneSourceRegardlessOfObservationOrder(){
        for(boolean reverse : new boolean[]{false, true}){
            var batch = new MediaSourceBatch();
            for(int idx = 0; idx < 10; idx++){
                batch.offer(ID, state(reverse ? 2 : 1), reverse ? Vec3.ZERO : new Vec3(500, 0, 0), reverse, Vec3.ZERO);
                batch.offer(ID, state(reverse ? 1 : 2), reverse ? new Vec3(500, 0, 0) : Vec3.ZERO, !reverse, Vec3.ZERO);
            }
            var sources = batch.drain();
            assertEquals(1, sources.size());
            assertTrue(sources.get(ID).relative());
            assertEquals(Vec3.ZERO, sources.get(ID).pos());
            assertEquals(2, sources.get(ID).state().revision());
            assertTrue(batch.drain().isEmpty());
        }
    }

    @Test void newestPlaybackAndNearestWorldPositionAreSelectedIndependently(){
        var batch = new MediaSourceBatch();
        batch.offer(ID, state(1), new Vec3(1, 0, 0), false, Vec3.ZERO);
        batch.offer(ID, state(9), new Vec3(500, 0, 0), false, Vec3.ZERO);
        var source = batch.drain().get(ID);
        assertEquals(new Vec3(1, 0, 0), source.pos());
        assertEquals(9, source.state().revision());
    }

    @Test void anOldPlayingCopyCannotOverrideANewerPause(){
        var batch = new MediaSourceBatch();
        batch.offer(ID, state(4).paused(20), Vec3.ZERO, false, Vec3.ZERO);
        batch.offer(ID, state(4), Vec3.ZERO, true, Vec3.ZERO);
        assertFalse(batch.drain().get(ID).state().playing());
        batch.offer(ID, state(8), Vec3.ZERO, true, Vec3.ZERO);
        batch.clear();
        assertTrue(batch.drain().isEmpty());
    }

    private static MediaPlaybackState state(long revision){
        return new MediaPlaybackState(ResourceLocation.parse("test:disc"), true, 0, 0, 1000, revision);
    }
}
