package com.rieno.gadgetsandgizmos.lib.compat.emi;

import com.rieno.gadgetsandgizmos.lib.client.recipe.ProcessingTransformationLayout;
import com.rieno.gadgetsandgizmos.lib.create.ProcessingTransformationRecipes;
import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.List;

// Present processing followed by passive completion without claiming automatic recipe transfer.
public final class ProcessingTransformationEmiRecipe extends BasicEmiRecipe{
    private final ProcessingTransformationRecipes.View view;

    public ProcessingTransformationEmiRecipe(EmiRecipeCategory category, ProcessingTransformationRecipes.View view,
                                            List<ItemStack> workstations){
        super(category, view.id(), ProcessingTransformationLayout.WIDTH, ProcessingTransformationLayout.HEIGHT);
        this.view = view;
        for(var ingredient : view.itemInputs()) inputs.add(EmiIngredient.of(ingredient));
        for(var fluids : view.fluidInputs()){
            inputs.add(EmiIngredient.of(fluids.stream().map(fluid ->
                    EmiStack.of(fluid.getFluid(), fluid.getComponentsPatch(), fluid.getAmount())).toList()));
        }
        outputs.add(EmiStack.of(view.result()));
        for(var workstation : workstations) catalysts.add(EmiStack.of(workstation));
    }

    @Override public RecipeHolder<?> getBackingRecipe(){ return null; }
    @Override public boolean supportsRecipeTree(){ return false; }

    @Override
    public void addWidgets(WidgetHolder widgets){
        widgets.addDrawable(0, 0, width, height, (graphics, mouseX, mouseY, delta) ->
                ProcessingTransformationLayout.render(graphics, 0, 0, view));
        for(int idx = 0; idx < inputs.size(); idx++){
            widgets.addSlot(inputs.get(idx), ProcessingTransformationLayout.inputX(idx),
                    ProcessingTransformationLayout.inputY(idx));
        }
        widgets.addSlot(EmiStack.of(view.intermediate()), ProcessingTransformationLayout.INTERMEDIATE_X,
                ProcessingTransformationLayout.OUTPUT_Y);
        widgets.addSlot(outputs.getFirst(), ProcessingTransformationLayout.RESULT_X,
                ProcessingTransformationLayout.OUTPUT_Y).recipeContext(this);
    }
}
