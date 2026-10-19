package com.rieno.gadgetsandgizmos.lib.client.render;

// Allow an entity or render proxy to suppress its nameplate independently of its renderer
public interface NameplateVisibility{
    // Return false to hide the nameplate; true retains the renderer's normal visibility rules
    boolean isNameplateVisible();
}
