package thaumcraft.common.lib.crafting;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OwnedRecipeCatalogTest {
    @Test void refreshesAndRemovesOnlyItsOwnReloadEntries() {
        var mirror = new OwnedRecipeCatalog<String, Object>();
        var catalog = new LinkedHashMap<String, Object>();
        Object oldRecipe = new Object(), freshRecipe = new Object();
        mirror.rebuild(catalog, Map.of("recipe", oldRecipe));
        assertSame(oldRecipe, catalog.get("recipe"));
        mirror.rebuild(catalog, Map.of("recipe", freshRecipe));
        assertSame(freshRecipe, catalog.get("recipe"));
        mirror.rebuild(catalog, Map.of());
        assertTrue(catalog.isEmpty());
    }

    @Test void existingAndLaterAddonOverridesSurviveReloadAndShutdown() {
        var mirror = new OwnedRecipeCatalog<String, Object>();
        var catalog = new LinkedHashMap<String, Object>();
        Object earlyAddon = new Object(), laterAddon = new Object();
        catalog.put("early", earlyAddon);
        mirror.rebuild(catalog, Map.of("early", new Object(), "later", new Object()));
        catalog.put("later", laterAddon);
        mirror.rebuild(catalog, Map.of("early", new Object(), "later", new Object()));
        mirror.clear(catalog);
        assertSame(earlyAddon, catalog.get("early"));
        assertSame(laterAddon, catalog.get("later"));
    }

    @Test void equalButNotIdenticalAddonReplacementIsNotRemoved() {
        var mirror = new OwnedRecipeCatalog<String, String>();
        var catalog = new LinkedHashMap<String, String>();
        String original = new String("value"), addon = new String("value");
        mirror.rebuild(catalog, Map.of("recipe", original));
        catalog.put("recipe", addon);
        mirror.clear(catalog);
        assertSame(addon, catalog.get("recipe"));
    }
}
