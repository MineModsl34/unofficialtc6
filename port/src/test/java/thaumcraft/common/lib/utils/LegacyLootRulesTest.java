package thaumcraft.common.lib.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static thaumcraft.common.lib.utils.LegacyLootRules.Rarity.COMMON;
import static thaumcraft.common.lib.utils.LegacyLootRules.Rarity.RARE;
import static thaumcraft.common.lib.utils.LegacyLootRules.Rarity.UNCOMMON;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.Test;
import thaumcraft.common.lib.utils.LegacyLootRules.EnchantmentRequest;
import thaumcraft.common.lib.utils.LegacyLootRules.Entry;
import thaumcraft.common.lib.utils.LegacyLootRules.GearItem;
import thaumcraft.common.lib.utils.LegacyLootRules.GearSlot;
import thaumcraft.common.lib.utils.LegacyLootRules.Rarity;
import thaumcraft.common.lib.utils.LegacyLootRules.Reward;
import thaumcraft.common.lib.utils.LegacyLootRules.Roll;

class LegacyLootRulesTest {
    private static final BiConsumer<Random, EnchantmentRequest> NO_ENCHANTMENT = (random, request) -> {
        throw new AssertionError("Unexpected enchantment helper invocation");
    };

    @Test
    void basePoolWeightsAndCountsIncludeEveryOldRewardWithoutRenormalizing() {
        assertEquals(2941, LegacyLootRules.totalWeight(COMMON));
        assertEquals(2871, LegacyLootRules.totalWeight(UNCOMMON));
        assertEquals(2638, LegacyLootRules.totalWeight(RARE));
        assertEquals(91, LegacyLootRules.pool(COMMON).size());
        assertEquals(130, LegacyLootRules.pool(UNCOMMON).size());
        assertEquals(130, LegacyLootRules.pool(RARE).size());
        for (Rarity rarity : Rarity.values()) {
            assertThrows(UnsupportedOperationException.class, () -> LegacyLootRules.pool(rarity).clear());
            assertEquals(rarity.index() + 1, LegacyLootRules.pool(rarity).getFirst().reward().count());
            assertTrue(LegacyLootRules.pool(rarity).stream().noneMatch(entry ->
                entry.reward().itemId().equals("thaumcraft:loot_bag")
                    || entry.reward().itemId().equals("thaumcraft:thaumonomicon")));
        }
    }

    @Test
    void weightedTicketsUseOriginalInsertionOrderAndExactHalfOpenRanges() {
        for (Rarity rarity : Rarity.values()) {
            int ticket = 0;
            for (Entry entry : LegacyLootRules.pool(rarity)) {
                for (int offset = 0; offset < entry.weight(); offset++) {
                    assertSame(entry, LegacyLootRules.entryAt(rarity, ticket + offset));
                }
                ticket += entry.weight();
            }
            assertEquals(LegacyLootRules.totalWeight(rarity), ticket);
            assertThrows(IllegalArgumentException.class, () -> LegacyLootRules.entryAt(rarity, -1));
            assertThrows(IllegalArgumentException.class,
                () -> LegacyLootRules.entryAt(rarity, LegacyLootRules.totalWeight(rarity)));
        }
        assertEquals("minecraft:gold_nugget", LegacyLootRules.entryAt(COMMON, 2499).reward().itemId());
        assertEquals("thaumcraft:salis_mundus", LegacyLootRules.entryAt(COMMON, 2500).reward().itemId());
        assertEquals("minecraft:chorus_fruit", LegacyLootRules.entryAt(COMMON, 2503).reward().itemId());
    }

    @Test
    void pearlsBaublesAndGoldenApplesRetainLegacyVariantOrChargeValues() {
        assertEquals(List.of("7:1"), variants(COMMON, "thaumcraft:primordial_pearl"));
        assertEquals(List.of("7:3", "6:1"), variants(UNCOMMON, "thaumcraft:primordial_pearl"));
        assertEquals(List.of("5:9", "3:3", "0:1"), variants(RARE, "thaumcraft:primordial_pearl"));
        assertEquals(List.of("0:10", "1:10", "2:10"), variants(COMMON, "thaumcraft:baubles"));
        assertEquals(List.of("4:5", "5:5", "6:5"), variants(UNCOMMON, "thaumcraft:baubles"));
        assertEquals(List.of("3:5"), variants(RARE, "thaumcraft:baubles"));
        assertEquals(List.of("1:1", "0:3"), variants(COMMON, "minecraft:golden_apple"));
        assertEquals(List.of("1:2", "0:6"), variants(UNCOMMON, "minecraft:golden_apple"));
        assertEquals(List.of("1:3", "0:9"), variants(RARE, "minecraft:golden_apple"));
        assertTrue(variants(COMMON, "thaumcraft:amulet_vis").isEmpty());
        assertEquals(List.of("0:6"), variants(UNCOMMON, "thaumcraft:amulet_vis"));
        assertEquals(List.of("0:6"), variants(RARE, "thaumcraft:amulet_vis"));
    }

    @Test
    void oldPotionCatalogIncludesUncraftableAndKeepsPotionSplashLingeringOrder() {
        assertEquals(37, LegacyLootRules.VANILLA_POTION_TYPES.size());
        assertEquals("minecraft:empty", LegacyLootRules.VANILLA_POTION_TYPES.getFirst());
        assertEquals("minecraft:luck", LegacyLootRules.VANILLA_POTION_TYPES.getLast());
        assertFalse(LegacyLootRules.VANILLA_POTION_TYPES.contains("minecraft:strong_slowness"));
        assertFalse(LegacyLootRules.VANILLA_POTION_TYPES.contains("minecraft:turtle_master"));
        assertFalse(LegacyLootRules.VANILLA_POTION_TYPES.contains("minecraft:wind_charged"));
        for (Rarity rarity : Rarity.values()) {
            List<Entry> potions = LegacyLootRules.pool(rarity).stream()
                .filter(entry -> entry.reward().potionType() != null).toList();
            int forms = rarity == COMMON ? 2 : 3;
            assertEquals(37 * forms, potions.size());
            for (int type = 0; type < 37; type++) {
                for (int form = 0; form < forms; form++) {
                    Entry entry = potions.get(type * forms + form);
                    assertEquals(List.of("minecraft:potion", "minecraft:splash_potion", "minecraft:lingering_potion").get(form),
                        entry.reward().itemId());
                    assertEquals(LegacyLootRules.VANILLA_POTION_TYPES.get(type), entry.reward().potionType());
                    assertEquals(2, entry.weight());
                }
            }
        }
    }

    @Test
    void containerCountIgnoresFortuneAndRetainsOneInclusiveThreeValueDraw() {
        for (Rarity rarity : Rarity.values()) {
            for (int value = 0; value < 3; value++) {
                TraceRandom random = new TraceRandom().integer(3, value);
                assertEquals(1 + rarity.index() + value, LegacyLootRules.containerDrawCount(rarity, random));
                random.assertFinished();
            }
        }
    }

    @Test
    void tntExplosionPreservesWholeStackAtInclusiveQuarterChance() {
        assertTrue(LegacyLootRules.survivesExplosion(4, 0));
        assertTrue(LegacyLootRules.survivesExplosion(4, 0.25F));
        assertFalse(LegacyLootRules.survivesExplosion(4, Math.nextUp(0.25F)));
        assertFalse(LegacyLootRules.survivesExplosion(4, 0.999F));
        assertTrue(LegacyLootRules.survivesExplosion(1, 0.999F));
    }

    @Test
    void gearMatrixRetainsWeaponOdditiesAndOldDurabilityEnchantability() {
        String[] weapons = {"iron_axe", "iron_sword", "golden_axe", "golden_sword",
            "thaumium_sword", "diamond_sword", "void_sword"};
        int[] weaponDamage = {250, 250, 32, 32, 500, 1561, 150};
        int[] weaponEnchantability = {14, 14, 22, 22, 22, 10, 10};
        int[] helmetDamage = {55, 77, 165, 165, 275, 363, 110};
        int[] chestDamage = {80, 112, 240, 240, 400, 528, 160};
        int[] legsDamage = {75, 105, 225, 225, 375, 495, 150};
        int[] bootsDamage = {65, 91, 195, 195, 325, 429, 130};
        int[] armorEnchantability = {15, 25, 12, 9, 25, 10, 10};
        for (int quality = 0; quality < 7; quality++) {
            GearItem weapon = LegacyLootRules.gearItem(GearSlot.WEAPON, quality);
            String prefix = quality == 4 || quality == 6 ? "thaumcraft:" : "minecraft:";
            assertEquals(prefix + weapons[quality], weapon.itemId());
            assertEquals(weaponDamage[quality], weapon.maxDamage());
            assertEquals(weaponEnchantability[quality], weapon.enchantability());
            assertEquals(helmetDamage[quality], LegacyLootRules.gearItem(GearSlot.HELMET, quality).maxDamage());
            assertEquals(chestDamage[quality], LegacyLootRules.gearItem(GearSlot.CHESTPLATE, quality).maxDamage());
            assertEquals(legsDamage[quality], LegacyLootRules.gearItem(GearSlot.LEGGINGS, quality).maxDamage());
            assertEquals(bootsDamage[quality], LegacyLootRules.gearItem(GearSlot.BOOTS, quality).maxDamage());
            for (GearSlot slot : List.of(GearSlot.HELMET, GearSlot.CHESTPLATE, GearSlot.LEGGINGS, GearSlot.BOOTS)) {
                assertEquals(armorEnchantability[quality], LegacyLootRules.gearItem(slot, quality).enchantability());
            }
        }
        assertEquals("thaumcraft:thaumium_legs", LegacyLootRules.gearItem(GearSlot.LEGGINGS, 4).itemId());
        assertEquals("thaumcraft:void_chest", LegacyLootRules.gearItem(GearSlot.CHESTPLATE, 6).itemId());
        assertThrows(IllegalArgumentException.class, () -> LegacyLootRules.gearItem(GearSlot.HELMET, -1));
        assertThrows(IllegalArgumentException.class, () -> LegacyLootRules.gearItem(GearSlot.HELMET, 7));
    }

    @Test
    void qualityConsumesFiveIndependentStrictChanceChecksEvenWithoutUpgrades() {
        float[] thresholds = {0.2F, 0.15F, 0.1F, 0.095F, 0.095F};
        TraceRandom atBoundary = new TraceRandom().integer(2, 0);
        TraceRandom belowBoundary = new TraceRandom().integer(2, 1);
        for (float threshold : thresholds) {
            atBoundary.floating(threshold);
            belowBoundary.floating(Math.nextDown(threshold));
        }
        assertEquals(0, LegacyLootRules.rollGearQuality(atBoundary));
        assertEquals(6, LegacyLootRules.rollGearQuality(belowBoundary));
        atBoundary.assertFinished();
        belowBoundary.assertFinished();
        for (int upgrade = 0; upgrade < 5; upgrade++) {
            TraceRandom random = new TraceRandom().integer(2, 0);
            for (int check = 0; check < 5; check++) {
                random.floating(check == upgrade ? 0 : 1);
            }
            assertEquals(1, LegacyLootRules.rollGearQuality(random));
            random.assertFinished();
        }
    }

    @Test
    void commonDoesNotDrawGearGateAndHigherRaritiesUseStrictThreshold() {
        TraceRandom common = new TraceRandom().integer(2941, 0);
        Roll commonRoll = LegacyLootRules.roll(COMMON, common, NO_ENCHANTMENT);
        assertFalse(commonRoll.gear());
        assertEquals(new Reward("minecraft:gold_nugget", 1, 0, null), commonRoll.reward());
        common.assertFinished();
        for (Rarity rarity : List.of(UNCOMMON, RARE)) {
            TraceRandom random = new TraceRandom().floating(0.025F * rarity.index())
                .integer(LegacyLootRules.totalWeight(rarity), 0);
            Roll roll = LegacyLootRules.roll(rarity, random, NO_ENCHANTMENT);
            assertFalse(roll.gear());
            assertEquals(rarity.index() + 1, roll.reward().count());
            random.assertFinished();
        }
    }

    @Test
    void uncommonGearUsesLegacyDamageBoundAndDoesNotEnchantAtOne() {
        TraceRandom random = new TraceRandom().floating(Math.nextDown(0.025F)).integer(2, 0)
            .floating(1).floating(1).floating(1).floating(1).floating(1)
            .integer(5, 0).integer(42, 41).integer(4, 1);
        Roll roll = LegacyLootRules.roll(UNCOMMON, random, NO_ENCHANTMENT);
        assertTrue(roll.gear());
        assertEquals(new Reward("minecraft:iron_axe", 1, 41, null), roll.reward());
        assertNull(roll.enchantment());
        random.assertFinished();
    }

    @Test
    void rareGearUsesThaumiumHelmetQualityFourAndTruncatesEnchantmentPower() {
        TraceRandom random = new TraceRandom().floating(Math.nextDown(0.05F)).integer(2, 1)
            .floating(0).floating(0).floating(0).floating(1).floating(1)
            .integer(5, 4).integer(46, 45).integer(4, 1).integer(18, 17)
            .integer(7, 6);
        List<EnchantmentRequest> calls = new ArrayList<>();
        Roll roll = LegacyLootRules.roll(RARE, random, (sameRandom, request) -> {
            assertSame(random, sameRandom);
            assertEquals(30, request.power());
            assertEquals(25, request.enchantability());
            assertFalse(request.allowTreasure());
            assertFalse(request.discardResult());
            calls.add(request);
            // Deliberately mocked enchantment draws; this test asserts callback
            // timing, not an implementation of the old enchantment catalog.
            assertEquals(6, sameRandom.nextInt(7));
        });
        assertTrue(roll.gear());
        assertEquals(new Reward("thaumcraft:thaumium_helm", 1, 45, null), roll.reward());
        assertNotNull(roll.enchantment());
        assertEquals(List.of(roll.enchantment()), calls);
        random.assertFinished();
    }

    @Test
    void gearEnchantmentChanceIsOneOfFourForUncommonTwoOfFourForRare() {
        for (Rarity rarity : List.of(UNCOMMON, RARE)) {
            for (int draw = 0; draw < 4; draw++) {
                TraceRandom random = new TraceRandom().floating(0).integer(2, 1)
                    .floating(0).floating(0).floating(0).floating(0).floating(0)
                    .integer(5, 0).integer(26, 25).integer(4, draw);
                boolean expectedEnchant = draw < rarity.index();
                if (expectedEnchant) {
                    random.integer(18, 0);
                }
                List<EnchantmentRequest> requests = new ArrayList<>();
                Roll roll = LegacyLootRules.roll(rarity, random, (sameRandom, request) -> requests.add(request));
                assertEquals(new Reward("thaumcraft:void_sword", 1, 25, null), roll.reward());
                assertEquals(expectedEnchant ? 1 : 0, requests.size());
                assertEquals(expectedEnchant, roll.enchantment() != null);
                random.assertFinished();
            }
        }
    }

    @Test
    void bookHelperResultIsDiscardedButItsDrawsHappenBeforeNextReward() {
        TraceRandom random = new TraceRandom().integer(2941, 2783).integer(18, 17)
            .integer(1, 0).integer(1, 0).floating(0.5F).floating(0.5F)
            .integer(2941, 0);
        Roll book = LegacyLootRules.roll(COMMON, random, (sameRandom, request) -> {
            assertEquals("minecraft:book", request.reward().itemId());
            assertEquals(5, request.power());
            assertEquals(1, request.enchantability());
            assertTrue(request.discardResult());
            // The first four actual 1.12 helper draws for a book (enchantability1).
            // Candidate selection requires the not-yet-ported enchantment catalog.
            sameRandom.nextInt(1);
            sameRandom.nextInt(1);
            sameRandom.nextFloat();
            sameRandom.nextFloat();
        });
        assertEquals(new Reward("minecraft:book", 1, 0, null), book.reward());
        assertTrue(book.enchantment().discardResult());
        assertEquals("minecraft:gold_nugget", LegacyLootRules.roll(COMMON, random, NO_ENCHANTMENT).reward().itemId());
        random.assertFinished();
    }

    @Test
    void noDefaultEnchantmentConsumerCanSilentlyProduceUnenchantedGameplayGear() {
        assertThrows(NullPointerException.class, () -> LegacyLootRules.roll(COMMON, new Random(), null));
    }

    private static List<String> variants(Rarity rarity, String item) {
        return LegacyLootRules.pool(rarity).stream().filter(entry -> entry.reward().itemId().equals(item))
            .map(entry -> entry.reward().legacyDamage() + ":" + entry.weight()).toList();
    }

    private record Draw(char type, int bound, float value) {}

    /** No superclass draw is allowed: failures reveal even an extra or skipped random call. */
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
            assertEquals(draw.bound(), bound, "Integer draw bound");
            assertTrue(draw.value() >= 0 && draw.value() < bound, "Scripted integer in range");
            return (int) draw.value();
        }

        @Override
        public float nextFloat() {
            return take('f').value();
        }

        private Draw take(char type) {
            assertFalse(draws.isEmpty(), "Unexpected random draw");
            Draw draw = draws.removeFirst();
            assertEquals(type, draw.type(), "Random draw type");
            return draw;
        }

        void assertFinished() {
            assertTrue(draws.isEmpty(), "Unconsumed random draws: " + draws);
        }
    }
}
