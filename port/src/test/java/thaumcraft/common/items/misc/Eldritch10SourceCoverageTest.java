package thaumcraft.common.items.misc;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Read-only source provenance, not a gameplay/visual acceptance test. No MC classes initialized. */
class Eldritch10SourceCoverageTest {
    private static final Path SOURCE = Path.of("../reference/tc6/src");
    private static final Path GENERATED = Path.of("src/generated/resources");

    @Test void allFiveActualEldritchResearchDefinitionsArePreserved() throws Exception {
        String relative = "thaumcraft/research/eldritch.json";
        var original = JsonParser.parseString(Files.readString(SOURCE.resolve("assets").resolve(relative))).getAsJsonObject();
        var nativeJson = JsonParser.parseString(Files.readString(GENERATED.resolve("data").resolve(relative))).getAsJsonObject();
        assertEquals(original, nativeJson);
        var keys = new TreeSet<String>();
        for (var entry : original.getAsJsonArray("entries")) keys.add(entry.getAsJsonObject().get("key").getAsString());
        assertEquals(Set.of("BASEELDRITCH", "VOIDSIPHON", "VOIDSEERPEARL", "VOIDROBEARMOR", "PRIMALCRUSHER"), keys);
    }

    @Test void danglingOuterLandsReferencesAreNotAnImplementedSourceDimension() throws Exception {
        assertTrue(Files.readString(SOURCE.resolve("thaumcraft/common/config/ModConfig.java")).contains("dimensionOuterId = -42"));
        assertTrue(Files.isRegularFile(SOURCE.resolve("assets/thaumcraft/textures/misc/eldritch_portal.png")));
        try (var files = Files.walk(SOURCE.resolve("thaumcraft"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                assertFalse(source.matches("(?s).*\\bclass\\s+\\w+\\s+extends\\s+WorldProvider\\b.*"), file.toString());
                assertFalse(source.contains("DimensionManager.registerDimension("), file.toString());
                assertFalse(source.contains("DimensionType.register("), file.toString());
                assertFalse(source.contains("class BlockEldritchPortal"), file.toString());
            }
        }
    }

    @Test void nativeItemModelsUseOriginalUnmodifiedTextures() throws Exception {
        for (String item : new String[]{"sanity_soap", "creative_flux_sponge"}) {
            var original = JsonParser.parseString(Files.readString(SOURCE.resolve("assets/thaumcraft/models/item/" + item + ".json"))).getAsJsonObject();
            var model = JsonParser.parseString(Files.readString(GENERATED.resolve("assets/thaumcraft/models/item/" + item + ".json"))).getAsJsonObject();
            String sourceTexture = original.getAsJsonObject("textures").get("layer0").getAsString();
            String nativeTexture = model.getAsJsonObject("textures").get("layer0").getAsString();
            assertEquals(sourceTexture.replace(":items/", ":item/"), nativeTexture);
            String name = sourceTexture.substring(sourceTexture.indexOf('/') + 1);
            assertArrayEquals(Files.readAllBytes(SOURCE.resolve("assets/thaumcraft/textures/items/" + name + ".png")),
                Files.readAllBytes(Path.of("src/main/resources/assets/thaumcraft/textures/item/" + name + ".png")));
        }
    }

    @Test void sourceSoapBlockingGetsTheRequiredNativeAbilityNotOnlyThePose() throws Exception {
        String vanilla = Files.readString(Path.of("../reference/mc-1.12.2/net/minecraft/entity/EntityLivingBase.java"));
        String original = Files.readString(SOURCE.resolve("thaumcraft/common/items/consumables/ItemSanitySoap.java"));
        String nativeJava = Files.readString(Path.of("src/main/java/thaumcraft/common/items/consumables/ItemSanitySoap.java"));
        assertTrue(original.contains("return EnumAction.BLOCK;"));
        assertTrue(vanilla.contains("item.getItemUseAction(this.activeItemStack) != EnumAction.BLOCK"));
        assertTrue(nativeJava.contains("return ability == ItemAbilities.SHIELD_BLOCK;"));
        assertFalse(nativeJava.contains("durability("));
    }
}
