package thaumcraft.client.jei;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.network.chat.Component;
import thaumcraft.api.aspects.Aspect;

public final class AspectCombinationCategory implements IRecipeCategory<AspectCombinationRecipe> {
    private final IGuiHelper gui;
    private final IDrawable icon;

    public AspectCombinationCategory(IGuiHelper gui) {
        this.gui = gui;
        icon = gui.createDrawableIngredient(AspectIngredientHelper.TYPE, new JeiAspectIngredient(Aspect.MAGIC.getTag()));
    }
    @Override public RecipeType<AspectCombinationRecipe> getRecipeType() { return AspectJeiRecipes.COMBINATIONS; }
    @Override public Component getTitle() { return Component.translatable("thaumcraft.jei.aspect_combinations"); }
    @Override public IDrawable getIcon() { return icon; }
    @Override public int getWidth() { return 160; }
    @Override public int getHeight() { return 38; }
    @Override public void setRecipe(IRecipeLayoutBuilder builder, AspectCombinationRecipe recipe, IFocusGroup focuses) {
        builder.addInputSlot(20, 10).setBackground(gui.getSlotDrawable(), -1, -1)
            .addIngredient(AspectIngredientHelper.TYPE, new JeiAspectIngredient(recipe.first()));
        builder.addInputSlot(48, 10).setBackground(gui.getSlotDrawable(), -1, -1)
            .addIngredient(AspectIngredientHelper.TYPE, new JeiAspectIngredient(recipe.second()));
        builder.addOutputSlot(124, 10).setBackground(gui.getSlotDrawable(), -1, -1)
            .addIngredient(AspectIngredientHelper.TYPE, new JeiAspectIngredient(recipe.result()));
    }
    @Override public void createRecipeExtras(IRecipeExtrasBuilder builder, AspectCombinationRecipe recipe, IFocusGroup focuses) {
        builder.addRecipeArrow().setPosition(84, 10);
    }
}
