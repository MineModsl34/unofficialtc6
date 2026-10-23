package thaumcraft.client.jei;

import java.util.List;
import java.util.Map;

/** Exact content/filter identity, independent of source map iteration and stack count. */
public record AspectContainerSubtype(List<JeiAspectIngredient> aspects, String filter) {
    public AspectContainerSubtype {
        aspects = List.copyOf(aspects);
        filter = filter == null ? "" : filter;
    }
    public static AspectContainerSubtype of(Map<String, Integer> amounts, String filter) {
        return new AspectContainerSubtype(JeiAspectIngredient.fromAmounts(amounts), filter);
    }
    public boolean isEmpty() { return aspects.isEmpty() && filter.isEmpty(); }
}
