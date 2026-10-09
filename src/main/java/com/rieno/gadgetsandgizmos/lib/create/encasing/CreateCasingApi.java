package com.rieno.gadgetsandgizmos.lib.create.encasing;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.simibubi.create.content.decoration.encasing.EncasableBlock;
import com.simibubi.create.content.decoration.encasing.EncasedBlock;
import com.simibubi.create.content.decoration.encasing.EncasingRegistry;
import com.simibubi.create.content.kinetics.belt.BeltBlock;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

// Register reusable Create casing variants, aliases, and belt materials
public final class CreateCasingApi{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Map<Item, Item> ALIASES = new HashMap<>();
    private static final Map<ResourceLocation, Block> BELT_CASINGS = new HashMap<>();
    private static final Map<Item, ResourceLocation> BELT_ITEMS = new HashMap<>();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private CreateCasingApi(){
    }

    // Add a native encased variant after block registration
    public static <B extends Block & EncasableBlock, E extends Block & EncasedBlock> void registerVariant(B base, E variant){
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(variant, "variant");
        if(EncasingRegistry.getVariants(base).contains(variant)) throw new IllegalArgumentException("Casing variant already registered");
        EncasingRegistry.addVariant(base, variant);
    }

    // Use an existing casing where no dedicated variant matches the held item
    public static void registerAlias(Item casing, Item equivalent){
        Objects.requireNonNull(casing, "casing");
        Objects.requireNonNull(equivalent, "equivalent");
        if(casing == equivalent || ALIASES.containsKey(casing)) throw new IllegalArgumentException("Casing alias already registered or self-referencing");
        ALIASES.put(casing, equivalent);
    }

    // Register a saved belt material with Andesite casing mechanics
    public static void registerBeltCasing(ResourceLocation id, Block casing){
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(casing, "casing");
        if(BELT_CASINGS.containsKey(id) || BELT_ITEMS.containsKey(casing.asItem())) throw new IllegalArgumentException("Belt casing already registered");
        BELT_CASINGS.put(id, casing);
        BELT_ITEMS.put(casing.asItem(), id);
    }

    // Resolve a saved belt material, or use Create's default when it is unavailable
    public static @Nullable Block getBeltCasing(@Nullable ResourceLocation id){
        return id == null ? null : BELT_CASINGS.get(id);
    }

    // Try an alias after Create has checked dedicated variants
    public static ItemInteractionResult tryAlias(BlockState state, Level level, BlockPos pos, ItemStack stack,
                                                 Player player, InteractionHand hand, BlockHitResult hit){
        Item equivalent = ALIASES.get(stack.getItem());
        if(equivalent == null) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        for(Block variant : EncasingRegistry.getVariants(state.getBlock())){
            if(!(variant instanceof EncasedBlock encased) || encased.getCasing().asItem() != equivalent) continue;
            if(!level.isClientSide){
                encased.handleEncasing(state, level, pos, stack, player, hand, hit);
                ((EncasableBlock) state.getBlock()).playEncaseSound(level, pos);
            }
            return ItemInteractionResult.SUCCESS;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    // Apply a registered belt material without consuming the casing
    public static ItemInteractionResult tryBeltCasing(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player){
        ResourceLocation id = BELT_ITEMS.get(stack.getItem());
        if(id == null || player.isShiftKeyDown() || !player.mayBuild()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if(!(state.getBlock() instanceof BeltBlock block)
                || !(level.getBlockEntity(pos) instanceof BeltBlockEntity belt)
                || !(belt instanceof CustomBeltCasing material)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if(level.isClientSide) return ItemInteractionResult.SUCCESS;
        if(!id.equals(material.getCasingMaterial())){
            belt.setCasingType(BeltBlockEntity.CasingType.ANDESITE);
            material.setCasingMaterial(id);
        }
        block.updateCoverProperty(level, pos, level.getBlockState(pos));
        SoundType sound = BELT_CASINGS.get(id).defaultBlockState().getSoundType(level, pos, player);
        level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS,
                (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
        return ItemInteractionResult.SUCCESS;
    }
}
