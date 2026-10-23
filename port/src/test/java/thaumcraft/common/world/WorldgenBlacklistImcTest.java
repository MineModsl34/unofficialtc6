package thaumcraft.common.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.InterModComms;
import org.junit.jupiter.api.Test;

class WorldgenBlacklistImcTest {
    private static final ResourceLocation KEY = ResourceLocation.parse("addon:future_biome");

    @Test
    void originalMethodNamesSelectSeparateModernRegistryKeyMaps() {
        var dimension = WorldgenBlacklistImc.parseMessage("dimensionBlacklist", " addon:future_biome = 2 ").orElseThrow();
        var biome = WorldgenBlacklistImc.parseMessage("biomeBlacklist", "addon:future_biome=-1").orElseThrow();
        assertTrue(dimension.dimension());
        assertEquals(KEY, dimension.key());
        assertEquals(2, dimension.mode());
        assertFalse(biome.dimension());
        assertEquals(KEY, biome.key());
        assertEquals(-1, biome.mode());
        assertFalse(WorldgenBlacklistImc.isBlacklistMethod("BiomeBlacklist"));
        assertFalse(WorldgenBlacklistImc.isBlacklistMethod(null));
    }

    @Test
    void parserAcceptsAllIntegerModesAndDeferredKeysButNeverGuessesNumericLegacyIds() {
        for (int mode : new int[]{0, 1, 2, -1, 7, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            var entry = WorldgenBlacklistImc.parseMessage("biomeBlacklist", "addon:future_biome=" + mode).orElseThrow();
            assertEquals(mode, entry.mode());
        }
        for (Object payload : new Object[]{
            null, 1, true, "0:1", "0=1", "minecraft:plains", "minecraft:plains=1=2",
            "minecraft:Plains=1", "minecraft:plains=2147483648", "minecraft:plains=word"
        }) {
            assertTrue(WorldgenBlacklistImc.parseMessage("biomeBlacklist", payload).isEmpty(), String.valueOf(payload));
        }
    }

    @Test
    void unrelatedLegacyIntegrationMessagesAreNeitherConsumedNorEvaluated() {
        var supplierCalls = new AtomicInteger();
        List<WorldgenBlacklistImc.Entry> received = new ArrayList<>();
        var message = new InterModComms.IMCMessage("addon", "thaumcraft", "harvestStandardCrop", () -> {
            supplierCalls.incrementAndGet();
            throw new AssertionError("another integration owns this payload");
        });
        assertFalse(WorldgenBlacklistImc.processMessage(message, received::add));
        assertEquals(0, supplierCalls.get());
        assertTrue(received.isEmpty());
    }

    @Test
    void suppliedMessagesReachIndependentStoresWithConfigurationPriorityAndReloadPersistence() {
        var store = new WorldgenBlacklists.Store();
        store.setConfig(WorldgenPolicy.EMPTY.withDimension(KEY, -1));
        for (String method : new String[]{"dimensionBlacklist", "biomeBlacklist"}) {
            var message = new InterModComms.IMCMessage("addon", "thaumcraft", method, () -> "addon:future_biome=0");
            assertTrue(WorldgenBlacklistImc.processMessage(message, entry -> {
                if (entry.dimension()) {
                    store.registerDimension(entry.key(), entry.mode());
                } else {
                    store.registerBiome(entry.key(), entry.mode());
                }
            }));
        }
        assertEquals(-1, store.policy().dimensionMode(KEY));
        assertEquals(0, store.policy().biomeMode(KEY));
        store.setConfig(WorldgenPolicy.EMPTY);
        assertEquals(0, store.policy().dimensionMode(KEY));
        assertEquals(0, store.policy().biomeMode(KEY));
    }

    @Test
    void malformedPayloadAndSupplierFailureAreIsolatedFromFollowingValidMessages() {
        List<WorldgenBlacklistImc.Entry> received = new ArrayList<>();
        var invalid = new InterModComms.IMCMessage("addon", "thaumcraft", "biomeBlacklist", () -> "0:1");
        var failing = new InterModComms.IMCMessage("addon", "thaumcraft", "biomeBlacklist", () -> {
            throw new IllegalStateException("broken addon supplier");
        });
        var valid = new InterModComms.IMCMessage("addon", "thaumcraft", "biomeBlacklist", () -> "addon:future_biome=7");
        assertFalse(WorldgenBlacklistImc.processMessage(invalid, received::add));
        assertFalse(WorldgenBlacklistImc.processMessage(failing, received::add));
        assertTrue(WorldgenBlacklistImc.processMessage(valid, received::add));
        assertEquals(1, received.size());
        assertEquals(new WorldgenBlacklistImc.Entry(false, KEY, 7), received.getFirst());
    }
}
