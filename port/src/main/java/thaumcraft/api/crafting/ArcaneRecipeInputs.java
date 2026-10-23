package thaumcraft.api.crafting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.common.lib.crafting.ArcaneWorkbenchCosts;

/** Source Object recipe constructors adapted to flattened items/native item tags.
 * Explicit IngredientNBTTC retains its source relaxed-NBT rules. Ordinary ore recipes
 * ignore stack NBT; a durable metadata value becomes exact native DAMAGE instead. */
public final class ArcaneRecipeInputs {
    private ArcaneRecipeInputs() {}
    public record Shape(ShapedRecipePattern pattern, boolean mirrored) {}
    public static List<Integer> crystals(AspectList aspects) {
        List<Integer> result = new ArrayList<>(6);
        for (var primal : ArcaneWorkbenchCosts.PRIMALS) result.add(aspects == null ? 0 : aspects.getAmount(primal));
        if (aspects != null) for (var aspect : aspects.getAspects())
            if (java.util.Arrays.stream(ArcaneWorkbenchCosts.PRIMALS).noneMatch(primal -> primal == aspect))
                throw new IllegalArgumentException("Arcane crystal slots accept primal costs only: " + aspect.getTag());
        return List.copyOf(result);
    }
    public static Ingredient ingredient(Object value) {
        if (value instanceof IngredientNBTTC custom) return custom.toVanilla();
        if (value instanceof ItemStack stack && !stack.isEmpty()) {
            if (thaumcraft.common.lib.research.LegacyScanIdentity.rawDamage(stack) == 32767)
                return thaumcraft.api.ThaumcraftApiHelper.wildcardIngredient(stack);
            if (stack.isDamageableItem() && stack.getDamageValue() != 32767) {
                ItemStack damageOnly = new ItemStack(stack.getItem()); damageOnly.setDamageValue(stack.getDamageValue());
                return new IngredientNBTTC(damageOnly).toVanilla();
            }
            return Ingredient.of(stack.getItem());
        }
        if (value instanceof String name) {
            String tag = switch (name) {
                case "plateIron" -> "plates/iron"; case "plateBrass" -> "plates/brass";
                case "plateThaumium" -> "plates/thaumium"; case "plateVoid" -> "plates/void";
                case "leather" -> "leathers"; case "paneGlass" -> "glass_panes";
                case "blockWool" -> null; default -> "";
            };
            if (tag == null) return Ingredient.of(net.minecraft.tags.ItemTags.WOOL);
            if (!tag.isEmpty()) return Ingredient.of(TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", tag)));
        }
        return CrucibleRecipe.ingredient(value);
    }
    public static List<Ingredient> shapeless(Object[] recipe) {
        if (recipe == null || recipe.length < 1 || recipe.length > 9) throw new IllegalArgumentException("Arcane recipe requires 1..9 ingredients");
        List<Ingredient> ingredients = new ArrayList<>(recipe.length);
        for (Object value : recipe) ingredients.add(ingredient(value));
        return ingredients;
    }
    public static Shape shaped(Object[] recipe) {
        if (recipe == null || recipe.length < 1) throw new IllegalArgumentException("Invalid shaped arcane recipe");
        boolean mirrored = true; int index = 0;
        if (recipe[0] instanceof Boolean value) {
            mirrored = value;
            if (recipe.length > 1 && recipe[1] instanceof Object[] nested) recipe = nested;
            else index++;
        }
        List<String> rows = new ArrayList<>();
        if (index < recipe.length && recipe[index] instanceof String[] array) {
            rows.addAll(List.of(array)); index++;
        } else while (index < recipe.length && recipe[index] instanceof String row) { rows.add(row); index++; }
        if (rows.isEmpty() || rows.size() > 3 || rows.get(0).length() < 1 || rows.get(0).length() > 3
            || (recipe.length - index) % 2 != 0) throw new IllegalArgumentException("Invalid shaped arcane recipe");
        Map<Character, Ingredient> key = new HashMap<>();
        while (index < recipe.length) {
            if (!(recipe[index++] instanceof Character symbol) || symbol == ' ') throw new IllegalArgumentException("Invalid arcane recipe symbol");
            if (key.put(symbol, ingredient(recipe[index++])) != null) throw new IllegalArgumentException("Duplicate arcane recipe symbol");
        }
        return new Shape(ShapedRecipePattern.of(key, rows), mirrored);
    }
}
