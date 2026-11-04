package com.rieno.gadgetsandgizmos.lib.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rieno.gadgetsandgizmos.lib.inventory.IndependentFluidContainer;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.fluids.tank.FluidTankRenderer;
import net.createmod.catnip.platform.NeoForgeCatnipServices;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.neoforged.neoforge.fluids.FluidStack;

// Render every stored fluid through Create's normal texture, tint, luminosity and translucent buffer handling
public class MultiFluidTankRenderer extends FluidTankRenderer implements HologramRendererProvider{
    private static final float HULL = 9F / 128;
    private static final float BOTTOM = 5F / 16;
    private static final float TOP = 1F / 4;

    public MultiFluidTankRenderer(BlockEntityRendererProvider.Context ctx){ super(ctx); }

    @Override public RetainedHologramRenderer createHologramRenderer(net.minecraft.world.level.block.entity.BlockEntity entity){
        if(getClass() != MultiFluidTankRenderer.class || !(entity instanceof FluidTankBlockEntity tank)) return null;
        return createRetainedTankRenderer(tank);
    }

    // Subclasses opt in when their additional geometry uses the same tank invalidation rules
    protected final RetainedHologramRenderer createRetainedTankRenderer(FluidTankBlockEntity tank){
        return createRetainedTankRenderer(tank, () -> 0L);
    }

    protected final RetainedHologramRenderer createRetainedTankRenderer(FluidTankBlockEntity tank, java.util.function.LongSupplier revision){
        return new RetainedTankRenderer(tank, (target, partial, poses, buffers, light, overlay) ->
                renderSafe(target, partial, poses, buffers, light, overlay), revision);
    }

    @Override protected void renderSafe(FluidTankBlockEntity tank, float partial, PoseStack poses,
                                        MultiBufferSource buffers, int light, int overlay){
        if(!(tank instanceof IndependentFluidContainer) || !tank.isController() || !Boolean.TRUE.equals(tank.getExtraData())){
            super.renderSafe(tank, partial, poses, buffers, light, overlay);
            return;
        }
        var level = tank.getFluidLevel();
        if(level == null) return;
        float height = tank.getHeight() - BOTTOM - TOP;
        if(height <= 0) return;
        var storage = tank.getTankInventory();
        var layers = FluidTankLayerLayout.layout(storage, storage.getCapacity(), level.getValue(partial));
        float far = tank.getWidth() - HULL;
        for(var layer : layers){
            float bottom = BOTTOM + (float)(layer.bottom() * height);
            float top = BOTTOM + (float)(layer.top() * height);
            if(top <= bottom) continue;
            renderLayer(layer.fluid(), HULL, bottom, HULL, far, top, far, poses, buffers, light);
        }
    }

    protected void renderLayer(FluidStack fluid, float minX, float minY, float minZ,
                               float maxX, float maxY, float maxZ, PoseStack poses, MultiBufferSource buffers, int light){
        NeoForgeCatnipServices.FLUID_RENDERER.renderFluidBox(fluid, minX, minY, minZ, maxX, maxY, maxZ,
                buffers, poses, light, false, true);
    }
}
