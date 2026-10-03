package com.rieno.gadgetsandgizmos.lib.client.tablet;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.client.ui.LayeredItemRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.function.IntConsumer;

// Browse paged network cargo with selectable items and fluid or FE gauges
public final class TabletInventoryView{
    private int offset;
    private int visible = 1;
    private ResourceLocation selected;

    public int offset(){ return offset; }
    public ResourceLocation selected(){ return selected; }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Reserve the supplied height for the browser, leaving request controls to its host
    public void render(TabletAppClientContext ctx, TabletAppPanel panel, CompoundTag data, int height, IntConsumer refresh){
        visible = Math.max(1, (height - 23) / 29);
        int total = data.getInt("Total");
        offset = Mth.clamp(offset, 0, Math.max(0, total - visible));
        panel.text(ctx, data.getInt("Containers") + " containers / " + total + " resources", 5, 5, ctx.width() - 91, 0xFFCDE7C5);
        panel.button(ctx, ctx.width() - 80, 0, 35, 19, "Up", offset > 0, () -> move(data, -visible, refresh));
        panel.button(ctx, ctx.width() - 41, 0, 36, 19, "Down", offset + visible < total, () -> move(data, visible, refresh));
        var rows = data.getList("Rows", Tag.TAG_COMPOUND);
        int start = offset - data.getInt("Offset");
        if(total == 0){
            panel.wrap(ctx, "No accessible cargo. Linked containers must be loaded.", 7, 30, ctx.width() - 14, height - 30);
            return;
        }
        if(start < 0 || start >= rows.size()){
            panel.text(ctx, "Loading inventory...", 7, 30, ctx.width() - 14, 0xFFB8CBE0);
            return;
        }
        for(int idx = 0; idx < visible && start + idx < rows.size(); idx++){
            var row = rows.getCompound(start + idx);
            int y = 23 + idx * 29;
            ResourceLocation id = ResourceLocation.tryParse(row.getString("Id"));
            if(id == null) continue;
            long amount = row.getLong("Amount");
            long capacity = row.getLong("Capacity");
            if("item".equals(row.getString("Type"))){
                panel.button(ctx, 5, y, ctx.width() - 15, 26, "", true, () -> selected = id);
                LayeredItemRenderer.renderVisible(ctx.graphics(), new ItemStack(BuiltInRegistries.ITEM.get(id)), ctx.left() + 9, ctx.top() + y + 5);
                panel.text(ctx, row.getString("Name"), 30, y + 3, ctx.width() - 49, id.equals(selected) ? 0xFFA9E68D : 0xFFEAF3FC);
                panel.text(ctx, "x " + amount + " / " + id, 30, y + 15, ctx.width() - 49, 0xFFB8CBE0);
            }else{
                boolean energy = "energy".equals(row.getString("Type"));
                panel.text(ctx, row.getString("Name") + " / " + amount + " / " + capacity + (energy ? " FE" : " mB"),
                        7, y + 2, ctx.width() - 24, 0xFFEAD19A);
                if(energy) TabletResourceGauge.energy(ctx.graphics(), amount, capacity, ctx.left() + 7, ctx.top() + y + 15, ctx.width() - 23, 9);
                else{
                    var fluid = BuiltInRegistries.FLUID.getOptional(id).orElse(Fluids.EMPTY);
                    FluidStack stack = fluid == Fluids.EMPTY ? FluidStack.EMPTY : new FluidStack(fluid, 1);
                    TabletResourceGauge.fluid(ctx.graphics(), stack, amount, capacity, ctx.left() + 7, ctx.top() + y + 15, ctx.width() - 23, 9);
                }
            }
        }
        if(total > visible){
            int track = visible * 29;
            int thumb = Math.max(6, track * visible / total);
            int y = 23 + (int) ((long) (track - thumb) * offset / Math.max(1, total - visible));
            ctx.graphics().fill(ctx.left() + ctx.width() - 6, ctx.top() + y, ctx.left() + ctx.width() - 3, ctx.top() + y + thumb, 0xFF8ABD7A);
        }
    }

    // Ask the host for another page only when the visible start changes
    public void move(CompoundTag data, int delta, IntConsumer refresh){
        int next = Mth.clamp(offset + delta, 0, Math.max(0, data.getInt("Total") - visible));
        if(next == offset) return;
        offset = next;
        refresh.accept(offset);
    }

    public CompoundTag save(){
        CompoundTag data = new CompoundTag();
        data.putInt("Offset", offset);
        if(selected != null) data.putString("Selected", selected.toString());
        return data;
    }

    public void load(CompoundTag data){
        offset = Math.max(0, data.getInt("Offset"));
        selected = ResourceLocation.tryParse(data.getString("Selected"));
    }
}
