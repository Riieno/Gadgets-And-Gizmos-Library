package com.rieno.gadgetsandgizmos.lib.color;

import java.util.Comparator;
import java.util.List;

// Order visual fluid layers from the densest at the bottom to the lightest at the surface
public record DensityColorPalette(List<Layer> layers){
    public record Layer(int color, int amount, float density){
        public Layer{
            if(amount < 0 || !Float.isFinite(density)) throw new IllegalArgumentException("Fluid amounts must be non-negative and density finite");
            color &= 0xFFFFFF;
        }
    }

    public DensityColorPalette{
        if(layers.size() > 16) throw new IllegalArgumentException("A fluid preview accepts at most sixteen layers");
        layers = layers.stream().filter(val -> val.amount > 0)
                .sorted(Comparator.comparingDouble(Layer::density).reversed()).toList();
        if(layers.stream().map(Layer::color).distinct().count() != layers.size()){
            throw new IllegalArgumentException("Fluid layers must have distinct colours");
        }
    }

    public WeightedColorGradient gradient(){
        return new WeightedColorGradient(layers.stream().map(val -> new WeightedColorGradient.Color(val.color, val.amount)).toList());
    }

    // Dense layers resist visual mixing more strongly than light layers
    public float mobility(int color){
        if(layers.size() < 2) return 1;
        double low = layers.getLast().density, range = layers.getFirst().density - low;
        for(var layer : layers){
            if(layer.color == (color & 0xFFFFFF)) return range > 0 ? (float)(1 - .55D * (layer.density - low) / range) : 1;
        }
        return 1;
    }

    // Mix with neighboring layers while respecting the amount of dye in each neighbor
    public int mixedColor(int color){
        for(int idx = 0; idx < layers.size(); idx++){
            if(layers.get(idx).color != (color & 0xFFFFFF)) continue;
            long red = 0, green = 0, blue = 0, total = 0;
            for(int neighbor = Math.max(0, idx - 1); neighbor <= Math.min(layers.size() - 1, idx + 1); neighbor++){
                var val = layers.get(neighbor);
                red += (long)(val.color >> 16 & 255) * val.amount;
                green += (long)(val.color >> 8 & 255) * val.amount;
                blue += (long)(val.color & 255) * val.amount;
                total += val.amount;
            }
            return (int)(red / total) << 16 | (int)(green / total) << 8 | (int)(blue / total);
        }
        return color & 0xFFFFFF;
    }
}
