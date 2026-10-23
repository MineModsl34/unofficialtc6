package thaumcraft.datagen;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Cheap source JSON structure evidence only, not gameplay/owner/runtime matrix acceptance. */
class InfusionRecipeAssetsTest {
    @Test void sevenCrystalClustersHaveExactComponentCentralAndRealClusterOutputs() throws IOException {
        String[] names = {"air", "fire", "water", "earth", "order", "entropy", "flux"};
        String[] tags = {"aer", "ignis", "aqua", "terra", "ordo", "perditio", "vitium"};
        for (int index = 0; index < names.length; index++) {
            JsonObject recipe = json("data/thaumcraft/recipe/crystalcluster" + names[index] + ".json");
            assertEquals("thaumcraft:infusion", recipe.get("type").getAsString());
            assertEquals("CRYSTALFARMER", recipe.get("research").getAsString());
            assertEquals(index == 6 ? 4 : 0, recipe.get("instability").getAsInt());
            assertEquals("thaumcraft:crystal_" + tags[index], recipe.getAsJsonObject("result").get("id").getAsString());
            assertEquals(3, recipe.getAsJsonObject("aspects").size());
            assertEquals(10, recipe.getAsJsonObject("aspects").get(tags[index]).getAsInt());
            assertEquals(10, recipe.getAsJsonObject("aspects").get("vitreus").getAsInt());
            assertEquals(5, recipe.getAsJsonObject("aspects").get("vinculum").getAsInt());
            assertEquals("thaumcraft:nbt", recipe.getAsJsonObject("central").get("type").getAsString());
            var aspects = recipe.getAsJsonObject("central").getAsJsonObject("stack").getAsJsonObject("components").getAsJsonObject("thaumcraft:aspects");
            assertEquals(1, aspects.size()); assertEquals(1, aspects.get(tags[index]).getAsInt());
            assertEquals(2, recipe.getAsJsonArray("components").size());
        }
    }
    @Test void eightSourceEnchantmentRecipesConsumeAnyActualEnchantedBookAndDoNotRegisterSourceFakeExamples() throws IOException {
        Map<String, String> materials = Map.of("burrowing", "minecraft:rabbit_foot", "collector", "minecraft:lead", "destructive", "minecraft:tnt", "refining", "thaumcraft:salis_mundus",
            "sounding", "minecraft:map", "arcing", "minecraft:redstone_block", "essence", "thaumcraft:crystal_essence");
        for (String name : List.of("burrowing", "collector", "destructive", "refining", "sounding", "arcing", "essence", "lamplight")) {
            JsonObject recipe = json("data/thaumcraft/recipe/ie" + name + ".json");
            assertEquals("thaumcraft:infusion_enchantment", recipe.get("type").getAsString());
            assertEquals(name.toUpperCase(java.util.Locale.ROOT), recipe.get("enchantment").getAsString());
            assertFalse(recipe.has("result") || recipe.has("central"), "Dynamic genuine central item, no fake tool output");
            var ingredients = recipe.getAsJsonArray("components"); assertEquals(2, ingredients.size());
            assertEquals("thaumcraft:nbt", ingredients.get(0).getAsJsonObject().get("type").getAsString());
            JsonObject book = ingredients.get(0).getAsJsonObject().getAsJsonObject("stack");
            assertEquals("minecraft:enchanted_book", book.get("id").getAsString()); assertFalse(book.has("components"));
            var second = ingredients.get(1).getAsJsonObject();
            if (name.equals("lamplight")) assertEquals("c:nitor", second.get("tag").getAsString()); else assertEquals(materials.get(name), second.get("item").getAsString());
            assertNull(getClass().getClassLoader().getResource("data/thaumcraft/recipe/ie" + name + "fake.json"));
        }
    }
    @Test void runicUpgradeIsDynamicAndNitorIngredientContainsAllSixteenRegisteredColors() throws IOException {
        JsonObject runic = json("data/thaumcraft/recipe/runicarmor.json");
        assertEquals("thaumcraft:infusion_runic", runic.get("type").getAsString()); assertEquals("RUNICSHIELDING", runic.get("research").getAsString());
        assertFalse(runic.has("result") || runic.has("aspects") || runic.has("components"), "Costs/components/output derive actual owner central runic charge");
        var nitor = json("data/c/tags/item/nitor.json").getAsJsonArray("values"); assertEquals(16, nitor.size());
        assertEquals(16, java.util.stream.StreamSupport.stream(nitor.spliterator(), false).map(element -> element.getAsString()).distinct().count());
        for (var color : nitor) assertTrue(color.getAsString().startsWith("thaumcraft:nitor_"));
    }
    private static JsonObject json(String path) throws IOException {
        try (InputStream input = InfusionRecipeAssetsTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(input, path); return JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
