package com.rieno.gadgetsandgizmos.lib.client.tablet;

import com.rieno.gadgetsandgizmos.lib.client.render.GuiTextureRegion;
import com.rieno.gadgetsandgizmos.lib.client.ui.GuiControlPreviews;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

// Draw the same cargo card in container manifests and embedded resource browsers
public final class PaintedItemCard{
    public static final int WIDTH = 72;
    public static final int HEIGHT = 66;
    public static final int ROW_HEIGHT = 67;
    public static final int COLUMNS = 4;
    private final GuiTextureRegion frame;

    public PaintedItemCard(ResourceLocation atlas){ frame = new GuiTextureRegion(atlas, 320, 400, 0, 334, WIDTH, HEIGHT); }

    public void draw(TabletAppClientContext ctx, ItemStack stack, String name, long count, int x, int y){
        frame.draw(ctx.graphics(), x, y);
        if(!stack.isEmpty()) ctx.graphics().renderItem(stack, x + 21, y + 9);
        GuiControlPreviews.drawFittedText(ctx.graphics(), ctx.font(), Component.literal("X" + count),
                x + 46, y + 6, 23, 22, 1, 0xFFECECEC, false);
        GuiControlPreviews.drawFittedWrappedText(ctx.graphics(), ctx.font(), Component.literal(name),
                x + 9, y + 44, 47, 14, .85F, 2, 0xFFECECEC, false);
    }
}
