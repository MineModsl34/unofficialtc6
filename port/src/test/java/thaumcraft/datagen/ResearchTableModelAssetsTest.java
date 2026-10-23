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

/** Original research_table JSON geometry/bindings, not an in-game visual acceptance test. */
class ResearchTableModelAssetsTest {
    @Test
    void sourceTabletopAndFourLegsKeepTheirFacesAndCoordinates() throws IOException {
        var model = json("assets/thaumcraft/models/block/research_table.json");
        assertEquals("minecraft:block/block", model.get("parent").getAsString(), "source forge:default-block item transforms");
        var elements = model.getAsJsonArray("elements");
        int[][] from = {{0, 10, 0}, {11, 0, 1}, {11, 0, 11}, {1, 0, 11}, {1, 0, 1}};
        int[][] to = {{16, 16, 16}, {15, 10, 5}, {15, 10, 15}, {5, 10, 15}, {5, 10, 5}};
        assertEquals(5, elements.size());
        for (int index = 0; index < 5; index++) {
            var element = elements.get(index).getAsJsonObject();
            for (int axis = 0; axis < 3; axis++) {
                assertEquals(from[index][axis], element.getAsJsonArray("from").get(axis).getAsInt());
                assertEquals(to[index][axis], element.getAsJsonArray("to").get(axis).getAsInt());
            }
            var faces = element.getAsJsonObject("faces");
            assertEquals(index == 0 ? 6 : 5, faces.size());
            assertEquals(index == 0 ? "#top" : "#bottom", faces.getAsJsonObject("down").get("texture").getAsString());
            assertEquals(index == 0, faces.has("up")); // Source leg geometry omits the hidden top face.
            for (String direction : List.of("north", "south", "east", "west")) {
                var face = faces.getAsJsonObject(direction);
                assertEquals(index == 0 && direction.equals("south") ? "#front" : "#side", face.get("texture").getAsString());
                assertEquals(index == 0, face.has("cullface"));
            }
            assertEquals(index != 0, faces.getAsJsonObject("down").has("cullface"));
        }
    }

    @Test
    void generatedFacingBindingsKeepNorthZeroAndTheSameItemModel() throws IOException {
        var variants = json("assets/thaumcraft/blockstates/research_table.json").getAsJsonObject("variants");
        assertEquals(4, variants.size());
        for (var facing : Map.of("north", 0, "east", 90, "south", 180, "west", 270).entrySet()) {
            var variant = variants.getAsJsonObject("facing=" + facing.getKey());
            assertEquals("thaumcraft:block/research_table", variant.get("model").getAsString());
            assertEquals(facing.getValue().intValue(), variant.has("y") ? variant.get("y").getAsInt() : 0);
        }
        assertEquals("thaumcraft:block/research_table",
            json("assets/thaumcraft/models/item/research_table.json").get("parent").getAsString());
        String axes = json("data/minecraft/tags/block/mineable/axe.json").getAsJsonArray("values").toString();
        assertTrue(axes.contains("\"thaumcraft:research_table\""));
        var loot = json("data/thaumcraft/loot_table/blocks/research_table.json");
        var entries = loot.getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries");
        assertEquals(1, entries.size());
        assertEquals("thaumcraft:research_table", entries.get(0).getAsJsonObject().get("name").getAsString());
    }

    @Test
    void everySourceTextureAndTheExistingBlockNameArePresent() throws IOException {
        var textures = json("assets/thaumcraft/models/block/research_table.json").getAsJsonObject("textures");
        assertEquals(5, textures.size());
        for (String texture : List.of("research_table_top", "research_table_side", "research_table_front", "base_wood_grain")) {
            try (InputStream stream = resource("assets/thaumcraft/textures/block/" + texture + ".png")) {
                assertArrayEquals(new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10}, stream.readNBytes(8));
            }
        }
        for (String locale : List.of("en_us", "ru_ru")) {
            assertFalse(json("assets/thaumcraft/lang/" + locale + ".json").get("block.thaumcraft.research_table").getAsString().isBlank());
        }
    }

    private static JsonObject json(String path) throws IOException {
        try (InputStream stream = resource(path)) {
            return JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static InputStream resource(String path) {
        InputStream stream = ResearchTableModelAssetsTest.class.getClassLoader().getResourceAsStream(path);
        assertNotNull(stream, path);
        return stream;
    }
}
