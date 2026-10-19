package com.rieno.gadgetsandgizmos.lib.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

// Edit text in a zoomed canvas without replacing the owning screen.
public final class InlineTextEditor{
    private final EditBox field;
    private final Font font;
    private boolean rightAligned;
    private final Consumer<String> accepted;
    private final Runnable cancelled;
    private boolean open = true;
    private boolean decorated = true;
    private int x;
    private int y;
    private float scale = 1;
    private int dragAnchor = -1;

    public InlineTextEditor(Font font, Component message, String text, int maximumLength,
                            Consumer<String> accepted, Runnable cancelled){
        this.font = font;
        field = new EditBox(font, 0, 0, 1, font.lineHeight + 2, message);
        field.setBordered(false);
        field.setTextColor(0xFFFFFFFF);
        field.setTextShadow(false);
        String initial = text == null ? "" : text;
        field.setMaxLength(Math.max(Math.max(1, maximumLength), initial.length()));
        field.setValue(initial);
        field.setFocused(true);
        field.setHighlightPos(0);
        this.accepted = accepted == null ? ignored -> {} : accepted;
        this.cancelled = cancelled == null ? () -> {} : cancelled;
    }

    // Supply screen coordinates and a screen-pixel width; text uses the canvas scale.
    public void setBounds(int x, int y, int width, float scale){
        this.x = x;
        this.y = y;
        this.scale = Float.isFinite(scale) && scale > 0 ? scale : 1;
        field.setWidth(Math.max(1, (int)Math.floor(width / this.scale)));
    }

    // Use the existing field background when editing text directly inside a control
    public void setDecorated(boolean decorated){
        this.decorated = decorated;
    }

    public void setTextColor(int color){
        field.setTextColor(color);
    }

    public void setRightAligned(boolean rightAligned){
        this.rightAligned = rightAligned;
    }

    private int alignmentOffset(){
        return rightAligned ? Math.max(0, field.getWidth() - font.width(field.getValue()) - 1) : 0;
    }

    public boolean isOpen(){
        return open;
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick){
        if(!open) return;
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1);
        if(decorated){
            graphics.fill(-1, -1, field.getWidth() + 1, field.getHeight() + 1, 0xDD17202B);
            graphics.fill(0, field.getHeight(), field.getWidth(), field.getHeight() + 1, 0xFFFFFFFF);
        }
        int offset = alignmentOffset();
        graphics.pose().translate(offset, 0, 0);
        field.render(graphics, (int)((mouseX - x) / scale) - offset, (int)((mouseY - y) / scale), partialTick);
        graphics.pose().popPose();
    }

    // An outside click submits text and remains available to the owning screen.
    public boolean mouseClicked(double mouseX, double mouseY, int button){
        if(!open) return false;
        double localX = (mouseX - x) / scale;
        double localY = (mouseY - y) / scale;
        if(localX < 0 || localX >= field.getWidth() || localY < 0 || localY >= field.getHeight()){
            accept();
            return false;
        }
        if(button == GLFW.GLFW_MOUSE_BUTTON_LEFT){
            field.onClick(localX - alignmentOffset(), localY);
            dragAnchor = field.getCursorPosition();
        }
        return true;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button){
        if(!open) return false;
        if(button == GLFW.GLFW_MOUSE_BUTTON_LEFT && dragAnchor >= 0){
            field.onClick((mouseX - x) / scale - alignmentOffset(), (mouseY - y) / scale);
            field.setHighlightPos(dragAnchor);
        }
        return true;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button){
        if(!open) return false;
        dragAnchor = -1;
        return true;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers){
        if(!open) return false;
        if(keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) accept();
        else if(keyCode == GLFW.GLFW_KEY_ESCAPE) cancel();
        else field.keyPressed(keyCode, scanCode, modifiers);
        return true;
    }

    public boolean charTyped(char codePoint, int modifiers){
        if(!open) return false;
        field.charTyped(codePoint, modifiers);
        return true;
    }

    // Finish once, before notifying the owner, so callbacks may safely rebuild the UI.
    public void accept(){
        if(!open) return;
        open = false;
        field.setFocused(false);
        accepted.accept(field.getValue());
    }

    public void cancel(){
        if(!open) return;
        open = false;
        field.setFocused(false);
        cancelled.run();
    }
}
