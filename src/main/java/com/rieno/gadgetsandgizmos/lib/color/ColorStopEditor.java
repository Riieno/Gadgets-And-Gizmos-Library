package com.rieno.gadgetsandgizmos.lib.color;

import java.util.ArrayList;

// Shared gradient editing rules keep endpoints, selection and minimum stop counts consistent
public final class ColorStopEditor{
    private ColorEffectPreset preset;
    private int selected = 1;
    public ColorStopEditor(ColorEffectPreset preset){ this.preset = preset; }
    public ColorEffectPreset preset(){ return preset; }
    public int selected(){ return selected; }
    public void select(int index){ selected = Math.clamp(index, 0, preset.stops().size() - 1); }
    public void mode(ColorEffectPreset.Mode mode){ preset = preset.withMode(mode); }
    public void animated(boolean value){ preset = preset.withAnimated(value); }
    public void additive(boolean value){ preset = preset.withAdditive(value); }
    public void reset(){ preset = ColorEffectPreset.DEFAULT; selected = 1; }
    public void color(int rgb){
        if(!preset.usesStops()){ preset = preset.withMode(ColorEffectPreset.Mode.SOLID).withColor(rgb); return; }
        var stops = new ArrayList<>(preset.stops());
        stops.set(selected, new ColorEffectPreset.Stop(stops.get(selected).position(), rgb));
        preset = new ColorEffectPreset(preset.mode(), preset.color(), stops, preset.animated(), preset.additive());
    }
    public boolean add(){
        if(preset.stops().size() >= ColorEffectPreset.MAX_STOPS) return false;
        int gap = 0;
        float size = -1;
        for(int idx = 0; idx < preset.stops().size() - 1; idx++){
            float distance = preset.stops().get(idx + 1).position() - preset.stops().get(idx).position();
            if(distance > size){ gap = idx; size = distance; }
        }
        var stops = new ArrayList<>(preset.stops());
        float position = (stops.get(gap).position() + stops.get(gap + 1).position()) / 2;
        selected = gap + 1;
        stops.add(selected, new ColorEffectPreset.Stop(position, preset.samplePosition(position)));
        preset = new ColorEffectPreset(preset.mode(), preset.color(), stops, preset.animated(), preset.additive());
        return true;
    }
    public boolean remove(){
        if(preset.stops().size() <= ColorEffectPreset.MIN_STOPS || selected == 0 || selected == preset.stops().size() - 1) return false;
        var stops = new ArrayList<>(preset.stops());
        stops.remove(selected);
        selected = Math.min(selected, stops.size() - 2);
        preset = new ColorEffectPreset(preset.mode(), preset.color(), stops, preset.animated(), preset.additive());
        return true;
    }
    public boolean move(float position){
        if(selected == 0 || selected == preset.stops().size() - 1 || !Float.isFinite(position)) return false;
        var stops = new ArrayList<>(preset.stops());
        float previous = stops.get(selected - 1).position(), next = stops.get(selected + 1).position();
        float margin = Math.min(.001F, (next - previous) / 4);
        float value = Math.clamp(position, previous + margin, next - margin);
        stops.set(selected, new ColorEffectPreset.Stop(value, stops.get(selected).color()));
        preset = new ColorEffectPreset(preset.mode(), preset.color(), stops, preset.animated(), preset.additive());
        return true;
    }
}
