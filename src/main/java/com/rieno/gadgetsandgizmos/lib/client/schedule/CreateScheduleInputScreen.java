package com.rieno.gadgetsandgizmos.lib.client.schedule;

import com.simibubi.create.content.trains.schedule.IScheduleInput;
import com.simibubi.create.foundation.gui.ModularGuiLine;
import com.simibubi.create.foundation.gui.ModularGuiLineBuilder;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

// Host Create's native schedule configuration without owning a schedule runtime
public final class CreateScheduleInputScreen extends Screen {
    private final Screen parent;
    private final IScheduleInput input;
    private final HolderLookup.Provider registries;
    private final Consumer<CompoundTag> save;
    private final ModularGuiLine line = new ModularGuiLine();
    private int left;
    private int top;

    public CreateScheduleInputScreen(Screen parent, IScheduleInput input,
                                    HolderLookup.Provider registries, Consumer<CompoundTag> save){
        super(input.getSummary().getSecond());
        this.parent = parent;
        this.input = input;
        this.registries = registries;
        this.save = save;
    }

    @Override
    protected void init(){
        left = Math.max(4, (width - 260) / 2);
        top = Math.max(4, (height - 124) / 2);
        line.clear();
        input.initConfigurationWidgets(new ModularGuiLineBuilder(font, line, left + 12, top + 45));
        line.loadValues(input.getData(), this::addRenderableWidget, this::addRenderableWidget);
        addRenderableWidget(Button.builder(Component.literal("Done"), button -> commit())
                .bounds(left + 12, top + 90, 112, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> onClose())
                .bounds(left + 136, top + 90, 112, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick){
        graphics.fill(0, 0, width, height, 0xA0000000);
        graphics.fill(left, top, left + 260, top + 124, 0xFF202C31);
        graphics.drawString(font, title, left + 12, top + 12, 0xFFFFFFFF, false);
        graphics.pose().pushPose();
        graphics.pose().translate(0, top + 45, 0);
        line.renderWidgetBG(left + 12, graphics);
        graphics.pose().popPose();
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers){
        if(key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER
                || key == GLFW.GLFW_KEY_S && hasControlDown()){
            commit();
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    private void commit(){
        CompoundTag data = input.getData().copy();
        line.saveValues(data);
        input.setData(registries, data);
        save.accept(input.getData().copy());
        onClose();
    }

    @Override
    public void onClose(){
        if(minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen(){
        return false;
    }
}
