package com.rieno.gadgetsandgizmos.lib.client.render;

import net.minecraft.world.entity.LivingEntity;

// Expose the real entity behind a render-only proxy for headwear selection
public interface HeadwearRenderSource {
    LivingEntity headwearSource();

    // Resolve the entity whose equipment and role determine its headwear
    static LivingEntity resolve(LivingEntity rendered) {
        if (rendered instanceof HeadwearRenderSource source) {
            LivingEntity original = source.headwearSource();
            if (original != null) return original;
        }
        return rendered;
    }
}
