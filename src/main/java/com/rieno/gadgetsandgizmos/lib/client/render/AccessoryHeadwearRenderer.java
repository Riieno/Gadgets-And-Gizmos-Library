package com.rieno.gadgetsandgizmos.lib.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;

// Render visible accessory headwear from its own stack independently of the armor slot
public final class AccessoryHeadwearRenderer{
    private final ResourceLocation texture;
    private final Supplier<PartialModel> hat;
    private final ToDoubleFunction<ItemStack> offset;
    private HumanoidModel<LivingEntity> armor;

    private AccessoryHeadwearRenderer(ResourceLocation texture, Supplier<PartialModel> hat,
                                   ToDoubleFunction<ItemStack> offset){
        this.texture = texture;
        this.hat = hat;
        this.offset = offset;
    }

    public static AccessoryHeadwearRenderer armor(ResourceLocation texture, ToDoubleFunction<ItemStack> offset){
        return new AccessoryHeadwearRenderer(texture, null, offset);
    }

    public static AccessoryHeadwearRenderer hat(Supplier<PartialModel> model, ToDoubleFunction<ItemStack> offset){
        return new AccessoryHeadwearRenderer(null, model, offset);
    }

    public void render(ItemStack stack, LivingEntity wearer, EntityModel<?> parent, PoseStack poses,
                       MultiBufferSource buffers, int light){
        if(wearer.isInvisible() || !(parent instanceof HumanoidModel<?> model) || !model.head.visible) return;
        float height = (float)offset.applyAsDouble(stack);
        if(hat != null){
            poses.pushPose();
            try{
                model.head.translateAndRotate(poses);
                if(wearer.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).getItem() instanceof net.minecraft.world.item.ArmorItem)
                    poses.scale(1.2F, 1.2F, 1.2F);
                poses.translate(0, -.5F - height / 16, 0);
                poses.scale(1, -1, -1);
                poses.translate(0, -.140625F, 0);
                poses.mulPose(Axis.XP.rotationDegrees(-8.5F));
                CachedBuffers.partial(hat.get(), Blocks.AIR.defaultBlockState()).disableDiffuse().light(light)
                        .renderInto(poses, buffers.getBuffer(Sheets.cutoutBlockSheet()));
            }finally{ poses.popPose(); }
            return;
        }
        if(armor == null) armor = new HumanoidModel<>(Minecraft.getInstance().getEntityModels()
                .bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR));
        armor.setAllVisible(false);
        armor.head.copyFrom(model.head);
        armor.hat.copyFrom(model.hat);
        armor.head.visible = true;
        armor.hat.visible = false;
        armor.head.xScale *= 1.05F;
        armor.head.yScale *= 1.05F;
        armor.head.zScale *= 1.05F;
        armor.young = model.young;
        new HeadOffsetArmorModel(armor, height).renderToBuffer(poses,
                ItemRenderer.getArmorFoilBuffer(buffers, RenderType.armorCutoutNoCull(texture), stack.hasFoil()),
                light, OverlayTexture.NO_OVERLAY, -1);
    }
}
