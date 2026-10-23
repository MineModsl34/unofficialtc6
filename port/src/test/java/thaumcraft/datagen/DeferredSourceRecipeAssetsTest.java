package thaumcraft.datagen;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Pure JSON/provenance audit. Does not bootstrap registries, launch the game or decode ItemStacks. */
class DeferredSourceRecipeAssetsTest {
    @Test void everySourceAstPayloadHasNativeSnapshotAndFailClosedRealDependencyGuards() throws IOException {
        JsonObject manifest = asset(TCDeferredSourceRecipeProvider.RESOURCE);
        TCDeferredSourceRecipeProvider.validate(manifest);
        assertEquals("5466b72c3d8e1f2afdab5bd720339ab703aa80d50a09244c20c0580ccaa553c8", manifest.get("sourceSha256").getAsString());
        assertTrue(manifest.getAsJsonArray("unresolved").isEmpty());
        assertEquals(125, manifest.getAsJsonArray("recipes").size());
        Map<String, Integer> kinds = new HashMap<>();
        for (var entry : manifest.getAsJsonArray("recipes")) {
            JsonObject row = entry.getAsJsonObject();
            String id = row.get("id").getAsString();
            assertFalse(id.contains("fake"));
            assertTrue(row.get("sourceExpression").getAsString().contains("Recipe("));
            JsonObject payload = row.getAsJsonObject("payload");
            assertEquals(payload, asset("data/thaumcraft/recipe/" + id.substring(11) + ".json"), id);
            kinds.merge(payload.get("type").getAsString(), 1, Integer::sum);
        }
        assertEquals(Map.of("thaumcraft:arcane_shaped", 51, "thaumcraft:arcane_shapeless", 6,
            "thaumcraft:infusion", 39, "thaumcraft:crucible", 19,
            "minecraft:crafting_shaped", 9, "minecraft:crafting_shapeless", 1), kinds);
    }

    @Test void missingConditionOrChangedSourceHashCannotBeSilentlyAccepted() throws IOException {
        JsonObject manifest = asset(TCDeferredSourceRecipeProvider.RESOURCE);
        JsonObject first = manifest.getAsJsonArray("recipes").get(0).getAsJsonObject().getAsJsonObject("payload");
        first.getAsJsonArray("neoforge:conditions").remove(0);
        assertThrows(IllegalArgumentException.class, () -> TCDeferredSourceRecipeProvider.validate(manifest));
        JsonObject corrupt = asset(TCDeferredSourceRecipeProvider.RESOURCE);
        corrupt.addProperty("sourceSha256", "x".repeat(64));
        assertThrows(IllegalArgumentException.class, () -> TCDeferredSourceRecipeProvider.validate(corrupt));
    }

    @Test void sourceVariantAndDynamicTagOutputsAreNotReplacedWithUnrelatedExistingItems() throws IOException {
        assertEquals("thaumcraft:wand_workbench", recipe("wand_workbench").getAsJsonObject("result").get("id").getAsString());
        assertEquals("thaumcraft:amulet_vis_crafted", recipe("visamulet").getAsJsonObject("result").get("id").getAsString());
        assertEquals("thaumcraft:turret_advanced", recipe("advancedcrossbow").getAsJsonObject("result").get("id").getAsString());
        assertEquals("thaumcraft:turret_bore", recipe("arcanebore").getAsJsonObject("result").get("id").getAsString());
        assertEquals("thaumcraft:liquid_death_bucket", recipe("liquiddeath").getAsJsonObject("result").get("id").getAsString());
        assertEquals("thaumcraft:seal_pickup", recipe("sealcollect").getAsJsonObject("result").get("id").getAsString());
        assertEquals("goggles", recipe("helmgoggles").getAsJsonObject("result").get("tag").getAsString());
        assertEquals("1b", recipe("helmgoggles").getAsJsonObject("result").get("value").getAsString());
        assertEquals("0", recipe("maskgrinningdevil").getAsJsonObject("result").get("value").getAsString());
        assertEquals("thaumcraft:arcane_ear", recipe("arcaneeartoggle").getAsJsonArray("ingredients").get(0).getAsJsonObject().get("item").getAsString());
        assertEquals("minecraft:lever", recipe("arcaneeartoggle").getAsJsonArray("ingredients").get(1).getAsJsonObject().get("item").getAsString());
        assertEquals("minecraft:strong_healing", recipe("verdantheartlife").getAsJsonArray("components").get(2).getAsJsonObject()
            .getAsJsonObject("stack").getAsJsonObject("components").getAsJsonObject("minecraft:potion_contents").get("potion").getAsString());
    }

    @Test void everyDeferredArcaneCrystalArrayUsesNativeAirFireWaterEarthOrderEntropyOrder() throws IOException {
        String[] nativeOrder = {"aer", "ignis", "aqua", "terra", "ordo", "perditio"};
        for (var entry : asset(TCDeferredSourceRecipeProvider.RESOURCE).getAsJsonArray("recipes")) {
            JsonObject row = entry.getAsJsonObject(), payload = row.getAsJsonObject("payload");
            if (!payload.get("type").getAsString().startsWith("thaumcraft:arcane_")) continue;
            JsonObject sourceCosts = row.getAsJsonObject("sourceCrystals");
            assertEquals(6, payload.getAsJsonArray("crystals").size());
            for (int index = 0; index < nativeOrder.length; index++) {
                String tag = nativeOrder[index];
                int expected = sourceCosts.has(tag) ? sourceCosts.get(tag).getAsInt() : 0;
                assertEquals(expected, payload.getAsJsonArray("crystals").get(index).getAsInt(), row.get("id").getAsString() + " " + tag);
            }
        }
        assertEquals(1, recipe("sealblank").getAsJsonArray("crystals").get(0).getAsInt());
        assertEquals(1, recipe("modaggression").getAsJsonArray("crystals").get(1).getAsInt());
        assertEquals(1, recipe("wand_workbench").getAsJsonArray("crystals").get(3).getAsInt());
    }

    private static JsonObject recipe(String id) throws IOException { return asset("data/thaumcraft/recipe/" + id + ".json"); }
    private static JsonObject asset(String name) throws IOException {
        try (var input = DeferredSourceRecipeAssetsTest.class.getClassLoader().getResourceAsStream(name)) {
            assertNotNull(input, name);
            return JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
