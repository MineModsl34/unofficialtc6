package thaumcraft.datagen;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Structural source-layer bindings and localization only; not client rendering acceptance. */
class CelestialModelAssetsTest {
    private static final String ASSETS = "assets/thaumcraft/";

    @Test
    void allThirteenModelsKeepTheOriginalSheetAndDistinctOverlayLayers() throws IOException {
        for (int meta = 0; meta < 13; meta++) {
            String variant = meta == 0 ? "sun" : meta < 5 ? "stars_" + meta : "moon_" + (meta - 4);
            String texture = variant.replace("_", "");
            var model = json("models/item/celestial_notes_" + variant + ".json");
            assertEquals("minecraft:item/generated", model.get("parent").getAsString());
            assertEquals("thaumcraft:item/celestial/sheet", model.getAsJsonObject("textures").get("layer0").getAsString());
            assertEquals("thaumcraft:item/celestial/" + texture, model.getAsJsonObject("textures").get("layer1").getAsString());
            assertEquals(2, model.getAsJsonObject("textures").size());
            assertFalse(model.has("loader"));
            assertPng("textures/item/celestial/" + texture + ".png");
        }
        assertPng("textures/item/celestial/sheet.png");
    }

    @Test
    void scribingToolUsesItsExistingSourceTextureNotANoteOrFallback() throws IOException {
        var model = json("models/item/scribing_tools.json");
        assertEquals("minecraft:item/handheld", model.get("parent").getAsString());
        assertEquals("thaumcraft:item/scribing_tools", model.getAsJsonObject("textures").get("layer0").getAsString());
        assertPng("textures/item/scribing_tools.png");
    }

    @Test
    void sharedLegacyNoteNameAndAllVariantTooltipsStayLocalized() throws IOException {
        for (String locale : List.of("en_us", "ru_ru", "zh_tw", "zh_cn", "ja_jp", "nl_nl", "fr_fr", "ko_kr")) {
            var lang = json("lang/" + locale + ".json");
            assertEquals(lang.get("item.thaumcraft.celestial_notes"), lang.get("item.celestial_notes"), locale);
            for (int meta = 0; meta < 13; meta++) {
                String variant = meta == 0 ? "sun" : meta < 5 ? "stars_" + meta : "moon_" + (meta - 4);
                String key = "item.celestial_notes." + variant + ".text";
                assertTrue(lang.has(key) && !lang.get(key).getAsString().isBlank(), locale + ": " + key);
            }
        }
    }

    private static JsonObject json(String path) throws IOException {
        try (InputStream stream = resource(path)) {
            return JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static void assertPng(String path) throws IOException {
        try (InputStream stream = resource(path)) {
            byte[] data = stream.readAllBytes();
            assertTrue(data.length > 24, path);
            assertArrayEquals(new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10}, java.util.Arrays.copyOf(data, 8), path);
        }
    }

    private static InputStream resource(String path) {
        InputStream stream = CelestialModelAssetsTest.class.getClassLoader().getResourceAsStream(ASSETS + path);
        assertNotNull(stream, ASSETS + path);
        return stream;
    }
}
