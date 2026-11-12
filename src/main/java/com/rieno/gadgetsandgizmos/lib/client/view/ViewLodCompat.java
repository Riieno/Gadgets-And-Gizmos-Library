package com.rieno.gadgetsandgizmos.lib.client.view;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import com.mojang.logging.LogUtils;
import org.joml.Matrix4fc;
import org.slf4j.Logger;

// Scope optional distant-terrain rendering and player state to secondary camera scenes
public final class ViewLodCompat{
    private static final List<Field> FIELDS = new ArrayList<>();
    private static Object state;
    private static boolean checked;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static BooleanSupplier enabled = () -> false;
    private static DistantHorizonFeeds distantHorizons;
    private static VoxyFeeds voxy;
    private static Object voxySystem;
    private static boolean dhFailed, voxyFailed, wasEnabled;
    private static long renderedPasses;
    private static String lastFailure = "";
    private static final java.util.Map<Object, ViewLodLayer> LAYERS = new java.util.IdentityHashMap<>();

    // Prevent construction of the optional integration
    private ViewLodCompat(){}

    // Let the owning client choose whether additional LOD capture work is allowed
    public static void setEnabled(BooleanSupplier supplier){ enabled = java.util.Objects.requireNonNull(supplier); }

    // Release optional GPU buffers when the client disables LOD feeds
    public static boolean isEnabled(){
        boolean val = enabled.getAsBoolean();
        if(wasEnabled && !val){
            LAYERS.values().forEach(ViewLodLayer::close); LAYERS.clear();
            ViewLodLayer.clear();
            if(distantHorizons != null) distantHorizons.close();
            if(voxy != null) voxy.close();
            distantHorizons = null; voxy = null;
            dhFailed = voxyFailed = false;
        }
        wasEnabled = val;
        return val;
    }

    // Retain the installed Voxy system without depending on a particular loader's client holder
    public static void observeVoxy(Object system){
        if(voxySystem == system) return;
        if(voxy != null) voxy.close();
        voxy = null; voxySystem = system; voxyFailed = false;
    }

    // Stop retaining an optional renderer before it frees the terrain shared by feeds
    public static void retireVoxy(Object system){ if(voxySystem == system) observeVoxy(null); }

    // Draw one optional backend using the capture's matrices and retained GPU targets
    static void render(Object owner, Matrix4fc view, Matrix4fc projection, float partialTick){
        if(!isEnabled()) return;
        Runnable restore = ViewLodGpuState.save();
        try{
            LAYERS.computeIfAbsent(owner, val -> new ViewLodLayer()).render(view, projection,
                    () -> draw(owner, view, projection, partialTick));
        }finally{ restore.run(); }
    }

    // Populate only the distant layer when its independent refresh budget allows it
    private static boolean draw(Object owner, Matrix4fc view, Matrix4fc projection, float partialTick){
        if(voxySystem != null && !voxyFailed){
            try{
                if(voxy == null) voxy = new VoxyFeeds(voxySystem);
                if(voxy.render(owner, view, projection)) renderedPasses++;
                return true;
            }catch(ReflectiveOperationException | RuntimeException | LinkageError err){
                voxyFailed = true;
                lastFailure = err.toString();
                LOGGER.warn("Camera Voxy LOD rendering is unavailable; retaining normal terrain", err);
            }
        }
        if(dhFailed) return false;
        try{
            if(distantHorizons == null) distantHorizons = DistantHorizonFeeds.create();
            if(distantHorizons != null && distantHorizons.render(owner, view, projection, partialTick)){ renderedPasses++; return true; }
        }catch(ReflectiveOperationException | RuntimeException | LinkageError err){
            dhFailed = true;
            lastFailure = err.toString();
            LOGGER.warn("Camera Distant Horizons LOD rendering is unavailable; retaining normal terrain", err);
        }
        return false;
    }

    // Retire only the resources owned by the source whose capture is closing
    static void release(Object owner){
        ViewLodLayer layer = LAYERS.remove(owner);
        if(layer != null) layer.close();
        if(distantHorizons != null) distantHorizons.release(owner);
        if(voxy != null) voxy.release(owner);
    }

    // Forget the old world's optional terrain owners before its GPU storage is retired
    static void clear(){
        LAYERS.values().forEach(ViewLodLayer::close); LAYERS.clear(); ViewLodLayer.clear();
        if(distantHorizons != null) distantHorizons.close();
        if(voxy != null) voxy.close();
        distantHorizons = null; voxy = null; voxySystem = null;
        dhFailed = voxyFailed = false;
    }

    // Resolve the shared render-state contract without requiring Distant Horizons to be installed
    private static void resolve(){
        if(checked) return;
        checked = true;
        try{
            Class<?> api = Class.forName("com.seibel.distanthorizons.core.api.internal.ClientApi", false,
                    ViewLodCompat.class.getClassLoader());
            try{ state = api.getField("RENDER_STATE").get(null); }
            catch(NoSuchFieldException err){ return; }
            if(state == null) return;
            for(Class<?> type = state.getClass(); type != Object.class; type = type.getSuperclass()){
                for(Field field : type.getDeclaredFields()){
                    if(Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())) continue;
                    field.setAccessible(true);
                    FIELDS.add(field);
                }
            }
        }catch(ClassNotFoundException err){
            state = null;
        }catch(ReflectiveOperationException err){
            throw new IllegalStateException("Unable to retain Distant Horizons render state", err);
        }
    }

    // Restore retained matrix values and level references after optional LOD hooks run
    public static Runnable save(){
        resolve();
        if(state == null || FIELDS.isEmpty()) return () -> {};
        Object[] values = new Object[FIELDS.size()];
        try{
            for(int idx = 0; idx < values.length; idx++){
                Object val = FIELDS.get(idx).get(state);
                values[idx] = val != null && val.getClass().getName().endsWith("DhMat4f")
                        ? val.getClass().getMethod("copy").invoke(val) : val;
            }
        }catch(ReflectiveOperationException err){
            throw new IllegalStateException("Unable to read Distant Horizons render state", err);
        }
        return () -> {
            try{
                for(int idx = 0; idx < values.length; idx++) FIELDS.get(idx).set(state, values[idx]);
            }catch(IllegalAccessException err){
                throw new IllegalStateException("Unable to restore Distant Horizons render state", err);
            }
        };
    }
}
