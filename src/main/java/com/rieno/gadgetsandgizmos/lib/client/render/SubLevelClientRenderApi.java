package com.rieno.gadgetsandgizmos.lib.client.render;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import dev.ryanhcode.sable.mixinterface.clip_overwrite.LevelPoseProviderExtension;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import it.unimi.dsi.fastutil.Function;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3dc;

import java.util.function.Supplier;

// Run client rendering with live SubLevel poses
public final class SubLevelClientRenderApi {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the SubLevel client render API
    private SubLevelClientRenderApi() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Run one action with interpolated SubLevel poses
    public static <T> T withPoses(ClientLevel level, float partialTicks, Supplier<T> action) {
        LevelPoseProviderExtension poses = (LevelPoseProviderExtension) level;
        Function<SubLevel, Pose3dc> poseProvider = key -> key instanceof ClientSubLevel clientSubLevel
                ? clientSubLevel.renderPose(partialTicks)
                : ((SubLevel) key).logicalPose();
        poses.sable$pushPoseSupplier(poseProvider);
        try {
            return action.get();
        } finally {
            poses.sable$popPoseSupplier();
        }
    }

    // Get one SubLevel render position
    public static Vec3 renderPosition(ClientSubLevel subLevel, float partialTicks) {
        Vector3dc pos = subLevel.renderPose(partialTicks).position();
        return new Vec3(pos.x(), pos.y(), pos.z());
    }
}
