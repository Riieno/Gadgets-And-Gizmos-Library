package com.rieno.gadgetsandgizmos.lib.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InlineTextEditorTest{
    @Test void enterAcceptsReplacementTextOnce(){
        List<String> saved = new ArrayList<>();
        InlineTextEditor editor = editor("Comment", 256, saved, new AtomicInteger());
        editor.charTyped('D', 0);
        editor.charTyped('o', 0);
        editor.charTyped('c', 0);
        editor.charTyped('k', 0);
        assertTrue(editor.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0));
        editor.accept();
        editor.cancel();
        assertEquals(List.of("Dock"), saved);
        assertFalse(editor.isOpen());
        assertFalse(editor.charTyped('x', 0));
    }

    @Test void escapeCancelsWithoutSaving(){
        List<String> saved = new ArrayList<>();
        AtomicInteger cancelled = new AtomicInteger();
        InlineTextEditor editor = editor("Comment", 256, saved, cancelled);
        editor.charTyped('x', 0);
        editor.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
        editor.cancel();
        editor.accept();
        assertTrue(saved.isEmpty());
        assertEquals(1, cancelled.get());
    }

    @Test void scaledBoundsConsumeInsideClicksAndPassOutsideClicksAfterSaving(){
        List<String> saved = new ArrayList<>();
        InlineTextEditor editor = editor("Dock", 256, saved, new AtomicInteger());
        editor.setBounds(100, 50, 120, 0.5F);
        assertTrue(editor.mouseClicked(219, 54, GLFW.GLFW_MOUSE_BUTTON_RIGHT));
        assertTrue(editor.isOpen());
        assertFalse(editor.mouseClicked(220, 54, GLFW.GLFW_MOUSE_BUTTON_LEFT));
        assertEquals(List.of("Dock"), saved);
    }

    @Test void existingLongLabelsArePreservedAndNewTextIsBounded(){
        List<String> saved = new ArrayList<>();
        String original = "A".repeat(300);
        InlineTextEditor editor = editor(original, 256, saved, new AtomicInteger());
        editor.accept();
        assertEquals(List.of(original), saved);
        saved.clear();
        editor = editor("", 3, saved, new AtomicInteger());
        for(char ch : "Dock".toCharArray()) editor.charTyped(ch, 0);
        editor.keyPressed(GLFW.GLFW_KEY_KP_ENTER, 0, 0);
        assertEquals(List.of("Doc"), saved);
    }

    private static InlineTextEditor editor(String initial, int maximum, List<String> saved, AtomicInteger cancelled){
        Font font = mock(Font.class);
        when(font.plainSubstrByWidth(anyString(), anyInt())).thenAnswer(call -> {
            String text = call.getArgument(0);
            int width = call.getArgument(1);
            return text.substring(0, Math.min(text.length(), Math.max(0, width)));
        });
        when(font.plainSubstrByWidth(anyString(), anyInt(), anyBoolean())).thenAnswer(call -> "");
        return new InlineTextEditor(font, Component.literal("Label"), initial, maximum, saved::add, cancelled::incrementAndGet);
    }
}
