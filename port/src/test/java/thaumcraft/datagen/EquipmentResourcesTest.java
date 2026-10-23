package thaumcraft.datagen;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EquipmentResourcesTest {
    @Test void allSourceOwnedModelsHaveMatchingNativeSnapshots() throws Exception {
        var manifest=JsonParser.parseString(Files.readString(Path.of("src/main/resources",TCEquipmentResourcesProvider.RESOURCE))).getAsJsonObject();TCEquipmentResourcesProvider.validate(manifest);assertEquals(23,manifest.getAsJsonArray("files").size());
        for(var entry:manifest.getAsJsonArray("files")){var row=entry.getAsJsonObject();assertEquals(row.get("payload"),JsonParser.parseString(Files.readString(Path.of("src/generated/resources/assets",row.get("path").getAsString()))));}
    }
    @Test void grappleHasBothActualGeometryModelsAndSourceLoadedPredicate() throws Exception {
        var base=JsonParser.parseString(Files.readString(Path.of("src/generated/resources/assets/thaumcraft/models/item/grapple_gun.json"))).getAsJsonObject();assertEquals(2,base.getAsJsonArray("overrides").size());
        assertEquals(1,base.getAsJsonArray("overrides").get(1).getAsJsonObject().getAsJsonObject("predicate").get("type").getAsInt());
        for(int index=1;index<=2;index++){var geometry=JsonParser.parseString(Files.readString(Path.of("src/generated/resources/assets/thaumcraft/models/item/grapple_gun_"+index+".json"))).getAsJsonObject();assertTrue(geometry.getAsJsonArray("elements").size()>5);assertEquals("thaumcraft:item/grapple_gun",geometry.getAsJsonObject("textures").get("side").getAsString());}
    }
}
