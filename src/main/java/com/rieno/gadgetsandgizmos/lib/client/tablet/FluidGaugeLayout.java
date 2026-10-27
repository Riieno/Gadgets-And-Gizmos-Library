package com.rieno.gadgetsandgizmos.lib.client.tablet;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Present shared-capacity fluid snapshots in descending amount order with contiguous gauge segments
public final class FluidGaugeLayout{
    private FluidGaugeLayout(){}

    public record FluidAmount(FluidStack fluid, long amount){}
    public record Segment(FluidAmount contents, int offset, int width){}

    // Snapshot rows use registry Id and Amount fields; group duplicate fluid IDs before sorting
    public static List<FluidAmount> contents(ListTag rows){
        Map<ResourceLocation, Long> amounts = new LinkedHashMap<>();
        if(rows == null) return List.of();
        for(int idx = 0; idx < rows.size(); idx++){
            var row = rows.getCompound(idx);
            var id = ResourceLocation.tryParse(row.getString("Id"));
            long amount = row.getLong("Amount");
            if(id == null || amount <= 0 || !BuiltInRegistries.FLUID.containsKey(id)
                    || BuiltInRegistries.FLUID.get(id) == Fluids.EMPTY) continue;
            amounts.merge(id, amount, (first, next) -> first > Long.MAX_VALUE - next ? Long.MAX_VALUE : first + next);
        }
        var fluids = new ArrayList<FluidAmount>();
        amounts.forEach((id, amount) -> fluids.add(new FluidAmount(new FluidStack(BuiltInRegistries.FLUID.get(id), 1), amount)));
        fluids.sort(Comparator.comparingLong(FluidAmount::amount).reversed()
                .thenComparing(entry -> BuiltInRegistries.FLUID.getKey(entry.fluid().getFluid()).toString()));
        return List.copyOf(fluids);
    }

    // Round cumulative boundaries so segments neither overlap nor multiply the filled capacity
    public static List<Segment> segments(List<FluidAmount> fluids, long capacity, int width){
        if(fluids == null || capacity <= 0 || width <= 0) return List.of();
        double total = 0;
        for(var entry : fluids) if(valid(entry)) total += entry.amount();
        if(total <= 0) return List.of();
        int filled = (int)Math.min(width, Math.max(1L, Math.round(Math.min(1D, total / capacity) * width)));
        List<Segment> segments = new ArrayList<>();
        double cumulative = 0;
        int start = 0;
        for(var entry : fluids){
            if(!valid(entry)) continue;
            cumulative += entry.amount();
            int end = Math.min(filled, Math.max(start, (int)Math.round(cumulative / total * filled)));
            segments.add(new Segment(entry, start, end - start));
            start = end;
        }
        return List.copyOf(segments);
    }

    private static boolean valid(FluidAmount entry){
        return entry != null && entry.fluid() != null && !entry.fluid().isEmpty() && entry.amount() > 0;
    }
}
