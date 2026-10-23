package thaumcraft.common.lib.crafting;

import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;

/** Source RecipesVoidRobeArmorDyes; real void robe armor only. */
public final class RecipesVoidRobeArmorDyes extends SourceRobeDyeRecipe {
    public RecipesVoidRobeArmorDyes(CraftingBookCategory category) { super(category, Family.VOID); }
    @Override public RecipeSerializer<?> getSerializer() { return OrdinaryCustomRecipes.VOID_ROBE_DYE; }
}
