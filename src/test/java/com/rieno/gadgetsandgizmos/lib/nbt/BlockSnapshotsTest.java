package com.rieno.gadgetsandgizmos.lib.nbt;

import com.rieno.gadgetsandgizmos.lib.util.RevisionSnapshotCache;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BlockSnapshotsTest{
    @BeforeAll static void bootstrap(){
        net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        net.neoforged.fml.ModList.of(List.of(), List.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test void compatibleFallbackRetainsPartialShapePropertiesWithoutEditingSource(){
        CompoundTag encoded = state("oldmod:slab");
        CompoundTag properties = new CompoundTag();
        properties.putString("type", "top"); properties.putString("waterlogged", "true");
        encoded.put("Properties", properties);
        var resolved = BlockStateSnapshots.read(BuiltInRegistries.BLOCK.asLookup(), encoded,
                Map.of(id("oldmod:slab"), id("minecraft:oak_slab")));
        assertNotNull(resolved);
        assertTrue(resolved.is(Blocks.OAK_SLAB));
        assertEquals(SlabType.TOP, resolved.getValue(BlockStateProperties.SLAB_TYPE));
        assertTrue(resolved.getValue(BlockStateProperties.WATERLOGGED));
        assertEquals("oldmod:slab", encoded.getString("Name"));
    }

    @Test void existingRegistrationsWinOverFallbacks(){
        assertTrue(BlockStateSnapshots.read(BuiltInRegistries.BLOCK.asLookup(), state("minecraft:oak_slab"),
                Map.of(id("minecraft:oak_slab"), id("minecraft:stone"))).is(Blocks.OAK_SLAB));
    }

    @Test void missingBlocksRemainDistinctFromExplicitAir(){
        assertNull(BlockStateSnapshots.read(BuiltInRegistries.BLOCK.asLookup(), state("missing:slab"), Map.of()));
        assertTrue(BlockStateSnapshots.read(BuiltInRegistries.BLOCK.asLookup(), state("minecraft:air"), Map.of()).isAir());
    }

    @Test void airSnapshotsNeverEnterBlockEntityLoading(){
        Level level = mock(Level.class);
        assertNull(BlockEntitySnapshots.create(level, BlockPos.ZERO, Blocks.AIR.defaultBlockState(), entity()));
        verifyNoInteractions(level);
    }

    @Test void mismatchedRegisteredEntityTypesNeverEnterBlockEntityLoading(){
        Level level = mock(Level.class);
        assertNull(BlockEntitySnapshots.create(level, BlockPos.ZERO, Blocks.FURNACE.defaultBlockState(), entity()));
        verifyNoInteractions(level);
    }

    @Test void unavailablePreviewsAreMemoizedUntilTheirBackingOrLevelChanges(){
        var cache = new RevisionSnapshotCache<Object>();
        var calls = new AtomicInteger();
        Object level = new Object();
        for(int frame = 0; frame < 1000; frame++) assertNull(cache.get(level, 0, () -> {calls.incrementAndGet();return null;}));
        assertEquals(1, calls.get());
        cache.invalidate();
        cache.get(level, 0, () -> {calls.incrementAndGet();return null;});
        cache.get(new Object(), 0, () -> {calls.incrementAndGet();return null;});
        assertEquals(3, calls.get());
    }

    private static CompoundTag state(String name){
        var data = new CompoundTag(); data.putString("Name", name); return data;
    }

    private static CompoundTag entity(){
        var data = new CompoundTag(); data.putString("id", "minecraft:chest"); return data;
    }

    private static ResourceLocation id(String name){ return ResourceLocation.parse(name); }
}
