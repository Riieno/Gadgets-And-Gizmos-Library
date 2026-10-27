package com.rieno.gadgetsandgizmos.lib.display;

import com.simibubi.create.api.registry.CreateBuiltInRegistries;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkBlockEntity;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;
import java.util.UUID;

// Retain the text actually transferred by a Create display source
public final class DisplayLinkData{
    private static final String KEY = "gadgetsngizmos:display_link_data";
    private static final String ID = "gadgetsngizmos:display_link_id";
    public static final int MAX_LINES = 64;
    public static final int MAX_LINE_LENGTH = 512;

    private DisplayLinkData(){}

    public static Snapshot capture(DisplayLinkContext ctx, int line, List<MutableComponent> text){
        var link = ctx.blockEntity();
        CompoundTag data = link.getPersistentData();
        UUID id = data.hasUUID(ID) ? data.getUUID(ID) : UUID.randomUUID();
        data.putUUID(ID, id);
        var sourceId = link.activeSource == null ? null : CreateBuiltInRegistries.DISPLAY_SOURCE.getKey(link.activeSource);
        String name = ctx.level().getBlockState(ctx.getSourcePos()).getBlock().getName().getString();
        Snapshot prev = read(link);
        Snapshot snapshot = new Snapshot(id, link.getBlockPos(), ctx.getSourcePos(), Math.max(0, line),
                text == null ? List.of() : text.stream().limit(MAX_LINES).map(component -> component == null ? "" : component.getString()).toList(),
                name, sourceId == null ? "" : sourceId.toString(), ctx.level().getGameTime(), prev == null ? 1 : prev.sequence() + 1);
        data.put(KEY, snapshot.toTag());
        link.setChanged();
        return snapshot;
    }

    public static Snapshot read(DisplayLinkBlockEntity link){
        return link == null ? null : Snapshot.fromTag(link.getPersistentData().getCompound(KEY));
    }

    public static List<String> lines(DisplayLinkBlockEntity link){
        Snapshot snapshot = read(link);
        if(snapshot != null) return snapshot.lines();
        var lines = link.getSourceConfig().getList("ComputerSourceList", 8);
        return lines.stream().limit(MAX_LINES).map(tag -> shorten(tag.getAsString())).toList();
    }

    private static String shorten(String text){ return text.substring(0, Math.min(text.length(), MAX_LINE_LENGTH)); }

    public record Snapshot(UUID id, BlockPos linkPos, BlockPos sourcePos, int targetLine, List<String> lines,
                           String sourceName, String sourceType, long updatedTick, long sequence){
        public Snapshot{
            java.util.Objects.requireNonNull(id);
            linkPos = linkPos.immutable(); sourcePos = sourcePos.immutable();
            targetLine = Math.max(0, targetLine);
            lines = lines == null ? List.of() : lines.stream().limit(MAX_LINES).map(line -> shorten(line == null ? "" : line)).toList();
            sourceName = sourceName == null ? "" : sourceName;
            sourceType = sourceType == null ? "" : sourceType;
        }

        public String text(){ return String.join("\n", lines); }

        public CompoundTag toTag(){
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Id", id); tag.putLong("Link", linkPos.asLong()); tag.putLong("Source", sourcePos.asLong());
            tag.putInt("TargetLine", targetLine);
            ListTag values = new ListTag(); lines.forEach(line -> values.add(StringTag.valueOf(line)));
            tag.put("Lines", values); tag.putString("SourceName", sourceName); tag.putString("SourceType", sourceType);
            tag.putLong("UpdatedTick", updatedTick); tag.putLong("Sequence", sequence);
            return tag;
        }

        public static Snapshot fromTag(CompoundTag tag){
            if(tag == null || !tag.hasUUID("Id")) return null;
            return new Snapshot(tag.getUUID("Id"), BlockPos.of(tag.getLong("Link")), BlockPos.of(tag.getLong("Source")),
                    tag.getInt("TargetLine"), tag.getList("Lines", 8).stream().map(value -> value.getAsString()).toList(),
                    tag.getString("SourceName"), tag.getString("SourceType"), tag.getLong("UpdatedTick"), tag.getLong("Sequence"));
        }
    }
}
