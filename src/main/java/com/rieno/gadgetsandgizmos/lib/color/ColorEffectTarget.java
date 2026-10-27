package com.rieno.gadgetsandgizmos.lib.color;

// Apply a reusable preset to content without coupling the library to its block entity
public interface ColorEffectTarget{
    ColorEffectPreset colorEffect();
    void applyColorEffect(ColorEffectPreset preset);
}
