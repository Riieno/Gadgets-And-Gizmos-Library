package com.rieno.gadgetsandgizmos.lib.inventory;

// Expose an explicitly selected fuel view independently of ordinary cargo and pump output
public interface FuelResourceStorage{
    ResourceRouting.Pool fuelResources();
}
