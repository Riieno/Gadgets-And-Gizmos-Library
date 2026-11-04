package com.rieno.gadgetsandgizmos.lib.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.util.SableBufferUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// Preserve empty plot bounds while decoding Sable tracking packets
@Mixin(value = SableBufferUtils.class, remap = false)
public abstract class SableBoundsPacketMixin{
    // Keep inverted empty corners from becoming a box spanning the entire integer range
    @Redirect(method = "read(Lio/netty/buffer/ByteBuf;Ldev/ryanhcode/sable/companion/math/BoundingBox3i;)Ldev/ryanhcode/sable/companion/math/BoundingBox3i;",
            at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/companion/math/BoundingBox3i;set(IIIIII)Ldev/ryanhcode/sable/companion/math/BoundingBox3i;"))
    private static BoundingBox3i gadgetsngizmos$readEmptyBounds(BoundingBox3i bounds,
            int minX, int minY, int minZ, int maxX, int maxY, int maxZ){
        return bounds.setUnchecked(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
