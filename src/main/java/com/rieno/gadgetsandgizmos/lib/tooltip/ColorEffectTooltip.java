package com.rieno.gadgetsandgizmos.lib.tooltip;

import com.rieno.gadgetsandgizmos.lib.color.ColorEffectPreset;
import com.rieno.gadgetsandgizmos.lib.inventory.PaletteDyeStorage;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

// Carry a saved color effect and ingredient counts without loading client classes
public record ColorEffectTooltip(ColorEffectPreset preset, List<StoredItem> ingredients) implements TooltipComponent{
    public record StoredItem(Item item, int count){
        public StoredItem{
            Objects.requireNonNull(item);
            count = Math.max(0, count);
        }
    }

    public ColorEffectTooltip{
        Objects.requireNonNull(preset);
        ingredients = List.copyOf(ingredients);
    }

    // Snapshot every palette slot, including empty dyes and the glow ink slot
    public static ColorEffectTooltip fromPalette(ColorEffectPreset preset, PaletteDyeStorage storage){
        var ingredients = new ArrayList<StoredItem>(PaletteDyeStorage.SLOTS);
        for(int slot = 0; slot < PaletteDyeStorage.SLOTS; slot++){
            ingredients.add(new StoredItem(PaletteDyeStorage.slotItem(slot), storage.count(slot)));
        }
        return new ColorEffectTooltip(preset, ingredients);
    }

    // Solid and default colors stay static while cycles always animate
    public boolean animationActive(){
        return preset.mode() == ColorEffectPreset.Mode.CYCLE
                || preset.mode() == ColorEffectPreset.Mode.GRADIENT && preset.animated();
    }
}
