package thaumcraft.common.lib.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import thaumcraft.common.lib.utils.LegacyLootRules.EnchantmentRequest;
import thaumcraft.common.lib.utils.LegacyLootRules.Reward;

/**
 * The 1.12.2 enchanting-table selection rules used by TC6 loot, independent of
 * modern enchantment data packs, registry order and RandomSource algorithms.
 * Actual effects are applied through the corresponding modern enchantment holder.
 */
public final class LegacyLootEnchantments {
    public enum Profile {
        BOOK, AXE, SWORD, BOOTS, LEGGINGS, CHESTPLATE, HELMET;

        public boolean armor() {
            return this == BOOTS || this == LEGGINGS || this == CHESTPLATE || this == HELMET;
        }
    }

    private enum Target {
        ARMOR, FEET, HEAD, CHEST, SWORD, AXE, BREAKABLE, BOW, FISHING, WEARABLE
    }

    /** Old registry key (sweeping, not sweeping_edge); min/max costs are inclusive. */
    public record Definition(String id, int weight, int maxLevel, int minBase, int minStep,
                             int maxBase, int maxStep, boolean treasure, Target target) {
        public int minCost(int level) {
            return minBase + (level - 1) * minStep;
        }

        public int maxCost(int level) {
            return maxBase + (level - 1) * maxStep;
        }

        public String modernId() {
            return "minecraft:" + (id.equals("sweeping") ? "sweeping_edge" : id);
        }

        public boolean supports(Profile profile) {
            if (profile == Profile.BOOK) {
                return true;
            }
            return switch (target) {
                case ARMOR, WEARABLE -> profile.armor();
                case FEET -> profile == Profile.BOOTS;
                case HEAD -> profile == Profile.HELMET;
                case CHEST -> profile == Profile.CHESTPLATE;
                case SWORD -> profile == Profile.SWORD;
                case AXE -> profile == Profile.AXE;
                case BREAKABLE -> true;
                case BOW, FISHING -> false;
            };
        }
    }

    public record Selection(Definition enchantment, int level) {}

    // Numeric 1.12 registry order, including gaps between armor, weapon, tool and bow IDs.
    public static final List<Definition> DEFINITIONS = List.of(
        def("protection", 10, 4, 1, 11, 12, 11, Target.ARMOR),
        def("fire_protection", 5, 4, 10, 8, 18, 8, Target.ARMOR),
        def("feather_falling", 5, 4, 5, 6, 11, 6, Target.FEET),
        def("blast_protection", 2, 4, 5, 8, 13, 8, Target.ARMOR),
        def("projectile_protection", 5, 4, 3, 6, 9, 6, Target.ARMOR),
        def("respiration", 2, 3, 10, 10, 40, 10, Target.HEAD),
        def("aqua_affinity", 2, 1, 1, 0, 41, 0, Target.HEAD),
        def("thorns", 1, 3, 10, 20, 61, 10, Target.CHEST),
        def("depth_strider", 2, 3, 10, 10, 25, 10, Target.FEET),
        treasure("frost_walker", 2, 2, 10, 10, 25, 10, Target.FEET),
        treasure("binding_curse", 1, 1, 25, 0, 50, 0, Target.WEARABLE),
        def("sharpness", 10, 5, 1, 11, 21, 11, Target.SWORD),
        def("smite", 5, 5, 5, 8, 25, 8, Target.SWORD),
        def("bane_of_arthropods", 5, 5, 5, 8, 25, 8, Target.SWORD),
        def("knockback", 5, 2, 5, 20, 61, 10, Target.SWORD),
        def("fire_aspect", 2, 2, 10, 20, 61, 10, Target.SWORD),
        def("looting", 2, 3, 15, 9, 61, 10, Target.SWORD),
        def("sweeping", 2, 3, 5, 9, 20, 9, Target.SWORD),
        def("efficiency", 10, 5, 1, 10, 61, 10, Target.AXE),
        def("silk_touch", 1, 1, 15, 0, 61, 10, Target.AXE),
        def("unbreaking", 5, 3, 5, 8, 61, 10, Target.BREAKABLE),
        def("fortune", 2, 3, 15, 9, 61, 10, Target.AXE),
        def("power", 10, 5, 1, 10, 16, 10, Target.BOW),
        def("punch", 2, 2, 12, 20, 37, 20, Target.BOW),
        def("flame", 2, 1, 20, 0, 50, 0, Target.BOW),
        def("infinity", 1, 1, 20, 0, 50, 0, Target.BOW),
        def("luck_of_the_sea", 2, 3, 15, 9, 61, 10, Target.FISHING),
        def("lure", 2, 3, 15, 9, 61, 10, Target.FISHING),
        treasure("mending", 2, 1, 25, 25, 75, 25, Target.BREAKABLE),
        treasure("vanishing_curse", 1, 1, 25, 0, 50, 0, Target.BREAKABLE)
    );

    private LegacyLootEnchantments() {}

    public static Profile profile(Reward reward) {
        String item = reward.itemId();
        if (item.equals("minecraft:book")) return Profile.BOOK;
        if (item.endsWith("_axe")) return Profile.AXE;
        if (item.endsWith("_sword")) return Profile.SWORD;
        if (item.endsWith("_boots")) return Profile.BOOTS;
        if (item.endsWith("_leggings") || item.endsWith("_legs")) return Profile.LEGGINGS;
        if (item.endsWith("_chestplate") || item.endsWith("_chest")) return Profile.CHESTPLATE;
        if (item.endsWith("_helmet") || item.endsWith("_helm")) return Profile.HELMET;
        throw new IllegalArgumentException("Not a legacy loot enchantment target: " + item);
    }

    /** Includes only the highest eligible level of each definition, as the old helper did. */
    public static List<Selection> candidates(Profile profile, int power, boolean allowTreasure) {
        List<Selection> candidates = new ArrayList<>();
        for (Definition definition : DEFINITIONS) {
            if ((!definition.treasure() || allowTreasure) && definition.supports(profile)) {
                for (int level = definition.maxLevel(); level >= 1; level--) {
                    if (power >= definition.minCost(level) && power <= definition.maxCost(level)) {
                        candidates.add(new Selection(definition, level));
                        break;
                    }
                }
            }
        }
        return candidates;
    }

    public static List<Selection> select(Random random, EnchantmentRequest request) {
        List<Selection> selected = new ArrayList<>();
        int enchantability = request.enchantability();
        if (enchantability <= 0) {
            return List.of();
        }
        int bound = enchantability / 4 + 1;
        int power = request.power() + 1 + random.nextInt(bound) + random.nextInt(bound);
        float variation = (random.nextFloat() + random.nextFloat() - 1.0F) * 0.15F;
        power = Math.max(1, Math.round((float) power + (float) power * variation));
        List<Selection> candidates = candidates(profile(request.reward()), power, request.allowTreasure());
        if (!candidates.isEmpty()) {
            selected.add(weighted(random, candidates));
            while (random.nextInt(50) <= power) {
                Definition last = selected.getLast().enchantment();
                candidates.removeIf(candidate -> !compatible(last, candidate.enchantment()));
                if (candidates.isEmpty()) {
                    break;
                }
                selected.add(weighted(random, candidates));
                power /= 2;
            }
        }
        return List.copyOf(selected);
    }

    public static boolean compatible(Definition first, Definition second) {
        String a = first.id();
        String b = second.id();
        if (a.equals(b)) return false;
        if (protection(a) && protection(b)) return a.equals("feather_falling") || b.equals("feather_falling");
        if (damage(a) && damage(b)) return false;
        if (a.equals("silk_touch") && lootBonus(b) || b.equals("silk_touch") && lootBonus(a)) return false;
        if (pair(a, b, "depth_strider", "frost_walker")) return false;
        return !pair(a, b, "infinity", "mending");
    }

    private static Selection weighted(Random random, List<Selection> candidates) {
        int total = candidates.stream().mapToInt(candidate -> candidate.enchantment().weight()).sum();
        int ticket = random.nextInt(total);
        for (Selection candidate : candidates) {
            ticket -= candidate.enchantment().weight();
            if (ticket < 0) return candidate;
        }
        throw new AssertionError("Unreachable old enchantment ticket");
    }

    private static boolean protection(String id) {
        return id.equals("protection") || id.equals("fire_protection") || id.equals("feather_falling")
            || id.equals("blast_protection") || id.equals("projectile_protection");
    }

    private static boolean damage(String id) {
        return id.equals("sharpness") || id.equals("smite") || id.equals("bane_of_arthropods");
    }

    private static boolean lootBonus(String id) {
        return id.equals("looting") || id.equals("fortune") || id.equals("luck_of_the_sea");
    }

    private static boolean pair(String a, String b, String first, String second) {
        return a.equals(first) && b.equals(second) || a.equals(second) && b.equals(first);
    }

    private static Definition def(String id, int weight, int levels, int min, int minStep,
                                  int max, int maxStep, Target target) {
        return new Definition(id, weight, levels, min, minStep, max, maxStep, false, target);
    }

    private static Definition treasure(String id, int weight, int levels, int min, int minStep,
                                       int max, int maxStep, Target target) {
        return new Definition(id, weight, levels, min, minStep, max, maxStep, true, target);
    }
}
