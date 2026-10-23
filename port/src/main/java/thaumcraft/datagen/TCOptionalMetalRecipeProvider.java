package thaumcraft.datagen;

import java.util.Map;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.neoforged.neoforge.common.conditions.NotCondition;
import net.neoforged.neoforge.common.conditions.TagEmptyCondition;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.lib.crafting.TaggedMetalRecipes;

/** Exact source 1 ingot ↔ 9 TC nuggets and 1 TC cluster → 2 ingots, with 1 XP smelting. */
public final class TCOptionalMetalRecipeProvider {
    private TCOptionalMetalRecipeProvider() {}
    public static void generate(RecipeOutput output) {
        metal(output, "copper", ItemsTC.clusterCopper, ItemsTC.nuggetCopper);
        metal(output, "tin", ItemsTC.clusterTin, ItemsTC.nuggetTin);
        metal(output, "silver", ItemsTC.clusterSilver, ItemsTC.nuggetSilver);
        metal(output, "lead", ItemsTC.clusterLead, ItemsTC.nuggetLead);
    }
    private static void metal(RecipeOutput output, String metal, Item cluster, Item nugget) {
        ResourceLocation tag = ResourceLocation.fromNamespaceAndPath("c", "ingots/" + metal);
        var condition = new NotCondition(new TagEmptyCondition(tag));
        output.accept(id(metal + "_from_cluster"), new TaggedMetalRecipes.Cooking("", Ingredient.of(cluster), tag, 2), null, condition);
        output.accept(id(metal + "nuggetstoingot"), new TaggedMetalRecipes.Shaped("",
            ShapedRecipePattern.of(Map.of('#', Ingredient.of(nugget)), "###", "###", "###"), tag, 1), null, condition);
        output.accept(id(metal + "tonuggets"), new ShapedRecipe("", CraftingBookCategory.MISC,
            ShapedRecipePattern.of(Map.of('#', Ingredient.of(TagKey.create(Registries.ITEM, tag))), "#"), new ItemStack(nugget, 9)), null, condition);
    }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("thaumcraft", path); }
}
