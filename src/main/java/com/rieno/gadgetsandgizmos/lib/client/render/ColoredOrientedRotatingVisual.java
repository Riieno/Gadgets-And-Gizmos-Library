package com.rieno.gadgetsandgizmos.lib.client.render;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.OrientedRotatingVisual;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import net.minecraft.core.Direction;

// Retain a host-supplied colour when Create refreshes an oriented kinetic instance
public class ColoredOrientedRotatingVisual<T extends KineticBlockEntity> extends OrientedRotatingVisual<T>{
    private final int color;

    public ColoredOrientedRotatingVisual(VisualizationContext ctx, T blockEntity, float partialTick,
                                         Direction modelFacing, Direction facing, Model model, int color){
        super(ctx, blockEntity, partialTick, modelFacing, facing, model);
        this.color = color & 0xFFFFFF;
        rotatingModel.colorRgb(this.color).setChanged();
    }

    @Override
    public void update(float partialTick){
        super.update(partialTick);
        rotatingModel.colorRgb(color).setChanged();
    }
}
