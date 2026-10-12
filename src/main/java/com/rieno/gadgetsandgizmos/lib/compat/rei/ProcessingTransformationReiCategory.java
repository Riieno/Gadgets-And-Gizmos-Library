package com.rieno.gadgetsandgizmos.lib.compat.rei;

import com.rieno.gadgetsandgizmos.lib.client.recipe.ProcessingTransformationLayout;
import com.rieno.gadgetsandgizmos.lib.create.ProcessingTransformationRecipes;
import me.shedaniel.math.Point;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.gui.Renderer;
import me.shedaniel.rei.api.client.gui.widgets.Widget;
import me.shedaniel.rei.api.client.gui.widgets.Widgets;
import me.shedaniel.rei.api.client.registry.display.DisplayCategory;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.basic.BasicDisplay;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.util.EntryIngredients;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// Show the processing ingredients, intermediate and completed transformation in REI.
public final class ProcessingTransformationReiCategory implements DisplayCategory<ProcessingTransformationReiCategory.Display>{
    private final CategoryIdentifier<Display> id;
    private final Component title;
    private final ItemStack icon;

    public ProcessingTransformationReiCategory(ResourceLocation id, Component title, ItemStack icon){
        this.id = CategoryIdentifier.of(id);
        this.title = title;
        this.icon = icon.copy();
    }

    @Override public CategoryIdentifier<Display> getCategoryIdentifier(){ return id; }
    @Override public Component getTitle(){ return title; }
    @Override public Renderer getIcon(){ return EntryStacks.of(icon); }
    @Override public int getDisplayHeight(){ return ProcessingTransformationLayout.HEIGHT; }
    @Override public int getDisplayWidth(Display display){ return ProcessingTransformationLayout.WIDTH; }

    @Override
    public List<Widget> setupDisplay(Display display, Rectangle bounds){
        List<Widget> widgets = new ArrayList<>();
        widgets.add(Widgets.createRecipeBase(bounds));
        widgets.add(Widgets.createDrawableWidget((graphics, mouseX, mouseY, delta) ->
                ProcessingTransformationLayout.render(graphics, bounds.x, bounds.y, display.view)));
        for(int idx = 0; idx < display.getInputEntries().size(); idx++){
            widgets.add(Widgets.createSlot(new Point(bounds.x + ProcessingTransformationLayout.inputX(idx),
                            bounds.y + ProcessingTransformationLayout.inputY(idx)))
                    .entries(display.getInputEntries().get(idx)).markInput());
        }
        widgets.add(Widgets.createSlot(new Point(bounds.x + ProcessingTransformationLayout.INTERMEDIATE_X,
                        bounds.y + ProcessingTransformationLayout.OUTPUT_Y))
                .entry(EntryStacks.of(display.view.intermediate())));
        widgets.add(Widgets.createSlot(new Point(bounds.x + ProcessingTransformationLayout.RESULT_X,
                        bounds.y + ProcessingTransformationLayout.OUTPUT_Y))
                .entries(display.getOutputEntries().getFirst()).markOutput());
        return widgets;
    }

    public static final class Display extends BasicDisplay{
        private final CategoryIdentifier<Display> category;
        private final ProcessingTransformationRecipes.View view;

        public Display(ResourceLocation category, ProcessingTransformationRecipes.View view){
            super(inputs(view), List.of(EntryIngredients.of(view.result())), Optional.of(view.id()));
            this.category = CategoryIdentifier.of(category);
            this.view = view;
        }

        private static List<EntryIngredient> inputs(ProcessingTransformationRecipes.View view){
            List<EntryIngredient> inputs = new ArrayList<>();
            for(var ingredient : view.itemInputs()) inputs.add(EntryIngredients.ofIngredient(ingredient));
            for(var fluids : view.fluidInputs()){
                inputs.add(EntryIngredient.of(fluids.stream().map(fluid -> EntryStacks.of(
                        dev.architectury.fluid.FluidStack.create(fluid.getFluid(), fluid.getAmount(),
                                fluid.getComponentsPatch()))).toList()));
            }
            return inputs;
        }

        @Override public CategoryIdentifier<Display> getCategoryIdentifier(){ return category; }
    }
}
