package thaumcraft.common.lib.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.enchantment.Enchantment;
import thaumcraft.common.lib.utils.LegacyLootEnchantments.Definition;
import thaumcraft.common.lib.utils.LegacyLootEnchantments.Selection;
import thaumcraft.common.lib.utils.LegacyLootRules.GearItem;
import thaumcraft.common.lib.utils.LegacyLootRules.GearSlot;
import thaumcraft.common.lib.utils.LegacyLootRules.Rarity;
import thaumcraft.common.lib.utils.LegacyLootRules.Reward;
import thaumcraft.common.lib.utils.LegacyLootRules.Roll;

/** Complete base TC6 loot adapter; construction rejects missing rewards before any random draw. */
public final class TCLegacyLoot {
    private static final List<String> BAUBLE_VARIANTS = List.of(
        "amulet_mundane", "ring_mundane", "girdle_mundane", "ring_apprentice",
        "amulet_fancy", "ring_fancy", "girdle_fancy");

    private final Map<String, Item> items;
    private final Map<String, Holder<Potion>> potions;
    private final Map<String, Holder<Enchantment>> enchantments;

    private TCLegacyLoot(Map<String, Item> items, Map<String, Holder<Potion>> potions,
                         Map<String, Holder<Enchantment>> enchantments) {
        this.items = Map.copyOf(items);
        this.potions = Map.copyOf(potions);
        this.enchantments = Map.copyOf(enchantments);
    }

    /**
     * Deliberately no fallback to registry AIR, vanilla equipment, or shorter pools.
     * Enchantment holders are resolved from the current world registry snapshot,
     * but old selection costs/eligibility remain LegacyLootEnchantments' rules.
     */
    public static TCLegacyLoot create(HolderLookup.Provider registries) {
        Map<String, Item> items = new HashMap<>();
        for (String id : requiredItemIds()) {
            ResourceLocation key = ResourceLocation.parse(id);
            Item item = BuiltInRegistries.ITEM.getOptional(key).orElseThrow(
                () -> new IllegalStateException("Missing complete TC6 loot reward: " + id));
            items.put(id, item);
        }
        for (GearSlot slot : GearSlot.values()) {
            for (int quality = 0; quality < 7; quality++) {
                GearItem gear = LegacyLootRules.gearItem(slot, quality);
                ItemStack stack = new ItemStack(items.get(gear.itemId()));
                if (stack.getMaxDamage() != gear.maxDamage()
                    || stack.getItem().getEnchantmentValue(stack) != gear.enchantability()) {
                    throw new IllegalStateException("Loot gear differs from TC6 durability/enchantability: " + gear.itemId());
                }
            }
        }
        ItemStack pearl = new ItemStack(items.get("thaumcraft:primordial_pearl"));
        if (pearl.getMaxDamage() != 8 || pearl.getMaxStackSize() != 1) {
            throw new IllegalStateException("Primordial pearl must retain TC6 damage charges and stack size");
        }

        Map<String, Holder<Potion>> potions = new HashMap<>();
        HolderLookup.RegistryLookup<Potion> potionRegistry = registries.lookupOrThrow(Registries.POTION);
        for (String id : LegacyLootRules.VANILLA_POTION_TYPES) {
            if (!id.equals("minecraft:empty")) {
                potions.put(id, potionRegistry.getOrThrow(ResourceKey.create(Registries.POTION, ResourceLocation.parse(id))));
            }
        }
        Map<String, Holder<Enchantment>> enchantments = new HashMap<>();
        HolderLookup.RegistryLookup<Enchantment> enchantmentRegistry = registries.lookupOrThrow(Registries.ENCHANTMENT);
        for (Definition definition : LegacyLootEnchantments.DEFINITIONS) {
            enchantments.put(definition.id(), enchantmentRegistry.getOrThrow(
                ResourceKey.create(Registries.ENCHANTMENT, ResourceLocation.parse(definition.modernId()))));
        }
        return new TCLegacyLoot(items, potions, enchantments);
    }

    public static Set<String> requiredItemIds() {
        Set<String> required = new LinkedHashSet<>();
        for (Rarity rarity : Rarity.values()) {
            LegacyLootRules.pool(rarity).forEach(entry -> required.add(modernItemId(entry.reward())));
        }
        for (GearSlot slot : GearSlot.values()) {
            for (int quality = 0; quality < 7; quality++) {
                required.add(LegacyLootRules.gearItem(slot, quality).itemId());
            }
        }
        return Set.copyOf(required);
    }

    public static String modernItemId(Reward reward) {
        if (reward.itemId().equals("thaumcraft:baubles")) {
            if (reward.legacyDamage() >= BAUBLE_VARIANTS.size()) {
                throw new IllegalArgumentException("Unknown TC6 bauble metadata: " + reward.legacyDamage());
            }
            return "thaumcraft:" + BAUBLE_VARIANTS.get(reward.legacyDamage());
        }
        if (reward.itemId().equals("minecraft:golden_apple")) {
            if (reward.legacyDamage() > 1) {
                throw new IllegalArgumentException("Unknown golden apple metadata: " + reward.legacyDamage());
            }
            if (reward.legacyDamage() == 1) return "minecraft:enchanted_golden_apple";
        }
        if (reward.itemId().equals("thaumcraft:amulet_vis") && reward.legacyDamage() != 0) {
            throw new IllegalArgumentException("Loot contains only the found vis amulet variant");
        }
        return reward.itemId();
    }

    public ItemStack generate(Rarity rarity, Random random) {
        thaumcraft.api.internal.WeightedRandomLoot.initializeDefaults(this);
        ItemStack[] chosenStack = new ItemStack[1];
        List<Selection> selected = new ArrayList<>();
        Roll roll = LegacyLootRules.roll(rarity, random,
            (sameRandom, request) -> selected.addAll(LegacyLootEnchantments.select(sameRandom, request)),
            (kind, rng) -> {
                chosenStack[0] = thaumcraft.api.internal.WeightedRandomLoot.choose(kind, rng).item.copy();
                return new Reward(BuiltInRegistries.ITEM.getKey(chosenStack[0].getItem()).toString(),
                    Math.max(1, chosenStack[0].getCount()), chosenStack[0].getDamageValue(), null);
            });
        ItemStack stack = chosenStack[0] == null ? materialize(roll.reward()) : chosenStack[0];
        if (roll.enchantment() != null && !roll.enchantment().discardResult()) {
            for (Selection selection : selected) {
                stack.enchant(enchantments.get(selection.enchantment().id()), selection.level());
            }
        }
        // Source quirk: old addRandomEnchantment returns a NEW enchanted book,
        // ignored by Utils.generateLoot. Selection still consumes all its RNG.
        return stack.copy();
    }

    /** Materializes descriptor components without consuming randomness. */
    public ItemStack materialize(Reward reward) {
        String id = modernItemId(reward);
        Item item = items.get(id);
        if (item == null) {
            throw new IllegalArgumentException("Not in the complete TC6 loot dependency set: " + id);
        }
        ItemStack stack = new ItemStack(item, reward.count());
        if (stack.isDamageableItem()) {
            stack.setDamageValue(reward.legacyDamage());
        } else if (reward.legacyDamage() != 0 && !reward.itemId().equals("thaumcraft:baubles")
                   && !reward.itemId().equals("minecraft:golden_apple")) {
            throw new IllegalArgumentException("Legacy damage cannot be discarded for " + reward.itemId());
        }
        if (reward.potionType() != null) {
            PotionContents contents = reward.potionType().equals("minecraft:empty")
                ? PotionContents.EMPTY : new PotionContents(requirePotion(reward.potionType()));
            stack.set(DataComponents.POTION_CONTENTS, contents);
        }
        return stack;
    }

    private Holder<Potion> requirePotion(String id) {
        Holder<Potion> potion = potions.get(id);
        if (potion == null) {
            throw new IllegalArgumentException("Not in the 1.12.2 potion catalog: " + id);
        }
        return potion;
    }
}
