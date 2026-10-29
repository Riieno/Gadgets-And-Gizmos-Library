package com.rieno.gadgetsandgizmos.lib.client.render;

import com.rieno.gadgetsandgizmos.lib.color.TextureColorAverage;
import com.rieno.gadgetsandgizmos.lib.geometry.SurfaceColorMesh;
import com.rieno.gadgetsandgizmos.lib.hologram.HologramChunk;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.client.model.data.ModelData;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

// Compose layered cube faces while keeping transparent windows and non-cube models intact
final class HologramSurfaceColors{
    private static final Map<TextureAtlasSprite, Integer> COLORS = new IdentityHashMap<>();
    private static final Map<BakedModel, Boolean> CUBES = new IdentityHashMap<>();
    private HologramSurfaceColors(){}

    static void clear(){ COLORS.clear(); CUBES.clear(); }

    static boolean collect(HologramChunk.SurfaceBlock block, HologramChunk chunk, RandomSource random, List<SurfaceColorMesh.Cell> cells){
        var state = block.state();
        if(state.hasBlockEntity() || !state.getFluidState().isEmpty() || !state.canOcclude()) return false;
        var model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
        if(Boolean.FALSE.equals(CUBES.get(model))) return false;
        random.setSeed(state.getSeed(block.pos()));
        int[] colors = new int[6];
        boolean[] opaque = new boolean[6];
        for(var layer : model.getRenderTypes(state, random, ModelData.EMPTY)){
            if(layer != RenderType.solid() && layer != RenderType.cutout() && layer != RenderType.cutoutMipped()) return false;
            for(int idx = 0; idx < 7; idx++){
                Direction face = idx == 6 ? null : Direction.values()[idx];
                random.setSeed(state.getSeed(block.pos()));
                for(var quad : model.getQuads(state, face, random, ModelData.EMPTY, layer)){
                    var side = quad.getDirection();
                    if(face != null && side != face || !unitFace(quad, side)){
                        CUBES.put(model, false);
                        return false;
                    }
                    int color = COLORS.computeIfAbsent(quad.getSprite(), HologramSurfaceColors::average);
                    opaque[side.ordinal()] |= (color >>> 24) == 255;
                    int tinted = color & 0xFF000000 | multiply(color, HologramProjectionRenderer.blockColor(quad, block, chunk));
                    colors[side.ordinal()] = TextureColorAverage.over(tinted, colors[side.ordinal()]);
                }
            }
        }
        for(boolean covered : opaque) if(!covered) return false;
        var pos = block.pos();
        for(Direction face : Direction.values()){
            if((block.faces() & 1 << face.ordinal()) == 0) continue;
            int plane, u, v;
            if(face.getAxis() == Direction.Axis.Y){ plane = pos.getY() + (face == Direction.UP ? 1 : 0); u = pos.getX(); v = pos.getZ(); }
            else if(face.getAxis() == Direction.Axis.Z){ plane = pos.getZ() + (face == Direction.SOUTH ? 1 : 0); u = pos.getX(); v = pos.getY(); }
            else{ plane = pos.getX() + (face == Direction.EAST ? 1 : 0); u = pos.getZ(); v = pos.getY(); }
            cells.add(new SurfaceColorMesh.Cell(face.ordinal(), plane, u, v, colors[face.ordinal()]));
        }
        return true;
    }

    private static int multiply(int color, int tint){
        return (color >> 16 & 255) * (tint >> 16 & 255) / 255 << 16
                | (color >> 8 & 255) * (tint >> 8 & 255) / 255 << 8 | (color & 255) * (tint & 255) / 255;
    }

    private static int average(TextureAtlasSprite sprite){
        var contents = sprite.contents();
        int[] frames = contents.getUniqueFrames().toArray();
        int width = contents.width(), height = contents.height(), area = width * height;
        return TextureColorAverage.argb(area * frames.length,
                idx -> sprite.getPixelRGBA(frames[idx / area], idx % width, idx % area / width));
    }

    private static boolean unitFace(BakedQuad quad, Direction face){
        if(quad.getDirection() != face || quad.getVertices().length != 32) return false;
        int corners = 0;
        for(int idx = 0; idx < 4; idx++){
            int offset = idx * 8;
            if(quad.getVertices()[offset + 3] != -1) return false;
            float x = Float.intBitsToFloat(quad.getVertices()[offset]), y = Float.intBitsToFloat(quad.getVertices()[offset + 1]), z = Float.intBitsToFloat(quad.getVertices()[offset + 2]);
            int unitX = Math.round(x), unitY = Math.round(y), unitZ = Math.round(z);
            // Rotated cube models retain small floating point offsets after baking
            if(!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)
                    || Math.abs(x - unitX) > .00001F || Math.abs(y - unitY) > .00001F || Math.abs(z - unitZ) > .00001F
                    || unitX < 0 || unitX > 1 || unitY < 0 || unitY > 1 || unitZ < 0 || unitZ > 1) return false;
            int plane = face.getAxis() == Direction.Axis.X ? unitX : face.getAxis() == Direction.Axis.Y ? unitY : unitZ;
            if(plane != (face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : 0)) return false;
            int u = face.getAxis() == Direction.Axis.X ? unitZ : unitX, v = face.getAxis() == Direction.Axis.Y ? unitZ : unitY;
            corners |= 1 << (u + v * 2);
        }
        return corners == 15;
    }
}
