package thaumcraft.datagen;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ProcessingAssetsTest {
    @Test void allTwentyReviewedNativeAssetsHaveReproducibleSnapshots() throws Exception {
        var manifest = TCProcessingAssetsProvider.read();
        assertEquals(20,manifest.getAsJsonArray("entries").size());
        for (var value : manifest.getAsJsonArray("entries")) {
            var row = value.getAsJsonObject(); var path = Path.of("src/generated/resources/assets/thaumcraft").resolve(row.get("path").getAsString());
            assertEquals(row.get("payload"),JsonParser.parseString(Files.readString(path)),path.toString());
        }
    }
    @Test void modelGeometryRemainsSourceWithNativeTextures() throws Exception {
        Path source = Path.of("../reference/tc6/src/assets/thaumcraft/models/block"),ported = Path.of("src/main/resources/assets/thaumcraft/models/block");
        for (String name : new String[]{"bellows","brain_box","infernal_furnace","smelter_aux","smelter_vent"}) {
            var old = JsonParser.parseString(Files.readString(source.resolve(name + ".json"))).getAsJsonObject();
            var model = JsonParser.parseString(Files.readString(ported.resolve(name + ".json"))).getAsJsonObject();
            assertEquals(old.get("elements"),model.get("elements"),name);
            for (var texture : model.getAsJsonObject("textures").entrySet()) {
                String id = texture.getValue().getAsString(); assertFalse(id.contains(":blocks/")); assertFalse(id.endsWith("fire_layer_0"));
            }
        }
        assertEquals(Files.readString(source.resolve("thaumatorium.obj")).replace("\r\n","\n").stripTrailing(),Files.readString(ported.resolve("thaumatorium.obj")).replace("\r\n","\n").stripTrailing());
        assertTrue(Files.readString(ported.resolve("thaumatorium.mtl")).contains("map_Kd #texture"));
    }
    @Test void pathTraversalAndDuplicateAssetsFailClosed() {
        var invalid = TCProcessingAssetsProvider.read().deepCopy();
        invalid.getAsJsonArray("entries").get(0).getAsJsonObject().addProperty("path","../../config.json");
        assertThrows(IllegalArgumentException.class,() -> TCProcessingAssetsProvider.validate(invalid));
        var duplicate = TCProcessingAssetsProvider.read().deepCopy(); duplicate.getAsJsonArray("entries").add(duplicate.getAsJsonArray("entries").get(0).deepCopy());
        assertThrows(IllegalArgumentException.class,() -> TCProcessingAssetsProvider.validate(duplicate));
    }
}
