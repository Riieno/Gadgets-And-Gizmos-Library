package com.rieno.gadgetsandgizmos.lib.client.ui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InlineTextEditorPresentationTest{
    @Test void plainEditingDoesNotPaintAnotherFieldOrUnderline() throws Exception{
        Font font = font();var editor = new InlineTextEditor(font, Component.empty(), "Text", 64, null, null);
        EditBox field = mock(EditBox.class);when(field.getWidth()).thenReturn(100);when(field.getHeight()).thenReturn(11);
        var access = InlineTextEditor.class.getDeclaredField("field");access.setAccessible(true);access.set(editor, field);
        GuiGraphics graphics = mock(GuiGraphics.class);PoseStack pose = new PoseStack();when(graphics.pose()).thenReturn(pose);
        editor.setDecorated(false);editor.setTextColor(0xFF123456);editor.render(graphics, 0, 0, 0);
        verify(graphics, never()).fill(anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
        verify(field).setTextColor(0xFF123456);verify(field).render(graphics, 0, 0, 0);
        assertTrue(pose.clear());
        editor.setDecorated(true);editor.render(graphics, 0, 0, 0);
        verify(graphics, times(2)).fill(anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
    }

    @Test void rightAlignedZoomedTextUsesItsVisibleGlyphPositionsForDragSelection(){
        List<String> saved = new ArrayList<>();
        var editor = new InlineTextEditor(font(), Component.empty(), "1234", 64, saved::add, null);
        editor.setDecorated(false);editor.setRightAligned(true);editor.setBounds(100, 50, 20, .5F);
        try(var screen = mockStatic(Screen.class)){
            assertTrue(editor.mouseClicked(118.5, 52, GLFW.GLFW_MOUSE_BUTTON_LEFT));
            assertTrue(editor.mouseDragged(117.5, 52, GLFW.GLFW_MOUSE_BUTTON_LEFT));
            editor.mouseReleased(117.5, 52, GLFW.GLFW_MOUSE_BUTTON_LEFT);
            editor.charTyped('x', 0);editor.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
        }
        assertEquals(List.of("x34"), saved);
    }

    private static Font font(){
        Font font = mock(Font.class);
        when(font.width(anyString())).thenAnswer(call -> ((String)call.getArgument(0)).length());
        when(font.plainSubstrByWidth(anyString(), anyInt())).thenAnswer(call -> {
            String value = call.getArgument(0);int width = call.getArgument(1);
            return value.substring(0, Math.min(value.length(), Math.max(0, width)));
        });
        when(font.plainSubstrByWidth(anyString(), anyInt(), anyBoolean())).thenAnswer(call -> "");
        return font;
    }
}
