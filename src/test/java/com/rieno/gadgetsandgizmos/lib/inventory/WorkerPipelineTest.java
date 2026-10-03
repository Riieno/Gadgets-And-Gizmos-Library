package com.rieno.gadgetsandgizmos.lib.inventory;

import com.rieno.gadgetsandgizmos.lib.worker.*;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.kinetics.belt.BeltBlock;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity;
import com.simibubi.create.content.logistics.funnel.AbstractFunnelBlock;
import com.simibubi.create.content.logistics.funnel.BeltFunnelBlock;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WorkerPipelineTest{
    @BeforeAll static void bootstrap() throws Exception{ WorkerSequenceTest.bootstrap(); }

    @Test void countsOnlyMatchingIngredientsAlreadyInsideFurnace(){
        Level level = mock(Level.class);
        BlockPos pos = new BlockPos(0, 64, 0);
        var furnace = mock(net.minecraft.world.level.block.entity.FurnaceBlockEntity.class);
        when(level.getBlockState(pos)).thenReturn(Blocks.FURNACE.defaultBlockState());
        when(level.getBlockEntity(pos)).thenReturn(furnace);
        when(furnace.getItem(0)).thenReturn(new ItemStack(Items.RAW_IRON, 7));
        var plan = new WorkerRecipePlan(ResourceLocation.parse("minecraft:iron_ingot_from_smelting_raw_iron"),
                ResourceLocation.parse("minecraft:smelting"), WorkerRecipePlan.Operation.PROCESSING,
                List.of(new WorkerRecipePlan.Input(item("raw_iron"), 1)), item("iron_ingot"), 1);
        try(var access = mockStatic(WorkerContainerAccess.class)){
            access.when(() -> WorkerContainerAccess.isLoaded(level, pos)).thenReturn(true);
            WorkerMachine machine = WorkerMachineRegistry.resolve(level, pos, null);
            assertNotNull(machine);
            assertEquals(List.of(7L), machine.preloadedInputs(plan, null));
            when(furnace.getItem(0)).thenReturn(new ItemStack(Items.RAW_COPPER, 7));
            assertEquals(List.of(0L), machine.preloadedInputs(plan, null));
        }
    }

    @Test void splitsSharedFluidBetweenStationsWithoutMutatingSimulation(){
        var first = new net.neoforged.neoforge.fluids.capability.templates.FluidTank(1000);
        var second = new net.neoforged.neoforge.fluids.capability.templates.FluidTank(1000);
        var water = new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER, 600);
        var firstPort = new WorkerFluidPort(first, stack -> stack.getFluid() == water.getFluid(), 300);
        var secondPort = new WorkerFluidPort(second, stack -> stack.getFluid() == water.getFluid(), 300);
        var simulate = net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE;
        var execute = net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;
        assertEquals(300, firstPort.fill(water, simulate));
        assertTrue(first.isEmpty());
        water.shrink(firstPort.fill(water, execute));
        water.shrink(secondPort.fill(water, execute));
        assertTrue(water.isEmpty());
        assertEquals(300, first.getFluidAmount());
        assertEquals(300, second.getFluidAmount());
    }

    @Test @SuppressWarnings({"rawtypes", "unchecked"})
    void feedsEveryDeployerAndReturnsOnlyCompletedPasses(){
        var id = ResourceLocation.parse("test:assembly_line");
        var typeId = ResourceLocation.parse("create:deploying");
        if(!BuiltInRegistries.RECIPE_TYPE.containsKey(typeId)){
            var registry = (net.minecraft.core.MappedRegistry<RecipeType<?>>)BuiltInRegistries.RECIPE_TYPE;
            registry.unfreeze();
            try{ Registry.register(registry, typeId, new RecipeType<>(){}); }
            finally{ registry.freeze(); }
        }
        var type = BuiltInRegistries.RECIPE_TYPE.get(typeId);
        var recipe = mock(SequencedAssemblyRecipe.class);
        when(recipe.getIngredient()).thenReturn(Ingredient.of(Items.IRON_INGOT));
        when(recipe.getTransitionalItem()).thenReturn(new ItemStack(Items.COMPASS));
        when(recipe.getLoops()).thenReturn(3);
        ProcessingRecipe first = mock(ProcessingRecipe.class);
        ProcessingRecipe second = mock(ProcessingRecipe.class);
        for(var stage : List.of(first, second)){
            when(stage.getIngredients()).thenReturn(NonNullList.of(Ingredient.EMPTY,
                    Ingredient.of(Items.IRON_INGOT, Items.COMPASS), Ingredient.of(Items.GOLD_NUGGET)));
            when(stage.getFluidIngredients()).thenReturn(NonNullList.create());
            doReturn(type).when(stage).getType();
        }
        when(recipe.getSequence()).thenReturn(List.of(new SequencedRecipe(first), new SequencedRecipe(second)));
        Level level = mock(Level.class);
        when(level.registryAccess()).thenReturn(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        when(level.getBlockState(any())).thenReturn(Blocks.AIR.defaultBlockState());
        var manager = mock(RecipeManager.class);
        when(level.getRecipeManager()).thenReturn(manager);
        when(manager.byKey(id)).thenReturn(Optional.of(new RecipeHolder<>(id, recipe)));
        BlockPos start = new BlockPos(0, 64, 0);
        BlockPos end = start.east();
        var belt = mock(BeltBlockEntity.class);
        when(belt.getController()).thenReturn(start);
        when(belt.getMovementFacing()).thenReturn(Direction.EAST);
        when(belt.getDirectionAwareBeltMovementSpeed()).thenReturn(1F);
        when(level.getBlockEntity(start)).thenReturn(belt);
        when(level.getBlockEntity(end)).thenReturn(belt);
        var deployer = mock(DeployerBlockEntity.class);
        when(deployer.getBlockState()).thenReturn(Blocks.DISPENSER.defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.DOWN));
        when(level.getBlockEntity(start.above(2))).thenReturn(deployer);
        when(level.getBlockEntity(end.above(2))).thenReturn(deployer);
        var base = new ItemStackHandler(1);
        var firstHeld = new ItemStackHandler(1);
        var secondHeld = new ItemStackHandler(1);
        var output = new ItemStackHandler(1);
        try(var topology = mockStatic(BeltBlock.class); var access = mockStatic(WorkerContainerAccess.class)){
            topology.when(() -> BeltBlock.getBeltChain(level, start)).thenReturn(List.of(start, end));
            access.when(() -> WorkerContainerAccess.isLoaded(eq(level), any())).thenReturn(true);
            access.when(() -> WorkerContainerAccess.itemHandlers(level, start, null)).thenReturn(List.of(base));
            access.when(() -> WorkerContainerAccess.itemHandlers(level, end, null)).thenReturn(List.of(output));
            access.when(() -> WorkerContainerAccess.itemHandlers(level, end.east(), null)).thenReturn(List.of(output));
            access.when(() -> WorkerContainerAccess.itemHandlers(level, start.above(2), null)).thenReturn(List.of(firstHeld));
            access.when(() -> WorkerContainerAccess.itemHandlers(level, end.above(2), null)).thenReturn(List.of(secondHeld));
            var plan = new WorkerRecipePlan(id, ResourceLocation.parse("create:sequenced_assembly"),
                    WorkerRecipePlan.Operation.PROCESSING,
                    List.of(new WorkerRecipePlan.Input(item("gold_nugget"), 6), new WorkerRecipePlan.Input(item("iron_ingot"), 1)),
                    item("clock"), 1);
            var machine = WorkerMachineRegistry.resolve(level, start, null);
            assertTrue(machine.supports(plan));
            ItemStack supplies = new ItemStack(Items.GOLD_NUGGET, 6);
            for(var port : machine.itemInputs(plan)) supplies = port.insertItem(0, supplies, false);
            assertTrue(supplies.isEmpty());
            assertEquals(3, firstHeld.getStackInSlot(0).getCount());
            assertEquals(3, secondHeld.getStackInSlot(0).getCount());
            var stockedPlan = new WorkerRecipePlan(id, ResourceLocation.parse("create:sequenced_assembly"),
                    WorkerRecipePlan.Operation.PROCESSING,
                    List.of(new WorkerRecipePlan.Input(item("gold_nugget"), 3),
                            new WorkerRecipePlan.Input(item("gold_nugget"), 3),
                            new WorkerRecipePlan.Input(item("iron_ingot"), 1)), item("clock"), 1);
            assertEquals(List.of(6L, 0L), machine.preloadedInputs(stockedPlan, null));
            var stockedDefinition = new WorkerRecipeDefinition(id,
                    ResourceLocation.parse("create:sequenced_assembly"),
                    WorkerRecipePlan.Operation.PROCESSING,
                    List.of(new WorkerRecipeDefinition.Ingredient(List.of(item("gold_nugget")), 3),
                            new WorkerRecipeDefinition.Ingredient(List.of(item("gold_nugget")), 3),
                            new WorkerRecipeDefinition.Ingredient(List.of(item("iron_ingot")), 1)), item("clock"), 1);
            assertEquals(List.of(3L, 3L, 0L), machine.preloadedInputs(stockedDefinition, null));
            firstHeld.setStackInSlot(0, new ItemStack(Items.GOLD_NUGGET, 6));
            secondHeld.setStackInSlot(0, ItemStack.EMPTY);
            assertEquals(List.of(3L, 0L), machine.preloadedInputs(stockedPlan, null));
            assertEquals(List.of(6L, 0L), machine.preloadedInputs(stockedPlan, null, 2L));
            firstHeld.setStackInSlot(0, new ItemStack(Items.GOLD_NUGGET, 3));
            secondHeld.setStackInSlot(0, new ItemStack(Items.GOLD_NUGGET, 3));
            assertTrue(base.getStackInSlot(0).isEmpty());
            ItemStack workpiece = new ItemStack(Items.IRON_INGOT);
            for(var port : machine.itemInputs(plan)) workpiece = port.insertItem(0, workpiece, false);
            assertTrue(workpiece.isEmpty());
            assertEquals(1, base.getStackInSlot(0).getCount());
            assertTrue(output.getStackInSlot(0).isEmpty());
            base.setStackInSlot(0, ItemStack.EMPTY);
            ItemStack intermediate = new ItemStack(Items.COMPASS);
            intermediate.set(AllDataComponents.SEQUENCED_ASSEMBLY,
                    new SequencedAssemblyRecipe.SequencedAssembly(id, 1, 1F / 6));
            output.setStackInSlot(0, intermediate);
            assertTrue(machine.recirculationOutputs(plan).stream().allMatch(port -> port.extractItem(0, 1, true).isEmpty()));
            intermediate.set(AllDataComponents.SEQUENCED_ASSEMBLY,
                    new SequencedAssemblyRecipe.SequencedAssembly(id, 2, 2F / 6));
            ItemStack returning = machine.recirculationOutputs(plan).stream()
                    .map(port -> port.extractItem(0, 1, false)).filter(stack -> !stack.isEmpty()).findFirst().orElseThrow();
            for(var port : machine.itemInputs(plan)) returning = port.insertItem(0, returning, false);
            assertTrue(returning.isEmpty());
            assertEquals(2, base.getStackInSlot(0).get(AllDataComponents.SEQUENCED_ASSEMBLY).step());
            when(level.getBlockEntity(end.above(2))).thenReturn(null);
            assertFalse(machine.supports(plan));
        }
    }

    // An endpoint on the output belt must feed an open upstream segment before the deployer
    @Test @SuppressWarnings({"rawtypes", "unchecked"})
    void feedsOpenBeltBeforeStationWhenOutputEndIsSelected(){
        var id = ResourceLocation.parse("test:upstream_assembly");
        var typeId = ResourceLocation.parse("create:deploying");
        if(!BuiltInRegistries.RECIPE_TYPE.containsKey(typeId)){
            var registry = (net.minecraft.core.MappedRegistry<RecipeType<?>>)BuiltInRegistries.RECIPE_TYPE;
            registry.unfreeze();
            try{ Registry.register(registry, typeId, new RecipeType<>(){}); }
            finally{ registry.freeze(); }
        }
        ProcessingRecipe stage = mock(ProcessingRecipe.class);
        when(stage.getIngredients()).thenReturn(NonNullList.of(Ingredient.EMPTY,
                Ingredient.of(Items.IRON_INGOT), Ingredient.of(Items.GOLD_NUGGET)));
        when(stage.getFluidIngredients()).thenReturn(NonNullList.create());
        doReturn(BuiltInRegistries.RECIPE_TYPE.get(typeId)).when(stage).getType();
        var recipe = mock(SequencedAssemblyRecipe.class);
        when(recipe.getIngredient()).thenReturn(Ingredient.of(Items.IRON_INGOT));
        when(recipe.getTransitionalItem()).thenReturn(new ItemStack(Items.COMPASS));
        when(recipe.getLoops()).thenReturn(1);
        when(recipe.getSequence()).thenReturn(List.of(new SequencedRecipe(stage)));
        Level level = mock(Level.class);
        when(level.registryAccess()).thenReturn(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        when(level.getBlockState(any())).thenReturn(Blocks.AIR.defaultBlockState());
        var manager = mock(RecipeManager.class);
        when(level.getRecipeManager()).thenReturn(manager);
        when(manager.byKey(id)).thenReturn(Optional.of(new RecipeHolder<>(id, recipe)));
        BlockPos start = new BlockPos(0, 64, 0);
        BlockPos station = start.east(2);
        BlockPos end = start.east(3);
        var belt = mock(BeltBlockEntity.class);
        when(belt.getController()).thenReturn(start);
        when(belt.getMovementFacing()).thenReturn(Direction.EAST);
        when(belt.getDirectionAwareBeltMovementSpeed()).thenReturn(1F);
        for(int idx = 0; idx < 4; idx++) when(level.getBlockEntity(start.east(idx))).thenReturn(belt);
        var deployer = mock(DeployerBlockEntity.class);
        when(deployer.getBlockState()).thenReturn(Blocks.DISPENSER.defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.DOWN));
        when(level.getBlockEntity(station.above(2))).thenReturn(deployer);
        var blocked = new ItemStackHandler(1);
        blocked.setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 64));
        var barrel = new ItemStackHandler(1);
        var open = new ItemStackHandler(1);
        var output = new ItemStackHandler(1);
        var held = new ItemStackHandler(1);
        var plan = new WorkerRecipePlan(id, ResourceLocation.parse("create:sequenced_assembly"),
                WorkerRecipePlan.Operation.PROCESSING,
                List.of(new WorkerRecipePlan.Input(item("gold_nugget"), 1),
                        new WorkerRecipePlan.Input(item("iron_ingot"), 1)), item("clock"), 1);
        BlockState inputFunnel = mock(BlockState.class);
        when(inputFunnel.getBlock()).thenReturn(mock(BeltFunnelBlock.class));
        when(inputFunnel.getValue(BeltFunnelBlock.SHAPE)).thenReturn(BeltFunnelBlock.Shape.PUSHING);
        when(inputFunnel.getOptionalValue(BlockStateProperties.POWERED)).thenReturn(Optional.of(false));
        when(level.getBlockState(start.above())).thenReturn(inputFunnel);
        try(var topology = mockStatic(BeltBlock.class); var access = mockStatic(WorkerContainerAccess.class);
            var funnel = mockStatic(AbstractFunnelBlock.class)){
            topology.when(() -> BeltBlock.getBeltChain(level, start)).thenReturn(
                    List.of(start, start.east(), station, end));
            funnel.when(() -> AbstractFunnelBlock.getFunnelFacing(inputFunnel)).thenReturn(Direction.EAST);
            access.when(() -> WorkerContainerAccess.isLoaded(eq(level), any())).thenReturn(true);
            access.when(() -> WorkerContainerAccess.itemHandlers(level, start, null)).thenReturn(List.of(blocked));
            access.when(() -> WorkerContainerAccess.itemHandlers(level, start.above().west(), null))
                    .thenReturn(List.of(barrel));
            access.when(() -> WorkerContainerAccess.itemHandlers(level, start.east(), null)).thenReturn(List.of(open));
            access.when(() -> WorkerContainerAccess.itemHandlers(level, end, null)).thenReturn(List.of(output));
            access.when(() -> WorkerContainerAccess.itemHandlers(level, station.above(2), null)).thenReturn(List.of(held));
            var machine = WorkerMachineRegistry.resolve(level, end, null);
            assertTrue(machine.supports(plan));
            WorkerArea fullArea = new WorkerArea(start, end.above(2));
            WorkerArea missingDrivers = new WorkerArea(start, end.above());
            assertTrue(machine.supports(plan, fullArea));
            assertFalse(machine.supports(plan, missingDrivers));
            assertEquals(List.of(end), machine.areaOutputs(plan, fullArea,
                    List.of(start.above().west(), end)));
            WorkerArea inputArea = new WorkerArea(start.west(), end.above(2));
            assertEquals(List.of(end), machine.areaOutputs(plan, inputArea,
                    List.of(start.above().west(), end)));
            WorkerArea wideArea = new WorkerArea(start, end.south(2).above(2));
            assertEquals(List.of(end, start.south(2)), machine.areaOutputs(plan, wideArea,
                    List.of(start.south(2), end)));
            assertTrue(machine.itemOutputs(plan, fullArea).contains(output));
            assertFalse(machine.itemOutputs(plan, fullArea).contains(barrel));
            ItemStack workpiece = new ItemStack(Items.IRON_INGOT);
            for(var port : machine.itemInputs(plan)) workpiece = port.insertItem(0, workpiece, false);
            assertTrue(workpiece.isEmpty());
            assertEquals(1, barrel.getStackInSlot(0).getCount());
            assertEquals(List.of(0L, 1L), machine.preloadedInputs(plan, inputArea));
            assertTrue(open.getStackInSlot(0).isEmpty());
            barrel.setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 64));
            workpiece = new ItemStack(Items.IRON_INGOT);
            for(var port : machine.itemInputs(plan)) workpiece = port.insertItem(0, workpiece, false);
            assertTrue(workpiece.isEmpty());
            assertEquals(1, open.getStackInSlot(0).getCount());
            assertTrue(output.getStackInSlot(0).isEmpty());
        }
    }

    private static WorkerResourceKey item(String path){
        return new WorkerResourceKey(WorkerResourceType.ITEM, ResourceLocation.withDefaultNamespace(path));
    }
}
