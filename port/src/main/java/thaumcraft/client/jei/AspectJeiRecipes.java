package thaumcraft.client.jei;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IModIngredientRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.IEssentiaContainerItem;
import thaumcraft.client.lib.ClientAspectSnapshot;

/** Registration entry points; recipe refreshes use only the current connection's snapshot. */
public final class AspectJeiRecipes {
    public static final RecipeType<AspectCombinationRecipe> COMBINATIONS =
        RecipeType.create("thaumcraft", "aspect_combinations", AspectCombinationRecipe.class);
    public static final RecipeType<ItemAspectRecipe> CONTENTS =
        RecipeType.create("thaumcraft", "item_aspects", ItemAspectRecipe.class);

    private AspectJeiRecipes() {}

    public static List<JeiAspectIngredient> ingredients() {
        return Aspect.aspects.values().stream().map(aspect -> new JeiAspectIngredient(aspect.getTag())).toList();
    }

    public static void registerIngredients(IModIngredientRegistration registration) {
        registration.register(AspectIngredientHelper.TYPE, ingredients(), new AspectIngredientHelper(),
            new AspectIngredientRenderer(), AspectIngredientHelper.CODEC);
    }

    public static void registerItemSubtypes(ISubtypeRegistration registration) {
        var interpreter = new AspectContainerSubtypeInterpreter();
        for (var item : BuiltInRegistries.ITEM)
            if (BuiltInRegistries.ITEM.getKey(item).getNamespace().equals("thaumcraft") && item instanceof IEssentiaContainerItem)
                registration.registerSubtypeInterpreter(item, interpreter);
    }

    public static List<AspectCombinationRecipe> combinations() {
        return Aspect.aspects.values().stream()
            .map(AspectCombinationRecipe::from).filter(Objects::nonNull).toList();
    }

    public static List<ItemAspectRecipe> contents() {
        var snapshot = ClientAspectSnapshot.view();
        return snapshot == null ? List.of() : snapshot.entries().stream().map(ItemAspectRecipe::new).toList();
    }

    /** Adds real JEI component variants (crystals, containers, enchantments) without guessed data. */
    public static List<ItemAspectRecipe> contents(Collection<ItemStack> candidates) {
        var snapshot = ClientAspectSnapshot.view();
        if (snapshot == null) return List.of();
        List<ItemAspectRecipe> recipes = new ArrayList<>();
        Map<Integer, List<ItemStack>> seen = new HashMap<>();
        for (var entry : snapshot.entries()) addUnique(recipes, seen, entry);
        for (var stack : candidates) {
            if (stack.isEmpty()) continue;
            var entry = snapshot.resolve(stack);
            if (entry != null) addUnique(recipes, seen, entry);
        }
        return List.copyOf(recipes);
    }

    private static void addUnique(List<ItemAspectRecipe> recipes, Map<Integer, List<ItemStack>> seen,
                                  ClientAspectSnapshot.Entry entry) {
        var stack = entry.stack();
        var bucket = seen.computeIfAbsent(ItemStack.hashItemAndComponents(stack), ignored -> new ArrayList<>());
        if (bucket.stream().anyMatch(previous -> ItemStack.isSameItemSameComponents(previous, stack))) return;
        bucket.add(stack);
        recipes.add(new ItemAspectRecipe(entry));
    }
}
