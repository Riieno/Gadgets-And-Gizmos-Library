package com.rieno.gadgetsandgizmos.lib.client.view;

import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL33;
import org.lwjgl.opengl.GL40;
import org.lwjgl.opengl.GL42;
import org.lwjgl.opengl.GL43;
import org.lwjgl.opengl.GL45;
import org.lwjgl.opengl.GL46;

// Retain indexed bindings which optional compute renderers change outside Minecraft's state cache
final class ViewLodGpuState{
    private final Runnable vanilla = ViewRenderState.save();
    private final boolean samplerObjects = GL.getCapabilities().OpenGL33;
    private final boolean compute = GL.getCapabilities().OpenGL43;
    private final boolean clip = GL.getCapabilities().OpenGL45;
    private final int[] storage = new int[10], uniforms = new int[4], samplers = new int[12];
    private final long[] storageStart = new long[10], storageSize = new long[10], uniformStart = new long[4], uniformSize = new long[4];
    private final int[] stencil = new int[14];
    private int indirect, dispatch, parameter, uniformBuffer, storageBuffer, image, imageLevel, imageLayer, imageAccess, imageFormat;
    private boolean imageLayered;
    private int origin, clipDepth;

    private ViewLodGpuState(){
        int[] keys = {GL11.GL_STENCIL_FUNC, GL11.GL_STENCIL_REF, GL11.GL_STENCIL_VALUE_MASK, GL11.GL_STENCIL_WRITEMASK,
                GL11.GL_STENCIL_FAIL, GL11.GL_STENCIL_PASS_DEPTH_FAIL, GL11.GL_STENCIL_PASS_DEPTH_PASS,
                GL20.GL_STENCIL_BACK_FUNC, GL20.GL_STENCIL_BACK_REF, GL20.GL_STENCIL_BACK_VALUE_MASK,
                GL20.GL_STENCIL_BACK_WRITEMASK, GL20.GL_STENCIL_BACK_FAIL,
                GL20.GL_STENCIL_BACK_PASS_DEPTH_FAIL, GL20.GL_STENCIL_BACK_PASS_DEPTH_PASS};
        for(int idx = 0; idx < keys.length; idx++) stencil[idx] = GL11.glGetInteger(keys[idx]);
        if(samplerObjects) for(int idx = 0; idx < samplers.length; idx++) samplers[idx] = GL30.glGetIntegeri(GL33.GL_SAMPLER_BINDING, idx);
        if(clip){ origin = GL11.glGetInteger(GL45.GL_CLIP_ORIGIN); clipDepth = GL11.glGetInteger(GL45.GL_CLIP_DEPTH_MODE); }
        if(!compute) return;
        read(GL43.GL_SHADER_STORAGE_BUFFER_BINDING, GL43.GL_SHADER_STORAGE_BUFFER_START, GL43.GL_SHADER_STORAGE_BUFFER_SIZE, storage, storageStart, storageSize);
        read(GL31.GL_UNIFORM_BUFFER_BINDING, GL31.GL_UNIFORM_BUFFER_START, GL31.GL_UNIFORM_BUFFER_SIZE, uniforms, uniformStart, uniformSize);
        storageBuffer = GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING); uniformBuffer = GL11.glGetInteger(GL31.GL_UNIFORM_BUFFER_BINDING);
        indirect = GL11.glGetInteger(GL40.GL_DRAW_INDIRECT_BUFFER_BINDING); dispatch = GL11.glGetInteger(GL43.GL_DISPATCH_INDIRECT_BUFFER_BINDING);
        if(GL.getCapabilities().OpenGL46) parameter = GL11.glGetInteger(GL46.GL_PARAMETER_BUFFER_BINDING);
        image = GL30.glGetIntegeri(GL42.GL_IMAGE_BINDING_NAME, 0); imageLevel = GL30.glGetIntegeri(GL42.GL_IMAGE_BINDING_LEVEL, 0);
        imageLayer = GL30.glGetIntegeri(GL42.GL_IMAGE_BINDING_LAYER, 0); imageLayered = GL30.glGetIntegeri(GL42.GL_IMAGE_BINDING_LAYERED, 0) != 0;
        imageAccess = GL30.glGetIntegeri(GL42.GL_IMAGE_BINDING_ACCESS, 0); imageFormat = GL30.glGetIntegeri(GL42.GL_IMAGE_BINDING_FORMAT, 0);
    }

    static Runnable save(){ ViewLodGpuState state = new ViewLodGpuState(); return state::restore; }

    private static void read(int binding, int start, int size, int[] ids, long[] starts, long[] sizes){
        for(int idx = 0; idx < ids.length; idx++){
            ids[idx] = GL30.glGetIntegeri(binding, idx);
            starts[idx] = GL32.glGetInteger64i(start, idx); sizes[idx] = GL32.glGetInteger64i(size, idx);
        }
    }

    private static void bind(int target, int[] ids, long[] starts, long[] sizes){
        for(int idx = 0; idx < ids.length; idx++){
            if(ids[idx] == 0 || sizes[idx] == 0) GL30.glBindBufferBase(target, idx, ids[idx]);
            else GL30.glBindBufferRange(target, idx, ids[idx], starts[idx], sizes[idx]);
        }
    }

    private void restore(){
        if(compute){
            bind(GL43.GL_SHADER_STORAGE_BUFFER, storage, storageStart, storageSize); bind(GL31.GL_UNIFORM_BUFFER, uniforms, uniformStart, uniformSize);
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, storageBuffer); GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER, uniformBuffer);
            GL15.glBindBuffer(GL40.GL_DRAW_INDIRECT_BUFFER, indirect); GL15.glBindBuffer(GL43.GL_DISPATCH_INDIRECT_BUFFER, dispatch);
            if(GL.getCapabilities().OpenGL46) GL15.glBindBuffer(GL46.GL_PARAMETER_BUFFER, parameter);
            GL42.glBindImageTexture(0, image, imageLevel, imageLayered, imageLayer, imageAccess, imageFormat);
        }
        if(clip) GL45.glClipControl(origin, clipDepth);
        if(samplerObjects) for(int idx = 0; idx < samplers.length; idx++) GL33.glBindSampler(idx, samplers[idx]);
        GL20.glStencilFuncSeparate(GL11.GL_FRONT, stencil[0], stencil[1], stencil[2]); GL20.glStencilMaskSeparate(GL11.GL_FRONT, stencil[3]);
        GL20.glStencilOpSeparate(GL11.GL_FRONT, stencil[4], stencil[5], stencil[6]);
        GL20.glStencilFuncSeparate(GL11.GL_BACK, stencil[7], stencil[8], stencil[9]); GL20.glStencilMaskSeparate(GL11.GL_BACK, stencil[10]);
        GL20.glStencilOpSeparate(GL11.GL_BACK, stencil[11], stencil[12], stencil[13]);
        vanilla.run();
    }
}
