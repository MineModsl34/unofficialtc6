package thaumcraft.datagen;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DeviceSupportAssetsTest {
    @Test void reviewedNineteenResourcesHaveExactSnapshots() throws Exception {
        var rows = TCDeviceSupportResourcesProvider.read().getAsJsonArray("files");
        assertEquals(19,rows.size());
        for (var value : rows) {
            var row = value.getAsJsonObject();
            Path path = Path.of("src/generated/resources/assets").resolve(row.get("path").getAsString());
            assertEquals(row.get("payload"),JsonParser.parseString(Files.readString(path)),path.toString());
        }
    }
    @Test void sourceRailRetainsAllTwelvePoweredSlopeMappings() throws Exception {
        var source = JsonParser.parseString(Files.readString(Path.of("../reference/tc6/src/assets/thaumcraft/blockstates/activator_rail.json")));
        var nativeState = JsonParser.parseString(Files.readString(Path.of("src/generated/resources/assets/thaumcraft/blockstates/activator_rail.json")));
        assertEquals(JsonParser.parseString(source.toString().replace("thaumcraft:activator","thaumcraft:block/activator")),nativeState);
        assertEquals(12,nativeState.getAsJsonObject().getAsJsonObject("variants").size());
    }
    @Test void realFluidAndTypedLinkHaveNativeModelBindings() throws Exception {
        var rows = TCDeviceSupportResourcesProvider.read().getAsJsonArray("files");
        for (var value : rows) {
            var row = value.getAsJsonObject();
            assertFalse(row.get("payload").toString().contains(":blocks/"));
            assertFalse(row.get("payload").toString().contains(":items/"));
        }
        var bucket = JsonParser.parseString(Files.readString(Path.of("src/generated/resources/assets/thaumcraft/models/item/purifying_fluid_bucket.json"))).getAsJsonObject();
        assertEquals("thaumcraft:purifying_fluid",bucket.get("fluid").getAsString());
        assertEquals("neoforge:fluid_container",bucket.get("loader").getAsString());
    }
}
