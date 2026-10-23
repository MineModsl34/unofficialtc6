package thaumcraft.common;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Read-only wiring/resource checks, not world, gameplay or network acceptance. */
class Stage10IntegrationContractTest {
    private static String java(String name) throws Exception {
        return Files.readString(Path.of("src/main/java/thaumcraft/" + name + ".java"));
    }
    @Test void sharedOwnersConsumeRealFactoriesWithoutAFluidBlockItem() throws Exception {
        assertTrue(java("common/config/ConfigBlocks").contains("Phase10RiftRegistries.createBlocks().forEach(ConfigBlocks::register)"));
        assertFalse(java("common/config/ConfigBlocks").contains("DeathFluidRegistries.createBlocks()"));
        String items = java("common/config/ConfigItems");
        assertTrue(items.contains("DeathFluidRegistries.createItems().forEach(ConfigItems::register)"));
        assertTrue(items.contains("Eldritch10ItemRegistries.createItems().forEach(ConfigItems::register)"));
        assertTrue(items.contains("ItemsTC.sanitySoap = thaumcraft.common.items.misc.Eldritch10ItemRegistries.SOAP"));
        assertTrue(items.contains("ItemsTC.creativeFluxSponge = thaumcraft.common.items.misc.Eldritch10ItemRegistries.SPONGE"));
        assertTrue(java("Registrar").contains("Phase10RiftRegistries.createEntityTypes()"));
        assertTrue(java("common/blocks/misc/BlockFluidDeath").contains("noLootTable()"));
        assertFalse(java("datagen/TCBlockLoot").contains("add(BlocksTC.liquidDeath,"));
    }
    @Test void auraTriggerAndStabilizerReachActualRiftsOnSourceCadences() throws Exception {
        String aura = java("common/world/aura/AuraEvents");
        assertTrue(aura.contains("level.getGameTime() % 20 != 0"));
        assertTrue(aura.contains("AuraHandler.riftTrigger.remove(level.dimension())"));
        assertTrue(aura.contains("!thaumcraft.common.config.ModConfig.CONFIG_MISC.wussMode"));
        assertTrue(aura.contains("EntityFluxRift.createRift(level, trigger)"));
        String stabilizer = java("common/tiles/devices/TileStabilizer");
        assertTrue(stabilizer.contains("ticks % 5 == 0"));
        assertTrue(stabilizer.contains("new AABB(worldPosition).inflate(8)"));
        assertTrue(stabilizer.contains("EntityFluxRift.EnumStability.VERY_STABLE && mitigate(1)"));
        assertTrue(stabilizer.contains("rift.addStability()"));
        assertTrue(stabilizer.contains("delay += 5"));
    }
    @Test void dissolveBypassesArmorButIsNotSourceMagic() throws Exception {
        Path data = Path.of("src/main/resources/data");
        var type = JsonParser.parseString(Files.readString(data.resolve("thaumcraft/damage_type/dissolve.json"))).getAsJsonObject();
        assertEquals("dissolve", type.get("message_id").getAsString());
        assertEquals(.3, type.get("exhaustion").getAsDouble());
        assertEquals("never", type.get("scaling").getAsString());
        var bypass = JsonParser.parseString(Files.readString(data.resolve("minecraft/tags/damage_type/bypasses_armor.json"))).getAsJsonObject();
        assertTrue(bypass.getAsJsonArray("values").asList().stream().anyMatch(value -> value.getAsString().equals("thaumcraft:dissolve")));
        var magic = JsonParser.parseString(Files.readString(data.resolve("thaumcraft/tags/damage_type/source_magic.json"))).getAsJsonObject();
        // Native tags allow either a string or an optional {id, required} entry.
        assertFalse(magic.getAsJsonArray("values").asList().stream().anyMatch(value ->
            (value.isJsonPrimitive() ? value.getAsString() : value.getAsJsonObject().get("id").getAsString()).equals("thaumcraft:dissolve")));
        assertTrue(java("api/damagesource/DamageSourceThaumcraft").contains("DamageSource dissolve(Level level)"));
    }
    @Test void siphonResourcesAndProvidersHaveGenuineBindings() throws Exception {
        String providers = java("datagen/DataGenerators");
        assertTrue(providers.contains("new TCFluxRiftAssetsProvider(output)"));
        assertTrue(providers.contains("new TCDeathFluidResourcesProvider(output)"));
        assertTrue(java("datagen/TCBlockLoot").contains("dropSelf(BlocksTC.voidSiphon)"));
        assertTrue(Files.readString(Path.of("src/generated/resources/data/minecraft/tags/block/mineable/pickaxe.json")).contains("thaumcraft:void_siphon"));
        var loot = JsonParser.parseString(Files.readString(Path.of("src/generated/resources/data/thaumcraft/loot_table/blocks/void_siphon.json"))).getAsJsonObject();
        assertEquals("thaumcraft:void_siphon", loot.getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries").get(0).getAsJsonObject().get("name").getAsString());
    }
}
