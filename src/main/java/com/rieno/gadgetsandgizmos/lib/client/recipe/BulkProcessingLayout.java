package com.rieno.gadgetsandgizmos.lib.client.recipe;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.state.BlockState;

// Share a bulk-processing recipe layout and powered block preview across viewers
public final class BulkProcessingLayout{
    public static final int WIDTH = 192;
    public static final int HEIGHT = 80;
    public static final int INPUT_X = 21;
    public static final int INPUT_Y = 48;

    private BulkProcessingLayout(){}

    // Align one result or a grid of processing results
    public static int outputX(int count, int idx){
        return count == 1 ? 156 : 118 + idx % 4 * 18;
    }

    public static int outputY(int count, int idx){
        return count == 1 ? 48 : 12 + idx / 4 * 18;
    }

    // Render the supplied block state without a block entity or particle effects
    public static void render(GuiGraphics graphics, int left, int top, BlockState state){
        AllGuiTextures.JEI_SHADOW.render(graphics, left + 55, top + 34);
        AllGuiTextures.JEI_LONG_ARROW.render(graphics, left + 54, top + 52);
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(left + 86.0D, top + 43.0D, 200.0D);
        pose.mulPose(Axis.XP.rotationDegrees(25.0F));
        pose.mulPose(Axis.YP.rotationDegrees(-45.0F));
        pose.scale(29.0F, -29.0F, 29.0F);
        pose.translate(-0.5D, -0.5D, -0.5D);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state, pose, graphics.bufferSource(),
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        graphics.flush();
        pose.popPose();
    }
}
