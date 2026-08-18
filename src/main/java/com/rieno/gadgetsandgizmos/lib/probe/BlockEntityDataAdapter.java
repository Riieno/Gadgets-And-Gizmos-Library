package com.rieno.gadgetsandgizmos.lib.probe;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;

// Adapt one supported block entity type to explicit graph data ports
public interface BlockEntityDataAdapter<T extends BlockEntity> {
    // Get the adapted block entity type
    Class<T> targetType();

    // Check if this adapter supports the target instance
    default boolean supports(T target) {
        return target != null;
    }

    // Get the explicit data ports
    List<BlockEntityDataPort> ports(T target);

    // Read one explicit data port
    default GraphValue read(T target, String port) {
        return null;
    }

    // Write one explicit data port
    default boolean write(T target, String port, GraphValue value) {
        return false;
    }
}
