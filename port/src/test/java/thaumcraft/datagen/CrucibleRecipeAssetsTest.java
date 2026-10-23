package thaumcraft.datagen;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import thaumcraft.api.aspects.Aspect;

/** Cheap source-data structure audit only, NOT runtime crafting or visual acceptance. */
class CrucibleRecipeAssetsTest {
    @Test void allThirtySevenSourceCrystalsRetainTheirOwnComponentAndExactTwoAspectCost() throws IOException {
        assertEquals(37, Aspect.aspects.size());
        for (Aspect aspect : Aspect.aspects.values()) {
            JsonObject recipe = recipe("vis_crystal_" + aspect.getTag());
            assertEquals("BASEALCHEMY", recipe.get("research").getAsString());
            assertEquals("thaumcraft:viscrystalgroup", recipe.get("group").getAsString());
            assertEquals("c:nuggets/quartz", recipe.getAsJsonObject("catalyst").get("tag").getAsString());
            assertAspects(recipe.getAsJsonObject("aspects"), Map.of(aspect.getTag(), 2));
            JsonObject result = recipe.getAsJsonObject("result");
            assertEquals("thaumcraft:crystal_essence", result.get("id").getAsString());
            assertEquals(1, result.get("count").getAsInt());
            assertAspects(result.getAsJsonObject("components").getAsJsonObject("thaumcraft:aspects"), Map.of(aspect.getTag(), 1));
        }
    }

    @Test void nitorRetainsSourceYellowOutputStageThreeGlowstoneAndThreeTenCosts() throws IOException {
        JsonObject recipe = recipe("nitor");
        assertEquals("UNLOCKALCHEMY@3", recipe.get("research").getAsString());
        assertEquals("thaumcraft:nitor_yellow", recipe.getAsJsonObject("result").get("id").getAsString());
        assertEquals("c:dusts/glowstone", recipe.getAsJsonObject("catalyst").get("tag").getAsString());
        assertAspects(recipe.getAsJsonObject("aspects"), Map.of("potentia", 10, "ignis", 10, "lux", 10));
    }

    @Test void metallurgicalStagesAndSameCatalystCompetingHedgeCostsAreNotFlattened() throws IOException {
        assertEquals("METALLURGY@1", recipe("brassingot").get("research").getAsString());
        assertAspects(recipe("brassingot").getAsJsonObject("aspects"), Map.of("instrumentum", 5));
        assertEquals("METALLURGY@2", recipe("thaumiumingot").get("research").getAsString());
        assertAspects(recipe("thaumiumingot").getAsJsonObject("aspects"), Map.of("praecantatio", 5, "terra", 5));
        assertEquals("BASEELDRITCH", recipe("voidingot").get("research").getAsString());
        assertEquals("thaumcraft:void_seed", recipe("voidingot").getAsJsonObject("catalyst").get("item").getAsString());
        assertAspects(recipe("voidingot").getAsJsonObject("aspects"), Map.of("metallum", 10, "vitium", 5));
        for (String id : new String[]{"hedge_tallow", "hedge_leather"}) {
            assertEquals("HEDGEALCHEMY@1", recipe(id).get("research").getAsString());
            assertEquals("minecraft:rotten_flesh", recipe(id).getAsJsonObject("catalyst").get("item").getAsString());
        }
        assertAspects(recipe("hedge_tallow").getAsJsonObject("aspects"), Map.of("ignis", 1));
        assertAspects(recipe("hedge_leather").getAsJsonObject("aspects"), Map.of("aer", 3, "bestia", 3));
    }

    @Test void currentPurificationRecipesHaveRealClusterOutputsAndDoNotInventFutureDevices() throws IOException {
        for (String metal : new String[]{"iron", "gold", "cinnabar"}) {
            JsonObject recipe = recipe("metal_purification_" + metal);
            assertEquals("METALPURIFICATION", recipe.get("research").getAsString());
            assertEquals("thaumcraft:cluster_" + metal, recipe.getAsJsonObject("result").get("id").getAsString());
            assertEquals("c:ores/" + metal, recipe.getAsJsonObject("catalyst").get("tag").getAsString());
            assertAspects(recipe.getAsJsonObject("aspects"), Map.of("metallum", 5, "ordo", 5));
        }
        for (String future : new String[]{"focus_1", "everfullurn", "liquiddeath", "sealcollect"}) {
            JsonObject deferred = recipe(future);
            assertFalse(deferred.getAsJsonArray("neoforge:conditions").isEmpty(), "No unguarded fake executable recipe for " + future);
            String outputId = deferred.getAsJsonObject("result").get("id").getAsString();
            assertTrue(java.util.stream.StreamSupport.stream(deferred.getAsJsonArray("neoforge:conditions").spliterator(), false)
                .map(entry -> entry.getAsJsonObject()).anyMatch(condition -> condition.get("type").getAsString().equals("neoforge:item_exists")
                    && condition.get("item").getAsString().equals(outputId)), "Unknown future output must be tested BEFORE decode");
        }
    }

    @Test void sourceCoalWildcardIsFlattenedToBothCoalAndCharcoalForRealAlumentum() throws IOException {
        JsonObject recipe = recipe("alumentum");
        assertEquals("ALUMENTUM", recipe.get("research").getAsString());
        assertEquals("thaumcraft:alumentum", recipe.getAsJsonObject("result").get("id").getAsString());
        assertEquals(2, recipe.getAsJsonArray("catalyst").size());
        assertEquals("minecraft:coal", recipe.getAsJsonArray("catalyst").get(0).getAsJsonObject().get("item").getAsString());
        assertEquals("minecraft:charcoal", recipe.getAsJsonArray("catalyst").get(1).getAsJsonObject().get("item").getAsString());
        assertAspects(recipe.getAsJsonObject("aspects"), Map.of("potentia", 10, "ignis", 10, "perditio", 5));
    }

    @Test void fourOptionalSourceMetalsRequireTheRealLoadedOreTag() throws IOException {
        for (String metal : new String[]{"copper", "tin", "silver", "lead"}) {
            JsonObject json = recipe("metal_purification_" + metal);
            JsonObject condition = json.getAsJsonArray("neoforge:conditions").get(0).getAsJsonObject();
            assertEquals("neoforge:not", condition.get("type").getAsString());
            assertEquals("neoforge:tag_empty", condition.getAsJsonObject("value").get("type").getAsString());
            assertEquals("c:ores/" + metal, condition.getAsJsonObject("value").get("tag").getAsString());
            assertEquals("thaumcraft:cluster_" + metal, json.getAsJsonObject("result").get("id").getAsString());
            assertAspects(json.getAsJsonObject("aspects"), Map.of("metallum", 5, "ordo", 5));
        }
    }

    private static JsonObject recipe(String id) throws IOException {
        String path = "data/thaumcraft/recipe/" + id + ".json";
        try (InputStream input = CrucibleRecipeAssetsTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(input, path);
            JsonObject result = JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals("thaumcraft:crucible", result.get("type").getAsString());
            assertFalse(result.has("pattern") || result.has("ingredients") || result.has("vis"), "Not a free vanilla or arcane substitute");
            assertEquals(1, result.getAsJsonObject("result").get("count").getAsInt());
            return result;
        }
    }
    private static void assertAspects(JsonObject actual, Map<String, Integer> expected) {
        assertEquals(expected.size(), actual.size());
        expected.forEach((key, amount) -> assertEquals(amount.intValue(), actual.get(key).getAsInt(), key));
    }
}
