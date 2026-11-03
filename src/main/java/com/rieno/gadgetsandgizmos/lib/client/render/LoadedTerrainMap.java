package com.rieno.gadgetsandgizmos.lib.client.render;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.Vec3;
import com.rieno.gadgetsandgizmos.lib.navigation.NavigationMapProjection;
import com.rieno.gadgetsandgizmos.lib.navigation.NavigationHeading;
import org.joml.Quaternionf;

import java.util.concurrent.atomic.AtomicInteger;

// Paint loaded terrain in bounded batches without requesting chunks or retaining a level
public final class LoadedTerrainMap implements AutoCloseable{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final int SIZE = 128;
    private static final int VIEW_SIZE = 64;
    private static final int SCALE = 4;
    private static final AtomicInteger IDS = new AtomicInteger();
    private final ResourceLocation id;
    private DynamicTexture texture;
    private long tick = Long.MIN_VALUE;
    private long sampled = Long.MIN_VALUE;
    private int centerX, centerZ, pendingX, pendingZ;
    private int row = SIZE;
    private ResourceLocation dimension;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public LoadedTerrainMap(ResourceLocation owner){
        id = ResourceLocation.fromNamespaceAndPath(owner.getNamespace(), "dynamic/terrain_map/" + IDS.incrementAndGet());
    }

    // Preserve the original north-up view while keeping an overscan margin for rotated maps
    public void render(GuiGraphics graphics, Vec3 center, int x, int y, int width, int height){
        if(!update(center)) return;
        graphics.fill(x, y, x + width, y + height, 0xFF263537);
        int inset = (SIZE - VIEW_SIZE) / 2;
        graphics.blit(id, x, y, width, height, inset, inset, VIEW_SIZE, VIEW_SIZE, SIZE, SIZE);
    }

    // Rotate cached terrain around the current vehicle rather than around the sampled frame centre
    public NavigationMapProjection render(GuiGraphics graphics, Vec3 center, double heading, int x, int y, int width, int height){
        var frame = new NavigationMapProjection(center, heading, width, height, VIEW_SIZE * SCALE);
        if(!update(center)) return frame;
        try(var clip = GuiPoseScissor.push(graphics, x, y, x + width, y + height)){
            graphics.fill(x, y, x + width, y + height, 0xFF263537);
            graphics.pose().pushPose();
            try{
                graphics.pose().translate(x + width * .5, y + height * .5, 0);
                graphics.pose().scale((float)frame.scale(), (float)frame.scale(), 1);
                graphics.pose().mulPose(new Quaternionf().rotationZ((float)Math.toRadians(-NavigationHeading.normalize(heading))));
                graphics.pose().translate(centerX - center.x, centerZ - center.z, 0);
                int span = SIZE * SCALE;
                graphics.blit(id, -span / 2, -span / 2, span, span, 0, 0, SIZE, SIZE, SIZE, SIZE);
            }finally{ graphics.pose().popPose(); }
        }
        return frame;
    }

    private boolean update(Vec3 center){
        var level = Minecraft.getInstance().level;
        if(level == null) return false;
        if(texture == null || !level.dimension().location().equals(dimension)){
            close();
            texture = new DynamicTexture(SIZE, SIZE, true);
            Minecraft.getInstance().getTextureManager().register(id, texture);
            dimension = level.dimension().location();
            centerX = (int)Math.floor(center.x / SCALE) * SCALE;
            centerZ = (int)Math.floor(center.z / SCALE) * SCALE;
            row = SIZE;
            tick = sampled = Long.MIN_VALUE;
        }
        long now = level.getGameTime();
        if(tick != now){
            tick = now;
            int currentX = (int)Math.floor(center.x / SCALE) * SCALE, currentZ = (int)Math.floor(center.z / SCALE) * SCALE;
            if(row == SIZE && (sampled == Long.MIN_VALUE || centerX != currentX || centerZ != currentZ || now - sampled >= 20 || now < sampled)){
                pendingX = (int)Math.floor(center.x / SCALE) * SCALE;
                pendingZ = (int)Math.floor(center.z / SCALE) * SCALE;
                row = 0;
            }
            if(row < SIZE) sample(level, now);
        }
        return true;
    }

    public double pixelX(double worldX, int width){ return width * (.5D + (worldX - centerX) / (VIEW_SIZE * SCALE)); }
    public double pixelZ(double worldZ, int height){ return height * (.5D + (worldZ - centerZ) / (VIEW_SIZE * SCALE)); }

    private void sample(ClientLevel level, long now){
        var pixels = texture.getPixels();
        if(pixels == null) return;
        var pos = new BlockPos.MutableBlockPos();
        int end = Math.min(SIZE, row + 8);
        for(; row < end; row++) for(int col = 0; col < SIZE; col++){
            int worldX = pendingX + (col - SIZE / 2) * SCALE;
            int worldZ = pendingZ + (row - SIZE / 2) * SCALE;
            int color = 0xFF373526;
            var chunk = level.getChunkSource().getChunk(worldX >> 4, worldZ >> 4, false);
            if(chunk != null){
                int altitude = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, worldX & 15, worldZ & 15);
                pos.set(worldX, altitude, worldZ);
                var state = chunk.getBlockState(pos);
                var mapColor = state.getMapColor(level, pos);
                if(mapColor != MapColor.NONE) color = mapColor.calculateRGBColor(MapColor.Brightness.NORMAL);
            }
            pixels.setPixelRGBA(col, row, color);
        }
        if(row == SIZE){
            texture.upload();
            centerX = pendingX;
            centerZ = pendingZ;
            sampled = now;
        }
    }

    @Override public void close(){
        if(texture != null) Minecraft.getInstance().getTextureManager().release(id);
        texture = null;
    }
}
