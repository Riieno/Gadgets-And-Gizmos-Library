package com.rieno.gadgetsandgizmos.lib.display;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DisplaySourceSelectionTest{
    @Test void clickedSourcesRemainSelectedWhenScheduleDataRefreshes(){
        ListTag sources = new ListTag();
        CompoundTag schedule = source("display_link", "display_link");
        CompoundTag graph = source("acc:graph", "graph");
        sources.add(schedule);
        sources.add(graph);
        for(String id : new String[]{"acc:graph", "display_link", "acc:graph"}){
            for(int idx = 0; idx < 20; idx++){
                schedule.getCompound("Frame").putInt("Refresh", idx);
                assertEquals(id, DisplaySourceSelection.selected(sources, id).getString("Id"));
            }
        }
    }

    @Test void presentationOnlySchedulesRemainSelectableBesideOtherSources(){
        ListTag sources = new ListTag();
        ListTag presentations = new ListTag();
        CompoundTag payload = source("adapter", "external");
        CompoundTag schedule = source("acc:schedule", "crn");
        sources.add(payload);
        presentations.add(source("adapter", "crn"));
        presentations.add(schedule);
        ListTag available = DisplaySourceSelection.available(sources, presentations);
        assertEquals(2, available.size());
        assertSame(payload, DisplaySourceSelection.selected(available, "adapter"));
        assertSame(schedule, DisplaySourceSelection.selected(available, "acc:schedule"));
    }

    @Test void missingSourcesAllowTheHostToChooseAFallback(){
        ListTag sources = new ListTag();
        sources.add(source("display_link", "display_link"));
        assertNull(DisplaySourceSelection.selected(sources, "removed"));
        assertNull(DisplaySourceSelection.selected(sources, ""));
        assertNull(DisplaySourceSelection.selected(null, "display_link"));
        assertTrue(DisplaySourceSelection.available(null, null).isEmpty());
    }

    private static CompoundTag source(String id, String state){
        CompoundTag source = new CompoundTag();
        source.putString("Id", id);
        CompoundTag frame = new CompoundTag();
        frame.putString("State", state);
        source.put("Frame", frame);
        return source;
    }
}
