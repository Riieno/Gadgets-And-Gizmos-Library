package com.rieno.gadgetsandgizmos.lib.zipline;

import net.minecraft.world.phys.Vec3;

// Any entity can opt into riding a powered zipline without the library knowing its implementation.
public interface ZiplineRider {
    void ziplineAttached();

    void ziplineDetached();

    void ziplineMoved(Vec3 gripPosition, Vec3 travelDirection);
}
