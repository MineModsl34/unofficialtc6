package thaumcraft.common.lib.crafting;

import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;

/** Source RecipesRobeArmorDyes; real cloth armor only. */
public final class RecipesRobeArmorDyes extends SourceRobeDyeRecipe {
    public RecipesRobeArmorDyes(CraftingBookCategory category) { super(category, Family.CLOTH); }
    @Override public RecipeSerializer<?> getSerializer() { return OrdinaryCustomRecipes.ROBE_DYE; }
}
