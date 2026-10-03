package com.rieno.gadgetsandgizmos.lib.inventory;

import com.rieno.gadgetsandgizmos.lib.worker.*;
import com.rieno.gadgetsandgizmos.lib.create.worker.CreateWorkerSequences;
import com.rieno.gadgetsandgizmos.lib.create.worker.CreateWorkerMachines;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.kinetics.belt.BeltBlock;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.saw.SawBlockEntity;
import com.simibubi.create.content.kinetics.press.MechanicalPressBlockEntity;
import com.simibubi.create.content.kinetics.press.PressingRecipe;
import com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity;
import com.simibubi.create.content.fluids.spout.SpoutBlockEntity;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedRecipe;
import net.minecraft.core.NonNullList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WorkerSequenceTest{
    private static final ResourceLocation ID = ResourceLocation.parse("test:assembly");
    @BeforeAll static void bootstrap() throws ClassNotFoundException{
        InventoryTestBootstrap.bootstrap();
        var root = (net.minecraft.core.MappedRegistry<?>) BuiltInRegistries.REGISTRY;
        root.unfreeze();
        try{
            Class.forName(AllDataComponents.class.getName());
        }finally{
            root.freeze();
        }
    }

    @Test void expandsAllLoopsAndKeepsSourceAndStageAcrossReload(){
        var recipe = assembly();
        var level = level(recipe);
        var definition = CreateWorkerSequences.definition(level, new RecipeHolder<>(ID, recipe), recipe);
        var chain = WorkerRecipePlanner.plan(item(Items.CLOCK), 2,
                Map.of(item(Items.IRON_INGOT), 2L, item(Items.GOLD_NUGGET), 6L, item(Items.REDSTONE), 6L),
                List.of(definition), null, plan -> true);
        assertTrue(chain.executable());
        var expanded = WorkerRecipeCatalog.expand(level, chain);
        assertEquals(6, expanded.steps().size());
        for(int idx = 0; idx < 6; idx++){
            var step = expanded.steps().get(idx);
            assertEquals(idx, step.plan().stage());
            assertEquals(2, step.requestedAmount());
            assertEquals(idx == 0 ? item(Items.IRON_INGOT) : item(Items.COMPASS), step.plan().inputs().getLast().resource());
            assertEquals(idx == 5 ? item(Items.CLOCK) : item(Items.COMPASS), step.plan().result());
        }
        UUID source = UUID.randomUUID();
        var orders = expanded.orders(UUID.randomUUID(), "Assembly", 0, null, null, source);
        WorkerTaskQueue queue = new WorkerTaskQueue();
        queue.enqueueAll(orders);
        queue = WorkerTaskQueue.fromTag(queue.toTag());
        assertEquals(source, queue.current().sourceEndpointId());
        assertEquals(5, queue.planned().getLast().recipePlan().stage());
        assertEquals(source, queue.planned().getLast().sourceEndpointId());
    }

    @Test void rejectsScheduleWithoutEnoughIngredientsForEveryLoop(){
        var recipe = assembly();
        var level = level(recipe);
        var definition = CreateWorkerSequences.definition(level, new RecipeHolder<>(ID, recipe), recipe);
        var result = WorkerRecipePlanner.planDetailed(item(Items.CLOCK), 1,
                Map.of(item(Items.IRON_INGOT), 1L, item(Items.GOLD_NUGGET), 2L, item(Items.REDSTONE), 3L),
                List.of(definition), null, plan -> true);
        assertFalse(result.chain().executable());
        assertTrue(result.details().stream().anyMatch(detail -> detail.contains("gold_nugget")
                && detail.contains("linked stock 2")), result.details().toString());
    }

    @Test void doesNotCollectOrReuseAnIntermediateAtTheWrongStage(){
        var recipe = assembly();
        var plan = new WorkerRecipePlan(ID, ResourceLocation.withDefaultNamespace("smelting"),
                WorkerRecipePlan.Operation.PROCESSING, List.of(new WorkerRecipePlan.Input(item(Items.COMPASS), 1)),
                item(Items.COMPASS), 1, 2);
        ItemStack stack = new ItemStack(Items.COMPASS);
        stack.set(AllDataComponents.SEQUENCED_ASSEMBLY, new SequencedAssemblyRecipe.SequencedAssembly(ID, 2, 0.3F));
        assertTrue(CreateWorkerSequences.matchesInput(plan, recipe, stack));
        assertFalse(CreateWorkerSequences.matchesOutput(plan, recipe, stack));
        stack.set(AllDataComponents.SEQUENCED_ASSEMBLY, new SequencedAssemblyRecipe.SequencedAssembly(ID, 3, 0.5F));
        assertFalse(CreateWorkerSequences.matchesInput(plan, recipe, stack));
        assertTrue(CreateWorkerSequences.matchesOutput(plan, recipe, stack));
    }

    @Test void recognizesOnlyRealIntermediateStacksForAreaRecirculation(){
        var recipe = assembly();
        var level = level(recipe);
        var plan = new WorkerRecipePlan(ID, ResourceLocation.withDefaultNamespace("smelting"),
                WorkerRecipePlan.Operation.PROCESSING, List.of(new WorkerRecipePlan.Input(item(Items.IRON_INGOT), 1)),
                item(Items.CLOCK), 1);
        ItemStack intermediate = new ItemStack(Items.COMPASS);
        assertFalse(WorkerRecipeCatalog.recirculates(level, plan, intermediate));
        intermediate.set(AllDataComponents.SEQUENCED_ASSEMBLY,
                new SequencedAssemblyRecipe.SequencedAssembly(ID, 2, 0.3F));
        assertTrue(WorkerRecipeCatalog.recirculates(level, plan, intermediate));
        intermediate.set(AllDataComponents.SEQUENCED_ASSEMBLY,
                new SequencedAssemblyRecipe.SequencedAssembly(ID, 6, 1F));
        assertFalse(WorkerRecipeCatalog.recirculates(level, plan, intermediate));
    }

    @Test void ordersBeltInputAndOutputByMovementDirection(){
        Level level = mock(Level.class);
        BeltBlockEntity belt = mock(BeltBlockEntity.class);
        List<BlockPos> chain = List.of(new BlockPos(0, 64, 0), new BlockPos(1, 64, 0),
                new BlockPos(2, 64, 0));
        when(belt.getController()).thenReturn(chain.getFirst());
        try(MockedStatic<BeltBlock> blocks = mockStatic(BeltBlock.class)){
            blocks.when(() -> BeltBlock.getBeltChain(level, chain.getFirst())).thenReturn(chain);
            when(belt.getDirectionAwareBeltMovementSpeed()).thenReturn(0.2F, -0.2F, 0F);
            assertEquals(chain, CreateWorkerMachines.movingBeltSegments(level, belt));
            assertEquals(List.of(chain.getLast(), chain.get(1), chain.getFirst()),
                    CreateWorkerMachines.movingBeltSegments(level, belt));
            assertTrue(CreateWorkerMachines.movingBeltSegments(level, belt).isEmpty());
        }
    }

    @Test void tracesProcessingAcrossSawBetweenSeparateBeltChains(){
        Level level = mock(Level.class);
        BeltBlockEntity left = mock(BeltBlockEntity.class);
        BeltBlockEntity right = mock(BeltBlockEntity.class);
        SawBlockEntity saw = mock(SawBlockEntity.class);
        BlockState sawState = mock(BlockState.class);
        BlockPos input = new BlockPos(0, 64, 0);
        BlockPos sawPos = input.east(2);
        BlockPos output = sawPos.east();
        when(level.isLoaded(any(BlockPos.class))).thenReturn(true);
        when(level.getBlockEntity(input)).thenReturn(left);
        when(level.getBlockEntity(input.east())).thenReturn(left);
        when(level.getBlockEntity(sawPos)).thenReturn(saw);
        when(level.getBlockEntity(output)).thenReturn(right);
        when(level.getBlockEntity(output.east())).thenReturn(right);
        when(left.getController()).thenReturn(input);
        when(right.getController()).thenReturn(output);
        when(left.getDirectionAwareBeltMovementSpeed()).thenReturn(0.2F);
        when(right.getDirectionAwareBeltMovementSpeed()).thenReturn(0.2F);
        when(left.getMovementFacing()).thenReturn(Direction.EAST);
        when(right.getMovementFacing()).thenReturn(Direction.EAST);
        when(saw.getSpeed()).thenReturn(16F);
        when(saw.getBlockState()).thenReturn(sawState);
        when(sawState.getValue(BlockStateProperties.FACING)).thenReturn(Direction.UP);
        when(saw.getItemMovementVec()).thenReturn(new Vec3(1, 0, 0));
        try(MockedStatic<BeltBlock> blocks = mockStatic(BeltBlock.class)){
            blocks.when(() -> BeltBlock.getBeltChain(level, input)).thenReturn(List.of(input, input.east()));
            blocks.when(() -> BeltBlock.getBeltChain(level, output)).thenReturn(List.of(output, output.east()));
            List<BlockPos> expected = List.of(input, input.east(), sawPos, output, output.east());
            assertEquals(expected, CreateWorkerMachines.processingLine(level, input));
            assertEquals(expected, CreateWorkerMachines.processingLine(level, sawPos));
            assertEquals(expected, CreateWorkerMachines.processingLine(level, output.east()));
        }
    }

    @Test void resolvesTheExactPressingStepWithItsProgressComponent(){
        Level level = level(assembly());
        BlockPos pos = new BlockPos(0, 64, 0);
        BeltBlockEntity belt = mock(BeltBlockEntity.class);
        MechanicalPressBlockEntity press = mock(MechanicalPressBlockEntity.class);
        when(level.isLoaded(any(BlockPos.class))).thenReturn(true);
        when(level.getBlockEntity(pos)).thenReturn(belt);
        when(level.getBlockState(pos)).thenReturn(Blocks.AIR.defaultBlockState());
        when(level.getBlockEntity(pos.above(2))).thenReturn(press);
        when(press.getRecipe(any(ItemStack.class))).thenAnswer(invocation -> {
            ItemStack input = invocation.getArgument(0);
            var progress = input.get(AllDataComponents.SEQUENCED_ASSEMBLY);
            assertNotNull(progress);
            assertEquals(ID, progress.id());
            assertEquals(1, progress.step());
            return Optional.of(new RecipeHolder<>(ID, mock(PressingRecipe.class)));
        });
        var plan = new WorkerRecipePlan(ID, ResourceLocation.parse("create:pressing"),
                WorkerRecipePlan.Operation.PROCESSING,
                List.of(new WorkerRecipePlan.Input(item(Items.COMPASS), 1)), item(Items.COMPASS), 1, 1);
        assertTrue(WorkerMachineRegistry.resolve(level, pos, null).supports(plan));
    }

    @Test void recognizesAnEmbeddedPressStageWhenThePressHasNoOuterRecipeMatch(){
        Level level = level(assembly());
        BlockPos pos = new BlockPos(0, 64, 0);
        when(level.isLoaded(any(BlockPos.class))).thenReturn(true);
        when(level.getBlockState(pos)).thenReturn(Blocks.AIR.defaultBlockState());
        when(level.getBlockEntity(pos)).thenReturn(mock(BeltBlockEntity.class));
        var press = mock(MechanicalPressBlockEntity.class);
        when(level.getBlockEntity(pos.above(2))).thenReturn(press);
        when(press.getRecipe(any(ItemStack.class))).thenReturn(Optional.empty());
        var plan = new WorkerRecipePlan(ID, ResourceLocation.parse("create:pressing"),
                WorkerRecipePlan.Operation.PROCESSING,
                List.of(new WorkerRecipePlan.Input(item(Items.IRON_INGOT), 1)), item(Items.COMPASS), 1, 0);
        assertTrue(WorkerMachineRegistry.resolve(level, pos, null).supports(plan));
    }

    @Test @SuppressWarnings({"rawtypes", "unchecked"})
    void resolvesSawAndPressAsOneAssemblyRoute(){
        RecipeType<?> cutting = createType("cutting");
        RecipeType<?> pressing = createType("pressing");
        SequencedAssemblyRecipe recipe = mock(SequencedAssemblyRecipe.class);
        when(recipe.getIngredient()).thenReturn(Ingredient.of(Items.IRON_INGOT));
        when(recipe.getTransitionalItem()).thenReturn(new ItemStack(Items.COMPASS));
        when(recipe.getLoops()).thenReturn(1);
        ProcessingRecipe sawStep = mock(ProcessingRecipe.class);
        ProcessingRecipe pressStep = mock(ProcessingRecipe.class);
        when(sawStep.getIngredients()).thenReturn(NonNullList.of(Ingredient.EMPTY,
                Ingredient.of(Items.IRON_INGOT)));
        when(pressStep.getIngredients()).thenReturn(NonNullList.of(Ingredient.EMPTY,
                Ingredient.of(Items.COMPASS)));
        when(sawStep.getFluidIngredients()).thenReturn(NonNullList.create());
        when(pressStep.getFluidIngredients()).thenReturn(NonNullList.create());
        doReturn(cutting).when(sawStep).getType();
        doReturn(pressing).when(pressStep).getType();
        when(recipe.getSequence()).thenReturn(List.of(new SequencedRecipe(sawStep), new SequencedRecipe(pressStep)));
        Level level = level(recipe);
        BlockPos start = new BlockPos(0, 64, 0);
        BlockPos sawPos = start.east(2);
        BlockPos finish = start.east(3);
        BeltBlockEntity first = mock(BeltBlockEntity.class);
        BeltBlockEntity last = mock(BeltBlockEntity.class);
        SawBlockEntity saw = mock(SawBlockEntity.class);
        MechanicalPressBlockEntity press = mock(MechanicalPressBlockEntity.class);
        when(level.isLoaded(any(BlockPos.class))).thenReturn(true);
        when(level.getBlockState(any(BlockPos.class))).thenReturn(Blocks.AIR.defaultBlockState());
        when(level.getBlockEntity(start)).thenReturn(first);
        when(level.getBlockEntity(start.east())).thenReturn(first);
        when(level.getBlockEntity(sawPos)).thenReturn(saw);
        when(level.getBlockEntity(finish)).thenReturn(last);
        when(level.getBlockEntity(finish.above(2))).thenReturn(press);
        when(first.getController()).thenReturn(start);
        when(last.getController()).thenReturn(finish);
        when(first.getDirectionAwareBeltMovementSpeed()).thenReturn(1F);
        when(last.getDirectionAwareBeltMovementSpeed()).thenReturn(1F);
        when(first.getMovementFacing()).thenReturn(Direction.EAST);
        when(last.getMovementFacing()).thenReturn(Direction.EAST);
        when(saw.getSpeed()).thenReturn(16F);
        BlockState sawState = mock(BlockState.class);
        when(saw.getBlockState()).thenReturn(sawState);
        when(sawState.getValue(BlockStateProperties.FACING)).thenReturn(Direction.UP);
        when(saw.getItemMovementVec()).thenReturn(new Vec3(1, 0, 0));
        when(press.getRecipe(any(ItemStack.class))).thenReturn(Optional.empty());
        WorkerRecipePlan plan = new WorkerRecipePlan(ID, ResourceLocation.parse("create:sequenced_assembly"),
                WorkerRecipePlan.Operation.PROCESSING,
                List.of(new WorkerRecipePlan.Input(item(Items.IRON_INGOT), 1)), item(Items.CLOCK), 1);
        try(MockedStatic<BeltBlock> blocks = mockStatic(BeltBlock.class);
            MockedStatic<WorkerContainerAccess> access = mockStatic(WorkerContainerAccess.class)){
            blocks.when(() -> BeltBlock.getBeltChain(level, start)).thenReturn(List.of(start, start.east()));
            blocks.when(() -> BeltBlock.getBeltChain(level, finish)).thenReturn(List.of(finish));
            access.when(() -> WorkerContainerAccess.isLoaded(eq(level), any())).thenReturn(true);
            var input = new ItemStackHandler(1);
            var output = new ItemStackHandler(1);
            access.when(() -> WorkerContainerAccess.itemHandlers(level, start, null)).thenReturn(List.of(input));
            access.when(() -> WorkerContainerAccess.itemHandlers(level, finish, null)).thenReturn(List.of(output));
            var machine = WorkerMachineRegistry.resolve(level, sawPos, null);
            assertTrue(machine.supports(plan));
            assertTrue(machine.supports(plan, new WorkerArea(start, finish.above(2))));
            assertFalse(machine.supports(plan, new WorkerArea(start, finish.above())));
            assertEquals(List.of(output), machine.itemOutputs(plan, new WorkerArea(start, finish.above(2))));
            assertEquals(List.of(finish), machine.areaOutputs(plan,
                    new WorkerArea(start, finish.above(2)), List.of(start, finish)));
            BlockPos designatedOutput = finish.above();
            var designatedInput = new ItemStackHandler(1);
            var designatedResult = new ItemStackHandler(1);
            access.when(() -> WorkerContainerAccess.itemHandlers(level, start, Direction.UP))
                    .thenReturn(List.of(designatedInput));
            access.when(() -> WorkerContainerAccess.itemHandlers(level, designatedOutput, Direction.DOWN))
                    .thenReturn(List.of(designatedResult));
            var site = new com.rieno.gadgetsandgizmos.lib.worker.WorkerMachineSite(
                    new WorkerArea(start, finish.above(2)),
                    List.of(new com.rieno.gadgetsandgizmos.lib.worker.WorkerMachineSite.Port(start, Direction.UP)),
                    List.of(new com.rieno.gadgetsandgizmos.lib.worker.WorkerMachineSite.Port(
                            designatedOutput, Direction.DOWN)));
            assertTrue(machine.supportsAt(plan, site));
            assertTrue(machine.itemInputsAt(plan, site).stream().anyMatch(handler ->
                    handler.insertItem(0, new ItemStack(Items.IRON_INGOT), true).isEmpty()));
        }
    }

    @Test @SuppressWarnings({"rawtypes", "unchecked"})
    void acceptsRefillableSpoutAcrossAssemblyLoops(){
        RecipeType<?> filling = createType("filling");
        SequencedAssemblyRecipe recipe = mock(SequencedAssemblyRecipe.class);
        when(recipe.getIngredient()).thenReturn(Ingredient.of(Items.IRON_INGOT));
        when(recipe.getTransitionalItem()).thenReturn(new ItemStack(Items.COMPASS));
        when(recipe.getLoops()).thenReturn(3);
        ProcessingRecipe stage = mock(ProcessingRecipe.class);
        when(stage.getIngredients()).thenReturn(NonNullList.of(Ingredient.EMPTY,
                Ingredient.of(Items.IRON_INGOT)));
        when(stage.getFluidIngredients()).thenReturn(NonNullList.of(null,
                SizedFluidIngredient.of(Fluids.WATER, 100)));
        doReturn(filling).when(stage).getType();
        when(recipe.getSequence()).thenReturn(List.of(new SequencedRecipe(stage)));
        Level level = level(recipe);
        BlockPos start = new BlockPos(0, 64, 0);
        BlockPos end = start.east();
        BeltBlockEntity belt = mock(BeltBlockEntity.class);
        when(belt.getController()).thenReturn(start);
        when(belt.getMovementFacing()).thenReturn(Direction.EAST);
        when(belt.getDirectionAwareBeltMovementSpeed()).thenReturn(1F);
        when(level.getBlockEntity(start)).thenReturn(belt);
        when(level.getBlockEntity(end)).thenReturn(belt);
        when(level.getBlockEntity(start.above(2))).thenReturn(mock(SpoutBlockEntity.class));
        when(level.getBlockState(any(BlockPos.class))).thenReturn(Blocks.AIR.defaultBlockState());
        WorkerRecipePlan plan = new WorkerRecipePlan(ID, ResourceLocation.parse("create:sequenced_assembly"),
                WorkerRecipePlan.Operation.PROCESSING,
                List.of(new WorkerRecipePlan.Input(new WorkerResourceKey(WorkerResourceType.FLUID,
                        BuiltInRegistries.FLUID.getKey(Fluids.WATER)), 300),
                        new WorkerRecipePlan.Input(item(Items.IRON_INGOT), 1)), item(Items.CLOCK), 1);
        try(MockedStatic<BeltBlock> blocks = mockStatic(BeltBlock.class);
            MockedStatic<WorkerContainerAccess> access = mockStatic(WorkerContainerAccess.class)){
            blocks.when(() -> BeltBlock.getBeltChain(level, start)).thenReturn(List.of(start, end));
            access.when(() -> WorkerContainerAccess.isLoaded(eq(level), any())).thenReturn(true);
            access.when(() -> WorkerContainerAccess.itemHandlers(level, start, null))
                    .thenReturn(List.of(new ItemStackHandler(1)));
            access.when(() -> WorkerContainerAccess.itemHandlers(level, end, null))
                    .thenReturn(List.of(new ItemStackHandler(1)));
            access.when(() -> WorkerContainerAccess.fluidHandlers(level, start.above(2), null))
                    .thenReturn(List.of(new FluidTank(100)));
            assertTrue(WorkerMachineRegistry.resolve(level, start, null)
                    .supports(plan, new WorkerArea(start, end.above(2))));
        }
    }

    @Test @SuppressWarnings({"rawtypes", "unchecked"})
    void routesMarkedWorkpieceAndDeployerSuppliesSeparately(){
        RecipeType<?> deploying = createType("deploying");
        SequencedAssemblyRecipe recipe = mock(SequencedAssemblyRecipe.class);
        when(recipe.getIngredient()).thenReturn(Ingredient.of(Items.GOLD_INGOT));
        when(recipe.getTransitionalItem()).thenReturn(new ItemStack(Items.COMPASS));
        when(recipe.getLoops()).thenReturn(1);
        when(recipe.getResultItem(any())).thenReturn(new ItemStack(Items.CLOCK));
        // Use vanilla items here so the test does not depend on Create's item registry bootstrap.
        List<Item> supplies = List.of(Items.STICK, Items.IRON_INGOT, Items.IRON_NUGGET);
        List<SequencedRecipe<?>> steps = new java.util.ArrayList<>();
        for(Item supply : supplies){
            ProcessingRecipe step = mock(ProcessingRecipe.class);
            when(step.getIngredients()).thenReturn(NonNullList.of(Ingredient.EMPTY,
                    Ingredient.of(Items.COMPASS), Ingredient.of(supply)));
            when(step.getFluidIngredients()).thenReturn(NonNullList.create());
            doReturn(deploying).when(step).getType();
            steps.add(new SequencedRecipe(step));
        }
        when(recipe.getSequence()).thenReturn(steps);
        Level level = level(recipe);
        BlockPos start = new BlockPos(0, 64, 0);
        List<BlockPos> line = java.util.stream.IntStream.range(0, 6).mapToObj(start::east).toList();
        BeltBlockEntity belt = mock(BeltBlockEntity.class);
        when(belt.getController()).thenReturn(start);
        when(belt.getMovementFacing()).thenReturn(Direction.EAST);
        when(belt.getDirectionAwareBeltMovementSpeed()).thenReturn(1F);
        when(level.isLoaded(any(BlockPos.class))).thenReturn(true);
        when(level.getBlockState(any(BlockPos.class))).thenReturn(Blocks.AIR.defaultBlockState());
        for(BlockPos pos : line) when(level.getBlockEntity(pos)).thenReturn(belt);
        BlockState driverState = mock(BlockState.class);
        when(driverState.getValue(BlockStateProperties.FACING)).thenReturn(Direction.DOWN);
        List<com.rieno.gadgetsandgizmos.lib.worker.WorkerMachineSite.Port> inputPorts = new java.util.ArrayList<>();
        inputPorts.add(new com.rieno.gadgetsandgizmos.lib.worker.WorkerMachineSite.Port(
                start.above(2), Direction.NORTH));
        for(int idx = 2; idx <= 4; idx++){
            BlockPos pos = line.get(idx).above(2);
            DeployerBlockEntity driver = mock(DeployerBlockEntity.class);
            when(driver.getBlockState()).thenReturn(driverState);
            when(level.getBlockEntity(pos)).thenReturn(driver);
            inputPorts.add(new com.rieno.gadgetsandgizmos.lib.worker.WorkerMachineSite.Port(pos, Direction.NORTH));
        }
        BlockPos output = line.getLast().east();
        WorkerRecipePlan plan = new WorkerRecipePlan(ID, ResourceLocation.parse("create:sequenced_assembly"),
                WorkerRecipePlan.Operation.PROCESSING,
                List.of(new WorkerRecipePlan.Input(item(Items.STICK), 1),
                        new WorkerRecipePlan.Input(item(Items.IRON_INGOT), 1),
                        new WorkerRecipePlan.Input(item(Items.IRON_NUGGET), 1),
                        new WorkerRecipePlan.Input(item(Items.GOLD_INGOT), 1)), item(Items.CLOCK), 1);
        try(MockedStatic<BeltBlock> blocks = mockStatic(BeltBlock.class);
            MockedStatic<WorkerContainerAccess> access = mockStatic(WorkerContainerAccess.class)){
            blocks.when(() -> BeltBlock.getBeltChain(level, start)).thenReturn(line);
            access.when(() -> WorkerContainerAccess.isLoaded(eq(level), any())).thenReturn(true);
            ItemStackHandler workpiece = new ItemStackHandler(1);
            access.when(() -> WorkerContainerAccess.itemHandlers(level, start.above(2), Direction.NORTH))
                    .thenReturn(List.of(workpiece));
            for(int idx = 2; idx <= 4; idx++){
                BlockPos pos = line.get(idx).above(2);
                ItemStackHandler held = new ItemStackHandler(1);
                held.setStackInSlot(0, new ItemStack(supplies.get(idx - 2)));
                access.when(() -> WorkerContainerAccess.itemHandlers(level, pos, null)).thenReturn(List.of(held));
                access.when(() -> WorkerContainerAccess.itemHandlers(level, pos, Direction.NORTH)).thenReturn(List.of(held));
            }
            access.when(() -> WorkerContainerAccess.itemHandlers(level, output, Direction.NORTH))
                    .thenReturn(List.of(new ItemStackHandler(1)));
            var site = new WorkerMachineSite(new WorkerArea(start, output.above(2)), inputPorts,
                    List.of(new WorkerMachineSite.Port(output, Direction.NORTH)));
            var machine = WorkerMachineRegistry.resolve(level, start, null);
            assertTrue(machine.supportsAt(plan, site));
            assertEquals(4, machine.itemInputsAt(plan, site).size());
            var definition = CreateWorkerSequences.definition(level, new RecipeHolder<>(ID, recipe), recipe);
            assertEquals(List.of(1L, 1L, 1L, 0L), machine.preloadedInputsAt(definition, site));
            var planned = WorkerRecipePlanner.planDetailed(item(Items.CLOCK), 1,
                    Map.of(item(Items.GOLD_INGOT), 1L), new WorkerRecipeIndex(List.of(definition)), null,
                    candidate -> machine.supportsAt(candidate, site), candidate -> machine.supportsAt(candidate, site),
                    null, candidate -> 0, (candidate, idx) ->
                            machine.preloadedInputsAt(candidate, site).get(idx));
            assertTrue(planned.chain().executable(), planned.failure().message() + " " + planned.details());
            assertTrue(machine.routeDiagnostics(plan, site).isEmpty());
            var stageOnly = new WorkerMachineSite(site.area(), inputPorts.subList(1, inputPorts.size()),
                    site.outputs());
            assertFalse(machine.supportsAt(plan, stageOnly));
            assertTrue(machine.routeDiagnostics(plan, stageOnly).getFirst()
                    .contains("workpiece input"));
        }
    }

    private static RecipeType<?> createType(String path){
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("create", path);
        RecipeType<?> type = BuiltInRegistries.RECIPE_TYPE.get(id);
        if(type != null && BuiltInRegistries.RECIPE_TYPE.containsKey(id)) return type;
        var registry = (net.minecraft.core.MappedRegistry<RecipeType<?>>)BuiltInRegistries.RECIPE_TYPE;
        registry.unfreeze();
        try{ return net.minecraft.core.Registry.register(registry, id, new RecipeType<>(){}); }
        finally{ registry.freeze(); }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static SequencedAssemblyRecipe assembly(){
        var recipe = mock(SequencedAssemblyRecipe.class);
        when(recipe.getIngredient()).thenReturn(Ingredient.of(Items.IRON_INGOT));
        when(recipe.getTransitionalItem()).thenReturn(new ItemStack(Items.COMPASS));
        when(recipe.getResultItem(any())).thenReturn(new ItemStack(Items.CLOCK));
        doReturn(RecipeType.CRAFTING).when(recipe).getType();
        when(recipe.getLoops()).thenReturn(3);
        ProcessingRecipe first = mock(ProcessingRecipe.class);
        ProcessingRecipe second = mock(ProcessingRecipe.class);
        when(first.getIngredients()).thenReturn(NonNullList.of(Ingredient.EMPTY,
                Ingredient.of(Items.COMPASS, Items.IRON_INGOT), Ingredient.of(Items.GOLD_NUGGET)));
        when(second.getIngredients()).thenReturn(NonNullList.of(Ingredient.EMPTY,
                Ingredient.of(Items.COMPASS), Ingredient.of(Items.REDSTONE)));
        when(first.getFluidIngredients()).thenReturn(NonNullList.create());
        when(second.getFluidIngredients()).thenReturn(NonNullList.create());
        doReturn(RecipeType.SMELTING).when(first).getType();
        doReturn(RecipeType.BLASTING).when(second).getType();
        when(recipe.getSequence()).thenReturn(List.of(new SequencedRecipe(first), new SequencedRecipe(second)));
        return recipe;
    }

    private static Level level(SequencedAssemblyRecipe recipe){
        Level level = mock(Level.class);
        var manager = mock(RecipeManager.class);
        when(level.getRecipeManager()).thenReturn(manager);
        when(level.registryAccess()).thenReturn(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        when(manager.byKey(ID)).thenReturn(Optional.of(new RecipeHolder<>(ID, recipe)));
        return level;
    }
    private static WorkerResourceKey item(Item item){
        return new WorkerResourceKey(WorkerResourceType.ITEM, BuiltInRegistries.ITEM.getKey(item));
    }
}
