package com.rieno.gadgetsandgizmos.lib.chat;

import java.util.UUID;

// Select one sublevel's occupants or a centered three-by-three chunk area
public record NearbyChatRange(UUID subLevelId, int chunkX, int chunkZ){
    public boolean contains(boolean sameLevel, UUID trackedSubLevelId, UUID containingSubLevelId,
                            int playerChunkX, int playerChunkZ){
        if(!sameLevel) return false;
        if(subLevelId != null){
            return subLevelId.equals(trackedSubLevelId) || subLevelId.equals(containingSubLevelId);
        }
        return Math.abs((long) playerChunkX - chunkX) <= 1
                && Math.abs((long) playerChunkZ - chunkZ) <= 1;
    }
}

