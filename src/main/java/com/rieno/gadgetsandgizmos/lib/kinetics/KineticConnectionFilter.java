package com.rieno.gadgetsandgizmos.lib.kinetics;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;

// Control individual Create kinetic connections without changing their geometry
public interface KineticConnectionFilter {
    // Check if this kinetic connection is allowed
    boolean allowsKineticConnection(KineticBlockEntity other, boolean outgoing);
}
