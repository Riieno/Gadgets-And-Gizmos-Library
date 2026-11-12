package com.rieno.gadgetsandgizmos.lib.client.view;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                    IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.Map;

// Draw resident DH buffers without rebuilding the player's render lists, framebuffers or temporal history
final class DistantHorizonFeeds implements AutoCloseable{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private final Object client, pass, shader, meta, enabled, mode, textured, atlas;
    private final Constructor<?> stateConstructor, paramsConstructor, matrixConstructor, listConstructor;
    private final Method update, wrappedLevel, profiler, fill, bind, render, free, listClear, listAdd, sections;
    private final Method configValue, sectionEnabled, minX, minZ, blockWidth, atlasBind;
    private final Method setMatrix;
    private final int temporalUniform, ditherUniform, noiseUniform, clipUniform;
    private final Field projection, modelView, frameTime, level, dhProjection, handler, tree, buffer, pos, activeShader, lightmap;
    private final Map<Object, Scene> scenes = new IdentityHashMap<>();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Leave clients without DH independent of its implementation classes
    static DistantHorizonFeeds create() throws ReflectiveOperationException{
        try{ return new DistantHorizonFeeds(); }
        catch(ClassNotFoundException err){ return null; }
    }

    // Resolve the optional 3.3 terrain contracts once
    @SuppressWarnings({"unchecked", "rawtypes"})
    private DistantHorizonFeeds() throws ReflectiveOperationException{
        Class<?> api = Class.forName("com.seibel.distanthorizons.core.api.internal.ClientApi");
        Field mc = api.getDeclaredField("MC_CLIENT"); mc.setAccessible(true); client = mc.get(null);
        wrappedLevel = client.getClass().getMethod("getWrappedClientLevel"); profiler = client.getClass().getMethod("getProfiler");
        Class<?> state = Class.forName("com.seibel.distanthorizons.core.api.internal.rendering.DhRenderState");
        Class<?> params = Class.forName("com.seibel.distanthorizons.core.render.RenderParams");
        Class<? extends Enum> passType = (Class<? extends Enum>)Class.forName("com.seibel.distanthorizons.api.enums.rendering.EDhApiRenderPass");
        pass = Enum.valueOf(passType, "OPAQUE_AND_TRANSPARENT");
        stateConstructor = state.getConstructor(); paramsConstructor = params.getConstructor();
        projection = state.getField("mcProjectionMatrix"); modelView = state.getField("mcModelViewMatrix");
        frameTime = state.getField("partialTickTime"); level = state.getField("clientLevelWrapper");
        matrixConstructor = projection.getType().getConstructor(Matrix4fc.class);
        dhProjection = params.getField("dhProjectionMatrix"); handler = params.getField("renderBufferHandler");
        setMatrix = dhProjection.getType().getMethod("set", dhProjection.getType());
        lightmap = params.getField("lightmap"); update = params.getMethod("update", passType, state);
        Class<?> handlerType = Class.forName("com.seibel.distanthorizons.core.render.RenderBufferHandler");
        tree = handlerType.getField("lodQuadTree");
        sections = tree.getType().getMethod("populateListWithEnabledRenderSections", ArrayList.class);
        Class<?> section = Class.forName("com.seibel.distanthorizons.core.render.QuadTree.LodRenderSection");
        buffer = section.getField("renderBufferContainer"); pos = section.getField("pos");
        sectionEnabled = section.getMethod("getRenderingEnabled");
        Class<?> sectionPos = Class.forName("com.seibel.distanthorizons.core.pos.DhSectionPos");
        minX = sectionPos.getMethod("getMinCornerBlockX", long.class); minZ = sectionPos.getMethod("getMinCornerBlockZ", long.class);
        blockWidth = sectionPos.getMethod("getBlockWidth", long.class);
        Class<?> lists = Class.forName("com.seibel.distanthorizons.core.util.objects.SortedArraySet");
        listConstructor = lists.getConstructor(Comparator.class); listClear = lists.getMethod("clear"); listAdd = lists.getMethod("add", Object.class);
        Class<?> shaderType = Class.forName("com.seibel.distanthorizons.common.render.openGl.terrain.GlDhTerrainShaderProgram_neoforge");
        shader = shaderType.getConstructor().newInstance(); shaderType.getMethod("tryInit").invoke(shader);
        temporalUniform = shaderType.getField("uFrameMod8").getInt(shader); ditherUniform = shaderType.getField("uDitherDhRendering").getInt(shader);
        noiseUniform = shaderType.getField("uNoiseEnabled").getInt(shader); clipUniform = shaderType.getField("uClipDistance").getInt(shader);
        bind = shaderType.getMethod("bind"); free = shaderType.getMethod("free");
        Class<?> renderParam = Class.forName("com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam");
        fill = shaderType.getMethod("fillUniformData", renderParam);
        render = shaderType.getMethod("render", params, boolean.class, lists, profiler.getReturnType());
        Class<?> metaType = Class.forName("com.seibel.distanthorizons.common.render.openGl.GlDhMetaRenderer_neoforge");
        meta = metaType.getField("INSTANCE").get(null); activeShader = metaType.getField("shaderProgramForThisFrame");
        enabled = Class.forName("com.seibel.distanthorizons.core.config.Config$Client").getField("quickEnableRendering").get(null);
        mode = Class.forName("com.seibel.distanthorizons.core.config.Config$Client$Advanced$Debugging").getField("rendererMode").get(null);
        textured = Class.forName("com.seibel.distanthorizons.core.config.Config$Client$Advanced$Graphics$Texture").getField("enableTexturedLods").get(null);
        configValue = enabled.getClass().getMethod("get");
        Class<?> atlasType = Class.forName("com.seibel.distanthorizons.common.render.openGl.terrain.GlBlockTextureAtlas");
        atlas = atlasType.getField("INSTANCE").get(null); atlasBind = atlasType.getMethod("bind");
    }

    // Use a private frustum and list while borrowing only uploaded world geometry
    boolean render(Object owner, Matrix4fc view, Matrix4fc ignored, float partialTick) throws ReflectiveOperationException{
        if(!Boolean.TRUE.equals(configValue.invoke(enabled)) || !"DEFAULT".equals(configValue.invoke(mode).toString())) return false;
        Scene scene = scenes.get(owner);
        if(scene == null){ scene = new Scene(); scenes.put(owner, scene); }
        Object wrapped = wrappedLevel.invoke(client);
        if(wrapped == null) return false;
        var pose = ViewSceneRenderer.capturePose(); var target = ViewSceneRenderer.captureTarget();
        Matrix4f proj = new Matrix4f().perspective((float)Math.toRadians(pose.fov()), target.width / (float)target.height, 8, 65_536);
        Object state = stateConstructor.newInstance();
        projection.set(state, matrixConstructor.newInstance(proj)); modelView.set(state, matrixConstructor.newInstance(view));
        frameTime.setFloat(state, partialTick); level.set(state, wrapped);
        update.invoke(scene.params, pass, state); setMatrix.invoke(dhProjection.get(scene.params), matrixConstructor.newInstance(proj));
        Object buffers = handler.get(scene.params), light = lightmap.get(scene.params);
        if(buffers == null || light == null) return false;
        scene.sections.clear(); sections.invoke(tree.get(buffers), scene.sections); listClear.invoke(scene.buffers);
        var frustum = new FrustumIntersection(new Matrix4f(proj).mul(view).translate(
                (float)-pose.position().x, (float)-pose.position().y, (float)-pose.position().z));
        for(Object section : scene.sections){
            if(!Boolean.TRUE.equals(sectionEnabled.invoke(section))) continue;
            Object val = buffer.get(section); if(val == null) continue;
            long key = pos.getLong(section); int x = (int)minX.invoke(null, key), z = (int)minZ.invoke(null, key), width = (int)blockWidth.invoke(null, key);
            if(frustum.testAab(x, -2048, z, x + width, 4096, z + width)) listAdd.invoke(scene.buffers, val);
        }
        Object prev = activeShader.get(meta);
        try{
            activeShader.set(meta, shader); bind.invoke(shader); fill.invoke(shader, scene.params);
            // Camera layers have no temporal accumulation or full-screen fade pass
            GL20.glUniform1i(temporalUniform, -1); GL20.glUniform1i(ditherUniform, 0); GL20.glUniform1i(noiseUniform, 0);
            GL20.glUniform1f(clipUniform, Math.max(8, ViewSceneRenderer.captureDistance() - 16));
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, (int)light.getClass().getMethod("getOpenGlId").invoke(light));
            if(Boolean.TRUE.equals(configValue.invoke(textured))) atlasBind.invoke(atlas);
            target.bindWrite(true); GL11.glEnable(GL11.GL_DEPTH_TEST); GL11.glDepthFunc(GL11.GL_LEQUAL); GL11.glDepthMask(true);
            render.invoke(shader, scene.params, true, scene.buffers, profiler.invoke(client));
            render.invoke(shader, scene.params, false, scene.buffers, profiler.invoke(client));
            return true;
        }finally{ activeShader.set(meta, prev); }
    }

    // Retire source lists without closing any borrowed DH terrain buffers
    void release(Object owner){ scenes.remove(owner); }
    @Override public void close(){
        scenes.clear();
        try{ free.invoke(shader); }catch(ReflectiveOperationException err){ throw new IllegalStateException("Unable to release camera DH shader", err); }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Keep cached render parameters and collection storage private to each camera
    private final class Scene{
        private final Object params, buffers;
        private final ArrayList<Object> sections = new ArrayList<>();
        private Scene() throws ReflectiveOperationException{
            params = paramsConstructor.newInstance();
            buffers = listConstructor.newInstance((Comparator<Object>)(a, b) -> Integer.compare(System.identityHashCode(a), System.identityHashCode(b)));
        }
    }
}
