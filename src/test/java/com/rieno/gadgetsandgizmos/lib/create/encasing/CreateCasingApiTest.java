package com.rieno.gadgetsandgizmos.lib.create.encasing;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.simibubi.create.content.kinetics.belt.BeltBlock;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CreateCasingApiTest{
    private static final ResourceLocation MATERIAL = ResourceLocation.parse("test:stone_casing");

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    @BeforeAll
    static void bootstrap(){
        SharedConstants.tryDetectVersion();
        try(MockedStatic<LoadingModList> loader = mockStatic(LoadingModList.class)){
            LoadingModList mods = mock(LoadingModList.class);
            when(mods.getModFiles()).thenReturn(List.of());
            loader.when(LoadingModList::get).thenReturn(mods);
            Bootstrap.bootStrap();
        }
        CreateCasingApi.registerAlias(Items.DIAMOND, Items.STONE);
        CreateCasingApi.registerBeltCasing(MATERIAL, Blocks.STONE);
    }

    @Test
    void aliasUsesTheRegisteredVariantWithoutConsumingItems(){
        ShaftBlock base = mock(ShaftBlock.class);
        CustomEncasedShaftBlock variant = mock(CustomEncasedShaftBlock.class);
        when(variant.getCasing()).thenReturn(Blocks.STONE);
        CreateCasingApi.registerVariant(base, variant);
        BlockState state = mock(BlockState.class);
        when(state.getBlock()).thenReturn(base);
        Level level = mock(Level.class);
        Player player = mock(Player.class);
        BlockHitResult hit = mock(BlockHitResult.class);
        ItemStack stack = new ItemStack(Items.DIAMOND, 3);

        assertEquals(ItemInteractionResult.SUCCESS, CreateCasingApi.tryAlias(state, level, BlockPos.ZERO, stack,
                player, InteractionHand.MAIN_HAND, hit));
        verify(variant).handleEncasing(state, level, BlockPos.ZERO, stack, player, InteractionHand.MAIN_HAND, hit);
        verify(base).playEncaseSound(level, BlockPos.ZERO);
        assertEquals(3, stack.getCount());
        assertEquals(ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION,
                CreateCasingApi.tryAlias(state, level, BlockPos.ZERO, new ItemStack(Items.DIRT), player, InteractionHand.MAIN_HAND, hit));
    }

    @Test
    void beltKeepsTheNativeCasingTypeAndSetsTheMaterial(){
        BeltBlock block = mock(BeltBlock.class);
        BlockState state = mock(BlockState.class);
        when(state.getBlock()).thenReturn(block);
        BeltBlockEntity belt = mock(BeltBlockEntity.class, withSettings().extraInterfaces(CustomBeltCasing.class));
        Level level = mock(Level.class);
        when(level.getBlockEntity(BlockPos.ZERO)).thenReturn(belt);
        when(level.getBlockState(BlockPos.ZERO)).thenReturn(state);
        Player player = mock(Player.class);
        when(player.mayBuild()).thenReturn(true);
        ItemStack stack = new ItemStack(Items.STONE, 4);

        assertEquals(ItemInteractionResult.SUCCESS, CreateCasingApi.tryBeltCasing(stack, state, level, BlockPos.ZERO, player));
        verify(belt).setCasingType(BeltBlockEntity.CasingType.ANDESITE);
        verify((CustomBeltCasing) belt).setCasingMaterial(MATERIAL);
        verify(block).updateCoverProperty(level, BlockPos.ZERO, state);
        assertEquals(4, stack.getCount());
        when(player.isShiftKeyDown()).thenReturn(true);
        assertEquals(ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION,
                CreateCasingApi.tryBeltCasing(stack, state, level, BlockPos.ZERO, player));
    }

    @Test
    void registrationsRejectDuplicatesAndMissingMaterialsFallBack(){
        assertThrows(IllegalArgumentException.class, () -> CreateCasingApi.registerAlias(Items.DIAMOND, Items.DIRT));
        assertThrows(IllegalArgumentException.class, () -> CreateCasingApi.registerAlias(Items.DIRT, Items.DIRT));
        assertThrows(IllegalArgumentException.class, () -> CreateCasingApi.registerBeltCasing(MATERIAL, Blocks.DIRT));
        assertNull(CreateCasingApi.getBeltCasing(null));
        assertNull(CreateCasingApi.getBeltCasing(ResourceLocation.parse("test:missing")));
        assertSame(Blocks.STONE, CreateCasingApi.getBeltCasing(MATERIAL));
        ShaftBlock base = mock(ShaftBlock.class);
        CustomEncasedShaftBlock variant = mock(CustomEncasedShaftBlock.class);
        CreateCasingApi.registerVariant(base, variant);
        assertThrows(IllegalArgumentException.class, () -> CreateCasingApi.registerVariant(base, variant));
    }
}
