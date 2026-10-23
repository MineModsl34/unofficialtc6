package thaumcraft.client.gui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.ThaumcraftApiHelper;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.crafting.CrucibleRecipe;
import thaumcraft.api.crafting.IArcaneRecipe;
import thaumcraft.api.crafting.InfusionRecipe;
import thaumcraft.api.crafting.RecipeMisc;
import thaumcraft.common.lib.crafting.InfusionEnchantmentRecipe;
import thaumcraft.common.lib.crafting.InfusionRecipes;
import thaumcraft.common.lib.crafting.InfusionRunicAugmentRecipe;
import thaumcraft.common.lib.enchantment.EnumInfusionEnchantment;
import thaumcraft.common.lib.research.ResearchRecipeDisplayCatalog;
import thaumcraft.common.lib.research.ResearchRecipeDisplayCatalog.Kind;

/** Live source API presentation. This never registers executable recipes or rolls a crafting result. */
final class ResearchApiRecipeDisplay {
    private ResearchApiRecipeDisplay() {}
    static boolean supported(Object value) {
        return value instanceof CrucibleRecipe || value instanceof InfusionRecipe || value instanceof Recipe<?>
            || value instanceof RecipeMisc || value instanceof ThaumcraftApi.BluePrint;
    }
    static String research(Object value) {
        return value instanceof thaumcraft.api.crafting.IThaumcraftRecipe recipe ? recipe.getResearch() : "";
    }
    static ResearchRecipeViewer.Recipe project(ResourceLocation id, Object value, HolderLookup.Provider registries) {
        if (value instanceof CrucibleRecipe recipe) return display(id, Kind.CRUCIBLE, recipe.getRecipeOutput(), 1, 1,
            List.of(choices(recipe.getCatalyst())), recipe.getAspects(), 0, 0, null);
        if (value instanceof InfusionRecipe recipe) {
            List<List<ItemStack>> ingredients = new ArrayList<>();
            List<ItemStack> centralChoices = choices(recipe.getRecipeInput()); ingredients.add(centralChoices);
            ItemStack central = centralChoices.isEmpty() ? ItemStack.EMPTY : centralChoices.getFirst();
            List<Ingredient> components = recipe instanceof InfusionRunicAugmentRecipe runic && !central.isEmpty()
                ? runic.getComponents(central) : recipe.getComponents();
            for (Ingredient ingredient : components) ingredients.add(choices(ingredient));
            List<ItemStack> representatives = ingredients.subList(1, ingredients.size()).stream()
                .map(slot -> slot.isEmpty() ? ItemStack.EMPTY : slot.getFirst()).toList();
            ItemStack result = ItemStack.EMPTY;
            if (recipe instanceof InfusionEnchantmentRecipe enchantment && !central.isEmpty()) {
                // The executable method randomly adds warp, even with owner=null. A book preview
                // only applies the deterministic enchantment change to a detached stack.
                result = central.copy(); int level = EnumInfusionEnchantment.getInfusionEnchantmentLevel(result, enchantment.enchantment);
                if (level < enchantment.enchantment.maxLevel)
                    EnumInfusionEnchantment.addInfusionEnchantment(result, enchantment.enchantment, level + 1);
            } else if (recipe instanceof InfusionRunicAugmentRecipe runic && !central.isEmpty()) {
                Object output = runic.getRecipeOutput(null, central, representatives);
                if (output instanceof ItemStack stack) result = stack.copy();
            } else if (recipe.getRecipeOutput() instanceof ItemStack stack) result = stack.copy();
            else if (recipe.getRecipeOutput() instanceof Object[] tag && !central.isEmpty()) {
                result = central.copy();
                if (!InfusionRecipes.applyOutputTag(result, (String)tag[0], (net.minecraft.nbt.Tag)tag[1], registries)) result = ItemStack.EMPTY;
            }
            return display(id, recipe instanceof InfusionEnchantmentRecipe ? Kind.ENCHANTMENT : Kind.INFUSION,
                result, 0, 0, ingredients, recipe.getAspects(null, central, representatives), 0,
                recipe.getInstability(null, central, representatives), null);
        }
        if (value instanceof RecipeMisc recipe) return display(id, Kind.MISC, recipe.getOutput(), 1, 1,
            List.of(List.of(recipe.getInput().copy())), null, 0, 0, null);
        if (value instanceof ThaumcraftApi.BluePrint blueprint) {
            List<ResearchRecipeDisplayCatalog.BlueprintCell> cells = new ArrayList<>();
            var parts = blueprint.getParts();
            for (int y = 0; y < parts.length; y++) for (int x = 0; x < parts[y].length; x++)
                for (int z = 0; z < parts[y][x].length; z++) {
                    var part = parts[y][x][z]; if (part == null) continue;
                    ItemStack stack = representative(part.getSource());
                    if (!stack.isEmpty()) cells.add(new ResearchRecipeDisplayCatalog.BlueprintCell(x, y, z,
                        BuiltInRegistries.ITEM.getKey(stack.getItem()) + ";" + stack.getCount() + ";0"));
                }
            ItemStack output = blueprint.getDisplayStack() == null ? ItemStack.EMPTY : blueprint.getDisplayStack().copy();
            var source = new ResearchRecipeDisplayCatalog.Display(id, Kind.BLUEPRINT, research(value), "", 0, 0,
                List.of(), Map.of(), 0, 0, List.of(), cells);
            return display(id, Kind.BLUEPRINT, output, 0, 0, java.util.Arrays.stream(blueprint.getIngredientList())
                .map(stack -> List.of(stack.copy())).toList(), null, 0, 0, source);
        }
        if (value instanceof Recipe<?> recipe) {
            List<List<ItemStack>> ingredients = recipe.getIngredients().stream().map(ResearchApiRecipeDisplay::choices).toList();
            int width = recipe instanceof ShapedRecipe shaped ? shaped.getWidth() : 3;
            int height = recipe instanceof ShapedRecipe shaped ? shaped.getHeight() : (ingredients.size() + 2) / 3;
            if (recipe instanceof thaumcraft.common.lib.crafting.RecipeArcaneWorkbench arcaneShape) {
                width = arcaneShape.pattern().width(); height = arcaneShape.pattern().height();
            }
            IArcaneRecipe arcane = value instanceof IArcaneRecipe typed ? typed : null;
            return display(id, arcane == null ? Kind.CRAFTING : Kind.ARCANE, recipe.getResultItem(registries), width, height,
                ingredients, arcane == null ? null : arcane.getCrystals(), arcane == null ? 0 : arcane.getVis(), 0, null);
        }
        throw new IllegalArgumentException("Unsupported source book recipe " + value);
    }
    private static List<ItemStack> choices(Ingredient ingredient) {
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack prototype : ingredient.getItems()) {
            if (prototype.getItem() instanceof thaumcraft.api.items.ItemGenericEssentiaContainer container
                    && !prototype.has(thaumcraft.api.items.TCDataComponents.ASPECTS)) {
                container.getTabVariants().stream().filter(ingredient::test).forEach(result::add);
            } else result.add(prototype.copy());
        }
        return List.copyOf(result);
    }
    private static ItemStack representative(Object value) {
        if (value instanceof ItemStack stack) return stack.copy();
        if (value instanceof BlockState state) return new ItemStack(state.getBlock());
        if (value instanceof ItemLike item) return new ItemStack(item);
        try { List<ItemStack> choices = choices(ThaumcraftApiHelper.getIngredient(value)); return choices.isEmpty() ? ItemStack.EMPTY : choices.getFirst(); }
        catch (IllegalArgumentException unsupported) { return ItemStack.EMPTY; }
    }
    private static ResearchRecipeViewer.Recipe display(ResourceLocation id, Kind kind, ItemStack output, int width, int height,
            List<List<ItemStack>> ingredients, AspectList costs, int vis, int instability, ResearchRecipeDisplayCatalog.Display source) {
        Map<String, Integer> aspects = new LinkedHashMap<>();
        if (costs != null) for (var aspect : costs.getAspects()) aspects.put(aspect.getTag(), costs.getAmount(aspect));
        return new ResearchRecipeViewer.Recipe(id, kind, output.copy(), "", width, height, List.copyOf(ingredients), List.of(),
            java.util.Collections.unmodifiableMap(aspects), vis, instability, source);
    }
}
