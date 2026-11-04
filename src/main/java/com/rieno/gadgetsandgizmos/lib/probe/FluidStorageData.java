package com.rieno.gadgetsandgizmos.lib.probe;

import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import com.rieno.gadgetsandgizmos.lib.graph.GraphPortOrder;
import com.rieno.gadgetsandgizmos.lib.inventory.FluidSelection;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

// Expose shared-capacity fluid amounts and independent output/fuel selectors without NBT access
public final class FluidStorageData implements BlockEntityDataProvider{
    private static final Map<String, String> READABLE = GraphPortOrder.ports(
            "fluid_levels", "map", "fluid_fill_levels", "map", "fluid_amount", "number",
            "fluid_capacity", "number", "fluid_fill", "number", "pump_fluid", "string", "fuel_fluid", "string");
    private static final Map<String, String> WRITABLE = GraphPortOrder.ports("pump_fluid", "string", "fuel_fluid", "string");
    private final Supplier<? extends IFluidHandler> storage;
    private final LongSupplier capacity;
    private final Supplier<? extends Collection<? extends IFluidHandler>> sources;
    private final Supplier<String> fuel;
    private final Supplier<String> pump;
    private final BiConsumer<Boolean, String> select;
    private final LongSupplier clock;
    private long choicesTick;
    private List<String> storedChoices;

    public FluidStorageData(Supplier<? extends IFluidHandler> storage, LongSupplier capacity,
            Supplier<? extends Collection<? extends IFluidHandler>> sources, Supplier<String> fuel,
            Supplier<String> pump, BiConsumer<Boolean, String> select, LongSupplier clock){
        this.storage = storage;
        this.capacity = capacity;
        this.sources = sources;
        this.fuel = fuel;
        this.pump = pump;
        this.select = select;
        this.clock = clock;
    }

    @Override public Map<String, String> graphReadableData(){ return READABLE; }
    @Override public Map<String, String> graphWritableData(){ return WRITABLE; }

    // Cache network discovery for one game tick; current selections remain available even when empty
    private List<String> options(){
        long tick = clock.getAsLong();
        if(storedChoices == null || choicesTick != tick){
            var ids = new TreeSet<String>();
            ids.addAll(levels(storage.get()).keySet());
            for(var handler : sources.get()) ids.addAll(levels(handler).keySet());
            storedChoices = List.copyOf(ids);
            choicesTick = tick;
        }
        var ids = new TreeSet<>(storedChoices);
        if(!fuel.get().isBlank()) ids.add(fuel.get());
        if(!pump.get().isBlank()) ids.add(pump.get());
        var result = new java.util.ArrayList<String>();
        result.add(FluidSelection.ANY);
        result.addAll(ids);
        return List.copyOf(result);
    }

    @Override public Map<String, List<String>> graphWritableOptions(){
        var options = options();
        return Map.of("pump_fluid", options, "fuel_fluid", options);
    }

    @Override public long graphDataSchemaRevision(){
        long revision = 0xcbf29ce484222325L;
        for(String option : options()){
            for(int idx = 0; idx < option.length(); idx++) revision = (revision ^ option.charAt(idx)) * 0x100000001b3L;
            revision *= 0x100000001b3L;
        }
        return revision;
    }

    @Override public GraphValue readGraphValue(String field){
        if("fuel_fluid".equals(field)) return GraphValue.string(fuel.get());
        if("pump_fluid".equals(field)) return GraphValue.string(pump.get());
        if(!READABLE.containsKey(field)) return null;
        var levels = levels(storage.get());
        long size = Math.max(0L, capacity.getAsLong());
        double total = levels.values().stream().mapToDouble(Long::doubleValue).sum();
        return switch(field){
            case "fluid_levels" -> GraphValue.map(levels);
            case "fluid_fill_levels" -> {
                Map<String, Double> fills = new LinkedHashMap<>();
                levels.forEach((id, amount) -> fills.put(id, size == 0 ? 0D : Math.min(1D, (double)amount / size)));
                yield GraphValue.map(fills);
            }
            case "fluid_amount" -> GraphValue.number(total);
            case "fluid_capacity" -> GraphValue.number(size);
            case "fluid_fill" -> GraphValue.number(size == 0 ? 0D : Math.min(1D, total / size));
            default -> null;
        };
    }

    @Override public boolean writeGraphValue(String field, GraphValue value){
        if(!WRITABLE.containsKey(field) || value == null || !"string".equals(value.type())) return false;
        var id = FluidSelection.resolve(value.asString());
        boolean forFuel = "fuel_fluid".equals(field);
        if(id.isEmpty()) return false;
        if(id.get().equals(forFuel ? fuel.get() : pump.get())) return true;
        select.accept(forFuel, id.get());
        return true;
    }

    // Aggregate component variants by fluid ID without applying the entry block's extraction policy
    private static Map<String, Long> levels(IFluidHandler handler){
        Map<String, Long> levels = new TreeMap<>();
        if(handler == null) return levels;
        for(int idx = 0; idx < handler.getTanks(); idx++){
            var stack = handler.getFluidInTank(idx);
            if(!stack.isEmpty()) levels.merge(BuiltInRegistries.FLUID.getKey(stack.getFluid()).toString(),
                    (long)stack.getAmount(), Long::sum);
        }
        return levels;
    }
}
