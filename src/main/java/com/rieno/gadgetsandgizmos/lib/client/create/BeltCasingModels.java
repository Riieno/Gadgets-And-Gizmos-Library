package com.rieno.gadgetsandgizmos.lib.client.create;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.simibubi.create.AllSpriteShifts;
import com.simibubi.create.foundation.model.BakedQuadHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

// Replace belt casing textures while retaining Create's belt and cover geometry
public final class BeltCasingModels{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final ModelProperty<ResourceLocation> MATERIAL_PROPERTY = new ModelProperty<>();
    private static final Map<ResourceLocation, ResourceLocation> TEXTURES = new HashMap<>();
    private static final ResourceLocation COVER = ResourceLocation.parse("create:block/andesite_belt_cover");
    private static final ResourceLocation FRAME = ResourceLocation.parse("create:block/funnel/andesite_funnel_frame");

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private BeltCasingModels(){
    }

    // Register an atlas texture for a saved belt casing material
    public static void register(ResourceLocation id, ResourceLocation texture){
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(texture, "texture");
        if(TEXTURES.putIfAbsent(id, texture) != null) throw new IllegalArgumentException("Belt casing texture already registered");
    }

    // Get the material sprite after the atlas has loaded
    public static @Nullable TextureAtlasSprite getSprite(ModelData data){
        ResourceLocation texture = TEXTURES.get(data.get(MATERIAL_PROPERTY));
        return texture == null ? null : Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(texture);
    }

    // Retexture only the stationary casing and its cover
    public static List<BakedQuad> replaceQuads(List<BakedQuad> quads, ModelData data){
        TextureAtlasSprite target = getSprite(data);
        if(target == null) return quads;
        List<BakedQuad> res = new ArrayList<>(quads.size());
        for(BakedQuad quad : quads){
            TextureAtlasSprite src = quad.getSprite();
            ResourceLocation name = src.contents().name();
            // Create shifts belt UVs but keeps the original brass sprite on the quad
            if(src == AllSpriteShifts.ANDESIDE_BELT_CASING.getOriginal()){
                src = AllSpriteShifts.ANDESIDE_BELT_CASING.getTarget();
            }else if(!name.equals(COVER) && !name.equals(FRAME)){
                res.add(quad);
                continue;
            }
            int[] vertices = quad.getVertices().clone();
            for(int idx = 0; idx < 4; idx++){
                float u = (BakedQuadHelper.getU(vertices, idx) - src.getU0()) / (src.getU1() - src.getU0());
                float v = (BakedQuadHelper.getV(vertices, idx) - src.getV0()) / (src.getV1() - src.getV0());
                BakedQuadHelper.setU(vertices, idx, target.getU0() + u * (target.getU1() - target.getU0()));
                BakedQuadHelper.setV(vertices, idx, target.getV0() + v * (target.getV1() - target.getV0()));
            }
            res.add(new BakedQuad(vertices, quad.getTintIndex(), quad.getDirection(), target, quad.isShade(), quad.hasAmbientOcclusion()));
        }
        return res;
    }
}
