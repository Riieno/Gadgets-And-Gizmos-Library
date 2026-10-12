package com.rieno.gadgetsandgizmos.lib.display;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ElementInspectorLayoutTest{
    @Test void textInputUsesTheSameRowsAsOtherTextControls(){
        ElementInspectorLayout input = ElementInspectorLayout.forType("text_input");
        assertTrue(input.hasFontControls());
        assertTrue(input.pairedStyles());
        assertEquals(ElementInspectorLayout.forType("button"), input);
        assertEquals(ElementInspectorLayout.forType("toggle"), input);
        assertTrue(input.fontSizeOffset() >= 14 + 18 + 9);
        assertTrue(input.colorLabelOffset("Color") >= input.fontSizeOffset() + 14);
        assertTrue(input.fontStyleOffset() - 12 >= input.colorLabelOffset("Color") + 14 + 15 + 14);
        assertTrue(input.fontStyleOffset() + 20 <= 154);
    }

    @Test void interactiveStyleButtonsFitSeparateColumns(){
        for(String type : new String[]{"button", "toggle", "text_input"}){
            ElementInspectorLayout layout = ElementInspectorLayout.forType(type);
            int contentWidth = 198;
            int fontWidth = layout.fontStyleWidth(contentWidth);
            int styleLeft = fontWidth + 8;
            assertTrue(styleLeft > fontWidth);
            assertTrue(styleLeft + fontWidth <= contentWidth);
        }
        assertEquals(198, ElementInspectorLayout.forType("text").fontStyleWidth(198));
    }

    @Test void valueAndBoxRowsKeepTheirExistingSpacing(){
        ElementInspectorLayout value = ElementInspectorLayout.forType("value");
        assertTrue(value.hasFontControls());
        assertEquals(84, value.colorLabelOffset("Color"));
        assertEquals(144, value.fontStyleOffset());
        ElementInspectorLayout box = ElementInspectorLayout.forType("box");
        assertFalse(box.hasFontControls());
        assertEquals(0, box.colorLabelOffset("Color"));
        assertEquals(46, box.colorLabelOffset("BorderColor"));
    }
}
