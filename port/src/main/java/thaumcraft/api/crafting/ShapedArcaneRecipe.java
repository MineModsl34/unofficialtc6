package thaumcraft.api.crafting;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.common.lib.crafting.RecipeArcaneWorkbench;

/** Source-named addon API, using the real native arcane recipe type/serializer. */
public class ShapedArcaneRecipe extends RecipeArcaneWorkbench {
    protected ShapedArcaneRecipe(String group, ShapedRecipePattern pattern, ItemStack result, String research, int vis, java.util.List<Integer> crystals) {
        super(group, pattern, result, research, vis, crystals);
    }
    public ShapedArcaneRecipe(ResourceLocation group, String research, int vis, AspectList crystals, ItemLike result, Object... recipe) {
        this(group, research, vis, crystals, new ItemStack(result), recipe);
    }
    public ShapedArcaneRecipe(ResourceLocation group, String research, int vis, AspectList crystals, ItemStack result, Object... recipe) {
        this(group, research, vis, crystals, result, ArcaneRecipeInputs.shaped(recipe));
    }
    private ShapedArcaneRecipe(ResourceLocation group, String research, int vis, AspectList crystals, ItemStack result, ArcaneRecipeInputs.Shape shape) {
        super(group == null ? "" : group.toString(), shape.pattern(), result, research, vis, ArcaneRecipeInputs.crystals(crystals));
        setMirrored(shape.mirrored());
    }
    /** Modern replacement for old Forge ShapedPrimer; native pattern carries ingredients/dimensions. */
    public ShapedArcaneRecipe(ResourceLocation group, String research, int vis, AspectList crystals, ItemStack result, ShapedRecipePattern pattern) {
        super(group == null ? "" : group.toString(), pattern, result, research, vis, ArcaneRecipeInputs.crystals(crystals));
    }
    @Override public ShapedArcaneRecipe setMirrored(boolean value) { super.setMirrored(value); return this; }
}
