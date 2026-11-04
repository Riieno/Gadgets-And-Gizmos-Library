package com.rieno.gadgetsandgizmos.lib.inventory;

// Expose physical storage separately from an automation capability that routes across a network
public interface LocalResourceStorage{
    // Return only this container's complete local multiblock storage, never a network facade
    ResourceRouting.Pool localResources();
}
