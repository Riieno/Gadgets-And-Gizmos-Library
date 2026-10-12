package com.rieno.gadgetsandgizmos.lib.client.ui;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

// Collect bounded text while the consuming screen supplies its widgets and presentation
public abstract class TextInputScreen extends Screen{
    // Return to this screen after saving or cancelling
    private final Screen parent;
    // Seed the first text field and retain edits when widgets are rebuilt
    private final String initialText;
    private EditBox input;
    // Bound the field before assigning its initial value
    private final int maximumLength;
    // Deliver confirmed text on the client thread
    private final Consumer<String> onConfirm;

    // Configure the text limit and confirmation callback without changing whitespace
    protected TextInputScreen(Screen parent, Component title, String initialText,
                              Consumer<String> onConfirm, int maximumLength){
        super(title);
        this.parent = parent;
        this.initialText = initialText == null ? "" : initialText;
        this.onConfirm = onConfirm;
        this.maximumLength = Math.max(1, maximumLength);
    }

    // Register and focus the consumer's field, preserving text across screen resizing
    protected final void initializeInput(EditBox field){
        String text = input == null ? initialText : input.getValue();
        input = field;
        input.setMaxLength(maximumLength);
        input.setValue(text);
        addRenderableWidget(input);
        setInitialFocus(input);
    }

    // Submit the field value and return to the parent screen
    protected final void confirm(){
        if(onConfirm != null) onConfirm.accept(input == null ? "" : input.getValue());
        onClose();
    }

    // Submit text with either Enter key
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers){
        if(keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER){
            confirm();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // Cancel without submitting text
    @Override
    public void onClose(){
        if(minecraft != null) minecraft.setScreen(parent);
    }

    // Leave the world visible while the consumer renders its dialog
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick){}

    // Keep live parent screens running while text is edited
    @Override
    public boolean isPauseScreen(){
        return parent == null || parent.isPauseScreen();
    }
}
