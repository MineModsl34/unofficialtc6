package thaumcraft.common.lib.crafting;

import java.util.LinkedHashMap;
import java.util.Map;

/** Identity ownership, not value equality: an add-on's explicit replacement is never ours. */
final class OwnedRecipeCatalog<K, V> {
    private final Map<K, V> owned = new LinkedHashMap<>();

    void rebuild(Map<K, V> catalog, Map<K, V> nativeRecipes) {
        owned.forEach((id, previous) -> {
            if (!nativeRecipes.containsKey(id) && catalog.get(id) == previous) catalog.remove(id);
        });
        Map<K, V> accepted = new LinkedHashMap<>();
        nativeRecipes.forEach((id, recipe) -> {
            V existing = catalog.get(id);
            if (existing == null || existing == owned.get(id)) {
                catalog.put(id, recipe);
                accepted.put(id, recipe);
            }
        });
        owned.clear();
        owned.putAll(accepted);
    }

    void clear(Map<K, V> catalog) {
        owned.forEach((id, previous) -> {
            if (catalog.get(id) == previous) catalog.remove(id);
        });
        owned.clear();
    }
}
