package com.rieno.gadgetsandgizmos.lib.client.schedule;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.simibubi.create.content.trains.schedule.IScheduleInput;
import com.simibubi.create.content.trains.schedule.DestinationSuggestions;
import com.simibubi.create.content.trains.schedule.Schedule;
import com.simibubi.create.content.trains.schedule.condition.ScheduleWaitCondition;
import com.simibubi.create.content.trains.schedule.destination.ScheduleInstruction;
import com.simibubi.create.foundation.gui.ModularGuiLine;
import com.simibubi.create.foundation.gui.ModularGuiLineBuilder;
import com.simibubi.create.foundation.gui.widget.Label;
import com.simibubi.create.foundation.gui.widget.ScrollInput;
import com.simibubi.create.foundation.gui.widget.SelectionScrollInput;
import com.rieno.gadgetsandgizmos.lib.client.ui.ItemPickerScreen;
import com.rieno.gadgetsandgizmos.lib.client.ui.LayeredItemRenderer;
import net.createmod.catnip.data.IntAttached;
import net.createmod.catnip.data.Pair;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

// Host Create's native schedule configuration without owning a schedule runtime
public final class CreateScheduleInputScreen extends Screen{
    // Retain the caller's screen, registry context and detached edit callbacks
    private final Screen parent;
    private IScheduleInput input;
    private final HolderLookup.Provider registries;
    private final Consumer<IScheduleInput> save;
    private final Function<IScheduleInput, List<String>> suggestions;
    private final Predicate<IScheduleInput> allowedType;
    // Retain unsaved native field values while opening the item browser or resizing
    private EditBox destinationField;
    private IScheduleInput fieldInput;
    private final ModularGuiLine line = new ModularGuiLine(){
        // Attach completion only to the destination field, never a numeric threshold
        @Override public void add(Pair<AbstractWidget, String> field){
            super.add(field);
            if(field.getFirst() instanceof EditBox box
                    && ("Text".equals(field.getSecond()) || "Dock".equals(field.getSecond()))){
                destinationField = box;
            }
        }
    };
    private final List<ScrollInput> scrollInputs = new ArrayList<>();
    private DestinationSuggestions destinationSuggestions;
    private int selectedSlot;
    private int left;
    private int top;

    // Preserve the data-only editor contract for existing callers
    public CreateScheduleInputScreen(Screen parent, IScheduleInput input,
                                    HolderLookup.Provider registries, Consumer<CompoundTag> save){
        this(parent, input, registries, edited -> save.accept(edited.getData().copy()),
                edited -> List.of(), edited -> edited.getId().equals(input.getId()));
    }

    // Edit a complete input including its type, native fields and item parameters
    public CreateScheduleInputScreen(Screen parent, IScheduleInput input,
                                    HolderLookup.Provider registries, Consumer<IScheduleInput> save,
                                    Function<IScheduleInput, List<String>> suggestions,
                                    Predicate<IScheduleInput> allowedType){
        super(input.getSummary().getSecond());
        this.parent = parent;
        this.input = input;
        this.registries = registries;
        this.save = save;
        this.suggestions = suggestions;
        this.allowedType = allowedType;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Attach the registered input's own widgets and parameter slots to the editor
    @Override
    protected void init(){
        saveFields();
        left = Math.max(4, (width - 310) / 2);
        top = Math.max(4, (height - 252) / 2);
        line.clear();
        scrollInputs.clear();
        destinationSuggestions = null;
        destinationField = null;
        List<IScheduleInput> types = types();
        List<Component> opts = types.stream().map(type -> (Component)Component.translatable(type.getId().getNamespace()
                + ".schedule." + (type instanceof ScheduleInstruction ? "instruction." : "condition.")
                + type.getId().getPath())).toList();
        SelectionScrollInput selector = new SelectionScrollInput(left + 12, top + 32, 286, 18);
        Label label = new Label(left + 17, top + 36, Component.empty());
        label.withShadow();
        selector.forOptions(opts).writingTo(label).titled(Component.translatable(input instanceof ScheduleInstruction
                ? "create.schedule.instruction_type" : "create.schedule.condition_type"));
        for(int idx = 0; idx < types.size(); idx++){
            if(types.get(idx).getId().equals(input.getId())) selector.setState(idx);
        }
        selector.calling(idx -> {
            IScheduleInput next = types.get(idx);
            if(next.getId().equals(input.getId())) return;
            input = next;
            selectedSlot = 0;
            rebuildWidgets();
        });
        addRenderableWidget(label);
        addRenderableWidget(selector);
        scrollInputs.add(selector);
        input.initConfigurationWidgets(new ModularGuiLineBuilder(font, line, left + 12, top + 70));
        line.loadValues(input.getData(), this::addRenderableWidget, this::addRenderableOnly);
        fieldInput = input;
        line.forEach(widget -> {
            if(widget instanceof ScrollInput scroll) scrollInputs.add(scroll);
        });
        List<IntAttached<String>> names = suggestions.apply(input).stream().distinct().sorted()
                .map(name -> IntAttached.with(0, name)).toList();
        if(destinationField != null && !names.isEmpty()){
            destinationSuggestions = new DestinationSuggestions(minecraft, this, destinationField, font, names, false, top + 18);
            destinationSuggestions.setAllowSuggestions(true);
            destinationField.setResponder(val -> destinationSuggestions.updateCommandInfo());
        }
        if(input.slotsTargeted() > 0){
            addRenderableWidget(Button.builder(Component.translatable("gui.recipebook.search_hint"), button -> browseItem())
                    .bounds(left + 172, top + 92, 126, 20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Done"), button -> commit())
                .bounds(left + 12, top + 225, 136, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> onClose())
                .bounds(left + 162, top + 225, 136, 20).build());
    }

    // Get every registered instruction or condition accepted by the caller
    private List<IScheduleInput> types(){
        List<IScheduleInput> res = new ArrayList<>();
        if(input instanceof ScheduleInstruction){
            for(var type : Schedule.INSTRUCTION_TYPES) addType(res, type.getSecond());
        }else{
            for(var type : Schedule.CONDITION_TYPES) addType(res, type.getSecond());
        }
        if(res.stream().noneMatch(type -> type.getId().equals(input.getId()))) res.addFirst(input);
        return res;
    }

    // Keep unavailable integrations out of the current type selector
    private void addType(List<IScheduleInput> types, Supplier<? extends IScheduleInput> factory){
        IScheduleInput type = factory.get();
        if(allowedType.test(type)) types.add(type);
    }

    // Preserve configured inventory filters while allowing registry items to be selected
    private void browseItem(){
        saveFields();
        ItemStack current = input.getItem(selectedSlot);
        ResourceLocation selected = current.isEmpty() ? null : BuiltInRegistries.ITEM.getKey(current.getItem());
        minecraft.setScreen(new ItemPickerScreen(this, selected,
                id -> input.setItem(selectedSlot, new ItemStack(BuiltInRegistries.ITEM.get(id)))));
    }

    // Refresh native scroll state and destination completions
    @Override public void tick(){
        scrollInputs.forEach(ScrollInput::tick);
        if(destinationSuggestions != null) destinationSuggestions.tick();
    }

    // Draw native controls, ghost parameters and the player's inventory
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick){
        graphics.fill(0, 0, width, height, 0xA0000000);
        graphics.fill(left, top, left + 310, top + 252, 0xFF202C31);
        graphics.drawString(font, input.getSummary().getSecond(), left + 12, top + 12, 0xFFFFFFFF, false);
        graphics.pose().pushPose();
        graphics.pose().translate(0, top + 66, 0);
        line.renderWidgetBG(left + 12, graphics);
        graphics.pose().popPose();
        for(int idx = 0; idx < input.slotsTargeted(); idx++){
            drawSlot(graphics, input.getItem(idx), left + 12 + idx * 22, top + 92, idx == selectedSlot);
        }
        if(minecraft.player != null && input.slotsTargeted() > 0){
            graphics.drawString(font, Component.translatable("container.inventory"), left + 12, top + 120, 0xFFB6C8D0, false);
            for(int idx = 0; idx < 36; idx++){
                int inventoryIdx = idx < 27 ? idx + 9 : idx - 27;
                drawSlot(graphics, minecraft.player.getInventory().getItem(inventoryIdx),
                        left + 12 + idx % 9 * 22, top + 133 + idx / 9 * 22, false);
            }
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        for(int idx = 0; idx < input.slotsTargeted(); idx++){
            if(mouseX < left + 12 + idx * 22 || mouseX >= left + 32 + idx * 22
                    || mouseY < top + 92 || mouseY >= top + 112) continue;
            List<Component> tooltip = input.getSecondLineTooltip(idx);
            if(tooltip != null && !tooltip.isEmpty()) graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
            else if(!input.getItem(idx).isEmpty()) graphics.renderTooltip(font, input.getItem(idx), mouseX, mouseY);
        }
        if(minecraft.player != null && input.slotsTargeted() > 0 && mouseX >= left + 12 && mouseX < left + 210
                && mouseY >= top + 133 && mouseY < top + 221){
            int idx = (mouseX - left - 12) / 22 + (mouseY - top - 133) / 22 * 9;
            ItemStack stack = minecraft.player.getInventory().getItem(idx < 27 ? idx + 9 : idx - 27);
            if(!stack.isEmpty()) graphics.renderTooltip(font, stack, mouseX, mouseY);
        }
        line.forEach(widget -> {
            if(widget instanceof com.simibubi.create.foundation.gui.widget.TooltipArea area && area.isMouseOver(mouseX, mouseY)){
                graphics.renderComponentTooltip(font, area.getToolTip(), mouseX, mouseY);
            }else if(widget instanceof ScrollInput scroll && scroll.isMouseOver(mouseX, mouseY)){
                graphics.renderComponentTooltip(font, scroll.getToolTip(), mouseX, mouseY);
            }
        });
        if(destinationSuggestions != null) destinationSuggestions.render(graphics, mouseX, mouseY);
    }

    // Draw one non-consuming item selection
    private void drawSlot(GuiGraphics graphics, ItemStack stack, int x, int y, boolean selected){
        graphics.fill(x, y, x + 20, y + 20, selected ? 0xFFE59A42 : 0xFF54717C);
        graphics.fill(x + 1, y + 1, x + 19, y + 19, 0xFF142027);
        LayeredItemRenderer.renderVisible(graphics, stack, x + 2, y + 2);
    }

    // Copy configured filters or carried items into the selected ghost parameter
    @Override public boolean mouseClicked(double x, double y, int button){
        if(destinationSuggestions != null && destinationSuggestions.mouseClicked((int)x, (int)y, button)) return true;
        for(int idx = 0; idx < input.slotsTargeted(); idx++){
            if(x < left + 12 + idx * 22 || x >= left + 32 + idx * 22 || y < top + 92 || y >= top + 112) continue;
            selectedSlot = idx;
            if(button == 1) input.setItem(idx, ItemStack.EMPTY);
            else if(minecraft.player != null && !minecraft.player.containerMenu.getCarried().isEmpty()){
                input.setItem(idx, minecraft.player.containerMenu.getCarried().copyWithCount(1));
            }
            return true;
        }
        if(minecraft.player != null && input.slotsTargeted() > 0 && x >= left + 12 && x < left + 210
                && y >= top + 133 && y < top + 221){
            int idx = (int)(x - left - 12) / 22 + (int)(y - top - 133) / 22 * 9;
            int inventoryIdx = idx < 27 ? idx + 9 : idx - 27;
            input.setItem(selectedSlot, minecraft.player.getInventory().getItem(inventoryIdx).copyWithCount(1));
            return true;
        }
        return super.mouseClicked(x, y, button);
    }

    // Let completion navigation consume keys before confirming the native form
    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers){
        if(destinationSuggestions != null && destinationSuggestions.keyPressed(key, scanCode, modifiers)) return true;
        if(key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER
                || key == GLFW.GLFW_KEY_S && hasControlDown()){
            commit();
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    // Route scrolling to destination completions before the native controls
    @Override public boolean mouseScrolled(double x, double y, double sx, double sy){
        if(destinationSuggestions != null && destinationSuggestions.mouseScrolled(sy)) return true;
        return super.mouseScrolled(x, y, sx, sy);
    }

    // Preserve native form edits across resizing and nested item selection
    @Override public void removed(){
        saveFields();
    }

    // Save fields without replacing the input's additional item or filter data
    private void saveFields(){
        if(fieldInput != input) return;
        CompoundTag data = input.getData().copy();
        line.saveValues(data);
        input.setData(registries, data);
    }

    // Return the complete edited input to the caller
    private void commit(){
        saveFields();
        save.accept(input);
        onClose();
    }

    // Return to the caller without accepting the detached input
    @Override
    public void onClose(){
        if(minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen(){
        return false;
    }
}
