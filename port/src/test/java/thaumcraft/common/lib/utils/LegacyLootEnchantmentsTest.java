package thaumcraft.common.lib.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import thaumcraft.common.lib.utils.LegacyLootEnchantments.Definition;
import thaumcraft.common.lib.utils.LegacyLootEnchantments.Profile;
import thaumcraft.common.lib.utils.LegacyLootEnchantments.Selection;
import thaumcraft.common.lib.utils.LegacyLootRules.EnchantmentRequest;
import thaumcraft.common.lib.utils.LegacyLootRules.Reward;

class LegacyLootEnchantmentsTest {
    @Test
    void onlyThirtyOldDefinitionsArePresentAndFourTreasureEnchantmentsAreExcluded() {
        assertEquals(30, LegacyLootEnchantments.DEFINITIONS.size());
        assertEquals(List.of("frost_walker", "binding_curse", "mending", "vanishing_curse"),
            LegacyLootEnchantments.DEFINITIONS.stream().filter(Definition::treasure).map(Definition::id).toList());
        for (int power = 1; power <= 100; power++) {
            assertTrue(LegacyLootEnchantments.candidates(Profile.BOOK, power, false).stream()
                .noneMatch(selection -> selection.enchantment().treasure()));
        }
        assertEquals("minecraft:sweeping_edge", definition("sweeping").modernId());
        assertTrue(LegacyLootEnchantments.DEFINITIONS.stream().noneMatch(definition ->
            List.of("swift_sneak", "soul_speed", "density", "breach", "wind_burst", "multishot", "impaling")
                .contains(definition.id())));
    }

    @Test
    void tableEligibilityKeepsForgeCanApplyVersusCanApplyAtEnchantingTableAsymmetry() {
        assertEquals(List.of("efficiency", "silk_touch", "unbreaking", "fortune"), ids(Profile.AXE, 30));
        assertEquals(List.of("sharpness", "smite", "bane_of_arthropods", "knockback", "fire_aspect", "looting", "sweeping", "unbreaking"),
            ids(Profile.SWORD, 30));
        assertFalse(ids(Profile.HELMET, 30).contains("thorns"));
        assertFalse(ids(Profile.BOOTS, 30).contains("thorns"));
        assertFalse(ids(Profile.LEGGINGS, 30).contains("thorns"));
        assertTrue(ids(Profile.CHESTPLATE, 30).contains("thorns"));
        assertTrue(ids(Profile.HELMET, 30).contains("aqua_affinity"));
        assertFalse(ids(Profile.CHESTPLATE, 30).contains("aqua_affinity"));
        assertTrue(ids(Profile.BOOTS, 30).contains("depth_strider"));
        assertFalse(ids(Profile.LEGGINGS, 30).contains("depth_strider"));
    }

    @Test
    void legacyCostBoundsIncludeSuperclassAndUnusedProtectionSpanQuirks() {
        assertEquals(12, definition("protection").maxCost(1));
        assertEquals(23, definition("protection").maxCost(2));
        assertEquals(10, definition("fire_protection").minCost(1));
        assertEquals(18, definition("fire_protection").maxCost(1));
        assertEquals(10, definition("thorns").minCost(1));
        assertEquals(61, definition("thorns").maxCost(1));
        assertEquals(50, definition("thorns").minCost(3));
        assertEquals(81, definition("thorns").maxCost(3));
        assertEquals(61, definition("silk_touch").maxCost(1));
        assertEquals(81, definition("unbreaking").maxCost(3));
        assertEquals(81, definition("fortune").maxCost(3));
        assertEquals(41, definition("efficiency").minCost(5));
        assertEquals(101, definition("efficiency").maxCost(5));
        assertEquals(List.of("protection", "feather_falling", "blast_protection", "projectile_protection", "aqua_affinity",
            "sharpness", "smite", "bane_of_arthropods", "knockback", "sweeping", "efficiency", "unbreaking", "power"), ids(Profile.BOOK, 6));
        Selection highest = LegacyLootEnchantments.candidates(Profile.SWORD, 30, false).stream()
            .filter(selection -> selection.enchantment().id().equals("smite")).findFirst().orElseThrow();
        assertEquals(4, highest.level());
    }

    @Test
    void incompatibilityRetainsFeatherFallingAndAllThreeLootBonusSilkExclusions() {
        for (Definition first : LegacyLootEnchantments.DEFINITIONS) {
            assertFalse(LegacyLootEnchantments.compatible(first, first));
            for (Definition second : LegacyLootEnchantments.DEFINITIONS) {
                assertEquals(LegacyLootEnchantments.compatible(first, second), LegacyLootEnchantments.compatible(second, first));
            }
        }
        assertFalse(compatible("protection", "fire_protection"));
        assertTrue(compatible("protection", "feather_falling"));
        assertFalse(compatible("sharpness", "smite"));
        for (String bonus : List.of("fortune", "looting", "luck_of_the_sea")) {
            assertFalse(compatible("silk_touch", bonus));
        }
        assertFalse(compatible("depth_strider", "frost_walker"));
        assertFalse(compatible("infinity", "mending"));
        assertTrue(compatible("fire_aspect", "looting"));
    }

    @Test
    void axeMultiEnchantHalvesPowerAfterEachExtraPickAndStopsAfterEmptyCandidateCheck() {
        TraceRandom random = new TraceRandom().integer(4, 0).integer(4, 0).floating(0.5F).floating(0.5F)
            .integer(18, 0).integer(50, 21).integer(8, 0).integer(50, 10).integer(5, 0).integer(50, 5);
        List<Selection> selected = LegacyLootEnchantments.select(random, request("minecraft:iron_axe", 20, 14));
        assertEquals(List.of("efficiency:3", "silk_touch:1", "unbreaking:3"), describe(selected));
        random.assertFinished();
    }

    @Test
    void bookUsesOldFullCatalogWeightAndStillConsumesContinuationDraw() {
        TraceRandom random = new TraceRandom().integer(1, 0).integer(1, 0).floating(0.5F).floating(0.5F)
            .integer(76, 0).integer(50, 49);
        assertEquals(List.of("protection:1"), describe(LegacyLootEnchantments.select(random,
            new EnchantmentRequest(new Reward("minecraft:book", 1, 0, null), 5, 1, true))));
        random.assertFinished();
    }

    @Test
    void upperVariationRoundsFloatPowerAndUsesInclusiveContinuationComparison() {
        TraceRandom random = new TraceRandom().integer(7, 6).integer(7, 6).floating(0.999F).floating(0.999F)
            .integer(7, 0).integer(50, 49).integer(5, 4).integer(50, 25);
        assertEquals(List.of("respiration:3", "unbreaking:3"), describe(LegacyLootEnchantments.select(random,
            request("thaumcraft:thaumium_helm", 30, 25))));
        random.assertFinished();
    }

    @Test
    void zeroEnchantabilityConsumesNoDrawAndNoCandidatesSkipWeightedAndContinuationDraws() {
        TraceRandom zero = new TraceRandom();
        assertTrue(LegacyLootEnchantments.select(zero, request("minecraft:iron_sword", 5, 0)).isEmpty());
        zero.assertFinished();
        TraceRandom noCandidates = new TraceRandom().integer(4, 0).integer(4, 0).floating(0.5F).floating(0.5F);
        assertTrue(LegacyLootEnchantments.select(noCandidates, request("minecraft:iron_sword", 200, 14)).isEmpty());
        noCandidates.assertFinished();
    }

    private static EnchantmentRequest request(String item, int power, int enchantability) {
        return new EnchantmentRequest(new Reward(item, 1, 0, null), power, enchantability, false);
    }

    private static Definition definition(String id) {
        return LegacyLootEnchantments.DEFINITIONS.stream().filter(definition -> definition.id().equals(id)).findFirst().orElseThrow();
    }

    private static boolean compatible(String a, String b) {
        return LegacyLootEnchantments.compatible(definition(a), definition(b));
    }

    private static List<String> ids(Profile profile, int power) {
        return LegacyLootEnchantments.candidates(profile, power, false).stream().map(selection -> selection.enchantment().id()).toList();
    }

    private static List<String> describe(List<Selection> selections) {
        return selections.stream().map(selection -> selection.enchantment().id() + ":" + selection.level()).toList();
    }

    private record Draw(char kind, int bound, float value) {}

    private static final class TraceRandom extends Random {
        private final ArrayDeque<Draw> draws = new ArrayDeque<>();

        TraceRandom integer(int bound, int value) {
            draws.add(new Draw('i', bound, value));
            return this;
        }

        TraceRandom floating(float value) {
            draws.add(new Draw('f', 0, value));
            return this;
        }

        @Override
        public int nextInt(int bound) {
            Draw draw = take('i');
            assertEquals(draw.bound(), bound);
            return (int) draw.value();
        }

        @Override
        public float nextFloat() {
            return take('f').value();
        }

        private Draw take(char kind) {
            assertFalse(draws.isEmpty(), "Unexpected random draw");
            Draw draw = draws.removeFirst();
            assertEquals(kind, draw.kind());
            return draw;
        }

        void assertFinished() {
            assertTrue(draws.isEmpty(), "Unconsumed draws: " + draws);
        }
    }
}
