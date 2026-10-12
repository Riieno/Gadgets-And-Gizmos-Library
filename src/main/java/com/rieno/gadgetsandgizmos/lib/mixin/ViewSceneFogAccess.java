package com.rieno.gadgetsandgizmos.lib.mixin;

import net.minecraft.client.renderer.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// Expose the vanilla atmosphere state for scoped secondary rendering
@Mixin(FogRenderer.class)
public interface ViewSceneFogAccess{
    // Read the current red component
    @Accessor("fogRed")
    static float gadgetsngizmos$getRed(){ throw new AssertionError(); }
    // Restore the current red component
    @Accessor("fogRed")
    static void gadgetsngizmos$setRed(float val){ throw new AssertionError(); }
    // Read the current green component
    @Accessor("fogGreen")
    static float gadgetsngizmos$getGreen(){ throw new AssertionError(); }
    // Restore the current green component
    @Accessor("fogGreen")
    static void gadgetsngizmos$setGreen(float val){ throw new AssertionError(); }
    // Read the current blue component
    @Accessor("fogBlue")
    static float gadgetsngizmos$getBlue(){ throw new AssertionError(); }
    // Restore the current blue component
    @Accessor("fogBlue")
    static void gadgetsngizmos$setBlue(float val){ throw new AssertionError(); }
    // Read the water tint target
    @Accessor("targetBiomeFog")
    static int gadgetsngizmos$getTarget(){ throw new AssertionError(); }
    // Restore the water tint target
    @Accessor("targetBiomeFog")
    static void gadgetsngizmos$setTarget(int val){ throw new AssertionError(); }
    // Read the previous water tint
    @Accessor("previousBiomeFog")
    static int gadgetsngizmos$getPrevious(){ throw new AssertionError(); }
    // Restore the previous water tint
    @Accessor("previousBiomeFog")
    static void gadgetsngizmos$setPrevious(int val){ throw new AssertionError(); }
    // Read the water tint transition time
    @Accessor("biomeChangedTime")
    static long gadgetsngizmos$getChangedTime(){ throw new AssertionError(); }
    // Restore the water tint transition time
    @Accessor("biomeChangedTime")
    static void gadgetsngizmos$setChangedTime(long val){ throw new AssertionError(); }
}
