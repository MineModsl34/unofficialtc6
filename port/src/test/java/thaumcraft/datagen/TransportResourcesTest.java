package thaumcraft.datagen;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TransportResourcesTest {
    @Test void reviewedResourcesAreBoundedAndAllGeneratedPayloadsExist() throws Exception {
        var manifest = JsonParser.parseString(Files.readString(Path.of("src/main/resources", TCTransportResourcesProvider.RESOURCE))).getAsJsonObject();
        TCTransportResourcesProvider.validate(manifest); assertEquals(18, manifest.getAsJsonArray("files").size());
        for (var entry : manifest.getAsJsonArray("files")) { var row = entry.getAsJsonObject(); Path root = Path.of("src/generated/resources", row.get("target").getAsString().equals("resource_pack") ? "assets" : "data");
            assertEquals(row.get("payload"), JsonParser.parseString(Files.readString(root.resolve(row.get("path").getAsString())))); }
    }
    @Test void filterCoreRetainsOriginalTintIndexAndConnectionStatesAreMultipart() throws Exception {
        var model = JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/thaumcraft/models/block/tube_filter_core.json"))).getAsJsonObject();
        var faces = model.getAsJsonArray("elements").get(0).getAsJsonObject().getAsJsonObject("faces");
        for (var entry : faces.entrySet()) assertEquals(1, entry.getValue().getAsJsonObject().get("tintindex").getAsInt());
        for (String name : new String[]{"tube", "tube_valve", "tube_restrict", "tube_oneway", "tube_filter", "tube_buffer"}) {
            var state = JsonParser.parseString(Files.readString(Path.of("src/generated/resources/assets/thaumcraft/blockstates/" + name + ".json"))).getAsJsonObject(); assertTrue(state.has("multipart")); assertEquals(7, state.getAsJsonArray("multipart").size());
        }
    }
}
