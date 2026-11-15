package com.rieno.gadgetsandgizmos.lib.client.ui;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS                                                        
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

// Collect a name inside a live host screen without changing its menu or lifecycle
public final class TextInputModal{
    public enum Surface{ PANEL, HEADER, BUTTON }
    @FunctionalInterface public interface Painter{
        void draw(GuiGraphics graphics, Surface surface, int x, int y, int width, int height, boolean hovered);
    }
    private final Font font;
    private final Component title;
    private final Component label;
    private final EditBox input;
    private final Consumer<String> confirmed;
    private final Runnable closed;
    private HelpModalFrame.Rect bounds = new HelpModalFrame.Rect(0, 0, 1, 1);
    private HelpModalStyle style = new HelpModalStyle(0xFF17212D, 0xFF202D3B, 0xFF3B4F69, 0xFFEAF2FF, 0xFF91A4BC, 0xFFAEBEFF);
    private Painter painter;
    private TextureAppearance texture;
    private GuiCanvasTransform transform;
    private Component confirmLabel = Component.literal("Create");

    // Describe painted artwork and control geometry without coupling a modal to one mod's assets
    public record TextureAppearance(com.rieno.gadgetsandgizmos.lib.client.render.GuiTextureRegion panel,
                                    Bounds input, Bounds cancel, Bounds accept,
                                    int titleY, int labelX, int labelY, int titleColor, int labelColor, float scale){}
    public record Bounds(int x, int y, int width, int height){
        private HelpModalFrame.Rect rect(){ return new HelpModalFrame.Rect(x, y, width, height); }
    }

    public TextInputModal(Font font, Component title, Component label, String initial, int limit, Consumer<String> confirmed, Runnable closed){
        this.font = font; this.title = title; this.label = label; this.confirmed = confirmed; this.closed = closed;
        input = new EditBox(font, 0, 0, 296, 18, label);
        input.setMaxLength(limit); input.setValue(initial); input.setFocused(true);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           FUNCTIONS                                                       
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public void appearance(HelpModalStyle style, Painter painter){ this.style = style; this.painter = painter; }
    public void texture(TextureAppearance appearance){ texture = appearance; input.setBordered(appearance == null); }
    public void confirmLabel(Component label){ confirmLabel = label; }
    public String value(){ return input.getValue(); }
    public void value(String val){ input.setValue(val); }

    public void render(GuiGraphics graphics, int width, int height, int mouseX, int mouseY, float partialTick){
        if(texture != null){ renderTexture(graphics, width, height, mouseX, mouseY, partialTick); return; }
        transform = null;
        int w = Math.min(320, width - 16), h = 126;
        bounds = new HelpModalFrame.Rect((width - w) / 2, (height - h) / 2, w, h);
        graphics.fill(0, 0, width, height, 0xA8000000);
        paint(graphics, Surface.PANEL, bounds, false);
        paint(graphics, Surface.HEADER, new HelpModalFrame.Rect(bounds.x(), bounds.y(), w, 26), false);
        graphics.drawString(font, title, bounds.x() + 10, bounds.y() + 9, style.text(), false);
        graphics.drawString(font, label, bounds.x() + 12, bounds.y() + 36, style.muted(), false);
        input.setX(bounds.x() + 12); input.setY(bounds.y() + 51);
        if(input.getWidth() != w - 24){ input.setWidth(w - 24); input.setCursorPosition(input.getCursorPosition()); }
        input.render(graphics, mouseX, mouseY, partialTick);
        button(graphics, cancel(), "Cancel", mouseX, mouseY); button(graphics, accept(), confirmLabel.getString(), mouseX, mouseY);
    }

    private void renderTexture(GuiGraphics graphics, int width, int height, int mouseX, int mouseY, float partialTick){
        var panel = texture.panel();
        transform = GuiCanvasTransform.fit(panel.width(), panel.height(), width, height, 8, texture.scale());
        bounds = new HelpModalFrame.Rect(0, 0, panel.width(), panel.height());
        int mx = (int)transform.localX(mouseX), my = (int)transform.localY(mouseY);
        graphics.fill(0, 0, width, height, 0xA8000000);
        graphics.pose().pushPose();
        try{
            graphics.pose().translate(transform.x(), transform.y(), 0); graphics.pose().scale(transform.scale(), transform.scale(), 1);
            panel.draw(graphics, 0, 0);
            String heading = font.plainSubstrByWidth(title.getString(), panel.width() - 16);
            graphics.drawString(font, heading, (panel.width() - font.width(heading)) / 2, texture.titleY(), texture.titleColor(), false);
            graphics.drawString(font, label, texture.labelX(), texture.labelY(), texture.labelColor(), false);
            var field = texture.input();
            input.setX(field.x()); input.setY(field.y()); input.setHeight(field.height()); input.setWidth(field.width());
            input.render(graphics, mx, my, partialTick);
            textureButton(graphics, cancel(), "Cancel", mx, my);
            textureButton(graphics, accept(), confirmLabel.getString(), mx, my);
        }finally{ graphics.pose().popPose(); }
    }

    private void textureButton(GuiGraphics graphics, HelpModalFrame.Rect rect, String text, int mx, int my){
        if(rect.contains(mx, my)) graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), 0x30FFFFFF);
        graphics.drawCenteredString(font, text, rect.x() + rect.width() / 2, rect.y() + (rect.height() - font.lineHeight) / 2, style.text());
    }

    public boolean mouseClicked(double x, double y, int button){
        if(transform != null){ x = transform.localX(x); y = transform.localY(y); }
        if(button != 0) return true;
        if(cancel().contains(x, y)){ closed.run(); return true; }
        if(accept().contains(x, y)){ confirm(); return true; }
        input.mouseClicked(x, y, button); return true;
    }
    public boolean mouseDragged(double x, double y, int button, double dx, double dy){
        if(transform != null){ x = transform.localX(x); y = transform.localY(y); dx /= transform.scale(); dy /= transform.scale(); }
        input.mouseDragged(x, y, button, dx, dy); return true;
    }
    public boolean charTyped(char ch, int modifiers){ input.charTyped(ch, modifiers); return true; }
    public boolean keyPressed(int key, int scan, int modifiers){
        if(key == GLFW.GLFW_KEY_ESCAPE) closed.run();
        else if(key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) confirm();
        else input.keyPressed(key, scan, modifiers);
        return true;
    }

    private void confirm(){ if(!input.getValue().isBlank()){ String val = input.getValue(); closed.run(); confirmed.accept(val); } }
    private HelpModalFrame.Rect cancel(){ return texture == null ? new HelpModalFrame.Rect(bounds.right() - 172, bounds.bottom() - 33, 76, 21) : texture.cancel().rect(); }
    private HelpModalFrame.Rect accept(){ return texture == null ? new HelpModalFrame.Rect(bounds.right() - 88, bounds.bottom() - 33, 76, 21) : texture.accept().rect(); }
    private void button(GuiGraphics graphics, HelpModalFrame.Rect rect, String text, int mx, int my){
        paint(graphics, Surface.BUTTON, rect, rect.contains(mx, my));
        graphics.drawString(font, text, rect.x() + (rect.width() - font.width(text)) / 2, rect.y() + 6, style.text(), false);
    }
    private void paint(GuiGraphics graphics, Surface surface, HelpModalFrame.Rect rect, boolean hovered){
        if(painter != null) painter.draw(graphics, surface, rect.x(), rect.y(), rect.width(), rect.height(), hovered);
        else{
            graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), hovered ? style.border() : surface == Surface.PANEL ? style.background() : style.panel());
            graphics.renderOutline(rect.x(), rect.y(), rect.width(), rect.height(), style.border());
        }
    }
}
