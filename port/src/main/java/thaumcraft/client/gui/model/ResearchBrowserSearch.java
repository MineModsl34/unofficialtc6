package thaumcraft.client.gui.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.common.lib.research.catalog.ResearchClientView.Snapshot;

/** Source browser search: accessible category KEYS, known entry localized names and the
 * source min(lastStage, storedStage+1) recipe outputs, deduplicated by recipe handle. No grants.
 */
public final class ResearchBrowserSearch {
    public record Result(String label, String key, boolean category, ResourceLocation recipe) {}
    private ResearchBrowserSearch() {}
    public static List<Result> project(Snapshot snapshot, List<String> accessibleCategories, String query,
                                      Function<String, String> localize,
                                      Function<ResourceLocation, List<String>> recipeOutputs) {
        if (snapshot == null) return List.of();
        String needle = (query == null ? "" : query).toLowerCase(Locale.ROOT);
        if (needle.length() > 15) needle = needle.substring(0, 15);
        List<Result> results = new ArrayList<>(); Set<ResourceLocation> checked = new HashSet<>();
        for (String category : accessibleCategories) if (category.toLowerCase(Locale.ROOT).contains(needle))
            results.add(new Result(localize.apply("tc.research_category." + category), category, true, null));
        for (var entry : snapshot.spec().entries()) {
            if (!snapshot.knows(entry.key())) continue;
            String name = localize.apply(entry.name());
            if (name.toLowerCase(Locale.ROOT).contains(needle)) results.add(new Result(name, entry.key(), false, null));
            if (entry.stages() == null || entry.stages().values().isEmpty()) continue;
            int stage = (int) Math.min(entry.stages().values().size() - 1L, (long) snapshot.storedStage(entry.key()) + 1);
            if (stage < 0) continue;
            var recipes = entry.stages().values().get(stage).recipes();
            if (recipes == null) continue;
            for (var raw : recipes.values()) {
                ResourceLocation id = raw.modernSyntax() ? ResourceLocation.tryParse(raw.normalized()) : null;
                if (id == null || !checked.add(id)) continue;
                for (String output : recipeOutputs.apply(id)) if (output.toLowerCase(Locale.ROOT).contains(needle))
                    results.add(new Result(output, entry.key(), false, id));
            }
        }
        results.sort(Comparator.comparing(Result::label));
        return List.copyOf(results);
    }
}
