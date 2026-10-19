package com.rieno.gadgetsandgizmos.lib.client.render;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LightLayer;
import org.joml.Vector3d;
import org.joml.Vector3dc;

// Sample entity and particle lighting within loaded plot bounds and a fixed vertical budget
public final class SubLevelEntityLighting{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final int MAX_VERTICAL_SAMPLES = 4096;

    private SubLevelEntityLighting(){}

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Preserve plot skylight shading while ignoring empty plots and invalid render poses
    public static int skyLight(int sky, Vector3dc worldProbe, Iterable<? extends SubLevel> bodies){
        return LightTexture.sky(sample(LightTexture.pack(0, Mth.clamp(sky, 0, 15)), worldProbe, bodies, true, false));
    }

    // Sample particle block light and bounded skylight using the current logical poses
    public static int particleLight(int packed, Vector3dc worldProbe, Iterable<? extends SubLevel> bodies){
        return sample(packed, worldProbe, bodies, false, true);
    }

    // Share guarded plot sampling between entity and particle lighting
    private static int sample(int packed, Vector3dc worldProbe, Iterable<? extends SubLevel> bodies, boolean render, boolean blockLight){
        int sky = LightTexture.sky(packed), block = LightTexture.block(packed);
        if(!finite(worldProbe)) return packed;
        Vector3d local = new Vector3d();
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos scan = new BlockPos.MutableBlockPos();
        for(SubLevel body : bodies){
            if(!(body instanceof ClientSubLevel client) || body.isRemoved()) continue;
            var bounds = body.getPlot().getBoundingBox();
            if(bounds.minX() > bounds.maxX() || bounds.minY() > bounds.maxY() || bounds.minZ() > bounds.maxZ()) continue;
            (render ? client.renderPose() : client.logicalPose()).transformPositionInverse(worldProbe, local);
            if(!finite(local)) continue;
            probe.set(coordinate(local.x), coordinate(local.y), coordinate(local.z));
            if(probe.getX() < bounds.minX() || probe.getX() > bounds.maxX()
                    || probe.getZ() < bounds.minZ() || probe.getZ() > bounds.maxZ()) continue;
            var level = body.getLevel();
            if(blockLight && probe.getY() >= level.getMinBuildHeight() && probe.getY() < level.getMaxBuildHeight())
                block = Math.max(block, level.getBrightness(LightLayer.BLOCK, probe));
            long top = Math.min((long) probe.getY() + 1, Math.min((long) bounds.maxY(), (long) level.getMaxBuildHeight() - 1));
            long bottom = Math.max(Math.max((long) bounds.minY(), level.getMinBuildHeight()), top - MAX_VERTICAL_SAMPLES + 1);
            for(long y = top; y >= bottom; y--){
                scan.set(probe.getX(), (int) y, probe.getZ());
                if(level.getBlockState(scan).isAir()) continue;
                sky = Math.min(sky, client.scaleSkyLight(level.getBrightness(LightLayer.SKY, probe)));
                break;
            }
        }
        return LightTexture.pack(block, sky);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Reject invalid transforms before converting them into integer scan coordinates
    private static boolean finite(Vector3dc pos){
        return Double.isFinite(pos.x()) && Double.isFinite(pos.y()) && Double.isFinite(pos.z());
    }

    // Floor extreme finite values without overflowing Minecraft's integer conversion
    private static int coordinate(double val){
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, Math.floor(val)));
    }
}
