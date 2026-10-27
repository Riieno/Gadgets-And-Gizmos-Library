package com.rieno.gadgetsandgizmos.lib.client.ui;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.blaze3d.systems.RenderSystem;
import com.rieno.gadgetsandgizmos.lib.client.render.GuiTextureRegion;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.Objects;

// Keep a checkbox and its label centered inside a compact control's actual hitbox
public final class CompactCheckbox extends AbstractButton{
/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        VARIABLES
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    private final Font font;
    private final BooleanSupplier selected;
    private final Consumer<Boolean> changed;
    private final Appearance appearance;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        FUNCTIONS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    public CompactCheckbox(int x, int y, int width, int height, Component label, Font font,
                           BooleanSupplier selected, Consumer<Boolean> changed){
        this(x, y, width, height, label, font, selected, changed, null);
    }

    // Painted checkboxes align their box and label to the left of the supplied hitbox
    public CompactCheckbox(int x, int y, int width, int height, Component label, Font font,
                           BooleanSupplier selected, Consumer<Boolean> changed, Appearance appearance){
        super(x, y, width, height, label);
        this.font = font;
        this.selected = selected;
        this.changed = changed;
        this.appearance = appearance;
    }

    public boolean selected(){ return selected.getAsBoolean(); }
    @Override public void onPress(){ changed.accept(!selected()); }

    @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick){
        if(width <= 0 || height <= 0 || alpha <= 0) return;
        if(appearance != null){ renderPainted(graphics); return; }
        int box = Math.min(10, Math.min(width, height));
        int gap = Math.min(4, Math.max(0, width - box));
        int labelWidth = font.width(getMessage());
        float factor = Math.min(1, Math.min(height / (float)font.lineHeight,
                (width - box - gap) / (float)Math.max(1, labelWidth)));
        int left = getX() + Math.round((width - box - gap - labelWidth * factor) / 2);
        String sprite = "widget/checkbox" + (selected() ? "_selected" : "") + (isHoveredOrFocused() ? "_highlighted" : "");
        RenderSystem.enableDepthTest();
        RenderSystem.enableBlend();
        graphics.setColor(1, 1, 1, alpha);
        graphics.blitSprite(ResourceLocation.withDefaultNamespace(sprite), left, getY() + (height - box) / 2, box, box);
        graphics.setColor(1, 1, 1, 1);
        if(factor <= 0) return;
        graphics.pose().pushPose();
        try{
            graphics.pose().translate(left + box + gap, getY() + (height - font.lineHeight * factor) / 2, 0);
            graphics.pose().scale(factor, factor, 1);
            graphics.drawString(font, getMessage(), 0, 0, active ? 0xE0E0E0 : 0xA0A0A0, false);
        }finally{ graphics.pose().popPose(); }
    }

    private void renderPainted(GuiGraphics graphics){
        GuiTextureRegion box = selected() ? appearance.checked() : appearance.unchecked();
        int top = getY() + (height - box.height()) / 2;
        box.draw(graphics, getX(), top);
        if(isHoveredOrFocused()) graphics.fill(getX() + 1, top + 1, getX() + box.width() - 1, top + box.height() - 1, 0x22FFFFFF);
        graphics.drawString(font, getMessage(), getX() + box.width() + appearance.gap(),
                getY() + (height - font.lineHeight) / 2, active ? 0xE2E2E2 : 0xA0A0A0, true);
    }

    public record Appearance(GuiTextureRegion unchecked, GuiTextureRegion checked, int gap){
        public Appearance{
            Objects.requireNonNull(unchecked);
            Objects.requireNonNull(checked);
            if(gap < 0 || unchecked.width() != checked.width() || unchecked.height() != checked.height()){
                throw new IllegalArgumentException("Checkbox textures must have matching dimensions and a nonnegative gap");
            }
        }
    }

    @Override protected void updateWidgetNarration(NarrationElementOutput output){
        output.add(NarratedElementType.TITLE, getMessage().copy().append(": ")
                .append(Component.translatable(selected() ? "options.on" : "options.off")));
        if(active) output.add(NarratedElementType.USAGE, Component.translatable(isFocused()
                ? "narration.checkbox.usage.focused" : "narration.checkbox.usage.hovered"));
    }
}
