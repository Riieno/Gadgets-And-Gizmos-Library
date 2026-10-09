package com.rieno.gadgetsandgizmos.lib.create.worker;

import com.rieno.gadgetsandgizmos.lib.worker.WorkerMachine;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerArea;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerMachineSite;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerContainerAccess;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerMachineRegistry;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerRecipePlan;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerResourceKey;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerResourceType;
import com.simibubi.create.content.kinetics.press.MechanicalPressBlockEntity;
import com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlockEntity;
import com.simibubi.create.content.kinetics.crafter.RecipeGridHandler;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltBlock;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.AdditionalAnswers.delegatesTo;

class CreateWorkerMachinesTest {
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        try(var loader = mockStatic(LoadingModList.class)) {
            LoadingModList mods = mock(LoadingModList.class);
            when(mods.getModFiles()).thenReturn(List.of());
            loader.when(LoadingModList::get).thenReturn(mods);
            Bootstrap.bootStrap();
        }
    }

    @Test void linkedPressUsesItsOpenWorkPosition() {
        Level level = mock(Level.class);
        BlockPos work = new BlockPos(4, 64, 7);
        BlockPos pressPos = work.above(2);
        MechanicalPressBlockEntity press = mock(MechanicalPressBlockEntity.class);
        BlockState air = mock(BlockState.class);
        when(air.isAir()).thenReturn(true);
        when(level.getBlockState(work)).thenReturn(air);
        when(level.getBlockEntity(pressPos)).thenReturn(press);

        CreateWorkerMachines adapter = new CreateWorkerMachines();
        assertEquals(work, adapter.accessPositions(new WorkerMachineRegistry.Context(level, pressPos, null)).getFirst());
        WorkerMachine machine = adapter.resolve(new WorkerMachineRegistry.Context(level, work, null));
        assertNotNull(machine);
        assertInstanceOf(GroundWorkpieceHandler.class, machine.itemInputs(null).getFirst());
        assertInstanceOf(GroundWorkpieceHandler.class, machine.itemOutputs(null).getFirst());
    }

    @Test void linkedDepotRecognizesPressTwoBlocksAbove() {
        Level level = mock(Level.class);
        BlockPos depotPos = new BlockPos(4, 64, 7);
        DepotBlockEntity depot = mock(DepotBlockEntity.class);
        MechanicalPressBlockEntity press = mock(MechanicalPressBlockEntity.class);
        RecipeManager manager = mock(RecipeManager.class);
        ResourceLocation id = ResourceLocation.parse("test:press_iron");
        RecipeHolder<?> recipe = new RecipeHolder<>(id,
                mock(com.simibubi.create.content.processing.recipe.ProcessingRecipe.class));
        when(level.getBlockEntity(depotPos)).thenReturn(depot);
        when(level.getBlockEntity(depotPos.above(2))).thenReturn(press);
        when(level.getRecipeManager()).thenReturn(manager);
        doReturn(Optional.of(recipe)).when(manager).byKey(id);
        doReturn(Optional.of(recipe)).when(press).getRecipe(any());
        WorkerRecipePlan plan = new WorkerRecipePlan(id, ResourceLocation.parse("create:pressing"),
                WorkerRecipePlan.Operation.PROCESSING,
                List.of(new WorkerRecipePlan.Input(new WorkerResourceKey(WorkerResourceType.ITEM,
                        BuiltInRegistries.ITEM.getKey(Items.IRON_INGOT)), 1L)),
                new WorkerResourceKey(WorkerResourceType.ITEM,
                        BuiltInRegistries.ITEM.getKey(Items.IRON_NUGGET)), 1L);
        WorkerMachine machine = new CreateWorkerMachines().resolve(
                new WorkerMachineRegistry.Context(level, depotPos, null));
        assertNotNull(machine);
        assertTrue(machine.supports(plan));
    }

    @Test void stagedCrafterInputsIncludeEveryOccupiedCell() {
        Level level = mock(Level.class);
        MechanicalCrafterBlockEntity first = mock(MechanicalCrafterBlockEntity.class);
        MechanicalCrafterBlockEntity second = mock(MechanicalCrafterBlockEntity.class);
        BlockPos firstPos = new BlockPos(2, 64, 3);
        BlockPos secondPos = firstPos.east();
        when(first.getBlockPos()).thenReturn(firstPos);
        when(second.getBlockPos()).thenReturn(secondPos);
        var firstInventory = new ItemStackHandler(1);
        firstInventory.setStackInSlot(0, new net.minecraft.world.item.ItemStack(Items.OAK_PLANKS));
        var secondInventory = new ItemStackHandler(1);
        secondInventory.setStackInSlot(0, new net.minecraft.world.item.ItemStack(Items.OAK_SLAB));
        when(first.getInventory()).thenReturn(mock(MechanicalCrafterBlockEntity.Inventory.class,
                delegatesTo(firstInventory)));
        when(second.getInventory()).thenReturn(mock(MechanicalCrafterBlockEntity.Inventory.class,
                delegatesTo(secondInventory)));
        try(var grid = mockStatic(RecipeGridHandler.class)) {
            grid.when(() -> RecipeGridHandler.getAllCraftersOfChain(first))
                    .thenReturn(List.of(first, second));
            WorkerMachine machine = new MechanicalCrafterWorkerMachine(
                    new WorkerMachineRegistry.Context(level, firstPos, null), first);
            assertEquals(2, machine.stagedItemInputs(null, null).size());
            assertEquals(Items.OAK_PLANKS, machine.stagedItemInputs(null, null).getFirst()
                    .getStackInSlot(0).getItem());
            assertEquals(Items.OAK_SLAB, machine.stagedItemInputs(null, null).get(1)
                    .getStackInSlot(0).getItem());
        }
    }

    @Test void markedBeltInputMustReachThePress(){
        Level level = mock(Level.class);
        BlockPos start = new BlockPos(4, 64, 7), work = start.east(), end = work.east();
        BeltBlockEntity belt = mock(BeltBlockEntity.class);
        when(belt.getController()).thenReturn(start);
        when(belt.getMovementFacing()).thenReturn(Direction.EAST);
        when(belt.getDirectionAwareBeltMovementSpeed()).thenReturn(1F);
        for(var pos : List.of(start, work, end)) when(level.getBlockEntity(pos)).thenReturn(belt);
        MechanicalPressBlockEntity press = mock(MechanicalPressBlockEntity.class);
        when(level.getBlockEntity(work.above(2))).thenReturn(press);
        RecipeManager manager = mock(RecipeManager.class);
        when(level.getRecipeManager()).thenReturn(manager);
        ResourceLocation id = ResourceLocation.parse("test:press_iron");
        RecipeHolder<?> recipe = new RecipeHolder<>(id,
                mock(com.simibubi.create.content.processing.recipe.ProcessingRecipe.class));
        doReturn(Optional.of(recipe)).when(manager).byKey(id);
        doReturn(Optional.of(recipe)).when(press).getRecipe(any());
        WorkerRecipePlan plan = new WorkerRecipePlan(id, ResourceLocation.parse("create:pressing"),
                WorkerRecipePlan.Operation.PROCESSING,
                List.of(new WorkerRecipePlan.Input(new WorkerResourceKey(WorkerResourceType.ITEM,
                        BuiltInRegistries.ITEM.getKey(Items.IRON_INGOT)), 1L)),
                new WorkerResourceKey(WorkerResourceType.ITEM, BuiltInRegistries.ITEM.getKey(Items.IRON_NUGGET)), 1L);
        var input = new ItemStackHandler(1);
        var workInput = new ItemStackHandler(1);
        var output = new ItemStackHandler(1);
        try(var blocks = mockStatic(BeltBlock.class);
            var access = mockStatic(WorkerContainerAccess.class)){
            blocks.when(() -> BeltBlock.getBeltChain(level, start)).thenReturn(List.of(start, work, end));
            access.when(() -> WorkerContainerAccess.isLoaded(eq(level), any())).thenReturn(true);
            access.when(() -> WorkerContainerAccess.itemHandlers(level, work, null)).thenReturn(List.of(workInput));
            access.when(() -> WorkerContainerAccess.itemHandlers(level, start, Direction.UP)).thenReturn(List.of(input));
            access.when(() -> WorkerContainerAccess.itemHandlers(level, end, Direction.UP)).thenReturn(List.of(output));
            var machine = new CreateWorkerMachines().resolve(new WorkerMachineRegistry.Context(level, work, null));
            assertTrue(machine.supports(plan));
            assertEquals(List.of(workInput), machine.itemInputs(plan));
            var site = new WorkerMachineSite(new WorkerArea(start, end.above(2)),
                    List.of(new WorkerMachineSite.Port(start, Direction.UP)), List.of());
            assertTrue(machine.supportsAt(plan, site));
            assertEquals(List.of(input), machine.itemInputsAt(plan, site));
            var downstream = new WorkerMachineSite(site.area(),
                    List.of(new WorkerMachineSite.Port(end, Direction.UP)), List.of());
            assertFalse(machine.supportsAt(plan, downstream));
            assertTrue(machine.itemInputsAt(plan, downstream).isEmpty());
        }
    }
}
