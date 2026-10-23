package thaumcraft.datagen;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IEssentiaContainerItem;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.crafting.IngredientNBTTC;
import thaumcraft.api.crafting.LabelVariantIngredient;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.items.consumables.ItemPhial;
import thaumcraft.common.lib.TCTags;

/** The source ordinary alchemy dependencies are ordinary crafting, never free arcane/infusion substitutes. */
public final class TCAlchemyNormalRecipeProvider extends RecipeProvider {
    public TCAlchemyNormalRecipeProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) { super(output, lookup); }
    @Override protected void buildRecipes(RecipeOutput output) { generate(output); }

    public static void generate(RecipeOutput output) {
        for (DyeColor dye : DyeColor.values()) {
            // Keep source recipe ids (old translation keys lightBlue/silver), not newly invented aliases.
            String sourceColor = dye == DyeColor.LIGHT_BLUE ? "lightblue" : dye == DyeColor.LIGHT_GRAY ? "silver" : dye.getName();
            shapeless(output, "nitordye" + sourceColor, "thaumcraft:nitorgroup", new ItemStack(BlocksTC.nitor.get(dye)),
                Ingredient.of(dye.getTag()), common("nitor"));
        }
        save(output, "phial", new ShapedRecipe("", CraftingBookCategory.MISC,
            ShapedRecipePattern.of(Map.of('C', Ingredient.of(Items.CLAY_BALL), 'G', common("glass_blocks")), " C ", "G G", " G "),
            new ItemStack(ItemsTC.phial, 8)));
        // Source ConfigRecipes:2907-2912 uses actual phial metadata0 (now phial_empty),
        // independently of the glass-bottle path and with no filled-aspect/NBT constraint.
        shapeless(output, "scribingtoolscraft1", "thaumcraft:inkwell", new ItemStack(ItemsTC.scribingTools),
            Ingredient.of(ItemsTC.phial), Ingredient.of(Items.FEATHER), Ingredient.of(TCTags.Items.SCRIBING_INK));
        shapeless(output, "jarlabel", "thaumcraft:jarlabels", new ItemStack(ItemsTC.label, 4),
            Ingredient.of(TCTags.Items.SCRIBING_INK), common("slime_balls"), Ingredient.of(Items.PAPER),
            Ingredient.of(Items.PAPER), Ingredient.of(Items.PAPER), Ingredient.of(Items.PAPER));
        for (Aspect aspect : Aspect.aspects.values()) {
            ItemStack label = new ItemStack(ItemsTC.label);
            ((IEssentiaContainerItem) label.getItem()).setAspects(label, new AspectList().add(aspect, 1));
            shapeless(output, "label_" + aspect.getTag(), "thaumcraft:jarlabels", label,
                new LabelVariantIngredient(false).toVanilla(), new IngredientNBTTC(ItemPhial.makeFilledPhial(aspect)).toVanilla());
        }
        shapeless(output, "jarlabelnull", "thaumcraft:jarlabels", new ItemStack(ItemsTC.label), new LabelVariantIngredient(true).toVanilla());
    }

    private static Ingredient common(String path) {
        return Ingredient.of(TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", path)));
    }
    private static void shapeless(RecipeOutput output, String id, String group, ItemStack result, Ingredient... inputs) {
        NonNullList<Ingredient> components = NonNullList.create(); components.addAll(List.of(inputs));
        save(output, id, new ShapelessRecipe(group, CraftingBookCategory.MISC, result, components));
    }
    private static void save(RecipeOutput output, String id, Recipe<?> recipe) {
        output.accept(ResourceLocation.fromNamespaceAndPath("thaumcraft", id), recipe, null);
    }
}
