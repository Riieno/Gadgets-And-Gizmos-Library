package com.rieno.gadgetsandgizmos.lib.client.create;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import com.simibubi.create.foundation.model.BakedQuadHelper;
import net.createmod.catnip.render.SpriteShiftEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

// Map connected planes and their perimeter strips to explicitly supplied atlas patches
public final class AtlasConnectedTextureBehaviour extends FacingConnectedTextureBehaviour implements ConnectedTextureQuadMapper{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        DEFAULTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Keep atlas policy in the consuming mod and orient perimeter strips along the panel edge
    private final Orientation orientation;
    private final PatchProvider patches;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Retain the source sprite, context type and thread-safe patch provider
    public AtlasConnectedTextureBehaviour(CTSpriteShiftEntry shift, Orientation orientation, PatchProvider patches){
        super(shift);
        this.orientation = Objects.requireNonNull(orientation, "orientation");
        this.patches = Objects.requireNonNull(patches, "patches");
    }

    // Run top and bottom strips across the panel width after their model UVs are rotated
    @Override
    protected Direction getUpDirection(BlockAndTintGetter reader, BlockPos pos, BlockState state, Direction face){
        if(orientation == Orientation.PERIMETER && face.getAxis().isVertical()
                && state.hasProperty(HorizontalDirectionalBlock.FACING)){
            return state.getValue(HorizontalDirectionalBlock.FACING).getClockWise();
        }
        return super.getUpDirection(reader, pos, state, face);
    }

    // Subdivide the original rectangular face without moving its surface or changing its winding
    @Override
    public List<BakedQuad> mapQuad(BlockState state, BakedQuad quad, CTSpriteShiftEntry shift, int connections){
        List<Patch> layout = Objects.requireNonNull(patches.patches(state, quad.getDirection(), connections), "layout");
        List<BakedQuad> res = new ArrayList<>(layout.size());
        float[] u = new float[4], v = new float[4];
        float minU = Float.POSITIVE_INFINITY, minV = Float.POSITIVE_INFINITY;
        float maxU = Float.NEGATIVE_INFINITY, maxV = Float.NEGATIVE_INFINITY;
        int[] source = quad.getVertices();
        Vec3[] positions = new Vec3[4];
        for(int idx = 0; idx < 4; idx++){
            positions[idx] = BakedQuadHelper.getXYZ(source, idx);
            u[idx] = SpriteShiftEntry.getUnInterpolatedU(shift.getOriginal(), BakedQuadHelper.getU(source, idx));
            v[idx] = SpriteShiftEntry.getUnInterpolatedV(shift.getOriginal(), BakedQuadHelper.getV(source, idx));
            minU = Math.min(minU, u[idx]); maxU = Math.max(maxU, u[idx]);
            minV = Math.min(minV, v[idx]); maxV = Math.max(maxV, v[idx]);
        }
        if(maxU <= minU || maxV <= minV) return List.of(quad);
        for(Patch patch : layout){
            var target = patch.sprite == null ? shift.getTarget()
                    : Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(patch.sprite);
            int[] vertices = source.clone();
            for(int idx = 0; idx < 4; idx++){
                boolean right = u[idx] > (minU + maxU) / 2, bottom = v[idx] > (minV + maxV) / 2;
                float pu = (right ? patch.face.maxU : patch.face.minU) / 16;
                float pv = (bottom ? patch.face.maxV : patch.face.minV) / 16;
                double x = 0, y = 0, z = 0;
                for(int corner = 0; corner < 4; corner++){
                    boolean cornerRight = u[corner] > (minU + maxU) / 2;
                    boolean cornerBottom = v[corner] > (minV + maxV) / 2;
                    float weight = (cornerRight ? pu : 1 - pu) * (cornerBottom ? pv : 1 - pv);
                    x += positions[corner].x * weight;
                    y += positions[corner].y * weight;
                    z += positions[corner].z * weight;
                }
                BakedQuadHelper.setXYZ(vertices, idx, new Vec3(x, y, z));
                BakedQuadHelper.setU(vertices, idx, target.getU(
                        (patch.atlas.minU + (patch.atlas.maxU - patch.atlas.minU) * u[idx]) / 16));
                BakedQuadHelper.setV(vertices, idx, target.getV(
                        (patch.atlas.minV + (patch.atlas.maxV - patch.atlas.minV) * v[idx]) / 16));
            }
            res.add(new BakedQuad(vertices, quad.getTintIndex(), quad.getDirection(), target,
                    quad.isShade(), quad.hasAmbientOcclusion()));
        }
        return res;
    }

    // Choose standard plane UVs or strips whose vertical texture axis follows the perimeter
    public enum Orientation{ PLANE, PERIMETER }

    // Supply immutable patch layouts for one state, face and connected texture index
    @FunctionalInterface
    public interface PatchProvider{
        // Resolve atlas regions without accessing mutable world or renderer state
        List<Patch> patches(BlockState state, Direction face, int connections);
    }

    // Pair a destination face rectangle with its source atlas rectangle
    public record Patch(Region face, Region atlas, @Nullable ResourceLocation sprite){
        // Reject incomplete patches before a chunk starts baking
        public Patch{
            Objects.requireNonNull(face, "face");
            Objects.requireNonNull(atlas, "atlas");
        }

        // Use the behaviour's target atlas when no separate patch sprite is needed
        public Patch(Region face, Region atlas){
            this(face, atlas, null);
        }
    }

    // Express face and atlas bounds in the same zero-to-sixteen UV units as block models
    public record Region(float minU, float minV, float maxU, float maxV){
        public static final Region FULL = new Region(0, 0, 16, 16);

        // Require a nonempty rectangle inside its face or atlas
        public Region{
            if(!(minU >= 0 && minV >= 0 && maxU <= 16 && maxV <= 16 && maxU > minU && maxV > minV)){
                throw new IllegalArgumentException("Texture regions must fit within zero-to-sixteen UV bounds");
            }
        }

        // Convert a pixel rectangle without assuming a square atlas or uniform cell layout
        public static Region pixels(int x, int y, int width, int height, int atlasWidth, int atlasHeight){
            if(atlasWidth <= 0 || atlasHeight <= 0) throw new IllegalArgumentException("Atlas dimensions must be positive");
            return new Region(x * 16F / atlasWidth, y * 16F / atlasHeight,
                    (x + width) * 16F / atlasWidth, (y + height) * 16F / atlasHeight);
        }
    }
}
