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

    // Supplemental settings keep the target's existing direct controls available
    default boolean isSupplemental(){
        return false;
    }

    // Check if this adapter supports the target instance
    default boolean supports(T target) {
        return target != null;
    }

    // Get the explicit data ports
    List<BlockEntityDataPort> ports(T target);

    // Change the revision when an adapted target gains or loses ports
    default long schemaRevision(T target){
        return 0L;
    }

    // Read one explicit data port
    default GraphValue read(T target, String port) {
        return null;
    }

    // Write one explicit data port
    default boolean write(T target, String port, GraphValue value) {
        return false;
    }

    // Let related settings apply in a stable order within one graph write
    default boolean writeAll(T target, java.util.Map<String, GraphValue> values){
        boolean changed = false;
        for(var entry : values.entrySet()) changed |= write(target, entry.getKey(), entry.getValue());
        return changed;
    }
}
