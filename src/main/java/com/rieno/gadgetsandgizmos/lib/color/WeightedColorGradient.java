package com.rieno.gadgetsandgizmos.lib.color;

import java.util.ArrayList;
import java.util.List;

// Give each color a share of a smooth gradient based on its stored amount
public record WeightedColorGradient(List<Color> colors){
    public record Color(int color, int amount){
        public Color{
            if(amount < 0) throw new IllegalArgumentException("Color amounts must be non-negative");
            color &= 0xFFFFFF;
        }
    }

    public record Band(double start, double end, int color){}

    public WeightedColorGradient{
        colors = colors.stream().filter(val -> val.amount > 0).toList();
    }

    // Omit absent colors and use the total stored amount as the full gradient length
    public List<Band> bands(){
        long total = 0, offset = 0;
        for(var val : colors) total += val.amount;
        var res = new ArrayList<Band>(colors.size());
        for(var val : colors){
            double start = offset / (double)total;
            offset += val.amount;
            res.add(new Band(start, offset / (double)total, val.color));
        }
        return List.copyOf(res);
    }

    // Keep each band centered on its own color and blend through the shared boundaries
    public int sample(double progress){
        var bands = bands();
        if(bands.isEmpty()) return 0;
        double val = Double.isFinite(progress) ? Math.clamp(progress, 0, 1) : 0;
        for(int idx = 0; idx < bands.size(); idx++){
            var band = bands.get(idx);
            if(val > band.end) continue;
            double middle = (band.start + band.end) * .5D;
            int prev = idx == 0 ? band.color : ColorGradient.blend(bands.get(idx - 1).color, band.color, .5F);
            int next = idx == bands.size() - 1 ? band.color : ColorGradient.blend(band.color, bands.get(idx + 1).color, .5F);
            return val <= middle
                    ? ColorGradient.blend(prev, band.color, (float)((val - band.start) / (middle - band.start)))
                    : ColorGradient.blend(band.color, next, (float)((val - middle) / (band.end - middle)));
        }
        return bands.getLast().color;
    }
}
