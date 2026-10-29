package com.rieno.gadgetsandgizmos.lib.client.render;

import net.minecraft.world.level.block.entity.BlockEntity;

// Registered renderers can supply a retained hologram presentation without changing normal world rendering
public interface HologramRendererProvider{
    // Return null when this block entity requires the ordinary compatibility renderer
    RetainedHologramRenderer createHologramRenderer(BlockEntity entity);
}
