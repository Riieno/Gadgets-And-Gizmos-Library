package com.rieno.gadgetsandgizmos.lib.control;

import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import com.rieno.gadgetsandgizmos.lib.interaction.InteractionOrigin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.Event;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

// Report a completed server control batch while leaving target-specific acceptance checks to consumers
public final class ControlDataWriteEvent extends Event{
    private final BlockEntity source;
    private final BlockEntity target;
    private final ResourceLocation operation;
    private final long revision;
    private final Map<String, GraphValue> values;
    private final @Nullable InteractionOrigin origin;

    public ControlDataWriteEvent(BlockEntity source, BlockEntity target, ResourceLocation operation,
                                 long revision, Map<String, GraphValue> values, @Nullable InteractionOrigin origin){
        this.source = source;
        this.target = target;
        this.operation = operation;
        this.revision = revision;
        this.values = Map.copyOf(values);
        this.origin = origin;
    }

    public BlockEntity source(){ return source; }
    public BlockEntity target(){ return target; }
    public ResourceLocation operation(){ return operation; }
    public long revision(){ return revision; }
    public Map<String, GraphValue> values(){ return values; }
    public @Nullable InteractionOrigin origin(){ return origin; }
}
