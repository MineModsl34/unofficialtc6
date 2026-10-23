package thaumcraft.datagen;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Read-only registry/datagen evidence; no Minecraft bootstrap or world is created. */
class CultistPortalBindingsTest {
    private static JsonObject json(String path) throws Exception {
        return JsonParser.parseString(Files.readString(Path.of(path))).getAsJsonObject();
    }
    @Test void portalHasSeparateRealStructureAndPieceRegistrations() throws Exception {
        String registrar = Files.readString(Path.of("src/main/java/thaumcraft/Registrar.java"));
        assertTrue(registrar.contains("\"cultist_portal\"), TCCultistPortalStructure.TYPE"));
        assertTrue(registrar.contains("\"cultist_portal\"), TCCultistPortalPiece.TYPE"));
        String bootstrap = Files.readString(Path.of("src/main/java/thaumcraft/common/world/structures/TCStructures.java"));
        assertTrue(bootstrap.contains("context.register(CULTIST_PORTAL, new TCCultistPortalStructure"));
        assertTrue(bootstrap.contains("context.register(CULTIST_PORTAL_SET, new StructureSet"));
        var structure = json("src/generated/resources/data/thaumcraft/worldgen/structure/cultist_portal.json");
        assertEquals("thaumcraft:cultist_portal", structure.get("type").getAsString());
        assertEquals("#c:is_overworld", structure.get("biomes").getAsString());
        assertEquals("top_layer_modification", structure.get("step").getAsString());
        assertEquals(0, structure.getAsJsonObject("spawn_overrides").size());
    }
    @Test void bothSurfaceAttemptsUseIdenticalEveryChunkPlacementButDistinctLocateTargets() throws Exception {
        var mound = json("src/generated/resources/data/thaumcraft/worldgen/structure_set/mound.json");
        var portal = json("src/generated/resources/data/thaumcraft/worldgen/structure_set/cultist_portal.json");
        assertEquals(mound.get("placement"), portal.get("placement"));
        var placement = portal.getAsJsonObject("placement");
        assertEquals(1, placement.get("spacing").getAsInt());
        assertEquals(0, placement.get("separation").getAsInt());
        assertEquals(0x544336, placement.get("salt").getAsInt());
        assertEquals("thaumcraft:mound", mound.getAsJsonArray("structures").get(0).getAsJsonObject().get("structure").getAsString());
        assertEquals("thaumcraft:cultist_portal", portal.getAsJsonArray("structures").get(0).getAsJsonObject().get("structure").getAsString());
    }
    @Test void registrySnapshotsHaveOneDatagenOwner() throws Exception {
        var manifest = json("src/main/resources/assets/thaumcraft/porting/mob8_resources.json");
        for (var row : manifest.getAsJsonArray("files")) {
            String path = row.getAsJsonObject().get("path").getAsString();
            assertFalse(path.contains("/worldgen/structure"), path);
            assertNotEquals("thaumcraft/neoforge/biome_modifier/source_mob_spawns.json", path);
        }
        String bootstrap = Files.readString(Path.of("src/main/java/thaumcraft/common/world/features/TCWorldgen.java"));
        assertTrue(bootstrap.contains(".add(Registries.STRUCTURE, TCStructures::bootstrapStructures)"));
        assertTrue(bootstrap.contains(".add(Registries.STRUCTURE_SET, TCStructures::bootstrapSets)"));
        assertTrue(bootstrap.contains("\"source_mob_spawns\""));
    }
    @Test void supportTagMatchesTheCanonicalSourceCatalogWithoutFakeRequiredFutureBlocks() throws Exception {
        var expected = new HashSet<>(thaumcraft.common.world.structures.TCCultistPortalSupport.sourceSupportIds());
        var actual = new HashSet<String>();
        var support = json("src/generated/resources/data/thaumcraft/tags/block/cultist_portal_support.json");
        String nativeBlocks = Files.readString(Path.of("../reference/mc-1.21.1/net/minecraft/world/level/block/Blocks.java"));
        for (var entry : support.getAsJsonArray("values")) {
            String id;
            if (entry.isJsonPrimitive()) {
                id = entry.getAsString();
                assertTrue(id.startsWith("minecraft:"), id);
                assertTrue(nativeBlocks.contains("\"" + id.substring(10) + "\""), id);
            } else {
                id = entry.getAsJsonObject().get("id").getAsString();
                assertTrue(id.startsWith("thaumcraft:"), id);
                assertFalse(entry.getAsJsonObject().get("required").getAsBoolean(), id);
            }
            assertTrue(actual.add(id), id);
        }
        assertEquals(expected, actual);
        for (String rejected : new String[]{"grass_block", "mycelium", "snow_block", "clay", "deepslate", "rooted_dirt"})
            assertFalse(actual.contains("minecraft:" + rejected), rejected);
        String provider = Files.readString(Path.of("src/main/java/thaumcraft/datagen/TCBlockTagsProvider.java"));
        assertTrue(provider.contains("TCCultistPortalSupport.sourceSupportIds()"));
        assertTrue(provider.contains("portalSupport.addOptional(location)"));
    }
}
