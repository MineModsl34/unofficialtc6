package thaumcraft.datagen;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import thaumcraft.api.aspects.Aspect;

/** Pure resource audit; no registry/Minecraft launch or gameplay acceptance. */
class AlchemyNormalRecipeAssetsTest {
    @Test void allSourceNitorDyeIdsAndColorsHaveTwoOrdinaryIngredients() throws IOException {
        String[] colors = {"white","orange","magenta","light_blue","yellow","lime","pink","gray","light_gray","cyan","purple","blue","brown","green","red","black"};
        for (String color : colors) {
            String oldName = color.equals("light_blue") ? "lightblue" : color.equals("light_gray") ? "silver" : color;
            JsonObject json = recipe("nitordye" + oldName);
            assertEquals("minecraft:crafting_shapeless", json.get("type").getAsString());
            assertEquals("thaumcraft:nitorgroup", json.get("group").getAsString());
            assertEquals("thaumcraft:nitor_" + color, json.getAsJsonObject("result").get("id").getAsString());
            assertEquals(2, json.getAsJsonArray("ingredients").size());
            assertEquals("c:dyes/" + color, json.getAsJsonArray("ingredients").get(0).getAsJsonObject().get("tag").getAsString());
            assertEquals("c:nitor", json.getAsJsonArray("ingredients").get(1).getAsJsonObject().get("tag").getAsString());
        }
    }

    @Test void allLabelsRequireBlankVariantAndExactTenUnitFilledPhial() throws IOException {
        for (Aspect aspect : Aspect.aspects.values()) {
            JsonObject json = recipe("label_" + aspect.getTag());
            assertEquals("minecraft:crafting_shapeless", json.get("type").getAsString());
            var inputs = json.getAsJsonArray("ingredients");
            assertFalse(inputs.get(0).getAsJsonObject().get("filled").getAsBoolean());
            assertEquals("thaumcraft:label_variant", inputs.get(0).getAsJsonObject().get("type").getAsString());
            JsonObject expected = inputs.get(1).getAsJsonObject().getAsJsonObject("stack");
            assertEquals("thaumcraft:phial_filled", expected.get("id").getAsString());
            assertEquals(10, expected.getAsJsonObject("components").getAsJsonObject("thaumcraft:aspects").get(aspect.getTag()).getAsInt());
            assertEquals(1, json.getAsJsonObject("result").getAsJsonObject("components").getAsJsonObject("thaumcraft:aspects").get(aspect.getTag()).getAsInt());
        }
        assertTrue(recipe("jarlabelnull").getAsJsonArray("ingredients").get(0).getAsJsonObject().get("filled").getAsBoolean());
        assertEquals(4, recipe("jarlabel").getAsJsonObject("result").get("count").getAsInt());
        assertEquals(6, recipe("jarlabel").getAsJsonArray("ingredients").size());
    }

    @Test void sourceEightEmptyPhialsAndPhialScribingPathArePresent() throws IOException {
        JsonObject phial = recipe("phial");
        assertEquals("minecraft:crafting_shaped", phial.get("type").getAsString());
        assertEquals(8, phial.getAsJsonObject("result").get("count").getAsInt());
        assertEquals("thaumcraft:phial_empty", phial.getAsJsonObject("result").get("id").getAsString());
        assertEquals(" G ", phial.getAsJsonArray("pattern").get(2).getAsString());
        assertEquals("thaumcraft:phial_empty", recipe("scribingtoolscraft1").getAsJsonArray("ingredients").get(0).getAsJsonObject().get("item").getAsString());
    }

    private static JsonObject recipe(String id) throws IOException {
        String resource = "data/thaumcraft/recipe/" + id + ".json";
        try (InputStream stream = AlchemyNormalRecipeAssetsTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(stream, resource);
            JsonObject json = JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            assertFalse(json.has("research") || json.has("aspects") || json.has("vis"), "Original ordinary crafting stays ordinary");
            return json;
        }
    }
}
