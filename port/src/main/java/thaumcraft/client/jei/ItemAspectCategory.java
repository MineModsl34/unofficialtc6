package thaumcraft.client.jei;

import java.util.List;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import thaumcraft.api.blocks.BlocksTC;

/** Item usages show its composition; aspect recipes show every item source via OUTPUT roles. */
public final class ItemAspectCategory implements IRecipeCategory<ItemAspectRecipe> {
    private final IGuiHelper gui;
    private final IDrawable icon;

    public ItemAspectCategory(IGuiHelper gui) {
        this.gui = gui;
        icon = gui.createDrawableIngredient(VanillaTypes.ITEM_STACK, new ItemStack(BlocksTC.smelterBasic));
    }
    @Override public RecipeType<ItemAspectRecipe> getRecipeType() { return AspectJeiRecipes.CONTENTS; }
    @Override public Component getTitle() { return Component.translatable("thaumcraft.jei.item_aspects"); }
    @Override public IDrawable getIcon() { return icon; }
    @Override public int getWidth() { return 160; }
    @Override public int getHeight() { return 100; }
    @Override public void setRecipe(IRecipeLayoutBuilder builder, ItemAspectRecipe recipe, IFocusGroup focuses) {
        builder.addInputSlot(72, 4).setBackground(gui.getSlotDrawable(), -1, -1).addItemStack(recipe.stack());
        int index = 0;
        // Separate slots preserve every quantity and give each aspect a proper JEI lookup role.
        for (var aspect : recipe.aspects()) builder.addSlot(RecipeIngredientRole.OUTPUT)
            .setSlotName("aspect_" + index++).addIngredient(AspectIngredientHelper.TYPE, aspect);
    }
    @Override public void createRecipeExtras(IRecipeExtrasBuilder builder, ItemAspectRecipe recipe, IFocusGroup focuses) {
        var slots = builder.getRecipeSlots().getSlots().stream()
            .filter(slot -> slot.getSlotName().orElse("").startsWith("aspect_")).toList();
        if (!slots.isEmpty()) builder.addScrollGridWidget(slots, 7, 2).setPosition(4, 28);
        builder.addScrollBoxWidget(156, 25, 2, 73)
            .setContents(List.of(Component.translatable("thaumcraft.jei.aspects_per_item")));
    }
}
