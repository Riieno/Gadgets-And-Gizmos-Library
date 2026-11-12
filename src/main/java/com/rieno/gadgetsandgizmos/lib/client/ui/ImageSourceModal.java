package com.rieno.gadgetsandgizmos.lib.client.ui;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.client.render.GuiTextureRegion;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.net.URI;
import java.util.Objects;
import java.util.function.Consumer;

// Choose a remote image or a host-owned disk upload without replacing the current screen
public final class ImageSourceModal{
    /*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        DEFAULTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    // Retain callbacks independently of the host screen's widgets
    private final String initial;
    private final Consumer<String> apply;
    private final Runnable upload, close;
    private EditBox url;
    private int x, y, width;
    private String error = "";
    // Keep painted text-input coordinates consistent for drawing and pointer input
    private TextureAppearance appearance;
    private float pointerScale = 1;
    private int pointerLeft, pointerTop;

    /*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        FUNCTIONS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    // Configure URL confirmation, disk upload and cancellation without replacing the parent screen
    public ImageSourceModal(String initial, Consumer<String> apply, Runnable upload, Runnable close){
        this.initial = initial == null ? "" : initial;
        this.apply = apply; this.upload = upload; this.close = close;
    }

    // Reuse the host's text-input panel and its other painted modal buttons
    public ImageSourceModal withAppearance(TextureAppearance appearance){
        this.appearance = Objects.requireNonNull(appearance);
        return this;
    }

    // Draw the URL field and source actions in the host's chosen presentation
    public void render(GuiGraphics graphics, Font font, int screenWidth, int screenHeight,
                       int mouseX, int mouseY, HelpModalStyle style){
        if(appearance != null){ renderTextured(graphics, font, screenWidth, screenHeight, mouseX, mouseY); return; }
        width = Math.min(360, Math.max(160, screenWidth - 24));
        x = (screenWidth - width) / 2; y = (screenHeight - 166) / 2;
        if(url == null){
            url = new EditBox(font, x + 12, y + 49, width - 24, 20, Component.literal("Image URL"));
            url.setMaxLength(4096); url.setValue(initial.startsWith("http") ? initial : ""); url.setFocused(true);
        }
        url.setX(x + 12); url.setY(y + 49); url.setWidth(width - 24);
        graphics.fill(0, 0, screenWidth, screenHeight, 0xB0000000);
        graphics.fill(x, y, x + width, y + 166, style.background());
        graphics.renderOutline(x, y, width, 166, style.border());
        graphics.drawString(font, "Upload Image", x + 12, y + 12, style.text(), false);
        graphics.drawString(font, "URL", x + 12, y + 34, style.muted(), false);
        url.render(graphics, mouseX, mouseY, 0);
        drawButton(graphics, font, x + 12, y + 80, width - 24, "Use URL", mouseX, mouseY, style);
        drawButton(graphics, font, x + 12, y + 108, width - 24, "Upload file from disk", mouseX, mouseY, style);
        graphics.drawString(font, error.isBlank() ? "Esc to cancel" : error, x + 12, y + 145,
                error.isBlank() ? style.muted() : 0xFFFF8888, false);
    }

    // Match the existing 190 by 87 text-input screen without stretching its panel artwork
    private void renderTextured(GuiGraphics graphics, Font font, int screenWidth, int screenHeight, int mouseX, int mouseY){
        pointerScale = Math.min(1, Math.min(Math.max(1, screenWidth - 16) / 190F, Math.max(1, screenHeight - 16) / 87F));
        pointerLeft = Math.round((screenWidth - 190 * pointerScale) / 2);
        pointerTop = Math.round((screenHeight - 87 * pointerScale) / 2);
        double localX = (mouseX - pointerLeft) / pointerScale, localY = (mouseY - pointerTop) / pointerScale;
        if(url == null){
            url = new EditBox(font, 28, 42, 134, 10, Component.literal("Image URL"));
            url.setBordered(false);
            url.setMaxLength(2048);
            url.setValue(initial.startsWith("http") ? initial : "");
            url.setFocused(true);
        }
        graphics.fill(0, 0, screenWidth, screenHeight, 0xA8000000);
        graphics.pose().pushPose();
        try{
            graphics.pose().translate(pointerLeft, pointerTop, 0);
            graphics.pose().scale(pointerScale, pointerScale, 1);
            appearance.panel().draw(graphics, 0, 0);
            GuiControlPreviews.drawFittedText(graphics, font, Component.literal("Upload Image"),
                    8, 2, 174, 12, 1, appearance.titleColor(), true);
            GuiControlPreviews.drawFittedText(graphics, font, Component.literal(error.isBlank() ? "Image URL" : error),
                    8, 24, 174, 12, 1, error.isBlank() ? appearance.promptColor() : 0xFFAA2222, false);
            url.render(graphics, (int)localX, (int)localY, 0);
            drawTextureButton(graphics, font, 6, 47, "Cancel", localX, localY);
            drawTextureButton(graphics, font, 56, 62, "From disk", localX, localY);
            drawTextureButton(graphics, font, 122, 62, "Use URL", localX, localY);
        }finally{ graphics.pose().popPose(); }
    }

    // Fit source actions over the same button artwork as the other modals
    private void drawTextureButton(GuiGraphics graphics, Font font, int left, int size, String label, double mouseX, double mouseY){
        appearance.button().draw(graphics, left, 72, size, 12);
        if(inside(mouseX, mouseY, left, 72, size, 12)) graphics.fill(left + 1, 73, left + size - 1, 83, 0x33FFFFFF);
        GuiControlPreviews.drawFittedText(graphics, font, Component.literal(label), left + 2, 73, size - 4, 10,
                1, 0xFFFFFFFF, true);
    }

    // Draw source actions when the host has not supplied painted artwork
    private void drawButton(GuiGraphics graphics, Font font, int left, int top, int size, String title,
                            int mouseX, int mouseY, HelpModalStyle style){
        graphics.fill(left, top, left + size, top + 20, inside(mouseX, mouseY, left, top, size, 20) ? style.border() : style.panel());
        graphics.drawCenteredString(font, title, left + size / 2, top + 6, style.text());
    }

    // Consume modal clicks before the host graph can receive them
    public boolean mouseClicked(double mouseX, double mouseY, int button){
        if(url == null) return true;
        if(appearance != null){
            mouseX = (mouseX - pointerLeft) / pointerScale;
            mouseY = (mouseY - pointerTop) / pointerScale;
            if(button == 0 && inside(mouseX, mouseY, 6, 72, 47, 12)){ close.run(); return true; }
            if(button == 0 && inside(mouseX, mouseY, 56, 72, 62, 12)){ close.run(); upload.run(); return true; }
            if(button == 0 && inside(mouseX, mouseY, 122, 72, 62, 12)){ submit(); return true; }
        }
        if(appearance == null && button == 0 && inside(mouseX, mouseY, x + 12, y + 80, width - 24, 20)){ submit(); return true; }
        if(appearance == null && button == 0 && inside(mouseX, mouseY, x + 12, y + 108, width - 24, 20)){ close.run(); upload.run(); return true; }
        url.mouseClicked(mouseX, mouseY, button);
        url.setFocused(inside(mouseX, mouseY, url.getX(), url.getY(), url.getWidth(), url.getHeight()));
        return true;
    }

    // Keep invalid URLs in the dialog so the user can correct them
    private void submit(){
        String source = url.getValue().strip();
        if(!validUrl(source)){ error = "Enter a valid HTTP or HTTPS image URL."; return; }
        apply.accept(source); close.run();
    }

    // Accept only complete HTTP or HTTPS sources
    public static boolean validUrl(String source){
        try{
            URI uri = URI.create(source);
            return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && !uri.getHost().isBlank();
        }catch(RuntimeException ignored){ return false; }
    }

    // Preserve the text-input screen's Enter and Escape shortcuts
    public boolean keyPressed(int key, int scan, int modifiers){
        if(key == GLFW.GLFW_KEY_ESCAPE){ close.run(); return true; }
        if(key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER){ if(url != null) submit(); return true; }
        if(url != null) url.keyPressed(key, scan, modifiers);
        return true;
    }

    // Forward text entry only to the modal's own field
    public boolean charTyped(char val, int modifiers){ if(url != null) url.charTyped(val, modifiers); return true; }

    // Select URL text using the same transform as the painted field
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY){
        if(url != null) url.mouseDragged((mouseX - pointerLeft) / pointerScale, (mouseY - pointerTop) / pointerScale,
                button, dragX / pointerScale, dragY / pointerScale);
        return true;
    }

    // Release text selection without forwarding the event to the graph
    public boolean mouseReleased(double mouseX, double mouseY, int button){
        if(url != null) url.mouseReleased((mouseX - pointerLeft) / pointerScale, (mouseY - pointerTop) / pointerScale, button);
        return true;
    }

    // Test a pointer in the field's active coordinate space
    private static boolean inside(double x, double y, int left, int top, int width, int height){
        return x >= left && x < left + width && y >= top && y < top + height;
    }

    // Supply the existing text-input artwork and the owning mod's modal button texture
    public record TextureAppearance(GuiTextureRegion panel, GuiTextureRegion button, int titleColor, int promptColor){
        // Reject artwork with a different text-input layout
        public TextureAppearance{
            Objects.requireNonNull(panel); Objects.requireNonNull(button);
            if(panel.width() != 190 || panel.height() != 87) throw new IllegalArgumentException("Expected a 190 by 87 text-input panel");
        }
    }
}
