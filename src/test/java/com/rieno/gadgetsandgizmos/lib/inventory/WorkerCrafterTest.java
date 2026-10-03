package com.rieno.gadgetsandgizmos.lib.inventory;

import com.rieno.gadgetsandgizmos.lib.worker.*;
import com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlockEntity;
import com.simibubi.create.content.kinetics.crafter.RecipeGridHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.AdditionalAnswers.delegatesTo;

class WorkerCrafterTest{
    @BeforeAll static void bootstrap(){ InventoryTestBootstrap.bootstrap(); }

    @Test void feedsEveryCrafterInRecipeLayoutAndUsesTheTerminalReceiver(){
        Level level = mock(Level.class);
        var recipe = new ShapedRecipe("", CraftingBookCategory.MISC,
                ShapedRecipePattern.of(Map.of('P', Ingredient.of(Items.OAK_PLANKS), 'S', Ingredient.of(Items.STICK)),
                        "PPP", " S ", " S "), new ItemStack(Items.WOODEN_PICKAXE));
        var id = ResourceLocation.withDefaultNamespace("wooden_pickaxe");
        var manager = mock(RecipeManager.class);
        when(level.getRecipeManager()).thenReturn(manager);
        when(manager.byKey(id)).thenReturn(Optional.of(new RecipeHolder<>(id, recipe)));
        when(level.getBlockState(any())).thenReturn(Blocks.AIR.defaultBlockState());
        List<MechanicalCrafterBlockEntity> chain = new ArrayList<>();
        List<ItemStackHandler> slots = new ArrayList<>();
        for(int idx = 0; idx < 9; idx++){
            var crafter = mock(MechanicalCrafterBlockEntity.class);
            var inventory = new ItemStackHandler(1){ @Override public int getSlotLimit(int slot){ return 1; } };
            when(crafter.getInventory()).thenReturn(mock(MechanicalCrafterBlockEntity.Inventory.class, delegatesTo(inventory)));
            BlockPos pos = new BlockPos(idx % 3, 66 - idx / 3, 0);
            when(crafter.getBlockPos()).thenReturn(pos);
            when(crafter.getLevel()).thenReturn(level);
            when(crafter.getBlockState()).thenReturn(Blocks.FURNACE.defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
            when(level.getBlockEntity(pos)).thenReturn(crafter);
            chain.add(crafter);
            slots.add(inventory);
        }
        var root = chain.getLast();
        when(root.getTargetDirection()).thenReturn(Direction.EAST);
        var output = new ItemStackHandler(9);
        try(var topology = mockStatic(RecipeGridHandler.class); var access = mockStatic(WorkerContainerAccess.class)){
            access.when(() -> WorkerContainerAccess.isLoaded(eq(level), any())).thenReturn(true);
            topology.when(() -> RecipeGridHandler.getAllCraftersOfChain(root)).thenReturn(chain);
            for(var crafter : chain) if(crafter != root){
                topology.when(() -> RecipeGridHandler.getTargetingCrafter(crafter)).thenReturn(root);
            }
            access.when(() -> WorkerContainerAccess.itemHandlers(level, root.getBlockPos().east(), Direction.WEST))
                    .thenReturn(List.of(output));
            var plan = new WorkerRecipePlan(id, ResourceLocation.withDefaultNamespace("crafting"), WorkerRecipePlan.Operation.CRAFTING,
                    List.of(new WorkerRecipePlan.Input(item("oak_planks"), 3), new WorkerRecipePlan.Input(item("stick"), 2)),
                    item("wooden_pickaxe"), 1);
            var machine = WorkerMachineRegistry.resolve(level, root.getBlockPos(), null);
            assertTrue(machine.supports(plan));
            assertEquals(9, machine.members().size());
            for(ItemStack stack : List.of(new ItemStack(Items.OAK_PLANKS, 3), new ItemStack(Items.STICK, 2))){
                for(var port : machine.itemInputs(plan)) stack = port.insertItem(0, stack, false);
                assertTrue(stack.isEmpty());
            }
            for(int idx = 0; idx < 3; idx++) assertTrue(slots.get(idx).getStackInSlot(0).is(Items.OAK_PLANKS));
            assertTrue(slots.get(4).getStackInSlot(0).is(Items.STICK));
            assertTrue(slots.get(7).getStackInSlot(0).is(Items.STICK));
            for(int idx : List.of(3, 5, 6, 8)) assertTrue(slots.get(idx).getStackInSlot(0).isEmpty());
            machine.start(plan);
            verify(root).checkCompletedRecipe(true);
            assertSame(output, machine.itemOutputs(plan).getFirst());
            var cross = new ShapedRecipe("", CraftingBookCategory.MISC,
                    ShapedRecipePattern.of(Map.of('P', Ingredient.of(Items.OAK_PLANKS)), " P ", "PPP", " P "),
                    new ItemStack(Items.WOODEN_PICKAXE));
            when(manager.byKey(id)).thenReturn(Optional.of(new RecipeHolder<>(id, cross)));
            var sparse = new WorkerRecipePlan(id, plan.processorType(), plan.operation(),
                    List.of(new WorkerRecipePlan.Input(item("oak_planks"), 5)), plan.result(), 1);
            List<MechanicalCrafterBlockEntity> removed = List.of(chain.get(0), chain.get(2), chain.get(6));
            chain.removeAll(removed);
            assertTrue(machine.supports(sparse));
            assertEquals(5, machine.itemInputs(sparse).size());
            topology.when(() -> RecipeGridHandler.getAllCraftersOfChain(root)).thenReturn(null);
            assertFalse(machine.supports(plan));
        }
    }

    private static WorkerResourceKey item(String name){
        return new WorkerResourceKey(WorkerResourceType.ITEM, ResourceLocation.withDefaultNamespace(name));
    }
}
