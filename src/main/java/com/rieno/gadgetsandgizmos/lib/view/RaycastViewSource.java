package com.rieno.gadgetsandgizmos.lib.view;

// Let a controlled lens expose an explicit sensor sample on the owning server thread
public interface RaycastViewSource extends ViewSource{
    ViewRaycast.Result castViewRay();
    // Share sensor reach and filtering with native client interaction handlers
    default double interactionRange(){ return 64; }
    default ViewRaycast.Filter interactionFilter(){ return new ViewRaycast.Filter(java.util.Set.of(), false); }
}
