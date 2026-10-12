package com.rieno.gadgetsandgizmos.lib.chat;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class NearbyChatRangeTest{
    private static final UUID SHIP = new UUID(1, 2);
    private static final UUID OTHER_SHIP = new UUID(3, 4);

    @Test
    void worldAudienceIncludesExactlyTheCenteredNineChunks(){
        NearbyChatRange range = new NearbyChatRange(null, 7, -3);
        for(int x = 5; x <= 9; x++){
            for(int z = -5; z <= -1; z++){
                assertEquals(Math.abs(x - 7) <= 1 && Math.abs(z + 3) <= 1,
                        range.contains(true, null, null, x, z), x + "," + z);
            }
        }
    }

    @Test
    void worldAudienceUsesActualPositionEvenForPlayersOnNearbyShips(){
        NearbyChatRange range = new NearbyChatRange(null, -1, -1);
        assertTrue(range.contains(true, OTHER_SHIP, null, 0, 0));
        assertFalse(range.contains(true, OTHER_SHIP, null, 1, 0));
        assertFalse(range.contains(true, null, null, -3, -1));
    }

    @Test
    void shipAudienceAcceptsWalkingAndSeatedOccupantsRegardlessOfChunkDistance(){
        NearbyChatRange range = new NearbyChatRange(SHIP, 0, 0);
        assertTrue(range.contains(true, SHIP, null, 10000, -10000));
        assertTrue(range.contains(true, null, SHIP, -10000, 10000));
    }

    @Test
    void shipAudienceExcludesNearbyPlayersOutsideThatShip(){
        NearbyChatRange range = new NearbyChatRange(SHIP, 0, 0);
        assertFalse(range.contains(true, OTHER_SHIP, OTHER_SHIP, 0, 0));
        assertFalse(range.contains(true, null, null, 0, 0));
    }

    @Test
    void audiencesNeverCrossDimensions(){
        assertFalse(new NearbyChatRange(null, 0, 0).contains(false, null, null, 0, 0));
        assertFalse(new NearbyChatRange(SHIP, 0, 0).contains(false, SHIP, SHIP, 0, 0));
    }

    @Test
    void chunkDifferencesDoNotOverflow(){
        assertFalse(new NearbyChatRange(null, Integer.MIN_VALUE, 0)
                .contains(true, null, null, Integer.MAX_VALUE, 0));
    }
}

