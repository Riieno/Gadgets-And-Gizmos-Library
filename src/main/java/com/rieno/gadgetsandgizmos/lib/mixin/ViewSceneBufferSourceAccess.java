package com.rieno.gadgetsandgizmos.lib.mixin;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.SequencedMap;

// Expose owned vertex storage so secondary renderers can release every allocation
@Mixin(MultiBufferSource.BufferSource.class)
public interface ViewSceneBufferSourceAccess{
    // Read the shared storage for non-fixed render types
    @Accessor("sharedBuffer") ByteBufferBuilder gadgetsngizmos$sharedBuffer();
    // Read the fixed render type storage, including buffers registered by other mods
    @Accessor("fixedBuffers") SequencedMap<RenderType, ByteBufferBuilder> gadgetsngizmos$fixedBuffers();
}
