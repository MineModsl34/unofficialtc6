package thaumcraft.datagen;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Generated source recipe/tag structure evidence only, not world/runtime acceptance. */
class ScribingRecipeAssetsTest {
    @Test
    void emptyPhialBottleAndWildcardRefillRetainAllThreeSourceRecipes() throws IOException {
        // ConfigRecipes:2907-2912: original phial metadata0 is the real flattened empty item,
        // now registered in stage5. This is not a new recipe or a bottle/filled-phial substitute.
        assertRecipe("scribingtoolscraft1", List.of("item:thaumcraft:phial_empty", "item:minecraft:feather",
            "tag:thaumcraft:legacy_scribing_ink"));
        assertRecipe("scribingtoolscraft2", List.of("item:minecraft:glass_bottle", "item:minecraft:feather",
            "tag:thaumcraft:legacy_scribing_ink"));
        assertRecipe("scribingtoolsrefill", List.of("item:thaumcraft:scribing_tools",
            "tag:thaumcraft:legacy_scribing_ink"));
    }

    @Test
    void phialCraftRetainsSourceEightEmptyPhialsClayAndThreeGlassPattern() throws IOException {
        // Source ConfigRecipes:2899-2901, old ItemStack(phial,8,0); metadata1 is not output.
        JsonObject recipe = json("data/thaumcraft/recipe/phial.json");
        assertEquals("minecraft:crafting_shaped", recipe.get("type").getAsString());
        var pattern = recipe.getAsJsonArray("pattern");
        assertEquals(3, pattern.size());
        assertEquals(" C ", pattern.get(0).getAsString()); assertEquals("G G", pattern.get(1).getAsString()); assertEquals(" G ", pattern.get(2).getAsString());
        var keys = recipe.getAsJsonObject("key"); assertEquals(2, keys.size());
        assertEquals("minecraft:clay_ball", keys.getAsJsonObject("C").get("item").getAsString());
        assertEquals("c:glass_blocks", keys.getAsJsonObject("G").get("tag").getAsString());
        var output = recipe.getAsJsonObject("result");
        assertEquals("thaumcraft:phial_empty", output.get("id").getAsString()); assertEquals(8, output.get("count").getAsInt());
        assertEquals(2, output.size(), "plain source empty metadata0, not filled aspects/components");
    }

    @Test
    void inkBridgeSeedsOnlyTheOriginalInkSacNotFlattenedBlackDyeOrGlowInk() throws IOException {
        var tag = json("data/thaumcraft/tags/item/legacy_scribing_ink.json");
        assertFalse(tag.has("replace") && tag.get("replace").getAsBoolean());
        var values = tag.getAsJsonArray("values");
        assertEquals(1, values.size());
        assertEquals("minecraft:ink_sac", values.get(0).getAsString());
    }

    private static void assertRecipe(String name, List<String> expectedIngredients) throws IOException {
        JsonObject recipe = json("data/thaumcraft/recipe/" + name + ".json");
        assertEquals("minecraft:crafting_shapeless", recipe.get("type").getAsString());
        assertEquals("misc", recipe.get("category").getAsString());
        assertEquals("thaumcraft:inkwell", recipe.get("group").getAsString());
        assertFalse(recipe.has("pattern") || recipe.has("key") || recipe.has("neoforge:conditions"));
        var result = recipe.getAsJsonObject("result");
        assertEquals("thaumcraft:scribing_tools", result.get("id").getAsString());
        assertEquals(1, result.get("count").getAsInt());
        assertEquals(2, result.size(), "plain output, no copied damage or custom components");
        var ingredients = new ArrayList<String>();
        for (var element : recipe.getAsJsonArray("ingredients")) {
            var ingredient = element.getAsJsonObject();
            assertEquals(1, ingredient.size(), "ordinary item/tag ingredient, no exact component constraints");
            String type = ingredient.has("item") ? "item" : "tag";
            ingredients.add(type + ":" + ingredient.get(type).getAsString());
        }
        assertEquals(expectedIngredients, ingredients);
    }

    private static JsonObject json(String path) throws IOException {
        try (InputStream stream = ScribingRecipeAssetsTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(stream, "runData must generate " + path);
            return JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
