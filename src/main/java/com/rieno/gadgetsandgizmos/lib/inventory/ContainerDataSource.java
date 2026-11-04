package com.rieno.gadgetsandgizmos.lib.inventory;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;

// Supply the physical containers represented by a storage display or network entry
public interface ContainerDataSource{
    // Return loaded storage members, including this block when it owns storage
    List<BlockEntity> dataContainers();

    // Prefer the attachment face when a container has no unsided capability
    default Direction dataSide(){ return null; }
}
