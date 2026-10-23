package thaumcraft.datagen;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class FocalAssetsTest {
    @Test void sourceWorkbenchIdentityAndRealLootRemainDistinctFromArcaneTable() {
        var rows = TCFocalAssetsProvider.read().getAsJsonArray("entries"); assertEquals(4,rows.size());
        var model = rows.get(0).getAsJsonObject().getAsJsonObject("payload"); assertEquals("thaumcraft:block/arcane_workbench",model.get("parent").getAsString()); assertEquals("thaumcraft:block/wand_workbench_top",model.getAsJsonObject("textures").get("top").getAsString());
        var loot = rows.get(3).getAsJsonObject().getAsJsonObject("payload"); assertEquals("thaumcraft:wand_workbench",loot.getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries").get(0).getAsJsonObject().get("name").getAsString());
    }
    @Test void escapingOrDuplicatePathsRejected() {
        var manifest = TCFocalAssetsProvider.read(); manifest.getAsJsonArray("entries").get(0).getAsJsonObject().addProperty("path","../escape.json"); assertThrows(IllegalArgumentException.class,() -> TCFocalAssetsProvider.validate(manifest));
    }
}
