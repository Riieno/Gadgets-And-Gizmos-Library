package com.rieno.gadgetsandgizmos.lib.client.render;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

// Cache tank tessellation while retaining exact fill interpolation and animated atlas textures
public final class RetainedTankRenderer implements RetainedHologramRenderer{
/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        VARIABLES
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    private final FluidTankBlockEntity tank;
    private final BlockEntityRenderer<FluidTankBlockEntity> draw;
    private final RenderGeometryCache geometry = new RenderGeometryCache();
    private final java.util.function.LongSupplier revision;
    private long prevRevision;
    private Snapshot snapshot;
    private int prevLight;
    private int prevOverlay;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        FUNCTIONS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    public RetainedTankRenderer(FluidTankBlockEntity tank, BlockEntityRenderer<FluidTankBlockEntity> draw){
        this(tank, draw, () -> 0L);
    }

    // Include host presentation changes without polling unrelated block state or UI data
    public RetainedTankRenderer(FluidTankBlockEntity tank, BlockEntityRenderer<FluidTankBlockEntity> draw,
                                java.util.function.LongSupplier revision){
        this.tank = Objects.requireNonNull(tank);
        this.draw = Objects.requireNonNull(draw);
        this.revision = Objects.requireNonNull(revision);
    }

    @Override
    public void render(float partialTick, PoseStack poses, MultiBufferSource buffers, int light, int overlay){
        long currentRevision = revision.getAsLong();
        boolean refresh = snapshot == null || prevLight != light || prevOverlay != overlay || prevRevision != currentRevision || !snapshot.matches(tank, partialTick);
        if(refresh){ snapshot = new Snapshot(tank, partialTick); prevLight = light; prevOverlay = overlay; prevRevision = currentRevision; }
        geometry.render(poses, buffers, refresh,
                target -> draw.render(tank, partialTick, new PoseStack(), target, light, overlay));
    }

    @Override public void close(){ geometry.clear(); snapshot = null; }

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        HELPERS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    private static float fill(FluidTankBlockEntity tank, float partialTick){
        var level = tank.getFluidLevel();
        return level == null ? 0 : level.getValue(partialTick);
    }

    private static final class Snapshot{
        private final FluidTankBlockEntity root;
        private final IFluidHandler storage;
        private final List<FluidStack> fluids;
        private final int capacity;
        private final int width;
        private final int height;
        private final int rootWidth;
        private final int rootHeight;
        private final float fill;
        private final Object windows;
        private final boolean controller;
        private final BlockPos controllerPos;

        private Snapshot(FluidTankBlockEntity tank, float partialTick){
            root = tank.getControllerBE();
            var inventory = tank.getTankInventory();
            storage = inventory;
            capacity = inventory.getCapacity();
            fluids = new ArrayList<>(storage.getTanks());
            for(int idx = 0; idx < storage.getTanks(); idx++) fluids.add(storage.getFluidInTank(idx).copy());
            width = tank.getWidth(); height = tank.getHeight();
            rootWidth = root == null ? 0 : root.getWidth(); rootHeight = root == null ? 0 : root.getHeight();
            fill = fill(tank, partialTick);
            windows = tank.getExtraData(); controller = tank.isController();
            var pos = tank.getController();
            controllerPos = pos == null ? null : pos.immutable();
        }

        private boolean matches(FluidTankBlockEntity tank, float partialTick){
            var inventory = tank.getTankInventory();
            if(root != tank.getControllerBE() || storage != inventory
                    || capacity != inventory.getCapacity() || fluids.size() != storage.getTanks()
                    || width != tank.getWidth() || height != tank.getHeight()
                    || rootWidth != (root == null ? 0 : root.getWidth()) || rootHeight != (root == null ? 0 : root.getHeight())
                    || Float.floatToIntBits(fill) != Float.floatToIntBits(fill(tank, partialTick))
                    || !Objects.equals(windows, tank.getExtraData()) || controller != tank.isController()
                    || !Objects.equals(controllerPos, tank.getController())) return false;
            for(int idx = 0; idx < fluids.size(); idx++){
                if(!FluidStack.matches(fluids.get(idx), storage.getFluidInTank(idx))) return false;
            }
            return true;
        }
    }
}
