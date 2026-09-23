package com.rieno.gadgetsandgizmos.lib.kinetics;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.simibubi.create.content.kinetics.RotationPropagator;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

// Resolve compatible neighbours and directions in a Create kinetic network
public final class KineticGraphHelper {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final @Nullable Method ROTATION_PROPAGATOR_CONNECTED_NEIGHBOURS =
            getRotationPropagatorMethod("getConnectedNeighbours", KineticBlockEntity.class);
    private static final @Nullable Method ROTATION_PROPAGATOR_SPEED_MODIFIER =
            getRotationPropagatorMethod("getRotationSpeedModifier", KineticBlockEntity.class, KineticBlockEntity.class);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the kinetic graph
    private KineticGraphHelper() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the connected neighbours
    @SuppressWarnings("unchecked")
    public static List<KineticBlockEntity> getConnectedNeighbours(KineticBlockEntity target) {
        if (ROTATION_PROPAGATOR_CONNECTED_NEIGHBOURS == null) {
            return Collections.emptyList();
        }
        try {
            return (List<KineticBlockEntity>) ROTATION_PROPAGATOR_CONNECTED_NEIGHBOURS.invoke(null, target);
        } catch (ReflectiveOperationException ignored) {
            return Collections.emptyList();
        }
    }

    // Add the diagonal positions used by large cogwheel meshing
    public static List<BlockPos> addLargeCogwheelPropagationLocations(BlockPos origin, List<BlockPos> neighbours) {
        BlockPos.betweenClosedStream(new BlockPos(-1, -1, -1), new BlockPos(1, 1, 1))
                .filter(offset -> offset.distSqr(BlockPos.ZERO) == 2.0D)
                .map(origin::offset)
                .filter(pos -> !neighbours.contains(pos))
                .forEach(neighbours::add);
        return neighbours;
    }

    // Get the rotation speed modifier
    @Nullable
    public static Float getRotationSpeedModifier(KineticBlockEntity from, KineticBlockEntity to) {
        if (ROTATION_PROPAGATOR_SPEED_MODIFIER == null) {
            return null;
        }
        try {
            return (Float) ROTATION_PROPAGATOR_SPEED_MODIFIER.invoke(null, from, to);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    // Get the rotation propagator method
    @Nullable
    private static Method getRotationPropagatorMethod(String name, Class<?>... parameterTypes) {
        try {
            Method method = RotationPropagator.class.getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
