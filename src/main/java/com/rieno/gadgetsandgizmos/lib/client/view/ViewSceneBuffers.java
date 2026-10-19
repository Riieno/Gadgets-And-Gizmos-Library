package com.rieno.gadgetsandgizmos.lib.client.view;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.rieno.gadgetsandgizmos.lib.mixin.ViewSceneBufferSourceAccess;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SectionBufferBuilderPack;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

// Release private scene storage after its terrain dispatcher has finished its jobs
final class ViewSceneBuffers{
    private ViewSceneBuffers(){}

    // Collect aliased fixed buffers once and include unused terrain layers and pooled builders
    static void close(RenderBuffers buffers){
        Set<ByteBufferBuilder> storage = Collections.newSetFromMap(new IdentityHashMap<>());
        collect(storage, buffers.fixedBufferPack());
        SectionBufferBuilderPack pack;
        while((pack = buffers.sectionBufferPool().acquire()) != null) collect(storage, pack);
        for(var source : new net.minecraft.client.renderer.MultiBufferSource.BufferSource[]{
                buffers.bufferSource(), buffers.crumblingBufferSource()}){
            var access = (ViewSceneBufferSourceAccess) source;
            storage.add(access.gadgetsngizmos$sharedBuffer());
            storage.addAll(access.gadgetsngizmos$fixedBuffers().values());
        }
        storage.forEach(ByteBufferBuilder::close);
    }

    // Retain allocation identity because entity sheets share the fixed terrain pack
    private static void collect(Set<ByteBufferBuilder> storage, SectionBufferBuilderPack pack){
        for(RenderType type : RenderType.chunkBufferLayers()) storage.add(pack.buffer(type));
    }
}
