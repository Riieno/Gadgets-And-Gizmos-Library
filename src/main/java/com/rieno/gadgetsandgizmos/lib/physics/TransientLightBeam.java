package com.rieno.gadgetsandgizmos.lib.physics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;

// Track temporary light blocks shared by moving beam emitters
@EventBusSubscriber(modid = "gadgetsngizmos")
public final class TransientLightBeam {
    private static final Map<Level, Map<BlockPos, Set<TransientLightBeam>>> OWNERS =
            Collections.synchronizedMap(new IdentityHashMap<>());

    private final BlockState lightState;
    private final Set<BlockPos> positions = new LinkedHashSet<>();
    private Level level;

    // Use a caller-owned light block for this beam
    public TransientLightBeam(BlockState lightState) {
        this.lightState = lightState;
    }

    // Place light sources along the visible beam without replacing other blocks
    public void update(Level level, Vec3 start, Vec3 direction, double length, double spacing) {
        if (level == null || level.isClientSide) {
            clear();
            return;
        }
        updateBeam(level, start, direction, length, spacing);
    }

    // Place local light sources without changing the server world
    public void updateClient(Level level, Vec3 start, Vec3 direction, double length, double spacing) {
        if (level == null || !level.isClientSide) {
            clear();
            return;
        }
        updateBeam(level, start, direction, length, spacing);
    }

    // Maintain one server light at a projected hit without replacing terrain or fluids
    public void updatePoint(Level level, Vec3 point){
        if(level == null || level.isClientSide || point == null){ clear(); return; }
        if(this.level != null && this.level != level) clear();
        this.level = level;
        Set<BlockPos> wanted = new LinkedHashSet<>();
        collect(level, point, wanted);
        for(BlockPos pos : new ArrayList<>(positions)) if(!wanted.contains(pos)) release(pos);
        for(BlockPos pos : wanted){
            if(!positions.contains(pos)) claim(pos);
            else if(level.getBlockState(pos).isAir()) level.setBlock(pos, lightState, 2);
        }
    }

    // Update the positions shared by beam emitters
    private void updateBeam(Level level, Vec3 start, Vec3 direction, double length, double spacing) {
        if (this.level != null && this.level != level) clear();
        if (start == null || direction == null
                || direction.lengthSqr() < 1.0E-8D || !Double.isFinite(length) || length <= 0.0D) {
            clear();
            return;
        }
        this.level = level;
        Vec3 axis = direction.normalize();
        double end = Math.min(length, 64.0D);
        double step = Math.max(1.0D, spacing);
        Set<BlockPos> wanted = new LinkedHashSet<>();
        for (double distance = 0.25D; distance <= end; distance += step) {
            collect(level, start.add(axis.scale(distance)), wanted);
        }
        collect(level, start.add(axis.scale(end)), wanted);
        for (BlockPos pos : new ArrayList<>(positions)) {
            if (!wanted.contains(pos)) release(pos);
        }
        for (BlockPos pos : wanted) {
            if (!positions.contains(pos)) {
                claim(pos);
            } else if (level.getBlockState(pos).isAir()) {
                level.setBlock(pos, lightState, 2);
            }
        }
    }

    // Remove all lights owned by this beam
    public void clear() {
        for (BlockPos pos : new ArrayList<>(positions)) release(pos);
        level = null;
    }

    // Inspect the active light positions for client renderers
    public Set<BlockPos> positions() {
        return Set.copyOf(positions);
    }

    // Check whether a temporary light still has an active emitter
    public static boolean isOwned(Level level, BlockPos pos) {
        Map<BlockPos, Set<TransientLightBeam>> atLevel = OWNERS.get(level);
        return atLevel != null && atLevel.containsKey(pos);
    }

    // Release emitters when a client or server leaves its level
    @SubscribeEvent
    public static void unloaded(LevelEvent.Unload evt){
        if(!(evt.getLevel() instanceof Level level)) return;
        Map<BlockPos, Set<TransientLightBeam>> atLevel = OWNERS.get(level);
        if(atLevel == null) return;
        Set<TransientLightBeam> beams = Collections.newSetFromMap(new IdentityHashMap<>());
        atLevel.values().forEach(beams::addAll);
        beams.forEach(TransientLightBeam::clear);
        OWNERS.remove(level);
    }

    // Add an unobstructed block position to the beam
    private void collect(Level level, Vec3 point, Set<BlockPos> wanted) {
        BlockPos pos = BlockPos.containing(point);
        if (!level.isInWorldBounds(pos) || !level.hasChunkAt(pos)) return;
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.is(lightState.getBlock())) wanted.add(pos.immutable());
    }

    // Claim a shared light block at this position
    private void claim(BlockPos pos) {
        if (level == null) return;
        Map<BlockPos, Set<TransientLightBeam>> atLevel = OWNERS.computeIfAbsent(level,
                ignored -> new java.util.HashMap<>());
        Set<TransientLightBeam> atPos = atLevel.computeIfAbsent(pos,
                ignored -> Collections.newSetFromMap(new IdentityHashMap<>()));
        if (atPos.isEmpty() && level.getBlockState(pos).isAir()
                && !level.setBlock(pos, lightState, 2)) {
            atLevel.remove(pos);
            if (atLevel.isEmpty()) OWNERS.remove(level);
            return;
        }
        if (!level.getBlockState(pos).is(lightState.getBlock())) {
            if (atPos.isEmpty()) atLevel.remove(pos);
            return;
        }
        atPos.add(this);
        positions.add(pos);
    }

    // Release a light only when no beam still uses it
    private void release(BlockPos pos) {
        positions.remove(pos);
        if (level == null) return;
        Map<BlockPos, Set<TransientLightBeam>> atLevel = OWNERS.get(level);
        if (atLevel == null) return;
        Set<TransientLightBeam> atPos = atLevel.get(pos);
        if (atPos == null) return;
        atPos.remove(this);
        if (!atPos.isEmpty()) return;
        atLevel.remove(pos);
        if (level.hasChunkAt(pos) && level.getBlockState(pos).is(lightState.getBlock())) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        }
        if (atLevel.isEmpty()) OWNERS.remove(level);
    }
}
