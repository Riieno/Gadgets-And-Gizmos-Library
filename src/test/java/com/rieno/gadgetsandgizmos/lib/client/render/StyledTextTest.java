package com.rieno.gadgetsandgizmos.lib.client.render;

import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Keep font styles intact while clipping labels against styled glyph widths
class StyledTextTest{
    // Every named style retains its formatting after the label is shortened
    @Test
    void clippingRetainsEverySupportedStyle(){
        Font font = font();
        for(String style : List.of("regular", "bold", "italic", "bold_italic", "underline", "strikethrough", "obfuscated")){
            var full = StyledText.literal("WWWWWW", style);
            var fitted = StyledText.fit(font, full.getString(), style, 12);
            assertEquals(full.getStyle(), fitted.getStyle(), style);
            assertTrue(font.width(fitted) <= 12, style);
            assertTrue(fitted.getString().length() < full.getString().length(), style);
        }
    }

    // Bold labels account for the extra width of each glyph before truncation
    @Test
    void boldClippingUsesStyledMeasurements(){
        Font font = font();
        assertEquals("WWWW", StyledText.fit(font, "WWWW", "regular", 24).getString());
        var bold = StyledText.fit(font, "WWWW", "bold", 24);
        assertEquals("WWW", bold.getString());
        assertTrue(bold.getStyle().isBold());
    }

    // Clipping never splits supplementary Unicode characters into invalid halves
    @Test
    void unicodeAndEmptyLabelsRemainValid(){
        Font font = font();
        assertEquals("A\uD834\uDD1E", StyledText.fit(font, "A\uD834\uDD1EB", "italic", 12).getString());
        assertEquals("", StyledText.fit(font, "Label", "underline", 0).getString());
        assertEquals("", StyledText.fit(font, null, "bold", 12).getString());
    }

    // Style aliases normalize without changing the text or leaking formatting into other labels
    @Test
    void literalFormattingIsExplicitAndIndependent(){
        var boldItalic = StyledText.literal("Label", " Bold-Italic ");
        assertTrue(boldItalic.getStyle().isBold());
        assertTrue(boldItalic.getStyle().isItalic());
        assertEquals("Label", boldItalic.getString());
        assertTrue(StyledText.literal("Label", "underline").getStyle().isUnderlined());
        assertTrue(StyledText.literal("Label", "strikethrough").getStyle().isStrikethrough());
        assertTrue(StyledText.literal("Label", "obfuscated").getStyle().isObfuscated());
        assertEquals(Style.EMPTY, StyledText.literal("Label", "unknown").getStyle());
        assertEquals(Style.EMPTY, StyledText.literal("Label", null).getStyle());
    }

    // Use Minecraft's real string splitter with deterministic normal and bold glyph advances
    private static Font font(){
        StringSplitter splitter = new StringSplitter((codePoint, style) -> style.isBold() ? 7 : 6);
        return new Font(id -> null, false){
            @Override public int width(FormattedText text){
                return (int)Math.ceil(splitter.stringWidth(text));
            }
            @Override public FormattedText substrByWidth(FormattedText text, int width){
                return splitter.headByWidth(text, width, Style.EMPTY);
            }
        };
    }
}
