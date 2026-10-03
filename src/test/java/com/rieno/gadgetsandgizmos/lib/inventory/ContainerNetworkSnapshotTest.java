package com.rieno.gadgetsandgizmos.lib.inventory;

import com.rieno.gadgetsandgizmos.lib.power.LongEnergyStorage;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerContainerAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ContainerNetworkSnapshotTest{
    @BeforeAll static void bootstrap(){ InventoryTestBootstrap.bootstrap(); }

    // Repeated links must not multiply cargo, and long FE values must stay exact
    @Test void combinesItemsFluidsAndLongEnergyOncePerContainer(){
        var level = mock(Level.class);
        var target = target(level, BlockPos.ZERO);
        var second = target(level, BlockPos.ZERO.east());
        var items = new ItemStackHandler(2);
        items.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 64));
        items.setStackInSlot(1, new ItemStack(Items.IRON_INGOT, 12));
        var water = spy(new FluidStack(Fluids.WATER, 1500));
        doReturn(net.minecraft.network.chat.Component.literal("Water")).when(water).getHoverName();
        var shared = mock(IItemHandler.class, withSettings().extraInterfaces(IFluidHandler.class));
        when(shared.getSlots()).thenReturn(2);
        when(shared.getStackInSlot(anyInt())).thenAnswer(call -> items.getStackInSlot(call.getArgument(0)));
        var fluid = (IFluidHandler) shared;
        when(fluid.getTanks()).thenReturn(1);
        when(fluid.getFluidInTank(0)).thenReturn(water);
        when(fluid.getTankCapacity(0)).thenReturn(4000);
        when(((LongEnergyStorage) target).getEnergyStored()).thenReturn(8_000_000_000L);
        when(((LongEnergyStorage) target).getMaxEnergyStored()).thenReturn(9_000_000_000L);
        when(((LongEnergyStorage) second).getEnergyStored()).thenReturn(4_000_000_000L);
        when(((LongEnergyStorage) second).getMaxEnergyStored()).thenReturn(5_000_000_000L);
        try(var access = mockStatic(WorkerContainerAccess.class)){
            access.when(() -> WorkerContainerAccess.isLoaded(eq(level), any())).thenReturn(true);
            access.when(() -> WorkerContainerAccess.itemHandlers(level, BlockPos.ZERO, null)).thenReturn(List.of(shared));
            access.when(() -> WorkerContainerAccess.fluidHandlers(level, BlockPos.ZERO, null)).thenReturn(List.of(fluid));
            var data = ContainerNetworkSnapshot.capture(List.of(target, target, second), 0, 32);
            assertEquals(2, data.getInt("Containers"));
            var rows = data.getList("Rows", Tag.TAG_COMPOUND);
            assertEquals(3, rows.size());
            assertEquals(76, rows.getCompound(0).getLong("Amount"));
            assertEquals(1500, rows.getCompound(1).getLong("Amount"));
            assertEquals(4000, rows.getCompound(1).getLong("Capacity"));
            assertEquals(12_000_000_000L, rows.getCompound(2).getLong("Amount"));
            assertEquals(14_000_000_000L, rows.getCompound(2).getLong("Capacity"));
            assertEquals(64, items.getStackInSlot(0).getCount());
        }
    }

    // Paging reaches every resource beyond the old detailed snapshot's 256-row limit
    @Test void pagesAllResourcesAndIgnoresUnloadedTargets(){
        var level = mock(Level.class);
        var target = target(level, BlockPos.ZERO);
        var unloaded = target(level, BlockPos.ZERO.east());
        var values = BuiltInRegistries.ITEM.stream().filter(item -> item != Items.AIR).limit(300).toList();
        var items = new ItemStackHandler(5000);
        for(int idx = 0; idx < values.size(); idx++) items.setStackInSlot(idx, new ItemStack(values.get(idx)));
        try(var access = mockStatic(WorkerContainerAccess.class)){
            access.when(() -> WorkerContainerAccess.isLoaded(level, BlockPos.ZERO)).thenReturn(true);
            access.when(() -> WorkerContainerAccess.itemHandlers(level, BlockPos.ZERO, null)).thenReturn(List.of(items));
            Set<String> ids = new HashSet<>();
            for(int offset = 0; offset < 301; offset += 32){
                var data = ContainerNetworkSnapshot.capture(List.of(target, unloaded), offset, 32);
                assertEquals(1, data.getInt("Containers"));
                assertEquals(301, data.getInt("Total"));
                var rows = data.getList("Rows", Tag.TAG_COMPOUND);
                assertTrue(rows.size() <= 32);
                for(int idx = 0; idx < rows.size(); idx++) assertTrue(ids.add(rows.getCompound(idx).getString("Id")));
            }
            assertEquals(301, ids.size());
            assertEquals(0, ContainerNetworkSnapshot.capture(List.of(target), -100, 32).getInt("Offset"));
            assertEquals(300, ContainerNetworkSnapshot.capture(List.of(target), Integer.MAX_VALUE, 32).getInt("Offset"));
        }
    }

    private static BlockEntity target(Level level, BlockPos pos){
        var target = mock(BlockEntity.class, withSettings().extraInterfaces(LongEnergyStorage.class));
        when(target.getLevel()).thenReturn(level);
        when(target.getBlockPos()).thenReturn(pos);
        when(level.getBlockEntity(pos)).thenReturn(target);
        when(level.isLoaded(pos)).thenReturn(true);
        when(level.getBlockState(pos)).thenReturn(Blocks.CHEST.defaultBlockState());
        return target;
    }

    // Linking both halves of a chest must still read its shared capability only once
    @Test void canonicalizesDoubleChestLinks(){
        var level = mock(Level.class);
        var left = target(level, BlockPos.ZERO);
        var right = target(level, BlockPos.ZERO.east());
        when(level.getBlockState(BlockPos.ZERO)).thenReturn(Blocks.CHEST.defaultBlockState()
                .setValue(net.minecraft.world.level.block.ChestBlock.TYPE, net.minecraft.world.level.block.state.properties.ChestType.LEFT));
        when(level.getBlockState(BlockPos.ZERO.east())).thenReturn(Blocks.CHEST.defaultBlockState()
                .setValue(net.minecraft.world.level.block.ChestBlock.TYPE, net.minecraft.world.level.block.state.properties.ChestType.RIGHT));
        var items = new ItemStackHandler(1);
        items.setStackInSlot(0, new ItemStack(Items.DIAMOND, 4));
        try(var access = mockStatic(WorkerContainerAccess.class)){
            access.when(() -> WorkerContainerAccess.isLoaded(eq(level), any())).thenReturn(true);
            access.when(() -> WorkerContainerAccess.itemHandlers(level, BlockPos.ZERO, null)).thenReturn(List.of(items));
            var data = ContainerNetworkSnapshot.capture(List.of(right, left), 0, 32);
            assertEquals(1, data.getInt("Containers"));
            assertEquals(4, data.getList("Rows", Tag.TAG_COMPOUND).getCompound(0).getLong("Amount"));
        }
    }
}
