package com.rieno.gadgetsandgizmos.lib.probe;

import com.rieno.gadgetsandgizmos.lib.display.DisplayLinkData;
import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkBlockEntity;

import java.util.List;

// Expose the most recent transferred display text through graph data ports
public final class CreateDisplayLinkDataAdapter implements BlockEntityDataAdapter<DisplayLinkBlockEntity>{
    @Override public Class<DisplayLinkBlockEntity> targetType(){ return DisplayLinkBlockEntity.class; }
    @Override public boolean isSupplemental(){ return true; }

    @Override public List<BlockEntityDataPort> ports(DisplayLinkBlockEntity target){
        return List.of(BlockEntityDataPort.readable("display_text", "string"),
                BlockEntityDataPort.readable("display_lines", "list"), BlockEntityDataPort.readable("line_count", "number"),
                BlockEntityDataPort.readable("source_name", "string"), BlockEntityDataPort.readable("source_type", "string"),
                BlockEntityDataPort.readable("updated_tick", "number"));
    }

    @Override public GraphValue read(DisplayLinkBlockEntity target, String port){
        var snapshot = DisplayLinkData.read(target);
        var lines = DisplayLinkData.lines(target);
        return switch(port){
            case "display_text" -> GraphValue.string(String.join("\n", lines));
            case "display_lines" -> GraphValue.list(lines);
            case "line_count" -> GraphValue.number(lines.size());
            case "source_name" -> GraphValue.string(snapshot == null ? "" : snapshot.sourceName());
            case "source_type" -> GraphValue.string(snapshot == null ? "" : snapshot.sourceType());
            case "updated_tick" -> GraphValue.number(snapshot == null ? 0 : snapshot.updatedTick());
            default -> null;
        };
    }
}
