package thaumcraft.client.jei;

import java.util.List;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.crafting.RecipeHolder;
import thaumcraft.api.ThaumcraftApiHelper;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.crafting.IArcaneRecipe;
import thaumcraft.api.crafting.ShapelessArcaneRecipe;
import thaumcraft.common.lib.crafting.ArcaneWorkbenchCosts;
import thaumcraft.common.lib.crafting.RecipeArcaneWorkbench;

final class ArcaneCategory extends ThaumcraftRecipeCategory<IArcaneRecipe> {
    ArcaneCategory(IGuiHelper gui) {
        super(gui, ThaumcraftJeiPlugin.ARCANE, BlocksTC.arcaneWorkbench, "thaumcraft.jei.arcane");
    }

    @Override public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<IArcaneRecipe> holder, IFocusGroup focuses) {
        var recipe = holder.value();
        var ingredients = recipe.getIngredients();
        List<Integer> cells = recipe instanceof RecipeArcaneWorkbench shaped
            ? ArcaneGridLayout.shapedSlots(shaped.pattern().width(), shaped.pattern().height())
            : java.util.stream.IntStream.range(0, ingredients.size()).boxed().toList();
        for (int index = 0; index < ingredients.size(); index++) {
            int cell = cells.get(index);
            builder.addInputSlot(8 + cell % 3 * 18, 4 + cell / 3 * 18)
                .setBackground(gui.getSlotDrawable(), -1, -1).addIngredients(ingredients.get(index));
        }
        var level = Minecraft.getInstance().level;
        if (level != null) output(builder, recipe.getResultItem(level.registryAccess()), 126, 22);
        for (int index = 0; index < 6; index++) {
            int count = recipe.crystals().get(index);
            if (count > 0) builder.addInputSlot(8 + index * 20, 64)
                .setBackground(gui.getSlotDrawable(), -1, -1)
                .addItemStack(ThaumcraftApiHelper.makeCrystal(ArcaneWorkbenchCosts.PRIMALS[index], count));
            if (count > 0) builder.addInvisibleIngredients(mezz.jei.api.recipe.RecipeIngredientRole.INPUT)
                .addIngredient(AspectIngredientHelper.TYPE, new JeiAspectIngredient(ArcaneWorkbenchCosts.PRIMALS[index].getTag(), count));
        }
    }

    @Override public void createRecipeExtras(IRecipeExtrasBuilder builder, RecipeHolder<IArcaneRecipe> holder, IFocusGroup focuses) {
        builder.addRecipeArrow().setPosition(82, 22);
        var text = details(holder.value().getResearch());
        text.add(Component.translatable("thaumcraft.jei.vis", holder.value().getVis()));
        if (holder.value() instanceof ShapelessArcaneRecipe) text.add(Component.translatable("thaumcraft.jei.shapeless"));
        detailsBox(builder, text, 88);
    }
}
