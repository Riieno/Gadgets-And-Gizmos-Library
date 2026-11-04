package com.rieno.gadgetsandgizmos.lib.client.render;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Locale;

// Style and fit single-line labels consistently across GUI and world renderers
public final class StyledText{
    // Keep text formatting stateless
    private StyledText(){}

    // Apply one named font style without adding formatting codes to the text
    public static MutableComponent literal(String text, String fontStyle){
        String styleId = fontStyle == null ? "regular"
                : fontStyle.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return Component.literal(text == null ? "" : text).withStyle(style -> switch(styleId){
            case "bold" -> style.withBold(true);
            case "italic" -> style.withItalic(true);
            case "bold_italic" -> style.withBold(true).withItalic(true);
            case "underline" -> style.withUnderlined(true);
            case "strikethrough" -> style.withStrikethrough(true);
            case "obfuscated" -> style.withObfuscated(true);
            default -> style;
        });
    }

    // Measure and clip using the styled glyph widths while retaining the selected style
    public static Component fit(Font font, String text, String fontStyle, int availableWidth){
        MutableComponent full = literal(text, fontStyle);
        if(availableWidth <= 0) return literal("", fontStyle);
        if(font.width(full) <= availableWidth) return full;
        return Component.literal(font.substrByWidth(full, availableWidth).getString()).setStyle(full.getStyle());
    }
}
