package com.rieno.gadgetsandgizmos.lib.client.view;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL43;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

// Share Voxy's world geometry while retaining separate viewports, shaders and small render targets
final class VoxyFeeds implements AutoCloseable{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private final Object model, geometry, nodes, cleaner, traversal;
    private final Constructor<?> pipelineConstructor;
    private final Object factory;
    private final Method createRenderer, getStore;
    private final List<?> samplers;
    private final List<Field> timerFields;
    private final Field timerRunning;
    private final Object mainTimer;
    private final Method startTimer;
    private final Map<Object, Scene> scenes = new IdentityHashMap<>();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Resolve optional implementation contracts only after the installed Voxy system is observed
    VoxyFeeds(Object system) throws ReflectiveOperationException{
        model = field(system, "modelService"); geometry = field(system, "geometryData");
        nodes = field(system, "nodeManager"); cleaner = field(system, "nodeCleaner"); traversal = field(system, "traversal");
        Class<?> pipelineType = Class.forName("me.cortex.voxy.client.core.NormalRenderPipeline", true, system.getClass().getClassLoader());
        pipelineConstructor = pipelineType.getDeclaredConstructor(nodes.getClass(), cleaner.getClass(), traversal.getClass(), BooleanSupplier.class);
        pipelineConstructor.setAccessible(true);
        Method factoryMethod = system.getClass().getDeclaredMethod("getRenderBackendFactory"); factoryMethod.setAccessible(true);
        factory = factoryMethod.invoke(null);
        createRenderer = method(factory.getClass(), "create", 3);
        getStore = model.getClass().getMethod("getStore");
        Class<?> timing = Class.forName("me.cortex.voxy.client.TimingStatistics", true, system.getClass().getClassLoader());
        Field all = timing.getDeclaredField("allSamplers"); all.setAccessible(true);
        samplers = List.copyOf((List<?>)all.get(null)); mainTimer = timing.getField("main").get(null);
        timerFields = java.util.Arrays.stream(mainTimer.getClass().getDeclaredFields())
                .filter(val -> !java.lang.reflect.Modifier.isStatic(val.getModifiers())).toList();
        for(Field field : timerFields) field.setAccessible(true);
        timerRunning = mainTimer.getClass().getDeclaredField("running"); timerRunning.setAccessible(true);
        startTimer = mainTimer.getClass().getMethod("start");
    }

    // Draw loaded geometry without recentering Voxy's terrain tracker
    boolean render(Object owner, Matrix4fc view, Matrix4fc projection) throws ReflectiveOperationException{
        int[] buffers = new int[10];
        int buffer = GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING);
        for(int idx = 0; idx < buffers.length; idx++) buffers[idx] = GL30.glGetIntegeri(GL43.GL_SHADER_STORAGE_BUFFER_BINDING, idx);
        try{ return renderScene(owner, view, projection); }
        finally{
            for(int idx = 0; idx < buffers.length; idx++) GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, idx, buffers[idx]);
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, buffer);
        }
    }

    // Keep allocation and draw bindings scoped to the same source
    private boolean renderScene(Object owner, Matrix4fc view, Matrix4fc projection) throws ReflectiveOperationException{
        Scene scene = scenes.get(owner);
        if(scene == null){ scene = new Scene(); scenes.put(owner, scene); }
        var pose = ViewSceneRenderer.capturePose(); var target = ViewSceneRenderer.captureTarget();
        if(pose == null || target == null) return false;
        scene.vanillaProjection.invoke(scene.viewport, projection);
        scene.projection.invoke(scene.viewport, new Matrix4f().perspective((float)Math.toRadians(pose.fov()),
                target.width / (float)target.height, 8, 65_536));
        scene.modelView.invoke(scene.viewport, view);
        scene.camera.invoke(scene.viewport, pose.position().x, pose.position().y, pose.position().z);
        scene.size.invoke(scene.viewport, target.width, target.height);
        scene.update.invoke(scene.viewport);
        scene.frame.setInt(scene.viewport, scene.frame.getInt(scene.viewport) + 1);
        scene.clearDepth.invoke(scene.boundingDepth, 0);
        Object[][] prev = new Object[samplers.size()][timerFields.size()];
        for(int idx = 0; idx < samplers.size(); idx++){
            for(int col = 0; col < timerFields.size(); col++) prev[idx][col] = timerFields.get(col).get(samplers.get(idx));
            timerRunning.setBoolean(samplers.get(idx), false);
        }
        try{
            startTimer.invoke(mainTimer);
            scene.prepare.invoke(scene.pipeline, scene.viewport);
            scene.render.invoke(scene.pipeline, scene.viewport, target.frameBufferId, target.width, target.height);
        }finally{
            for(int idx = 0; idx < samplers.size(); idx++){
                for(int col = 0; col < timerFields.size(); col++) timerFields.get(col).set(samplers.get(idx), prev[idx][col]);
            }
        }
        return true;
    }

    // Release only this source's viewport and private pipeline; world geometry remains owned by Voxy
    void release(Object owner){
        Scene scene = scenes.remove(owner);
        if(scene == null) return;
        try{
            scene.deleteViewport.invoke(scene.viewport);
            scene.freePipeline.invoke(scene.pipeline);
        }catch(ReflectiveOperationException err){ throw new IllegalStateException("Unable to release camera Voxy buffers", err); }
    }

    @Override public void close(){ for(Object owner : List.copyOf(scenes.keySet())) release(owner); }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Find a cached implementation field across Voxy's renderer inheritance
    private static Object field(Object target, String name) throws ReflectiveOperationException{
        for(Class<?> type = target.getClass(); type != null; type = type.getSuperclass()){
            try{ Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(target); }
            catch(NoSuchFieldException ignored){}
        }
        throw new NoSuchFieldException(name);
    }

    // Resolve public erased contracts without importing the optional renderer's classes
    private static Method method(Class<?> type, String name, int count) throws NoSuchMethodException{
        for(Method method : type.getMethods()){
            if(method.getName().equals(name) && method.getParameterCount() == count){ method.setAccessible(true); return method; }
        }
        throw new NoSuchMethodException(type.getName() + "." + name);
    }

    // Retain per-camera occlusion history and framebuffer storage between refreshes
    private final class Scene{
        private final Object pipeline, viewport, boundingDepth;
        private final Method vanillaProjection, projection, modelView, camera, size, update, clearDepth, prepare, render, deleteViewport, freePipeline;
        private final Field frame;

        private Scene() throws ReflectiveOperationException{
            pipeline = pipelineConstructor.newInstance(nodes, cleaner, traversal, (BooleanSupplier)() -> false);
            method(pipeline.getClass(), "setupExtraModelBakeryData", 1).invoke(pipeline, model);
            Object renderer = createRenderer.invoke(factory, pipeline, getStore.invoke(model), geometry);
            method(pipeline.getClass(), "setSectionRenderer", 1).invoke(pipeline, renderer);
            viewport = method(renderer.getClass(), "createViewport", 0).invoke(renderer);
            Class<?> type = viewport.getClass();
            vanillaProjection = method(type, "setVanillaProjection", 1); projection = method(type, "setProjection", 1);
            modelView = method(type, "setModelView", 1); camera = method(type, "setCamera", 3); size = method(type, "setScreenSize", 2);
            update = method(type, "update", 0); deleteViewport = method(type, "delete", 0); frame = type.getField("frameId");
            boundingDepth = type.getField("depthBoundingBuffer").get(viewport); clearDepth = method(boundingDepth.getClass(), "clear", 1);
            prepare = method(pipeline.getClass(), "preSetup", 1); render = method(pipeline.getClass(), "runPipeline", 4);
            freePipeline = method(pipeline.getClass(), "free", 0);
        }
    }
}
