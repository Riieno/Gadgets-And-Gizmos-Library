package com.rieno.gadgetsandgizmos.lib.client.view;

import com.rieno.gadgetsandgizmos.lib.view.ViewReference;
import com.rieno.gadgetsandgizmos.lib.client.render.HologramRenderScope;

import java.util.LinkedHashSet;
import java.util.Set;

// Keep a cached GUI's displayed feeds active between its own surface redraws
public final class ViewSceneRequests{
    private static ViewSceneRequests active;
    private final Set<ViewReference> sources = new LinkedHashSet<>();

    // Replace the cached demand with only the feeds drawn by this GUI frame
    public void record(Runnable draw){
        ViewSceneRequests prev = active;
        sources.clear();
        active = this;
        try{
            draw.run();
        }finally{
            active = prev;
        }
    }

    // Call once per player frame only while the GUI's presenting surface is visible
    public void refresh(){ if(!HologramRenderScope.isActive()) sources.forEach(ViewSceneRenderer::keepVisible); }

    // Retain a frozen surface's textures without scheduling captures
    public void retain(){ if(!HologramRenderScope.isActive()) sources.forEach(ViewSceneRenderer::keepRetained); }

    // Capture demand without exposing the renderer's allocations to GUI owners
    static void requested(ViewReference ref){
        if(active != null) active.sources.add(ref);
    }
}
