package com.rieno.gadgetsandgizmos.lib.plot;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** A named collection of time based graph samples with paged network updates. */
public final class PlotPointTimeline {
    @Deprecated
    public static final int MAX_SAMPLES = 2048;
    public static final int MAX_UPDATE_SAMPLES = 512;
    private final List<StampedSample> samples = new ArrayList<>();
    private long firstTick = Long.MIN_VALUE;
    private final Map<String, Long> seriesTicks = new LinkedHashMap<>();
    private final Map<String, Double> seriesOffsets = new LinkedHashMap<>();
    private UUID timelineId = UUID.randomUUID();
    private long revision;
    private List<Sample> cachedSamples;
    private Map<String, Integer> cachedSeriesColors;

    public enum XMode {
        IGNORE, TIME_OFFSET, ABSOLUTE;

        public static List<String> optionIds() {
            return List.of("ignore", "time_offset", "absolute");
        }

        public static XMode fromId(String id) {
            if (id == null) return IGNORE;
            return switch (id) {
                case "time_offset" -> TIME_OFFSET;
                case "absolute" -> ABSOLUTE;
                default -> IGNORE;
            };
        }
    }

    public record Sample(String name, double x, double y, int color) {
    }

    private record StampedSample(long revision, Sample sample) {
    }

    public void add(String name, double y, int color, long gameTick) {
        add(name, 0.0D, y, color, gameTick, XMode.IGNORE);
    }

    public void add(String name, double x, double y, int color, long gameTick, XMode mode) {
        if (!Double.isFinite(y) || !Double.isFinite(x)) {
            return;
        }
        if (firstTick == Long.MIN_VALUE || gameTick < firstTick) {
            firstTick = gameTick;
        }
        String label = normalizeName(name);
        XMode selected = mode == null ? XMode.IGNORE : mode;
        long start = seriesTicks.computeIfAbsent(label, ignored -> gameTick);
        double elapsed = (gameTick - start) / 20.0D;
        double position = switch (selected) {
            case IGNORE -> elapsed;
            case TIME_OFFSET -> seriesOffsets.computeIfAbsent(label, ignored -> x) + elapsed;
            case ABSOLUTE -> x;
        };
        samples.add(new StampedSample(++revision,
                new Sample(label, position, y, color | 0xFF000000)));
        invalidateCache();
    }

    public List<Sample> samples() {
        if (cachedSamples == null) {
            List<Sample> snapshot = new ArrayList<>(samples.size());
            for (StampedSample entry : samples) snapshot.add(entry.sample());
            cachedSamples = List.copyOf(snapshot);
        }
        return cachedSamples;
    }

    public Map<String, Integer> seriesColors() {
        if (cachedSeriesColors == null) {
            Map<String, Integer> colors = new LinkedHashMap<>();
            for (StampedSample entry : samples) {
                colors.putIfAbsent(entry.sample().name(), entry.sample().color());
            }
            cachedSeriesColors = Collections.unmodifiableMap(colors);
        }
        return cachedSeriesColors;
    }

    public UUID timelineId() {
        return timelineId;
    }

    public long revision() {
        return revision;
    }

    /** Drops all recorded samples and starts a fresh session timeline. */
    public void clear() {
        samples.clear();
        seriesTicks.clear();
        seriesOffsets.clear();
        firstTick = Long.MIN_VALUE;
        timelineId = UUID.randomUUID();
        revision = 0;
        invalidateCache();
    }

    public void reset(String name) {
        String label = normalizeName(name);
        samples.removeIf(entry -> entry.sample().name().equals(label));
        seriesTicks.remove(label);
        seriesOffsets.remove(label);
        if (samples.isEmpty()) firstTick = Long.MIN_VALUE;
        timelineId = UUID.randomUUID();
        for (int index = 0; index < samples.size(); index++) {
            samples.set(index, new StampedSample(index + 1L, samples.get(index).sample()));
        }
        revision = samples.size();
        invalidateCache();
    }

    private void invalidateCache() {
        cachedSamples = null;
        cachedSeriesColors = null;
    }

    private static String normalizeName(String name) {
        String label = name == null || name.isBlank() ? "Plot" : name.strip();
        return label.length() > 64 ? label.substring(0, 64) : label;
    }

    public CompoundTag toTag() {
        CompoundTag tag = headerTag();
        ListTag entries = new ListTag();
        for (StampedSample sample : samples) entries.add(sampleTag(sample));
        tag.put("Samples", entries);
        return tag;
    }

    public CompoundTag toUpdateTag(UUID knownTimelineId, long knownRevision) {
        return toUpdateTag(knownTimelineId, knownRevision, MAX_UPDATE_SAMPLES);
    }

    public CompoundTag toUpdateTag(UUID knownTimelineId, long knownRevision, int maxSamples) {
        boolean delta = timelineId.equals(knownTimelineId)
                && knownRevision >= 0 && knownRevision <= revision;
        CompoundTag tag = delta ? new CompoundTag() : headerTag();
        if (delta) {
            tag.putBoolean("Delta", true);
            tag.putUUID("TimelineId", timelineId);
            tag.putLong("BaseRevision", knownRevision);
        }
        int start = delta ? firstAfter(knownRevision) : 0;
        int end = (int) Math.min(samples.size(), (long) start + Math.max(1, maxSamples));
        ListTag entries = new ListTag();
        for (int index = start; index < end; index++) entries.add(sampleTag(samples.get(index)));
        tag.putLong("Revision", end < samples.size() ? samples.get(end - 1).revision() : revision);
        tag.putBoolean("HasMore", end < samples.size());
        tag.put("Samples", entries);
        return tag;
    }

    private int firstAfter(long knownRevision) {
        int low = 0;
        int high = samples.size();
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (samples.get(middle).revision() <= knownRevision) low = middle + 1;
            else high = middle;
        }
        return low;
    }

    private CompoundTag headerTag() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("TimelineId", timelineId);
        tag.putLong("Revision", revision);
        tag.putLong("FirstTick", firstTick);
        CompoundTag timers = new CompoundTag();
        for (Map.Entry<String, Long> entry : seriesTicks.entrySet()) {
            CompoundTag timer = new CompoundTag();
            timer.putLong("FirstTick", entry.getValue());
            timer.putDouble("Offset", seriesOffsets.getOrDefault(entry.getKey(), 0.0D));
            timers.put(entry.getKey(), timer);
        }
        tag.put("SeriesTimers", timers);
        return tag;
    }

    public boolean applyUpdateTag(CompoundTag tag) {
        if (tag == null) return false;
        if (!tag.getBoolean("Delta")) {
            fromTag(tag);
            return true;
        }
        if (!tag.hasUUID("TimelineId") || !timelineId.equals(tag.getUUID("TimelineId"))) return false;
        long baseRevision = tag.getLong("BaseRevision");
        long nextRevision = tag.getLong("Revision");
        if (baseRevision > revision) return false;
        if (nextRevision <= revision) return true;
        ListTag entries = tag.getList("Samples", Tag.TAG_COMPOUND);
        long lastSequence = revision;
        List<StampedSample> incoming = new ArrayList<>(entries.size());
        for (Tag raw : entries) {
            CompoundTag entry = (CompoundTag) raw;
            long sequence = entry.getLong("Sequence");
            if (sequence <= revision) continue;
            if (sequence <= lastSequence || sequence > nextRevision) return false;
            Sample sample = readSample(entry);
            if (sample == null) return false;
            incoming.add(new StampedSample(sequence, sample));
            lastSequence = sequence;
        }
        for (StampedSample sample : incoming) {
            samples.add(sample);
        }
        if (nextRevision != revision) invalidateCache();
        revision = nextRevision;
        return true;
    }

    private static CompoundTag sampleTag(StampedSample sample) {
        CompoundTag entry = new CompoundTag();
        entry.putLong("Sequence", sample.revision());
        entry.putString("Name", sample.sample().name());
        entry.putDouble("X", sample.sample().x());
        entry.putDouble("Y", sample.sample().y());
        entry.putInt("Color", sample.sample().color());
        return entry;
    }

    private static Sample readSample(CompoundTag entry) {
        double x = entry.getDouble("X");
        double y = entry.getDouble("Y");
        return Double.isFinite(x) && Double.isFinite(y)
                ? new Sample(entry.getString("Name"), x, y, entry.getInt("Color")) : null;
    }

    public void fromTag(CompoundTag tag) {
        samples.clear();
        seriesTicks.clear();
        seriesOffsets.clear();
        timelineId = tag.hasUUID("TimelineId") ? tag.getUUID("TimelineId") : UUID.randomUUID();
        firstTick = tag.contains("FirstTick", Tag.TAG_LONG)
                ? tag.getLong("FirstTick") : Long.MIN_VALUE;
        CompoundTag timers = tag.getCompound("SeriesTimers");
        for (String name : timers.getAllKeys()) {
            CompoundTag timer = timers.getCompound(name);
            seriesTicks.put(name, timer.getLong("FirstTick"));
            seriesOffsets.put(name, timer.getDouble("Offset"));
        }
        ListTag entries = tag.getList("Samples", Tag.TAG_COMPOUND);
        revision = Math.max(tag.getLong("Revision"), entries.size());
        long legacyRevision = revision - entries.size();
        for (int index = 0; index < entries.size(); index++) {
            CompoundTag entry = entries.getCompound(index);
            Sample sample = readSample(entry);
            if (sample != null) {
                long sequence = entry.contains("Sequence", Tag.TAG_LONG)
                        ? entry.getLong("Sequence") : legacyRevision + index + 1;
                samples.add(new StampedSample(sequence, sample));
                if (!seriesTicks.containsKey(entry.getString("Name")) && firstTick != Long.MIN_VALUE) {
                    seriesTicks.put(entry.getString("Name"), firstTick);
                    seriesOffsets.put(entry.getString("Name"), 0.0D);
                }
            }
        }
        invalidateCache();
    }

    public static int parseColor(String value) {
        if (value == null || value.isBlank()) {
            return 0xFF25C6D8;
        }
        String hex = value.strip();
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        } else if (hex.startsWith("0x") || hex.startsWith("0X")) {
            hex = hex.substring(2);
        }
        try {
            if (hex.length() == 6 || hex.length() == 8) {
                return (int) Long.parseLong(hex, 16) | 0xFF000000;
            }
        } catch (NumberFormatException ignored) {
        }
        return 0xFF25C6D8;
    }
}
