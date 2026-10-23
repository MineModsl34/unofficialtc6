package thaumcraft.common.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class WorldgenPolicyTest {
    private static final ResourceLocation DIMENSION = ResourceLocation.parse("example:dimension");
    private static final ResourceLocation BIOME = ResourceLocation.parse("example:biome");

    @Test
    void emptyMapsMatchTheOriginalUnblacklistedDefault() {
        WorldgenPolicy policy = WorldgenPolicy.EMPTY;
        assertEquals(-1, policy.dimensionMode(DIMENSION));
        assertEquals(-1, policy.biomeMode(BIOME));
        assertTrue(policy.allowsOres(DIMENSION, BIOME));
        assertTrue(policy.allowsVegetation(DIMENSION, BIOME));
        assertTrue(policy.allowsAura(BIOME));
        assertTrue(policy.allowsStructures(DIMENSION));
        assertEquals(-1, policy.dimensionMode(null));
        assertEquals(-1, policy.biomeMode(null));
        assertTrue(policy.allowsAura(null));
    }

    @Test
    void explicitConfigurationOverridesImmutableAddonSnapshot() {
        WorldgenPolicy addons = WorldgenPolicy.EMPTY.withDimension(DIMENSION, 0).withBiome(BIOME, 2);
        WorldgenPolicy config = WorldgenPolicy.EMPTY.withDimension(DIMENSION, -1);
        WorldgenPolicy merged = addons.overriddenBy(config);
        assertEquals(-1, merged.dimensionMode(DIMENSION));
        assertEquals(2, merged.biomeMode(BIOME));
        assertEquals(0, addons.dimensionMode(DIMENSION));
        assertEquals(-1, WorldgenPolicy.EMPTY.biomeMode(BIOME));
        assertEquals(0, addons.overriddenBy(WorldgenPolicy.EMPTY).dimensionMode(DIMENSION));
    }

    @Test
    void dimensionModesRetainActualOresVegetationStructureAndAuraAsymmetry() {
        for (int mode : new int[]{-1, 0, 1, 2, 3, -2, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            WorldgenPolicy policy = WorldgenPolicy.fromEntries(List.of("example:dimension=" + mode), List.of());
            assertEquals(mode, policy.dimensionMode(DIMENSION));
            assertEquals(mode != 0 && mode != 2, policy.allowsOres(DIMENSION, BIOME), "ores mode " + mode);
            assertEquals(mode == -1, policy.allowsVegetation(DIMENSION, BIOME), "vegetation mode " + mode);
            assertEquals(mode == -1, policy.allowsStructures(DIMENSION), "structure mode " + mode);
            assertTrue(policy.allowsAura(BIOME), "actual aura generation ignores dimension mode " + mode);
        }
    }

    @Test
    void biomeModesRetainActualOresVegetationAndAuraComparisons() {
        for (int mode : new int[]{-1, 0, 1, 2, 3, -2, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            WorldgenPolicy policy = WorldgenPolicy.fromEntries(List.of(), List.of("example:biome=" + mode));
            assertEquals(mode, policy.biomeMode(BIOME));
            assertEquals(mode != 0 && mode != 2, policy.allowsOres(DIMENSION, BIOME), "ores mode " + mode);
            assertEquals(mode == -1, policy.allowsVegetation(DIMENSION, BIOME), "vegetation mode " + mode);
            assertEquals(mode == -1, policy.allowsAura(BIOME), "aura mode " + mode);
            assertTrue(policy.allowsStructures(DIMENSION), "surface structures ignore biome mode " + mode);
        }
    }

    @Test
    void eitherBlacklistCanBlockOresAndBothMustBeAbsentForVegetation() {
        for (int dimensionMode : new int[]{-1, 0, 1, 2}) {
            for (int biomeMode : new int[]{-1, 0, 1, 2}) {
                WorldgenPolicy policy = WorldgenPolicy.fromEntries(
                    List.of("example:dimension=" + dimensionMode), List.of("example:biome=" + biomeMode));
                assertEquals(dimensionMode != 0 && dimensionMode != 2 && biomeMode != 0 && biomeMode != 2,
                    policy.allowsOres(DIMENSION, BIOME));
                assertEquals(dimensionMode == -1 && biomeMode == -1, policy.allowsVegetation(DIMENSION, BIOME));
            }
        }
    }

    @Test
    void duplicateLastWinsAndExplicitMinusOneCanRemoveBlacklistBehavior() {
        WorldgenPolicy policy = WorldgenPolicy.fromEntries(
            List.of("example:dimension=2", "example:dimension=-1"),
            List.of("example:biome=0", "example:biome=1"));
        assertEquals(-1, policy.dimensionMode(DIMENSION));
        assertEquals(1, policy.biomeMode(BIOME));
        assertTrue(policy.allowsOres(DIMENSION, BIOME));
        assertFalse(policy.allowsAura(BIOME));
        assertTrue(policy.allowsStructures(DIMENSION));
    }

    @Test
    void snapshotDoesNotFollowSubsequentSourceListMutations() {
        List<String> dimensions = new ArrayList<>(List.of("example:dimension=0"));
        List<String> biomes = new ArrayList<>(List.of("example:biome=1"));
        WorldgenPolicy first = WorldgenPolicy.fromEntries(dimensions, biomes);
        dimensions.clear();
        biomes.set(0, "example:biome=-1");
        WorldgenPolicy second = WorldgenPolicy.fromEntries(dimensions, biomes);
        assertEquals(0, first.dimensionMode(DIMENSION));
        assertEquals(1, first.biomeMode(BIOME));
        assertFalse(first.allowsOres(DIMENSION, BIOME));
        assertFalse(first.allowsAura(BIOME));
        assertEquals(-1, second.dimensionMode(DIMENSION));
        assertEquals(-1, second.biomeMode(BIOME));
        assertTrue(second.allowsOres(DIMENSION, BIOME));
        assertTrue(second.allowsAura(BIOME));
    }

    @Test
    void validatorAcceptsExplicitRegistryKeysAndAllOriginalIntegerModes() {
        for (String entry : new String[]{
            "minecraft:overworld=0", "minecraft:plains=1", "example:folder/biome=2",
            "example:future_dimension=-1", "example:key=3", "example:key=-2",
            "example:key=-2147483648", "example:key=2147483647", " minecraft:plains = 1 "
        }) {
            assertTrue(WorldgenPolicy.isValidEntry(entry), entry);
        }
    }

    @Test
    void malformedAndNumericLegacyEntriesAreRejectedRatherThanSilentlyReinterpreted() {
        for (Object entry : new Object[]{
            "", "plains=0", "0:1", "0=1", "minecraft:=1", ":plains=1", "minecraft:plains:extra=1",
            "minecraft:Plains=1", "minecraft:pl ains=1", "minecraft:plains", "minecraft:plains=",
            "minecraft:plains=1=2", "minecraft:plains=1.0", "minecraft:plains=word",
            "minecraft:plains=2147483648", "minecraft:plains=-2147483649", 1, true
        }) {
            assertFalse(WorldgenPolicy.isValidEntry(entry), String.valueOf(entry));
            assertThrows(IllegalArgumentException.class, () -> WorldgenPolicy.fromEntries(List.of(entry), List.of()));
            assertThrows(IllegalArgumentException.class, () -> WorldgenPolicy.fromEntries(List.of(), List.of(entry)));
        }
        assertFalse(WorldgenPolicy.isValidEntry(null));
    }
}
