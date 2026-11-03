package com.rieno.gadgetsandgizmos.lib.client.render;

import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

// Draw a block's item model, fluid bucket or state model without creating a block entity
public final class GuiBlockPreview{
    private GuiBlockPreview(){}

    public static void render(GuiGraphics graphics, BlockState state, int x, int y, int size){
        if(size <= 0 || state == null || state.isAir()) return;
        ItemStack stack = new ItemStack(state.getBlock().asItem());
        if(stack.isEmpty() && !state.getFluidState().isEmpty()) stack = new ItemStack(state.getFluidState().getType().getBucket());
        graphics.pose().pushPose();
        try{
            if(!stack.isEmpty()){
                graphics.pose().translate(x, y, 0);
                graphics.pose().scale(size / 16F, size / 16F, 1);
                graphics.renderItem(stack, 0, 0);
            }else{
                graphics.pose().translate(x + size / 2F, y + size / 2F, 100);
                graphics.pose().mulPose(Axis.XP.rotationDegrees(25));
                graphics.pose().mulPose(Axis.YP.rotationDegrees(-45));
                graphics.pose().scale(size * .65F, -size * .65F, size * .65F);
                graphics.pose().translate(-.5, -.5, -.5);
                Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state, graphics.pose(), graphics.bufferSource(),
                        LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                graphics.flush();
            }
        }finally{ graphics.pose().popPose(); }
    }
}
