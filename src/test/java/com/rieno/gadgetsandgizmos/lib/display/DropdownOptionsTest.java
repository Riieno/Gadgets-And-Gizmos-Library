package com.rieno.gadgetsandgizmos.lib.display;

import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import com.rieno.gadgetsandgizmos.lib.graph.HudInteractionPorts;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

// Verify collection labels preserve each option's original graph value
class DropdownOptionsTest{
    @Test void namedNestedCollectionsKeepTheirKeysAndValues(){
        var home = GraphValue.map(Map.of("x", 12, "y", 64, "z", -8));
        var storage = GraphValue.list(List.of("crate", GraphValue.map(Map.of("slots", 27))));
        var data = new LinkedHashMap<String, GraphValue>();
        data.put("Home", home); data.put("Storage", storage);
        var options = DropdownOptions.entries(GraphValue.map(data));
        assertEquals(List.of("Home", "Storage"), options.stream().map(DropdownOptions.Option::label).toList());
        assertEquals(home, DropdownOptions.find(options, "Home").value());
        assertEquals(storage, DropdownOptions.find(options, "Storage").value());
        assertEquals("Home", DropdownOptions.selectedKey(options, home));
        assertNull(DropdownOptions.find(options, "Missing"));
    }

    @Test void listsLabelScalarsAndUseIndicesForNestedCollections(){
        var nested = GraphValue.map(Map.of("x", 42));
        var options = DropdownOptions.entries(GraphValue.list(List.of("One", 2D, true, nested, List.of(1, 2))));
        assertEquals(List.of("One", "2", "true", "3", "4"), options.stream().map(DropdownOptions.Option::label).toList());
        assertEquals(nested, DropdownOptions.find(options, "3").value());
        assertEquals("list", DropdownOptions.find(options, "4").value().type());
        assertEquals("number", DropdownOptions.find(options, "1").value().type());
    }

    @Test void emptyAndInvalidSourcesHaveNoSelectableOptions(){
        for(var val : List.of(GraphValue.map(Map.of()), GraphValue.list(List.of()), GraphValue.string("Not a list"), GraphValue.number(12)))
            assertTrue(DropdownOptions.entries(val).isEmpty());
        assertTrue(DropdownOptions.accepts("map")); assertTrue(DropdownOptions.accepts("list"));
        assertFalse(DropdownOptions.accepts("any")); assertFalse(DropdownOptions.accepts("string"));
    }

    @Test void identitiesRemainDistinctWhenValuesMatch(){
        var options = DropdownOptions.entries(GraphValue.map(Map.of("First", 5, "Second", 5, "", 8)));
        assertEquals("First", DropdownOptions.find(options, "First").key());
        assertEquals("Second", DropdownOptions.find(options, "Second").key());
        assertEquals(8, DropdownOptions.find(options, "").value().asNumber());
    }

    @Test void designersOfferEachRequestedPresetAndAllFontControls(){
        assertEquals(List.of("Neumorphic", "Glassmorphic", "Material", "Flat"),
                HudStyleCatalog.options("dropdown").stream().map(HudStyleCatalog.Option::label).toList());
        for(var option : HudStyleCatalog.options("dropdown")) assertEquals(option.id(), HudStyleCatalog.style("dropdown", option.id()));
        assertTrue(ElementInspectorLayout.forType("dropdown").hasFontControls());
        assertTrue(ElementInspectorLayout.forType("dropdown").pairedStyles());
    }

    @Test void derivedOutputsAreIdentifiedByTheirOwnControlAndValuePort(){
        var data = new CompoundTag(); var elements = new ListTag(); var elm = new CompoundTag();
        elm.putString("Type", "dropdown"); elm.putString("ValuePort", "destination"); elements.add(elm); data.put("WidgetElements", elements);
        assertTrue(HudInteractionPorts.isValueType(data, "destination", "dropdown"));
        assertFalse(HudInteractionPorts.isValueType(data, "other", "dropdown"));
        assertFalse(HudInteractionPorts.isValueType(data, "destination", "button"));
    }
}
