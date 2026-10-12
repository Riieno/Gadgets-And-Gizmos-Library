package com.rieno.gadgetsandgizmos.lib.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import org.joml.Quaternionf;
import org.joml.Vector3f;

// Draw head armor at a saved height along the wearer's rotated head
public final class HeadOffsetArmorModel extends Model{
    private final HumanoidModel<?> model;
    private final float offsetPixels;

    // Wrap an already posed head armor model without changing its shared parts
    public HeadOffsetArmorModel(HumanoidModel<?> model, float offsetPixels){
        super(model::renderType);
        if(!Float.isFinite(offsetPixels)) throw new IllegalArgumentException("Head offset must be finite");
        this.model = model;
        this.offsetPixels = offsetPixels;
    }

    // Apply the adjustment to every armor pass and restore the caller's pose
    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int light, int overlay, int color){
        Vector3f offset = new Vector3f(0, -offsetPixels * model.head.yScale / 16.0F, 0);
        offset.rotate(new Quaternionf().rotationZYX(model.head.zRot, model.head.yRot, model.head.xRot));
        poseStack.pushPose();
        try{
            poseStack.translate(offset.x, offset.y, offset.z);
            model.renderToBuffer(poseStack, buffer, light, overlay, color);
        }finally{
            poseStack.popPose();
        }
    }
}
