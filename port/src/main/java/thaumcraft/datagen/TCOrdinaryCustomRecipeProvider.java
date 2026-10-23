package thaumcraft.datagen;

import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.common.conditions.ItemExistsCondition;
import net.neoforged.neoforge.common.conditions.OrCondition;
import thaumcraft.common.lib.crafting.RecipeTripleMeatTreat;
import thaumcraft.common.lib.crafting.RecipesRobeArmorDyes;
import thaumcraft.common.lib.crafting.RecipesVoidRobeArmorDyes;

/** Conditions skip genuinely absent later food/gear; executable serializers are NOT skipped. */
public final class TCOrdinaryCustomRecipeProvider extends RecipeProvider {
    public TCOrdinaryCustomRecipeProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) { super(output, lookup); }
    @Override protected void buildRecipes(RecipeOutput output) { generate(output); }
    public static void generate(RecipeOutput output) {
        output.accept(id("robedye"), new RecipesRobeArmorDyes(CraftingBookCategory.MISC), null,
            anyItem("cloth_chest", "cloth_legs", "cloth_boots"));
        output.accept(id("voidarmordye"), new RecipesVoidRobeArmorDyes(CraftingBookCategory.MISC), null,
            anyItem("void_robe_helm", "void_robe_chest", "void_robe_legs"));
        output.accept(id("triplemeattreat"), new RecipeTripleMeatTreat(CraftingBookCategory.MISC), null,
            new ItemExistsCondition(id("triple_meat_treat")));
    }
    private static ICondition anyItem(String... names) {
        return new OrCondition(java.util.Arrays.stream(names).<ICondition>map(name -> new ItemExistsCondition(id(name))).toList());
    }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("thaumcraft", path); }
}
