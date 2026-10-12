package com.rieno.gadgetsandgizmos.lib.compat.jei;

import com.rieno.gadgetsandgizmos.lib.client.recipe.ProcessingTransformationLayout;
import com.rieno.gadgetsandgizmos.lib.create.ProcessingTransformationRecipes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

// Index the completed item while displaying its intermediate processing result.
public final class ProcessingTransformationJeiCategory implements IRecipeCategory<ProcessingTransformationRecipes.View>{
    private final RecipeType<ProcessingTransformationRecipes.View> type;
    private final Component title;
    private final IDrawable icon;

    public ProcessingTransformationJeiCategory(ResourceLocation id, Component title, ItemStack icon, IGuiHelper gui){
        type = new RecipeType<>(id, ProcessingTransformationRecipes.View.class);
        this.title = title;
        this.icon = gui.createDrawableItemStack(icon);
    }

    @Override public RecipeType<ProcessingTransformationRecipes.View> getRecipeType(){ return type; }
    @Override public Component getTitle(){ return title; }
    @Override public IDrawable getIcon(){ return icon; }
    @Override public int getWidth(){ return ProcessingTransformationLayout.WIDTH; }
    @Override public int getHeight(){ return ProcessingTransformationLayout.HEIGHT; }
    @Override public ResourceLocation getRegistryName(ProcessingTransformationRecipes.View view){ return view.id(); }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, ProcessingTransformationRecipes.View view, IFocusGroup focuses){
        int idx = 0;
        for(var ingredient : view.itemInputs()){
            builder.addInputSlot(ProcessingTransformationLayout.inputX(idx), ProcessingTransformationLayout.inputY(idx))
                    .addIngredients(ingredient).setStandardSlotBackground();
            idx++;
        }
        for(var fluids : view.fluidInputs()){
            var slot = builder.addInputSlot(ProcessingTransformationLayout.inputX(idx),
                    ProcessingTransformationLayout.inputY(idx)).setStandardSlotBackground();
            for(var fluid : fluids){
                slot.addFluidStack(fluid.getFluid(), fluid.getAmount(), fluid.getComponentsPatch());
            }
            idx++;
        }
        builder.addSlot(RecipeIngredientRole.RENDER_ONLY, ProcessingTransformationLayout.INTERMEDIATE_X,
                        ProcessingTransformationLayout.OUTPUT_Y)
                .addItemStack(view.intermediate()).setStandardSlotBackground();
        builder.addOutputSlot(ProcessingTransformationLayout.RESULT_X, ProcessingTransformationLayout.OUTPUT_Y)
                .addItemStack(view.result()).setOutputSlotBackground();
    }

    @Override
    public void draw(ProcessingTransformationRecipes.View view, IRecipeSlotsView slots, GuiGraphics graphics,
                     double mouseX, double mouseY){
        ProcessingTransformationLayout.render(graphics, 0, 0, view);
    }
}
