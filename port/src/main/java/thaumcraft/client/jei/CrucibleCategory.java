package thaumcraft.client.jei;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import net.minecraft.world.item.crafting.RecipeHolder;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.crafting.CrucibleRecipe;

final class CrucibleCategory extends ThaumcraftRecipeCategory<CrucibleRecipe> {
    CrucibleCategory(IGuiHelper gui) {
        super(gui, ThaumcraftJeiPlugin.CRUCIBLE, BlocksTC.crucible, "thaumcraft.jei.crucible");
    }

    @Override public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<CrucibleRecipe> holder, IFocusGroup focuses) {
        builder.addInputSlot(24, 8).setBackground(gui.getSlotDrawable(), -1, -1).addIngredients(holder.value().getCatalyst());
        output(builder, holder.value().getRecipeOutput(), 120, 8);
        var costs = holder.value().getAspects();
        for (var aspect : costs.getAspects()) builder.addInvisibleIngredients(mezz.jei.api.recipe.RecipeIngredientRole.INPUT)
            .addIngredient(AspectIngredientHelper.TYPE, new JeiAspectIngredient(aspect.getTag(), costs.getAmount(aspect)));
    }

    @Override public void createRecipeExtras(IRecipeExtrasBuilder builder, RecipeHolder<CrucibleRecipe> holder, IFocusGroup focuses) {
        builder.addRecipeArrow().setPosition(70, 8);
        var text = details(holder.value().getResearch());
        aspects(text, holder.value().getAspects());
        detailsBox(builder, text, 36);
    }
}
