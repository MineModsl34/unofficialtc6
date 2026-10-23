package thaumcraft.datagen;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Source-data-only tests; do not decode ItemStacks, bootstrap registries or consume furnace RNG. */
class SmeltingBonusAssetsTest {
    @Test void allThirtyFiveLogicalRulesRetainSourceOrderAndSnapshot() throws IOException {
        JsonObject manifest = asset(TCSmeltingBonusProvider.RESOURCE);
        TCSmeltingBonusProvider.validate(manifest);
        assertEquals("5466b72c3d8e1f2afdab5bd720339ab703aa80d50a09244c20c0580ccaa553c8",manifest.get("sourceSha256").getAsString());
        var rules = manifest.getAsJsonArray("rules"); assertEquals(35,rules.size());
        for (int index=0;index<rules.size();index++) {
            JsonObject row=rules.get(index).getAsJsonObject(),payload=row.getAsJsonObject("payload");
            assertEquals(index,payload.get("source_order").getAsInt()); assertEquals(2963+index,row.get("sourceLine").getAsInt());
            assertEquals(payload,asset("data/thaumcraft/smelting_bonus/"+row.get("id").getAsString().substring(11)+".json"));
            assertEquals(index<22?0.33F:List.of(0.025F,0.01F,0.01F,0.025F,0.02F,0.01F,0.025F,0.01F,0.01F,0.02F,0.01F,0.01F,0.02F).get(index-22),payload.get("chance").getAsFloat());
        }
        assertEquals("oreGold",rules.get(0).getAsJsonObject().getAsJsonObject("payload").getAsJsonObject("input").get("legacy_ore").getAsString());
        assertEquals("oreGold",rules.get(26).getAsJsonObject().getAsJsonObject("payload").getAsJsonObject("input").get("legacy_ore").getAsString());
        assertNotEquals(rules.get(0).getAsJsonObject().getAsJsonObject("payload").get("output"),rules.get(26).getAsJsonObject().getAsJsonObject("payload").get("output"));
    }
    @Test void nativeWildcardsStayExactlyFourFishAndEightClustersWithoutExtraRngTrials() throws IOException {
        var rules=asset(TCSmeltingBonusProvider.RESOURCE).getAsJsonArray("rules");
        var fish=rules.get(19).getAsJsonObject().getAsJsonObject("payload").getAsJsonObject("input").getAsJsonArray("items");
        assertEquals(List.of("minecraft:cod","minecraft:salmon","minecraft:tropical_fish","minecraft:pufferfish"),
            java.util.stream.StreamSupport.stream(fish.spliterator(),false).map(value->value.getAsString()).toList());
        assertEquals(8,rules.get(34).getAsJsonObject().getAsJsonObject("payload").getAsJsonObject("input").getAsJsonArray("items").size());
        for (int index=16;index<22;index++) assertEquals("neoforge:item_exists",rules.get(index).getAsJsonObject().getAsJsonObject("payload").getAsJsonArray("neoforge:conditions").get(0).getAsJsonObject().get("type").getAsString());
    }
    @Test void unknownFutureOutputCannotLoseItsPreDecodeCondition() throws IOException {
        JsonObject manifest=asset(TCSmeltingBonusProvider.RESOURCE);
        manifest.getAsJsonArray("rules").get(16).getAsJsonObject().getAsJsonObject("payload").getAsJsonArray("neoforge:conditions").remove(0);
        assertThrows(IllegalArgumentException.class,()->TCSmeltingBonusProvider.validate(manifest));
    }
    private static JsonObject asset(String name) throws IOException {
        try(var input=SmeltingBonusAssetsTest.class.getClassLoader().getResourceAsStream(name)) {
            assertNotNull(input,name); return JsonParser.parseString(new String(input.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
