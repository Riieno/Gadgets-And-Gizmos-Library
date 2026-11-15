package com.rieno.gadgetsandgizmos.lib.display;

import com.rieno.gadgetsandgizmos.lib.script.ScriptInput;
import com.rieno.gadgetsandgizmos.lib.view.ViewReference;
import com.rieno.gadgetsandgizmos.lib.view.ViewSource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.function.Function;
import java.util.function.Predicate;

// Resolve target previews through host lookup and access rules without evaluating a graph
public final class DisplayPreviewSources{
    private final Function<CompoundTag, BlockEntity> resolve;
    private final Predicate<BlockEntity> allowed;
    private final Function<BlockEntity, AccDisplaySource> fallback;

    public DisplayPreviewSources(Function<CompoundTag, BlockEntity> resolve, Predicate<BlockEntity> allowed,
                                 Function<BlockEntity, AccDisplaySource> fallback){
        this.resolve = resolve; this.allowed = allowed; this.fallback = fallback;
    }

    public CompoundTag frame(ServerPlayer player, CompoundTag target, int width, int height){
        if(VirtualDisplaySources.isReference(target)) return VirtualDisplaySources.frame(player.server, target, width, height, player);
        BlockEntity src = resolve.apply(target);
        if(src == null || src.isRemoved() || !allowed.test(src)) return new CompoundTag();
        if(src instanceof ViewSource view && view.isViewAvailable()){
            var frame = new CompoundTag(); frame.putString("Format", "camera"); frame.put("ViewSource", ViewReference.of(src).toTag()); return frame;
        }
        AccDisplaySource source = AccDisplaySourceRegistry.source(src);
        if(source == null) source = fallback.apply(src);
        return source == null ? new CompoundTag() : content(source.frame(src, Math.clamp(width, 1, 1600), Math.clamp(height, 1, 1200)));
    }

    public boolean input(ServerPlayer player, CompoundTag target, ScriptInput input){
        if(VirtualDisplaySources.isReference(target)) return VirtualDisplaySources.input(player.server, target, player, input);
        BlockEntity src = resolve.apply(target);
        if(src == null || src.isRemoved() || !allowed.test(src)) return false;
        AccDisplaySource source = AccDisplaySourceRegistry.source(src);
        if(source == null) source = fallback.apply(src);
        return source != null && source.input(src, input.action(), input.x(), input.y(), input.value());
    }

    // External transports can wrap their source frame without changing its media format
    public static CompoundTag content(CompoundTag frame){
        if(frame == null) return new CompoundTag();
        for(int depth = 0; depth < 8 && frame.getString("Format").isBlank() && frame.contains("External"); depth++) frame = frame.getCompound("External");
        return frame.copy();
    }
}
