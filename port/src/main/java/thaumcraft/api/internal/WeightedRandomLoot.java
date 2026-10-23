package thaumcraft.api.internal;

import java.util.ArrayList;
import java.util.Random;
import net.minecraft.util.random.Weight;
import net.minecraft.util.random.WeightedEntry;
import net.minecraft.world.item.ItemStack;
import thaumcraft.common.lib.utils.LegacyLootRules;
import thaumcraft.common.lib.utils.TCLegacyLoot;

/** Public mutable loot bag pools, with lazily resolved native default stacks and addon entries. */
public class WeightedRandomLoot implements WeightedEntry {
    public ItemStack item;
    public int itemWeight;
    public static ArrayList<WeightedRandomLoot> lootBagCommon = new ArrayList<>();
    public static ArrayList<WeightedRandomLoot> lootBagUncommon = new ArrayList<>();
    public static ArrayList<WeightedRandomLoot> lootBagRare = new ArrayList<>();
    private static boolean defaultsReady;

    public WeightedRandomLoot(ItemStack stack, int weight) {
        if (stack == null || weight < 0) throw new IllegalArgumentException("Invalid loot bag entry");
        item = stack; itemWeight = weight;
    }
    @Override public Weight getWeight() { return Weight.of(itemWeight); }
    public static ArrayList<WeightedRandomLoot> pool(LegacyLootRules.Rarity rarity) {
        return switch (rarity) { case COMMON -> lootBagCommon; case UNCOMMON -> lootBagUncommon; case RARE -> lootBagRare; };
    }
    public static synchronized void initializeDefaults(TCLegacyLoot resolver) {
        if (defaultsReady) return;
        for (var rarity : LegacyLootRules.Rarity.values()) {
            ArrayList<WeightedRandomLoot> defaults = new ArrayList<>();
            for (var entry : LegacyLootRules.pool(rarity)) defaults.add(new WeightedRandomLoot(resolver.materialize(entry.reward()), entry.weight()));
            pool(rarity).addAll(defaults);
        }
        defaultsReady = true;
    }
    public static WeightedRandomLoot choose(LegacyLootRules.Rarity rarity, Random random) {
        int total = 0;
        for (var entry : pool(rarity)) total = Math.addExact(total, entry.itemWeight);
        if (total <= 0) throw new IllegalStateException("Empty weighted loot bag pool");
        int ticket = random.nextInt(total);
        for (var entry : pool(rarity)) if ((ticket -= entry.itemWeight) < 0) return entry;
        throw new IllegalStateException("Loot bag pool changed during selection");
    }
}
