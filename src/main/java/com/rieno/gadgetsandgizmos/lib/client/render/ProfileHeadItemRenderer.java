package com.rieno.gadgetsandgizmos.lib.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rieno.gadgetsandgizmos.lib.item.ProfileHeadItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

// Render profile-backed items through Minecraft's normal player head renderer
public final class ProfileHeadItemRenderer extends BlockEntityWithoutLevelRenderer{
    public ProfileHeadItemRenderer(){
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    // Create the client extension after Minecraft's render services become available
    public static IClientItemExtensions extensions(){
        return new IClientItemExtensions(){
            private ProfileHeadItemRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer(){
                if(renderer == null) renderer = new ProfileHeadItemRenderer();
                return renderer;
            }
        };
    }

    // Draw the resolved player head in every item display context
    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack pose,
                             MultiBufferSource buf, int light, int overlay){
        if(!(stack.getItem() instanceof ProfileHeadItem item)) return;
        Minecraft.getInstance().getItemRenderer().getBlockEntityRenderer()
                .renderByItem(item.headStack(stack), ctx, pose, buf, light, overlay);
    }
}
