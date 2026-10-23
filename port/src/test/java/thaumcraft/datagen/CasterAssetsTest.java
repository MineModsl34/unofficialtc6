package thaumcraft.datagen;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CasterAssetsTest{
    @Test void sevenSourceModelsResolveAndGauntletHasRealFocusGeometry(){var manifest=TCCasterAssetsProvider.read();assertEquals(7,manifest.getAsJsonArray("entries").size());Set<String> paths=new HashSet<>();for(var entry:manifest.getAsJsonArray("entries")){var row=entry.getAsJsonObject();paths.add(row.get("path").getAsString());assertFalse(row.get("payload").toString().contains("thaumcraft:items/"));}assertTrue(paths.contains("models/item/caster_gauntlet_focus.json"));assertTrue(paths.contains("models/item/caster_basic_focus_model.json"));var model=manifest.getAsJsonArray("entries").asList().stream().map(x->x.getAsJsonObject()).filter(x->x.get("path").getAsString().equals("models/item/caster_basic.json")).findFirst().orElseThrow().getAsJsonObject("payload");assertEquals(2,model.getAsJsonArray("overrides").size());}
}
