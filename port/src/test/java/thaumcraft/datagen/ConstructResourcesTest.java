package thaumcraft.datagen;

import com.google.gson.JsonParser;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConstructResourcesTest {
    @Test void threeFlattenedPlacerModelsAndTwoSilkBridgeTagsHaveReviewedSnapshots() throws Exception {var manifest=JsonParser.parseString(Files.readString(Path.of("src/main/resources",TCConstructResourcesProvider.RESOURCE))).getAsJsonObject();TCConstructResourcesProvider.validate(manifest);for(var row:manifest.getAsJsonArray("files")){var entry=row.getAsJsonObject();String target=entry.get("target").getAsString().equals("resource_pack")?"assets":"data";assertEquals(entry.get("payload"),JsonParser.parseString(Files.readString(Path.of("src/generated/resources",target,entry.get("path").getAsString()))));}}
}
