package thaumcraft.common.lib.utils;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.function.BiConsumer;

/**
 * Source-derived TC6 loot data and random decisions, not a runtime drop provider.
 *
 * <p>The base pools describe TC6 with vanilla 1.12.2 potion types, before addon
 * registrations. Legacy metadata is deliberately retained: it cannot be resolved
 * as modern item damage indiscriminately. In particular baubles and golden apples
 * use variants, whereas primordial pearls and equipment use damage.</p>
 *
 * <p>There is intentionally no ItemStack resolver or default enchantment callback.
 * Missing TC items must not be omitted, replaced, or treated as empty gear (which
 * would retry and change the distribution). A future adapter must implement the
 * old enchantment catalog, eligibility, weights and random consumption; calling
 * the modern enchantment helper or enumerating modern potions is not equivalent.</p>
 */
public final class LegacyLootRules {
    public enum Rarity {
        COMMON(0), UNCOMMON(1), RARE(2);

        private final int index;

        Rarity(int index) {
            this.index = index;
        }

        public int index() {
            return index;
        }
    }

    /** Slot order is the nextInt(5) order used by Utils.genGear, not modern equipment ordinals. */
    public enum GearSlot {
        WEAPON, BOOTS, LEGGINGS, CHESTPLATE, HELMET
    }

    /** potionType is a legacy registry key, or null for an ordinary item reward. */
    public record Reward(String itemId, int count, int legacyDamage, String potionType) {
        public Reward {
            Objects.requireNonNull(itemId, "itemId");
            if (count <= 0 || legacyDamage < 0) {
                throw new IllegalArgumentException("Invalid legacy reward stack");
            }
        }
    }

    public record Entry(Reward reward, int weight) {
        public Entry {
            Objects.requireNonNull(reward, "reward");
            if (weight <= 0) {
                throw new IllegalArgumentException("Loot weight must be positive");
            }
        }
    }

    public record GearItem(String itemId, int maxDamage, int enchantability) {}

    /**
     * Both old call sites passed allowTreasure=false. BOOK's returned enchanted
     * stack was ignored, so discardResult=true still requires consuming every
     * old enchantment random draw, but must not turn the reward into an enchanted book.
     */
    public record EnchantmentRequest(Reward reward, int power, int enchantability, boolean discardResult) {
        public boolean allowTreasure() {
            return false;
        }
    }

    /** enchantment is null when no old helper invocation occurred. */
    public record Roll(Reward reward, boolean gear, EnchantmentRequest enchantment) {}

    private static final float[] QUALITY_UPGRADE_CHANCES = {0.2F, 0.15F, 0.1F, 0.095F, 0.095F};

    // Exact vanilla 1.12.2 registry order, including the uncraftable "empty" entry.
    // TC6 itself registered effects, but no additional PotionType entries.
    public static final List<String> VANILLA_POTION_TYPES = List.of(
        "empty", "water", "mundane", "thick", "awkward",
        "night_vision", "long_night_vision", "invisibility", "long_invisibility",
        "leaping", "long_leaping", "strong_leaping", "fire_resistance", "long_fire_resistance",
        "swiftness", "long_swiftness", "strong_swiftness", "slowness", "long_slowness",
        "water_breathing", "long_water_breathing", "healing", "strong_healing", "harming", "strong_harming",
        "poison", "long_poison", "strong_poison", "regeneration", "long_regeneration", "strong_regeneration",
        "strength", "long_strength", "strong_strength", "weakness", "long_weakness", "luck"
    ).stream().map(name -> "minecraft:" + name).toList();

    private static final Map<Rarity, List<Entry>> POOLS = createPools();
    private static final GearItem[][] GEAR = createGear();

    private LegacyLootRules() {}

    public static List<Entry> pool(Rarity rarity) {
        return POOLS.get(Objects.requireNonNull(rarity, "rarity"));
    }

    public static int totalWeight(Rarity rarity) {
        return pool(rarity).stream().mapToInt(Entry::weight).sum();
    }

    /** BlockLoot: common 1..3, uncommon 2..4, rare 3..5, with no fortune adjustment. */
    public static int containerDrawCount(Rarity rarity, Random random) {
        return 1 + rarity.index() + random.nextInt(3);
    }

    /** Old Block.dropBlockAsItemWithChance filters whole reward stacks with an inclusive comparison. */
    public static boolean survivesExplosion(float radius, float randomDraw) {
        return randomDraw <= 1.0F / radius;
    }

    /** Implements 1.12 WeightedRandom's ordered half-open ticket ranges. */
    public static Entry entryAt(Rarity rarity, int ticket) {
        if (ticket < 0 || ticket >= totalWeight(rarity)) {
            throw new IllegalArgumentException("Ticket outside legacy loot pool");
        }
        for (Entry entry : pool(rarity)) {
            ticket -= entry.weight();
            if (ticket < 0) {
                return entry;
            }
        }
        throw new AssertionError("Unreachable weighted ticket");
    }

    public static GearItem gearItem(GearSlot slot, int quality) {
        if (quality < 0 || quality > 6) {
            throw new IllegalArgumentException("Gear quality outside 0..6");
        }
        return GEAR[Objects.requireNonNull(slot, "slot").ordinal()][quality];
    }

    public static int rollGearQuality(Random random) {
        int quality = random.nextInt(2);
        for (float chance : QUALITY_UPGRADE_CHANCES) {
            if (random.nextFloat() < chance) {
                quality++;
            }
        }
        return quality;
    }

    /**
     * Audits one Utils.generateLoot decision using the same Random instance.
     * The mandatory callback consumes old EnchantmentHelper draws immediately,
     * before a subsequent loot roll. Its generated enchantments are not modeled
     * here; this method alone must not be used to supply real gameplay drops.
     */
    public static Roll roll(Rarity rarity, Random random,
                            BiConsumer<Random, EnchantmentRequest> enchantmentDraws) {
        return roll(rarity, random, enchantmentDraws, (kind, rng) -> entryAt(kind, rng.nextInt(totalWeight(kind))).reward());
    }

    public static Roll roll(Rarity rarity, Random random,
            BiConsumer<Random, EnchantmentRequest> enchantmentDraws,
            java.util.function.BiFunction<Rarity, Random, Reward> chooseReward) {
        Objects.requireNonNull(rarity, "rarity");
        Objects.requireNonNull(random, "random");
        Objects.requireNonNull(enchantmentDraws, "enchantmentDraws");
        int rarityIndex = rarity.index();
        if (rarityIndex > 0 && random.nextFloat() < 0.025F * rarityIndex) {
            int quality = rollGearQuality(random);
            GearItem item = gearItem(GearSlot.values()[random.nextInt(5)], quality);
            Reward reward = new Reward(item.itemId(), 1, random.nextInt(1 + item.maxDamage() / 6), null);
            EnchantmentRequest request = null;
            if (random.nextInt(4) < rarityIndex) {
                request = enchant(reward, rarityIndex, item.enchantability(), false, random, enchantmentDraws);
            }
            // Every slot/quality in the original range has an item: genGear's
            // empty-item recursive retry cannot occur in this complete base matrix.
            return new Roll(reward, true, request);
        }

        Reward reward = chooseReward.apply(rarity, random);
        EnchantmentRequest request = reward.itemId().equals("minecraft:book")
            ? enchant(reward, rarityIndex, 1, true, random, enchantmentDraws) : null;
        return new Roll(reward, false, request);
    }

    private static EnchantmentRequest enchant(Reward reward, int rarity, int enchantability, boolean discarded,
                                              Random random, BiConsumer<Random, EnchantmentRequest> draws) {
        // Even common books consume nextInt(18), although their power is always 5.
        int power = (int) (5.0F + rarity * 0.75F * random.nextInt(18));
        EnchantmentRequest request = new EnchantmentRequest(reward, power, enchantability, discarded);
        draws.accept(random, request);
        return request;
    }

    private static Map<Rarity, List<Entry>> createPools() {
        Map<Rarity, List<Entry>> pools = new EnumMap<>(Rarity.class);
        for (Rarity rarity : Rarity.values()) {
            pools.put(rarity, new ArrayList<>());
        }
        add(pools, "minecraft:gold_nugget", 1, 0, 2500, Rarity.COMMON);
        add(pools, "minecraft:gold_nugget", 2, 0, 2250, Rarity.UNCOMMON);
        add(pools, "minecraft:gold_nugget", 3, 0, 2000, Rarity.RARE);
        add(pools, "thaumcraft:salis_mundus", 1, 0, 3, Rarity.COMMON);
        add(pools, "thaumcraft:salis_mundus", 1, 0, 6, Rarity.UNCOMMON);
        add(pools, "thaumcraft:salis_mundus", 1, 0, 9, Rarity.RARE);
        for (String item : List.of("chorus_fruit", "compass", "cookie")) {
            add(pools, "minecraft:" + item, 1, 0, 5, Rarity.values());
        }
        add(pools, "thaumcraft:primordial_pearl", 1, 7, 1, Rarity.COMMON);
        add(pools, "thaumcraft:primordial_pearl", 1, 7, 3, Rarity.UNCOMMON);
        add(pools, "thaumcraft:primordial_pearl", 1, 6, 1, Rarity.UNCOMMON);
        add(pools, "thaumcraft:primordial_pearl", 1, 5, 9, Rarity.RARE);
        add(pools, "thaumcraft:primordial_pearl", 1, 3, 3, Rarity.RARE);
        add(pools, "thaumcraft:primordial_pearl", 1, 0, 1, Rarity.RARE);
        add(pools, "minecraft:nether_star", 1, 0, 1, Rarity.RARE);
        add(pools, "minecraft:diamond", 1, 0, 10, Rarity.COMMON);
        add(pools, "minecraft:diamond", 1, 0, 50, Rarity.UNCOMMON, Rarity.RARE);
        add(pools, "minecraft:emerald", 1, 0, 15, Rarity.COMMON);
        add(pools, "minecraft:emerald", 1, 0, 75, Rarity.UNCOMMON, Rarity.RARE);
        add(pools, "minecraft:gold_ingot", 1, 0, 100, Rarity.values());
        add(pools, "minecraft:ender_pearl", 1, 0, 100, Rarity.values());
        add(pools, "thaumcraft:amulet_vis", 1, 0, 6, Rarity.UNCOMMON, Rarity.RARE);
        for (int metadata = 0; metadata < 3; metadata++) {
            add(pools, "thaumcraft:baubles", 1, metadata, 10, Rarity.COMMON);
        }
        add(pools, "thaumcraft:baubles", 1, 3, 5, Rarity.RARE);
        for (int metadata = 4; metadata < 7; metadata++) {
            add(pools, "thaumcraft:baubles", 1, metadata, 5, Rarity.UNCOMMON);
        }
        add(pools, "minecraft:experience_bottle", 1, 0, 5, Rarity.COMMON);
        add(pools, "minecraft:experience_bottle", 1, 0, 10, Rarity.UNCOMMON);
        add(pools, "minecraft:experience_bottle", 1, 0, 20, Rarity.RARE);
        add(pools, "minecraft:golden_apple", 1, 1, 1, Rarity.COMMON);
        add(pools, "minecraft:golden_apple", 1, 1, 2, Rarity.UNCOMMON);
        add(pools, "minecraft:golden_apple", 1, 1, 3, Rarity.RARE);
        add(pools, "minecraft:golden_apple", 1, 0, 3, Rarity.COMMON);
        add(pools, "minecraft:golden_apple", 1, 0, 6, Rarity.UNCOMMON);
        add(pools, "minecraft:golden_apple", 1, 0, 9, Rarity.RARE);
        add(pools, "minecraft:book", 1, 0, 10, Rarity.values());
        for (String potion : VANILLA_POTION_TYPES) {
            add(pools, new Reward("minecraft:potion", 1, 0, potion), 2, Rarity.values());
            add(pools, new Reward("minecraft:splash_potion", 1, 0, potion), 2, Rarity.values());
            add(pools, new Reward("minecraft:lingering_potion", 1, 0, potion), 2, Rarity.UNCOMMON, Rarity.RARE);
        }
        // The three local commonLoot arrays in old postInitLoot were never read.
        // They are not a second pool and must not add loot bags or thaumonomicons.
        pools.replaceAll((rarity, entries) -> List.copyOf(entries));
        return Map.copyOf(pools);
    }

    private static void add(Map<Rarity, List<Entry>> pools, String item, int count, int metadata,
                            int weight, Rarity... rarities) {
        add(pools, new Reward(item, count, metadata, null), weight, rarities);
    }

    private static void add(Map<Rarity, List<Entry>> pools, Reward reward, int weight, Rarity... rarities) {
        Entry entry = new Entry(reward, weight);
        for (Rarity rarity : rarities) {
            pools.get(rarity).add(entry);
        }
    }

    private static GearItem[][] createGear() {
        GearItem[][] gear = new GearItem[5][7];
        String[] weapons = {"minecraft:iron_axe", "minecraft:iron_sword", "minecraft:golden_axe",
            "minecraft:golden_sword", "thaumcraft:thaumium_sword", "minecraft:diamond_sword", "thaumcraft:void_sword"};
        int[] weaponDurability = {250, 250, 32, 32, 500, 1561, 150};
        int[] weaponEnchantability = {14, 14, 22, 22, 22, 10, 10};
        String[] materials = {"leather", "golden", "chainmail", "iron", "thaumium", "diamond", "void"};
        int[] armorFactors = {5, 7, 15, 15, 25, 33, 10};
        int[] armorEnchantability = {15, 25, 12, 9, 25, 10, 10};
        String[] vanillaSuffixes = {"boots", "leggings", "chestplate", "helmet"};
        String[] tcSuffixes = {"boots", "legs", "chest", "helm"};
        int[] armorDurabilityBase = {13, 15, 16, 11};
        for (int quality = 0; quality < 7; quality++) {
            gear[0][quality] = new GearItem(weapons[quality], weaponDurability[quality], weaponEnchantability[quality]);
            boolean tc = quality == 4 || quality == 6;
            for (int slot = 1; slot < 5; slot++) {
                String item = (tc ? "thaumcraft:" : "minecraft:") + materials[quality] + "_"
                    + (tc ? tcSuffixes[slot - 1] : vanillaSuffixes[slot - 1]);
                gear[slot][quality] = new GearItem(item, armorDurabilityBase[slot - 1] * armorFactors[quality],
                    armorEnchantability[quality]);
            }
        }
        return gear;
    }
}
