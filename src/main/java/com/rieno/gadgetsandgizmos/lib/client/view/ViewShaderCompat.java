package com.rieno.gadgetsandgizmos.lib.client.view;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.blaze3d.platform.GlStateManager;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;

// Supply a secondary Iris pipeline without rebuilding shader packs or changing mesh formats
public final class ViewShaderCompat{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Map<Class<?>, Object> PIPELINES = new HashMap<>();
    private static final Map<Class<?>, Object> DEFAULTS = new HashMap<>();
    private static Field vertexLayout;
    private static boolean vertexLayoutChecked;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Prevent construction of the optional shader adapter
    private ViewShaderCompat(){}
    // Upload the layout carried by the mesh even if shaders changed during compilation
    public static void withMeshFormat(Runnable upload){
        if(!vertexLayoutChecked){
            vertexLayoutChecked = true;
            try{
                Class<?> type = Class.forName("net.irisshaders.iris.vertices.ImmediateState");
                vertexLayout = type.getField("renderWithExtendedVertexFormat");
            }catch(ClassNotFoundException err){
                vertexLayout = null;
            }catch(NoSuchFieldException err){
                throw new IllegalStateException("Iris vertex layout contract is unavailable", err);
            }
        }
        if(vertexLayout == null){
            upload.run();
            return;
        }
        try{
            boolean prev = vertexLayout.getBoolean(null);
            vertexLayout.setBoolean(null, false);
            try{
                upload.run();
            }finally{
                vertexLayout.setBoolean(null, prev);
            }
        }catch(IllegalAccessException err){
            throw new IllegalStateException("Iris vertex layout could not be restored", err);
        }
    }
    // Use Iris's unshaded contract without invoking its settings-changing constructor
    public static Object capturePipeline(Object original){
        try{
            Class<?> contract = Class.forName("net.irisshaders.iris.pipeline.WorldRenderingPipeline",
                    false, original.getClass().getClassLoader());
            return PIPELINES.computeIfAbsent(contract, type -> Proxy.newProxyInstance(type.getClassLoader(),
                    new Class<?>[]{type}, (proxy, method, args) -> invoke(proxy, method, args)));
        }catch(ClassNotFoundException err){
            throw new IllegalStateException("Iris world pipeline contract is unavailable", err);
        }
    }
    // Decode Iris terrain for the plain feed shader without changing the player's mesh settings
    public static String terrainShader(ResourceLocation name, String src){
        if(!ViewSceneRenderer.isCapturing() || !"sodium".equals(name.getNamespace())
                || !"include/chunk_vertex.glsl".equals(name.getPath())) return src;
        try{
            Class<?> type = Class.forName("net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings");
            Object settings = type.getField("INSTANCE").get(null);
            Object format = type.getMethod("getVertexFormat").invoke(settings);
            if(!format.getClass().getName().contains("XHFPModelVertexType")) return src;
            // Iris stores uncentred light coordinates and tangent handedness in the material byte
            src = src.replace("_vert_tex_light_coord = vec2(a_LightAndData.xy) / vec2(256.0);",
                    "_vert_tex_light_coord = (vec2(a_LightAndData.xy) + vec2(8.0)) / vec2(256.0);")
                    .replace("_material_params = a_LightAndData[2];", "_material_params = 3u;");
            // Apply separate ambient occlusion to colour while keeping cutout opacity independent
            if((boolean) type.getMethod("shouldUseSeparateAo").invoke(settings)){
                src = src.replace("_vert_color = a_Color;", "_vert_color = vec4(a_Color.rgb * a_Color.a, 1.0);");
            }
        }catch(ClassNotFoundException err){
            return src;
        }catch(ReflectiveOperationException err){
            throw new IllegalStateException("Iris terrain layout is unavailable", err);
        }
        return src;
    }
    // Keep every shader pass and shadow draw outside secondary captures
    private static Object invoke(Object proxy, Method method, Object[] args) throws ReflectiveOperationException{
        String name = method.getName();
        if("equals".equals(name)) return proxy == args[0];
        if("hashCode".equals(name)) return System.identityHashCode(proxy);
        if("toString".equals(name)) return "Gadgets & Gizmos display capture";
        if("beginLevelRendering".equals(name)){
            ViewSceneRenderer.captureTarget().bindWrite(true);
            GlStateManager._glUseProgram(0);
        }
        Class<?> type = method.getReturnType();
        if(type == void.class) return null;
        if(type == boolean.class){
            return "shouldDisableVanillaEntityShadows".equals(name)
                    || name.startsWith("shouldRender") && !name.contains("Weather");
        }
        if(type == int.class) return 0;
        if(type == float.class) return 0.0F;
        if(type == double.class) return 0.0D;
        if(type == long.class) return 0L;
        if(type == OptionalInt.class) return OptionalInt.empty();
        if(type.isEnum()){
            String selected = "getPhase".equals(name) ? "NONE"
                    : "getParticleRenderingSettings".equals(name) ? "MIXED" : "DEFAULT";
            for(Object val : type.getEnumConstants()) if(((Enum<?>) val).name().equals(selected)) return val;
        }
        if(Map.class.isAssignableFrom(type)) return Object2ObjectMaps.emptyMap();
        if("getFrameUpdateNotifier".equals(name)){
            Object notifier = DEFAULTS.get(type);
            if(notifier == null){ notifier = type.getConstructor().newInstance(); DEFAULTS.put(type, notifier); }
            return notifier;
        }
        return null;
    }
}
