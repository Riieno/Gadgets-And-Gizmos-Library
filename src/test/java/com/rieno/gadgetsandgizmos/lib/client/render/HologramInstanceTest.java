package com.rieno.gadgetsandgizmos.lib.client.render;

import dev.engine_room.flywheel.api.instance.InstanceHandle;
import dev.engine_room.flywheel.lib.instance.ColoredLitInstance;
import net.minecraft.client.renderer.LightTexture;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HologramInstanceTest{
    @Test void staticInstancesKeepTheirGeometryAndFreezeOnlyOnce(){
        var handle = new Handle();
        var rotating = new com.simibubi.create.content.kinetics.base.RotatingInstance(null, handle);
        rotating.rotationalSpeed = 64; rotating.rotationOffset = 22;
        FlywheelHologramVisuals.freeze(rotating);
        assertEquals(0, rotating.rotationalSpeed); assertEquals(22, rotating.rotationOffset);
        assertEquals(1, handle.changes);
        FlywheelHologramVisuals.freeze(rotating); assertEquals(1, handle.changes);
        var scroll = new com.simibubi.create.content.processing.burner.ScrollInstance(null, handle);
        scroll.speedU = 2; scroll.speedV = -3; scroll.offsetU = .5F;
        FlywheelHologramVisuals.freeze(scroll);
        assertEquals(0, scroll.speedU); assertEquals(0, scroll.speedV); assertEquals(.5F, scroll.offsetU);
    }
    @Test
    void stableTintDoesNotUploadTheInstanceAgain(){
        var handle = new Handle();
        var instance = new ColoredLitInstance(null, handle){};
        FlywheelHologramVisuals.hologramLighting(instance);
        assertEquals(1, handle.changes);
        assertEquals(90, Byte.toUnsignedInt(instance.red));
        assertEquals(210, Byte.toUnsignedInt(instance.green));
        assertEquals(255, Byte.toUnsignedInt(instance.blue));
        assertEquals(190, Byte.toUnsignedInt(instance.alpha));
        assertEquals(LightTexture.FULL_BRIGHT, instance.light);
        for(int idx = 0; idx < 1000; idx++) FlywheelHologramVisuals.hologramLighting(instance);
        assertEquals(1, handle.changes);
    }

    @Test
    void nativeColorAndLightChangesAreRestoredExactlyOnce(){
        var handle = new Handle();
        var instance = new ColoredLitInstance(null, handle){};
        FlywheelHologramVisuals.hologramLighting(instance);
        instance.light = 0;
        FlywheelHologramVisuals.hologramLighting(instance);
        assertEquals(2, handle.changes);
        instance.alpha = 0;
        FlywheelHologramVisuals.hologramLighting(instance);
        assertEquals(3, handle.changes);
        FlywheelHologramVisuals.hologramLighting(instance);
        assertEquals(3, handle.changes);
    }

    private static final class Handle implements InstanceHandle{
        private int changes;
        @Override public void setChanged(){ changes++; }
        @Override public void setDeleted(){}
        @Override public void setVisible(boolean visible){}
        @Override public boolean isVisible(){ return true; }
    }
}
