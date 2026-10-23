package thaumcraft.datagen;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/** JSON declarations only; conditions do not prove actual gear/food runtime is ported. */
class OrdinaryCustomRecipeAssetsTest {
    @Test void bothRobeRecipesRetainSourceIdsAndAreConditionalOnActualArmor() throws IOException {
        for (String name : List.of("robedye", "voidarmordye")) {
            JsonObject recipe = json(name);
            assertEquals("thaumcraft:" + name, recipe.get("type").getAsString());
            assertFalse(recipe.has("ingredients") || recipe.has("result"), "A real custom serializer, no item-only/fake output substitute");
            var condition = recipe.getAsJsonArray("neoforge:conditions").get(0).getAsJsonObject();
            assertEquals("neoforge:or", condition.get("type").getAsString());
            assertEquals(3, condition.getAsJsonArray("values").size());
            for (var element : condition.getAsJsonArray("values")) {
                var item = element.getAsJsonObject(); assertEquals("neoforge:item_exists", item.get("type").getAsString());
                assertTrue(item.get("item").getAsString().startsWith(name.equals("robedye") ? "thaumcraft:cloth_" : "thaumcraft:void_robe_"));
            }
        }
    }
    @Test void meatTreatWaitsForGenuineSourceFoodInsteadOfAPlaceholder() throws IOException {
        JsonObject recipe = json("triplemeattreat");
        assertEquals("thaumcraft:triplemeattreat", recipe.get("type").getAsString());
        var condition = recipe.getAsJsonArray("neoforge:conditions").get(0).getAsJsonObject();
        assertEquals("neoforge:item_exists", condition.get("type").getAsString());
        assertEquals("thaumcraft:triple_meat_treat", condition.get("item").getAsString());
        assertFalse(recipe.has("result") || recipe.has("ingredients"));
    }
    private static JsonObject json(String name) throws IOException {
        try (InputStream input = OrdinaryCustomRecipeAssetsTest.class.getClassLoader().getResourceAsStream("data/thaumcraft/recipe/" + name + ".json")) {
            assertNotNull(input, name); return JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
