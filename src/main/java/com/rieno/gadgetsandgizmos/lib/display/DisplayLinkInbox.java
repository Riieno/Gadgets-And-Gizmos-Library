package com.rieno.gadgetsandgizmos.lib.display;

import com.rieno.gadgetsandgizmos.lib.graph.GraphTargetPortLayout;
import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.CompoundTag;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

// Keep independent incoming display streams and stable graph sections across reloads
public final class DisplayLinkInbox{
    public static final int MAX_STREAMS = 64;
    private static final List<GraphTargetPortLayout.Port> PORTS = List.of(
            port("exec", "Updated", "exec"), port("text", "Text", "string"), port("lines", "Lines", "list"),
            port("value", "Value", "any"), port("line_count", "Line Count", "number"),
            port("target_line", "Target Line", "number"), port("source_name", "Source Name", "string"),
            port("source_type", "Source Type", "string"), port("updated_tick", "Updated Tick", "number"),
            port("sequence", "Sequence", "number"));
    private final Map<UUID, DisplayLinkData.Snapshot> streams = new LinkedHashMap<>();
    private long schemaRevision;

    public boolean accept(DisplayLinkData.Snapshot snapshot){
        if(snapshot == null || !streams.containsKey(snapshot.id()) && streams.size() >= MAX_STREAMS) return false;
        var prev = streams.put(snapshot.id(), snapshot);
        if(prev == null || !label(prev).equals(label(snapshot))) schemaRevision++;
        return true;
    }

    public long schemaRevision(){ return schemaRevision; }
    public List<DisplayLinkData.Snapshot> streams(){ return List.copyOf(streams.values()); }

    // The owner removes only links it can confirm are gone or point elsewhere
    public boolean removeIf(Predicate<DisplayLinkData.Snapshot> removed){
        boolean changed = streams.values().removeIf(removed);
        if(changed) schemaRevision++;
        return changed;
    }

    public GraphTargetPortLayout.Layout layout(){
        return GraphTargetPortLayout.compose(streams.values().stream().map(snapshot ->
                new GraphTargetPortLayout.Target(snapshot.id().toString(), label(snapshot), PORTS)).toList(), false);
    }

    public GraphValue read(String id, String field){
        DisplayLinkData.Snapshot snapshot;
        try{ snapshot = streams.get(UUID.fromString(id)); }catch(IllegalArgumentException err){ return null; }
        if(snapshot == null) return null;
        return switch(field){
            case "text" -> GraphValue.string(snapshot.text());
            case "lines" -> GraphValue.list(snapshot.lines());
            case "value" -> parsedValue(snapshot.lines().isEmpty() ? "" : snapshot.lines().getFirst());
            case "line_count" -> GraphValue.number(snapshot.lines().size());
            case "target_line" -> GraphValue.number(snapshot.targetLine());
            case "source_name" -> GraphValue.string(snapshot.sourceName());
            case "source_type" -> GraphValue.string(snapshot.sourceType());
            case "updated_tick" -> GraphValue.number(snapshot.updatedTick());
            case "sequence" -> GraphValue.number(snapshot.sequence());
            default -> null;
        };
    }

    public ListTag save(){
        ListTag tag = new ListTag(); streams.values().forEach(snapshot -> tag.add(snapshot.toTag())); return tag;
    }

    public void load(ListTag tag){
        streams.clear();
        for(var raw : tag){
            if(raw instanceof CompoundTag value) accept(DisplayLinkData.Snapshot.fromTag(value));
        }
        schemaRevision++;
    }

    private static GraphTargetPortLayout.Port port(String id, String label, String type){
        return new GraphTargetPortLayout.Port(id, label, type);
    }

    private static String label(DisplayLinkData.Snapshot snapshot){
        var pos = snapshot.linkPos();
        return snapshot.sourceName() + " @ " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    private static GraphValue parsedValue(String text){
        try{
            double val = Double.parseDouble(text.strip());
            if(Double.isFinite(val)) return GraphValue.number(val);
        }catch(NumberFormatException ignored){}
        return GraphValue.string(text);
    }
}
