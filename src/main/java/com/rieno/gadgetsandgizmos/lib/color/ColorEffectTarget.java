package com.rieno.gadgetsandgizmos.lib.color;

// Apply a reusable preset to content without coupling the library to its block entity
public interface ColorEffectTarget{
    default boolean acceptsColorEffect(){ return true; }
    default ColorEffectTarget colorEffectTarget(){ return this; }
    ColorEffectPreset colorEffect();
    void applyColorEffect(ColorEffectPreset preset);
}
