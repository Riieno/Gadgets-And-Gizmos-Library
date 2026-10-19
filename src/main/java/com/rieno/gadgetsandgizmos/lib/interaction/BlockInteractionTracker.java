package com.rieno.gadgetsandgizmos.lib.interaction;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;

// Associate native and host interactions with loaded blocks, including blocks without entities
@EventBusSubscriber(modid = "gadgetsngizmos")
public final class BlockInteractionTracker{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Map<Level, Map<BlockPos, Entry>> ORIGINS = new WeakHashMap<>();
    private BlockInteractionTracker(){}

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Record accepted host interactions as well as vanilla block use
    public static void record(Level level, BlockPos pos, ServerPlayer player){
        if(level == null || pos == null || player == null || level.isClientSide) return;
        Level target = target(level, pos);
        Map<BlockPos, Entry> entries = ORIGINS.computeIfAbsent(target,
                ignored -> new LinkedHashMap<>(64, 0.75F, true));
        InteractionOrigin origin = InteractionOrigin.of(player);
        entries.put(pos.immutable(), new Entry(origin, origin, target.getGameTime(), target.getBlockState(pos).getBlock()));
        while(entries.size() > 4096) entries.remove(entries.keySet().iterator().next());
    }
    // Read the retained player information for an arbitrary bound block
    public static @Nullable InteractionOrigin last(Level level, BlockPos pos){
        if(level == null || pos == null) return null;
        Entry entry = entry(level, pos);
        return entry == null ? null : entry.last;
    }
    // Attribute new signals only to an interaction in the requested time window
    public static @Nullable InteractionOrigin recent(Level level, BlockPos pos, long ticks){
        if(level == null || pos == null) return null;
        Entry entry = entry(level, pos);
        long elapsed = entry == null ? Long.MAX_VALUE : level.getGameTime() - entry.signalTick;
        return elapsed >= 0 && elapsed <= ticks ? entry.signal : null;
    }
    // Propagate an attributed signal while keeping old player information separate from new anonymous signals
    public static void signal(Level level, BlockPos pos, @Nullable InteractionOrigin origin){
        if(level == null || level.isClientSide || pos == null) return;
        Level target = target(level, pos);
        Map<BlockPos, Entry> entries = ORIGINS.computeIfAbsent(target,
                ignored -> new LinkedHashMap<>(64, 0.75F, true));
        Entry prev = entry(level, pos);
        if(prev == null && origin == null) return;
        entries.put(pos.immutable(), new Entry(origin == null ? prev.last : origin, origin, target.getGameTime(),
                target.getBlockState(pos).getBlock()));
        while(entries.size() > 4096) entries.remove(entries.keySet().iterator().next());
    }
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void used(PlayerInteractEvent.RightClickBlock evt){
        if(evt.isCanceled() && !evt.getCancellationResult().consumesAction()) return;
        if(evt.getEntity() instanceof ServerPlayer player) record(evt.getLevel(), evt.getPos(), player);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void attacked(PlayerInteractEvent.LeftClickBlock evt){
        if(evt.getAction() == PlayerInteractEvent.LeftClickBlock.Action.START
                && evt.getEntity() instanceof ServerPlayer player) record(evt.getLevel(), evt.getPos(), player);
    }
    @SubscribeEvent
    public static void unloaded(LevelEvent.Unload evt){ if(evt.getLevel() instanceof Level level) ORIGINS.remove(level); }
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Prevent newly placed blocks from inheriting the previous block's interaction identity
    private static @Nullable Entry entry(Level level, BlockPos pos){
        Level target = target(level, pos);
        Map<BlockPos, Entry> entries = ORIGINS.get(target);
        if(entries == null || !target.hasChunkAt(pos)) return null;
        Entry entry = entries.get(pos);
        if(entry != null && entry.block != target.getBlockState(pos).getBlock()){
            entries.remove(pos);
            return null;
        }
        return entry;
    }
    private static Level target(Level level, BlockPos pos){
        var body = SableLevelApi.containing(level, pos);
        return body == null ? level : body.getLevel();
    }
    private record Entry(@Nullable InteractionOrigin last, @Nullable InteractionOrigin signal, long signalTick, Block block){}
}
