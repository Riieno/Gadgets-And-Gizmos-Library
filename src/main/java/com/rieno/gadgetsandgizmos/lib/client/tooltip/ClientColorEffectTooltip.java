package com.rieno.gadgetsandgizmos.lib.client.tooltip;

import com.rieno.gadgetsandgizmos.lib.color.ColorEffectPreset;
import com.rieno.gadgetsandgizmos.lib.tooltip.ColorEffectTooltip;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

// Preview saved colors and reveal virtual ingredient counts while Shift is held
public final class ClientColorEffectTooltip implements ClientTooltipComponent{
    private static final int COLUMNS = 9;
    private static final int SLOT_SIZE = 22;
    private static final int INGREDIENT_Y = 70;
    private final ColorEffectTooltip data;

    public ClientColorEffectTooltip(ColorEffectTooltip data){ this.data = data; }

    private static Component text(String key, Object... args){
        return Component.translatable("gadgetsngizmos.tooltip.color_effect." + key, args);
    }

    private Component mode(){
        var preset = data.preset();
        var mode = text("mode." + preset.mode().name().toLowerCase(Locale.ROOT));
        var label = text("mode", mode).copy().withStyle(ChatFormatting.GOLD);
        if(preset.mode() == ColorEffectPreset.Mode.SOLID){
            label.append(Component.literal(String.format(Locale.ROOT, " #%06X", preset.color()))
                    .withStyle(ChatFormatting.GRAY));
        }
        return label;
    }

    private static Component status(String key, boolean enabled){
        var state = Component.translatable(enabled ? "options.on" : "options.off")
                .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.GRAY);
        return text(key, state);
    }

    @Override public int getHeight(){
        if(data.ingredients().isEmpty()) return 56;
        return Screen.hasShiftDown() ? INGREDIENT_Y + (data.ingredients().size() + COLUMNS - 1) / COLUMNS * SLOT_SIZE + 4 : 69;
    }

    @Override public int getWidth(Font font){
        int width = Math.max(160, Math.min(COLUMNS, data.ingredients().size()) * SLOT_SIZE);
        width = Math.max(width, font.width(mode()));
        width = Math.max(width, font.width(status("animated", data.animationActive())));
        width = Math.max(width, font.width(status("additive", data.preset().additive())));
        width = Math.max(width, font.width(text("default_colors")) + 4);
        if(!data.ingredients().isEmpty()){
            width = Math.max(width, font.width(text(Screen.hasShiftDown() ? "ingredients" : "hold_shift")));
        }
        return width;
    }

    @Override public void renderImage(Font font, int x, int y, GuiGraphics graphics){
        int width = getWidth(font);
        graphics.drawString(font, mode(), x, y, 0xDDDDDD, false);
        graphics.fill(x, y + 13, x + width, y + 26, 0xFF666666);
        if(data.preset().active()){
            for(int offset = 1; offset < width - 1; offset++){
                int color = data.preset().samplePosition((offset - 1) / (float)(width - 3));
                graphics.fill(x + offset, y + 14, x + offset + 1, y + 25, 0xFF000000 | color);
            }
            if(data.preset().usesStops()){
                for(var stop : data.preset().stops()){
                    int tick = x + 1 + Math.round(stop.position() * (width - 3));
                    graphics.fill(tick, y + 26, tick + 1, y + 29, 0xFF000000 | stop.color());
                }
            }
        }else{
            graphics.fill(x + 1, y + 14, x + width - 1, y + 25, 0xFF242424);
            graphics.drawCenteredString(font, text("default_colors"), x + width / 2, y + 15, 0xAAAAAA);
        }
        graphics.drawString(font, status("animated", data.animationActive()), x, y + 32, 0xCCCCCC, false);
        graphics.drawString(font, status("additive", data.preset().additive()), x, y + 43, 0xCCCCCC, false);
        if(data.ingredients().isEmpty()) return;
        if(!Screen.hasShiftDown()){
            graphics.drawString(font, text("hold_shift").copy().withStyle(ChatFormatting.DARK_GRAY), x, y + 56, 0x888888, false);
            return;
        }
        graphics.drawString(font, text("ingredients"), x, y + 56, 0xEACB76, false);
        for(int index = 0; index < data.ingredients().size(); index++){
            var ingredient = data.ingredients().get(index);
            int slotX = x + index % COLUMNS * SLOT_SIZE;
            int slotY = y + INGREDIENT_Y + index / COLUMNS * SLOT_SIZE;
            graphics.fill(slotX, slotY, slotX + SLOT_SIZE - 1, slotY + SLOT_SIZE - 1, 0x55333333);
            graphics.renderItem(new ItemStack(ingredient.item()), slotX + 2, slotY + 1);
            graphics.pose().pushPose();
            try{
                graphics.pose().translate(0, 0, 200);
                String count = Integer.toString(ingredient.count());
                graphics.drawString(font, count, slotX + SLOT_SIZE - 2 - font.width(count), slotY + 11,
                        ingredient.count() == 0 ? 0x999999 : 0xFFFFFF, true);
            }finally{
                graphics.pose().popPose();
            }
        }
    }
}
