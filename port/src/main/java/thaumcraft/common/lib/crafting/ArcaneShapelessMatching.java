package thaumcraft.common.lib.crafting;

import java.util.List;
import java.util.function.Predicate;
import net.neoforged.neoforge.common.util.RecipeMatcher;

/** The source Forge shapeless bipartite matcher, bounded to the source 3x3 matrix.
 * No recipe/item registry initialization is needed to test its overlap semantics. */
public final class ArcaneShapelessMatching {
    private ArcaneShapelessMatching() {}
    public static <T> boolean matches(List<T> inputs, List<? extends Predicate<T>> ingredients) {
        return !inputs.isEmpty() && inputs.size() <= 9 && inputs.size() == ingredients.size()
            && RecipeMatcher.findMatches(inputs, ingredients) != null;
    }
}
