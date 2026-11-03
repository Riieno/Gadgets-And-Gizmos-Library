package com.rieno.gadgetsandgizmos.lib.client.tablet;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.inventory.InventoryMenu;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;
import com.rieno.gadgetsandgizmos.lib.client.render.GuiPoseScissor;

// Draw bounded fluid and FE fill levels for tablet resource snapshots
public final class TabletResourceGauge{
    private TabletResourceGauge(){}

    // Draw the actual fluid's still texture inside a proportional fill bar
    public static void fluid(GuiGraphics graphics, FluidStack stack, long amount, long capacity,
                             int x, int y, int width, int height){
        frame(graphics, x, y, width, height);
        if(stack == null || stack.isEmpty() || amount <= 0 || capacity <= 0) return;
        int fill = fillWidth(amount, capacity, width - 2);
        if(fill <= 0) return;
        var extension = IClientFluidTypeExtensions.of(stack.getFluid());
        var sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                .apply(extension.getStillTexture(stack));
        int tint = extension.getTintColor(stack);
        float red = ((tint >> 16) & 255) / 255.0F;
        float green = ((tint >> 8) & 255) / 255.0F;
        float blue = (tint & 255) / 255.0F;
        float alpha = ((tint >>> 24) & 255) == 0 ? 1.0F : ((tint >>> 24) & 255) / 255.0F;
<<<<<<< Updated upstream
        for(int offset = 0; offset < fill; offset += 16){
            int tile = Math.min(16, fill - offset);
            graphics.blit(x + 1 + offset, y + 1, 0, tile, height - 2, sprite, red, green, blue, alpha);
=======
        try(var clip = GuiPoseScissor.push(graphics, x, y, x + width, y + height)){
            for(int row = 0; row < height; row += 16) for(int col = 0; col < width; col += 16)
                graphics.blit(x + col, y + row, 0, 16, 16, sprite, red, green, blue, alpha);
        }
    }

    // Fill a host-painted frame without introducing another border or scaling fluid texels
    public static void fluidFill(GuiGraphics graphics, FluidStack stack, long amount, long capacity,
                                 int x, int y, int width, int height){
        if(stack == null || stack.isEmpty() || width <= 0 || height <= 0) return;
        int fill = fillWidth(amount, capacity, width);
        if(fill > 0) fluidTexture(graphics, stack, x, y, fill, height);
    }

    // Keep all segments in the host's painted interior at their shared capacity fraction
    public static void fluidsFill(GuiGraphics graphics, java.util.List<FluidGaugeLayout.FluidAmount> fluids, long capacity,
                                  int x, int y, int width, int height){
        if(width <= 0 || height <= 0) return;
        for(var segment : FluidGaugeLayout.segments(fluids, capacity, width)){
            if(segment.width() > 0) fluidTexture(graphics, segment.contents().fluid(), x + segment.offset(), y, segment.width(), height);
>>>>>>> Stashed changes
        }
    }

    // Draw FE with a red fill and bright upper edge
    public static void energy(GuiGraphics graphics, long amount, long capacity,
                              int x, int y, int width, int height){
        frame(graphics, x, y, width, height);
        int fill = fillWidth(amount, capacity, width - 2);
        if(fill <= 0) return;
        graphics.fill(x + 1, y + 1, x + 1 + fill, y + height - 1, 0xFFB82D3F);
        graphics.fill(x + 1, y + 1, x + 1 + fill, y + 3, 0xFFFF6771);
    }

    private static void frame(GuiGraphics graphics, int x, int y, int width, int height){
        if(width < 3 || height < 3) return;
        graphics.fill(x, y, x + width, y + height, 0xFF62748A);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xFF182530);
    }

    private static int fillWidth(long amount, long capacity, int width){
        if(width <= 0 || amount <= 0 || capacity <= 0) return 0;
        return (int) Math.min(width, Math.max(1L, Math.round((double) amount / capacity * width)));
    }
}
