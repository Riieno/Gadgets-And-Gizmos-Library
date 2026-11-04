package com.rieno.gadgetsandgizmos.lib.client.ui;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;

// Replace a painted value label with an inline number editor while it has focus
public final class InlineNumberField extends AbstractWidget{
/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        VARIABLES
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    private final Font font;
    private final double min;
    private final double max;
    private final boolean wholeNumbers;
    private final int textInset;
    private final DoubleSupplier selected;
    private final DoubleFunction<Component> label;
    private final DoubleFunction<String> format;
    private final DoubleConsumer changed;
    private InlineTextEditor editor;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        FUNCTIONS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    public InlineNumberField(int x, int y, int width, int height, Font font, int textInset,
                             double min, double max, boolean wholeNumbers, DoubleSupplier selected,
                             DoubleFunction<Component> label, DoubleFunction<String> format, DoubleConsumer changed){
        super(x, y, width, height, label.apply(selected.getAsDouble()));
        if(!Double.isFinite(min) || !Double.isFinite(max) || max <= min || textInset < 0 || textInset >= width){
            throw new IllegalArgumentException("Invalid inline number bounds");
        }
        this.font = font;
        this.min = min;
        this.max = max;
        this.wholeNumbers = wholeNumbers;
        this.textInset = textInset;
        this.selected = selected;
        this.label = label;
        this.format = format;
        this.changed = changed;
    }

    public boolean editing(){ return editor != null && editor.isOpen(); }

    // Accept finite values within the control's range without snapping decimal fields to slider steps
    private void accept(String text){
        editor = null;
        super.setFocused(false);
        try{
            double val = Double.parseDouble(text.strip());
            if(!Double.isFinite(val)) return;
            val = Math.clamp(val, min, max);
            if(wholeNumbers) val = Math.clamp(Math.round(val), min, max);
            if(val != selected.getAsDouble()) changed.accept(val);
        }catch(NumberFormatException ignored){}
    }

    public void commit(){ if(editing()) editor.accept(); }

    @Override public void setFocused(boolean focused){
        if(focused && !editing()){
            editor = new InlineTextEditor(font, getMessage(), format.apply(selected.getAsDouble()), 32,
                    this::accept, () -> { editor = null; super.setFocused(false); });
            editor.setDecorated(false);
            positionEditor();
        }else if(!focused) commit();
        super.setFocused(focused);
    }

    private void positionEditor(){
        editor.setBounds(getX() + textInset, getY() + (height - font.lineHeight) / 2,
                Math.max(1, width - textInset - 10), 1);
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button){
        if(editing() && !isMouseOver(mouseX, mouseY)) commit();
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override public void onClick(double mouseX, double mouseY){
        if(!editing()){ setFocused(true); return; }
        positionEditor();
        editor.mouseClicked(mouseX, mouseY, 0);
    }

    @Override protected void onDrag(double mouseX, double mouseY, double dragX, double dragY){
        if(editing()) editor.mouseDragged(mouseX, mouseY, 0);
    }

    @Override public void onRelease(double mouseX, double mouseY){
        if(editing()) editor.mouseReleased(mouseX, mouseY, 0);
    }

    @Override public boolean keyPressed(int key, int scanCode, int modifiers){
        if(!editing()) return false;
        if(key == org.lwjgl.glfw.GLFW.GLFW_KEY_TAB){ commit(); return false; }
        return editor.keyPressed(key, scanCode, modifiers);
    }

    @Override public boolean charTyped(char codePoint, int modifiers){
        return editing() && editor.charTyped(codePoint, modifiers);
    }

    @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick){
        setMessage(label.apply(selected.getAsDouble()));
        if(editing()){
            positionEditor();
            editor.render(graphics, mouseX, mouseY, partialTick);
        }else{
            float scale = Math.min(1, Math.max(1, width - textInset - 10) / (float)Math.max(1, font.width(getMessage())));
            graphics.pose().pushPose();
            try{
                graphics.pose().translate(getX() + textInset, getY() + (height - font.lineHeight * scale) / 2, 0);
                graphics.pose().scale(scale, scale, 1);
                graphics.drawString(font, getMessage(), 0, 0, active ? 0xE2E2E2 : 0xA0A0A0, true);
            }finally{ graphics.pose().popPose(); }
        }
    }

    @Override protected void updateWidgetNarration(NarrationElementOutput output){
        output.add(NarratedElementType.TITLE, label.apply(selected.getAsDouble()));
    }
}
