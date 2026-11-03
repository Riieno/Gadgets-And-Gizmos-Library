package com.rieno.gadgetsandgizmos.lib.tablet;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

// Keep card appearance separate from its live control state
public record ControlCardStyle(Size size, int color, ResourceLocation icon, ClockCardSettings clock){
    public ControlCardStyle(Size size, int color, ResourceLocation icon){ this(size, color, icon, ClockCardSettings.DEFAULT); }
    public ControlCardStyle{
        size = size == null ? Size.SINGLE : size;
        color &= 0xFFFFFF;
        icon = icon == null || icon.getPath().isBlank() ? ResourceLocation.withDefaultNamespace("redstone") : icon;
        clock = clock == null ? ClockCardSettings.DEFAULT : clock;
    }

    public enum Size{
        SINGLE(1, 1, "Single"), WIDE(2, 1, "Wide"), TALL(1, 2, "Tall");
        public final int columns, rows;
        public final String label;
        Size(int columns, int rows, String label){ this.columns = columns; this.rows = rows; this.label = label; }
        public static Size fromId(String id){
            for(var size : values()) if(size.name().equalsIgnoreCase(id)) return size;
            return SINGLE;
        }
    }

    public CompoundTag toTag(){
        var tag = new CompoundTag();
        tag.putString("Size", size.name().toLowerCase(java.util.Locale.ROOT));
        tag.putInt("Color", color);
        tag.putString("Icon", icon.toString());
        tag.put("Clock", clock.toTag());
        return tag;
    }

    public static ControlCardStyle fromTag(CompoundTag tag){
        return new ControlCardStyle(Size.fromId(tag.getString("Size")), tag.contains("Color") ? tag.getInt("Color") : 0x303030,
                ResourceLocation.tryParse(tag.getString("Icon")), ClockCardSettings.fromTag(tag.getCompound("Clock")));
    }
}
