package com.rieno.gadgetsandgizmos.lib.inventory;

import com.rieno.gadgetsandgizmos.lib.worker.*;
import com.simibubi.create.content.kinetics.press.MechanicalPressBlockEntity;
import com.simibubi.create.content.kinetics.press.PressingRecipe;
import com.simibubi.create.content.kinetics.fan.IAirCurrentSource;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WorkerMachineTest{
    private static final BlockPos POS = new BlockPos(4, 64, 8);
    @BeforeAll static void bootstrap(){ InventoryTestBootstrap.bootstrap(); }
    private org.mockito.MockedStatic<net.neoforged.neoforge.event.EventHooks> fuels;

    @BeforeEach void loadFuelValues(){
        fuels = mockStatic(net.neoforged.neoforge.event.EventHooks.class, CALLS_REAL_METHODS);
        fuels.when(() -> net.neoforged.neoforge.event.EventHooks.getItemBurnTime(any(), anyInt(), any()))
                .thenAnswer(call -> {
                    ItemStack stack = call.getArgument(0);
                    return stack.is(Items.COAL) ? 1600 : stack.is(Items.STICK) ? 100 : 0;
                });
    }
    @AfterEach void closeFuelValues(){ fuels.close(); }

    @Test void resolvesAStationAfterItsChunkBecomesAvailable(){
        Level level = level();
        when(level.getBlockState(POS)).thenReturn(Blocks.CRAFTING_TABLE.defaultBlockState());
        when(level.isLoaded(POS)).thenReturn(false);
        assertNull(WorkerMachineRegistry.resolve(level, POS, null));
        verify(level, never()).getBlockEntity(POS);
        when(level.isLoaded(POS)).thenReturn(true);
        assertTrue(WorkerMachineRegistry.resolve(level, POS, null).virtualCrafting());
    }

    @Test void resolvesActualNineSlotRecipeAtInventoryFreeTable(){
        Level level = level();
        when(level.getBlockState(POS)).thenReturn(Blocks.CRAFTING_TABLE.defaultBlockState());
        var recipe = new ShapedRecipe("", CraftingBookCategory.MISC,
                ShapedRecipePattern.of(Map.of('I', Ingredient.of(Items.IRON_INGOT)), "III", "III", "III"),
                new ItemStack(Items.IRON_BLOCK));
        var plan = plan("crafting", Items.IRON_INGOT, 9, Items.IRON_BLOCK);
        when(level.getRecipeManager().byKey(plan.recipeId())).thenReturn(Optional.of(new RecipeHolder<>(plan.recipeId(), recipe)));
        var grid = WorkerCraftingGrid.create(recipe, plan);
        assertEquals(3, grid.width());
        assertEquals(3, grid.height());
        assertTrue(recipe.matches(grid, level));
        var machine = WorkerMachineRegistry.resolve(level, POS, null);
        assertNotNull(machine);
        assertTrue(machine.virtualCrafting());
        assertTrue(machine.supports(plan));
        assertTrue(machine.itemInputs(plan).isEmpty());
    }

    @Test void preservesEmptySlotsAndBacktracksOverlappingIngredientChoices(){
        var recipe = new ShapedRecipe("", CraftingBookCategory.MISC,
                ShapedRecipePattern.of(Map.of('A', Ingredient.of(Items.IRON_INGOT, Items.GOLD_INGOT),
                        'I', Ingredient.of(Items.IRON_INGOT)), "A I", " I "), new ItemStack(Items.BUCKET));
        var plan = new WorkerRecipePlan(id("bucket"), id("crafting"), WorkerRecipePlan.Operation.CRAFTING,
                List.of(new WorkerRecipePlan.Input(item(Items.IRON_INGOT), 2),
                        new WorkerRecipePlan.Input(item(Items.GOLD_INGOT), 1)), item(Items.BUCKET), 1);
        var grid = WorkerCraftingGrid.create(recipe, plan);
        assertTrue(grid.getItem(0).is(Items.GOLD_INGOT));
        assertTrue(grid.getItem(1).isEmpty());
        assertTrue(recipe.matches(grid, level()));
    }

    @Test void furnaceSeparatesIngredientsFuelAndOutput(){
        Level level = level();
        var furnace = new FurnaceBlockEntity(POS, Blocks.FURNACE.defaultBlockState());
        furnace.setLevel(level);
        when(level.getBlockState(POS)).thenReturn(Blocks.FURNACE.defaultBlockState());
        when(level.getBlockEntity(POS)).thenReturn(furnace);
        var machine = WorkerMachineRegistry.resolve(level, POS, null);
        var plan = plan("smelting", Items.RAW_IRON, 1, Items.IRON_INGOT);
        var supply = machine.supply(plan, Map.of(item(Items.COAL), 4L));
        assertEquals(item(Items.COAL), supply.resource());
        assertEquals(1, supply.amount());
        assertTrue(machine.itemInputs(null).getFirst().insertItem(0, new ItemStack(Items.COAL), false).isEmpty());
        assertTrue(furnace.getItem(1).is(Items.COAL));
        assertTrue(furnace.getItem(0).isEmpty());
        assertTrue(machine.supply(plan, Map.of()).ready());
        machine.itemInputs(plan).getFirst().insertItem(0, new ItemStack(Items.RAW_IRON), false);
        assertTrue(furnace.getItem(0).is(Items.RAW_IRON));
        assertTrue(machine.itemOutputs(plan).stream().noneMatch(handler -> {
            for(int idx = 0; idx < handler.getSlots(); idx++) if(handler.getStackInSlot(idx).is(Items.RAW_IRON)) return true;
            return false;
        }));
    }

    @Test void reservesEnoughShortBurningFuelAndReturnsEmptyBuckets(){
        Level level = level();
        var furnace = new FurnaceBlockEntity(POS, Blocks.FURNACE.defaultBlockState());
        furnace.setLevel(level);
        when(level.getBlockState(POS)).thenReturn(Blocks.FURNACE.defaultBlockState());
        when(level.getBlockEntity(POS)).thenReturn(furnace);
        var machine = WorkerMachineRegistry.resolve(level, POS, null);
        var plan = plan("smelting", Items.RAW_IRON, 1, Items.IRON_INGOT);
        assertEquals(2, machine.supply(plan, Map.of(item(Items.STICK), 9L)).amount());
        furnace.setItem(1, new ItemStack(Items.BUCKET));
        var supply = machine.supply(plan, Map.of(item(Items.COAL), 1L));
        assertTrue(supply.withdrawal());
        assertEquals(item(Items.BUCKET), supply.resource());
    }

    @Test void depotRequiresItsOwnPressRatherThanAnyNearbyPress(){
        Level level = level();
        when(level.getBlockEntity(POS)).thenReturn(mock(DepotBlockEntity.class));
        when(level.getBlockEntity(POS.east().above(2))).thenReturn(mock(MechanicalPressBlockEntity.class));
        var plan = new WorkerRecipePlan(id("sheet"), ResourceLocation.parse("create:pressing"),
                WorkerRecipePlan.Operation.PROCESSING, List.of(new WorkerRecipePlan.Input(item(Items.IRON_INGOT), 1)),
                item(Items.IRON_NUGGET), 1);
        assertFalse(WorkerMachineRegistry.resolve(level, POS, null).supports(plan));
        MechanicalPressBlockEntity press = mock(MechanicalPressBlockEntity.class);
        when(level.getBlockEntity(POS.above(2))).thenReturn(press);
        when(press.getRecipe(any(ItemStack.class))).thenReturn(Optional.of(
                new RecipeHolder<>(id("other_sheet"), mock(PressingRecipe.class))));
        assertFalse(WorkerMachineRegistry.resolve(level, POS, null).supports(plan));
        when(press.getRecipe(any(ItemStack.class))).thenReturn(Optional.of(
                new RecipeHolder<>(id("sheet"), mock(PressingRecipe.class))));
        assertTrue(WorkerMachineRegistry.resolve(level, POS, null).supports(plan));
        assertTrue(WorkerMachineRegistry.accessPositions(level, POS.above(2)).contains(POS));
    }

    @Test void offsetDepotUsesOnlyAProcessingSourceThatAcceptsItsRecipe(){
        Level level = level();
        when(level.getBlockEntity(POS)).thenReturn(mock(DepotBlockEntity.class));
        BlockPos sourcePos = POS.east(2).above().south();
        BlockEntity sourceEntity = mock(BlockEntity.class,
                withSettings().extraInterfaces(IAirCurrentSource.class, WorkerAirProcessingSource.class));
        WorkerAirProcessingSource source = (WorkerAirProcessingSource)sourceEntity;
        when(level.getBlockEntity(sourcePos)).thenReturn(sourceEntity);
        when(source.workerProcessingTargets(level)).thenReturn(List.of(POS));
        var plan = new WorkerRecipePlan(id("iron_ingot"), id("smelting"),
                WorkerRecipePlan.Operation.PROCESSING,
                List.of(new WorkerRecipePlan.Input(item(Items.RAW_IRON), 1)), item(Items.IRON_INGOT), 1);
        var machine = WorkerMachineRegistry.resolve(level, POS, null);
        assertNotNull(machine);
        assertFalse(machine.supports(plan));
        when(source.supportsWorkerRecipe(level, POS, plan)).thenReturn(true);
        assertTrue(machine.supports(plan));
        assertTrue(WorkerMachineRegistry.accessPositions(level, sourcePos).contains(POS));
    }

    @Test void discoversAnAirSourceForEveryDepotAroundALongTunnel(){
        Level level = level();
        BlockPos sourcePos = new BlockPos(0, 64, 0);
        BlockEntity sourceEntity = mock(BlockEntity.class,
                withSettings().extraInterfaces(IAirCurrentSource.class, WorkerAirProcessingSource.class));
        WorkerAirProcessingSource source = (WorkerAirProcessingSource)sourceEntity;
        Map<BlockPos, BlockEntity> entities = new java.util.HashMap<>();
        entities.put(sourcePos, sourceEntity);
        for(int distance : List.of(1, 32)){
            for(int x = -1; x <= 1; x++){
                for(int y = -1; y <= 1; y++){
                    if(x == 0 && y == 0) continue;
                    entities.put(sourcePos.offset(x, y, distance), mock(DepotBlockEntity.class));
                }
            }
        }
        when(level.getBlockEntity(any())).thenAnswer(call -> entities.get(call.getArgument(0)));
        var plan = new WorkerRecipePlan(id("iron_ingot"), id("smelting"),
                WorkerRecipePlan.Operation.PROCESSING,
                List.of(new WorkerRecipePlan.Input(item(Items.RAW_IRON), 1)), item(Items.IRON_INGOT), 1);
        when(source.supportsWorkerRecipe(eq(level), any(), eq(plan))).thenReturn(true);
        for(BlockPos target : entities.keySet()){
            if(target.equals(sourcePos)) continue;
            var machine = WorkerMachineRegistry.resolve(level, target, null);
            assertNotNull(machine, target.toString());
            assertTrue(machine.supports(plan), target.toString());
        }
    }

    private static Level level(){
        Level level = mock(Level.class);
        when(level.isLoaded(any())).thenReturn(true);
        when(level.getBlockState(any())).thenReturn(Blocks.AIR.defaultBlockState());
        when(level.registryAccess()).thenReturn(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        var manager = mock(RecipeManager.class);
        when(level.getRecipeManager()).thenReturn(manager);
        when(manager.byKey(any())).thenReturn(Optional.empty());
        return level;
    }

    private static WorkerRecipePlan plan(String type, net.minecraft.world.item.Item input, long amount,
                                          net.minecraft.world.item.Item output){
        return new WorkerRecipePlan(id("recipe"), id(type), WorkerRecipePlan.Operation.CRAFTING,
                List.of(new WorkerRecipePlan.Input(item(input), amount)), item(output), 1);
    }
    private static ResourceLocation id(String name){ return ResourceLocation.withDefaultNamespace(name); }
    private static WorkerResourceKey item(net.minecraft.world.item.Item item){
        return new WorkerResourceKey(WorkerResourceType.ITEM, BuiltInRegistries.ITEM.getKey(item));
    }
}
