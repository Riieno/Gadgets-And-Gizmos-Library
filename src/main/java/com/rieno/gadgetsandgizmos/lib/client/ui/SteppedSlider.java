package com.rieno.gadgetsandgizmos.lib.client.ui;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;

// Share a draggable, keyboard-accessible slider with fixed steps and caller-owned labels
public final class SteppedSlider extends AbstractSliderButton{
/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        VARIABLES
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    private final double min;
    private final double max;
    private final double step;
    private final DoubleFunction<Component> label;
    private final DoubleConsumer changed;
    private final PaintedSliderStyle style;
    private double selected;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        FUNCTIONS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    public SteppedSlider(int x, int y, int width, int height, double min, double max, double step,
                         double initial, DoubleFunction<Component> label, DoubleConsumer changed){
        this(x, y, width, height, min, max, step, initial, label, changed, null);
    }

    // Painted sliders keep their label for narration while the host draws it in a separate value well
    public SteppedSlider(int x, int y, int width, int height, double min, double max, double step,
                         double initial, DoubleFunction<Component> label, DoubleConsumer changed, PaintedSliderStyle style){
        super(x, y, width, height, Component.empty(), 0);
        if(!Double.isFinite(min) || !Double.isFinite(max) || !Double.isFinite(step) || max <= min || step <= 0){
            throw new IllegalArgumentException("Invalid slider range");
        }
        this.min = min;
        this.max = max;
        this.step = step;
        this.label = label;
        this.changed = changed;
        this.style = style;
        selected = quantize(Double.isFinite(initial) ? initial : min);
        value = (selected - min) / (max - min);
        updateMessage();
    }

    public double selected(){ return selected; }

    public double fraction(){ return (selected - min) / (max - min); }

    // Synchronize an exact typed or external value without quantizing it or notifying the owner
    public void synchronize(double val){
        if(!Double.isFinite(val)) return;
        selected = Math.clamp(val, min, max);
        value = fraction();
        updateMessage();
    }

    @Override public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick){
        if(style == null){ super.renderWidget(graphics, mouseX, mouseY, partialTick); return; }
        style.draw(graphics, getX(), getY(), width, height, fraction(), isHoveredOrFocused());
    }

    @Override public void onClick(double mouseX, double mouseY){
        if(style == null) super.onClick(mouseX, mouseY);
        else selectAt(mouseX);
    }

    @Override protected void onDrag(double mouseX, double mouseY, double dragX, double dragY){
        if(style == null) super.onDrag(mouseX, mouseY, dragX, dragY);
        else selectAt(mouseX);
    }

    private void selectAt(double mouseX){
        value = style.fractionAt(mouseX, getX(), width);
        applyValue();
        updateMessage();
    }

    // Arrow keys move by one actual step instead of a fraction of the widget width
    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers){
        if(active && (key == org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT || key == org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT)){
            double next = quantize(selected + (key == org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT ? -step : step));
            value = (next - min) / (max - min);
            applyValue();
            updateMessage();
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }
    private double quantize(double val){ return Math.clamp(min + Math.round((val - min) / step) * step, min, max); }
    @Override protected void updateMessage(){ if(label != null) setMessage(label.apply(selected)); }
    @Override protected void applyValue(){
        double next = quantize(min + value * (max - min));
        if(next == selected) return;
        selected = next;
        changed.accept(next);
    }
}
