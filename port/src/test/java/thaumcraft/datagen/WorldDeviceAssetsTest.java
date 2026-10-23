package thaumcraft.datagen;

import com.google.gson.JsonParser;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorldDeviceAssetsTest {
    @Test void reviewedDeviceManifestIsNativeAndEverySnapshotMatches() throws Exception { var manifest = JsonParser.parseString(Files.readString(Path.of("src/main/resources",TCWorldDeviceAssetsProvider.RESOURCE))).getAsJsonObject(); TCWorldDeviceAssetsProvider.validate(manifest); assertEquals(118,manifest.getAsJsonArray("entries").size()); for (var entry : manifest.getAsJsonArray("entries")) { var row = entry.getAsJsonObject(); assertEquals(row.get("payload"),JsonParser.parseString(Files.readString(Path.of("src/generated/resources/assets/thaumcraft",row.get("path").getAsString())))); } }
    @Test void lampsRetainSeparateBodyAndSixSourceMountOrientations() throws Exception { for (String name : new String[]{"lamp_arcane","lamp_growth","lamp_fertility"}) { var state = JsonParser.parseString(Files.readString(Path.of("src/generated/resources/assets/thaumcraft/blockstates/" + name + ".json"))).getAsJsonObject(); assertEquals(8,state.getAsJsonArray("multipart").size()); } }
    @Test void allTwentyDeviceBlockstatesAndItemsExist() { for (String name : new String[]{"vis_generator","vis_battery","recharge_pedestal","levitator","lamp_arcane","lamp_growth","lamp_fertility","everfull_urn","hungry_chest","jar_brain","arcane_ear","arcane_ear_toggle","redstone_relay","dioptra","spa","potion_sprayer","pattern_crafter","condenser","condenser_lattice","condenser_lattice_dirty"}) { assertTrue(Files.isRegularFile(Path.of("src/generated/resources/assets/thaumcraft/blockstates/" + name + ".json")),name); assertTrue(Files.isRegularFile(Path.of("src/generated/resources/assets/thaumcraft/models/item/" + name + ".json")),name); } }
}
