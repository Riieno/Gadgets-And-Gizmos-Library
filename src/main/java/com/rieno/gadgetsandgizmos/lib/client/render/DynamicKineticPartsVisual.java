package com.rieno.gadgetsandgizmos.lib.client.render;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.FlatLit;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

// Own animated model parts alongside one GPU-driven kinetic shaft
public abstract class DynamicKineticPartsVisual<T extends KineticBlockEntity>
        extends ColoredOrientedRotatingVisual<T> implements SimpleDynamicVisual{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Keep every created part under the visual's lighting and deletion lifecycle
    private final List<TransformedInstance> parts = new ArrayList<>();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Retain Create's shaft rotation and the host's model orientation
    protected DynamicKineticPartsVisual(VisualizationContext ctx, T be, float partialTick,
            Direction modelFacing, Direction facing, Model shaft, int color){
        super(ctx, be, partialTick, modelFacing, facing, shaft, color);
    }

    // Add one reusable GPU instance owned by this visual
    protected final TransformedInstance addPart(Model model){
        TransformedInstance part = instancerProvider().instancer(InstanceTypes.TRANSFORMED, model).createInstance();
        parts.add(part);
        relight(part);
        return part;
    }

    // Update only model transforms while Flywheel retains their vertex buffers
    @Override public final void beginFrame(DynamicVisual.Context ctx){ animate(ctx.partialTick()); }

    // Apply the host's model-specific transforms
    protected abstract void animate(float partialTick);

    // Relight all owned instances when the host's lighting changes
    @Override public void updateLight(float partialTick){
        super.updateLight(partialTick);
        relight(parts.toArray(FlatLit[]::new));
    }

    // Release every GPU instance when its block entity or level unloads
    @Override protected void _delete(){
        parts.forEach(Instance::delete);
        parts.clear();
        super._delete();
    }

    // Include articulated parts in the ordinary breaking overlay
    @Override public void collectCrumblingInstances(Consumer<Instance> consumer){
        super.collectCrumblingInstances(consumer);
        parts.forEach(consumer);
    }
}
