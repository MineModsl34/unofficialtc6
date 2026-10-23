package thaumcraft.common.lib.research;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.network.chat.Component;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.items.TCDataComponents;
import thaumcraft.api.research.ScanOreDictionary;
import thaumcraft.common.lib.research.catalog.ResearchCatalogParser;
import thaumcraft.common.lib.research.catalog.ResearchCatalogSpec.ItemKind;
import thaumcraft.common.lib.research.catalog.ResearchCatalogSpec.ItemToken;
import thaumcraft.common.lib.research.theorycraft.TheorycraftInventory;

/** Source tokens resolved through explicit flattening identities and live registries/tags.
 * Absent later-phase items stay EMPTY, never AIR-as-success or free requirements.
 * Craft hashes use proven old translation keys, never modern registry-name substitutes. */
public final class ResearchItemResolver {
    // Original basics.json retains nitor;1;4 although BETA26 ConfigBlocks:328-330
    // registers one BlockItem per EnumDyeColor. Descriptor compatibility resolves that
    // finite obsolete family to the actual colored item; scans/craft hashes still use
    // the real BETA26 nitor_<sourceColor>@0 identity, never invented tile.nitor@meta.
    private static final List<String> NITOR_COLORS = List.of("white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
        "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black");
    private ResearchItemResolver() {}
    public static ItemToken parse(String raw) { return ResearchCatalogParser.parseItemToken(raw); }
    public static ItemStack resolve(ItemToken token) {
        return resolve(token, null);
    }
    public static ItemStack resolve(ItemToken token, HolderLookup.Provider registries) {
        List<ItemStack> alternatives = alternatives(token, registries);
        return alternatives.isEmpty() ? ItemStack.EMPTY : alternatives.getFirst().copy();
    }
    public static List<ItemStack> alternatives(ItemToken token) {
        return alternatives(token, null);
    }
    public static List<ItemStack> alternatives(ItemToken token, HolderLookup.Provider registries) {
        if (token == null || token.count() <= 0 || token.count() > 1_000_000) return List.of();
        if (token.kind() == ItemKind.OREDICT) {
            TagKey<Item> tag = oreTag(token.name());
            if (tag == null) return List.of();
            var result = new ArrayList<ItemStack>();
            BuiltInRegistries.ITEM.getTag(tag).ifPresent(holders -> holders.forEach(holder -> {
                if (holder.value() != Items.AIR) result.add(new ItemStack(holder, token.count()));
            }));
            return List.copyOf(result);
        }
        if (token.legacyId() == null || !token.legacyId().modernSyntax()) return List.of();
        var id = ResourceLocation.fromNamespaceAndPath(token.legacyId().namespace(), token.legacyId().path());
        if (id.equals(ResourceLocation.parse("thaumcraft:nitor"))) {
            if (token.damage() != 32767 && (token.damage() < 0 || token.damage() >= NITOR_COLORS.size())) return List.of();
            var colors = new ArrayList<ItemStack>();
            for (int metadata = 0; metadata < NITOR_COLORS.size(); metadata++) {
                if (token.damage() != 32767 && token.damage() != metadata) continue;
                var modern = ResourceLocation.fromNamespaceAndPath("thaumcraft", "nitor_" + NITOR_COLORS.get(metadata));
                var proven = LegacyScanIdentity.knownItems().get(modern);
                if (proven == null || !proven.sourceProven() || !BuiltInRegistries.ITEM.containsKey(modern)) continue;
                var stack = new ItemStack(BuiltInRegistries.ITEM.get(modern), token.count());
                if (!stack.isEmpty() && applyLegacyTag(stack, token, registries)) colors.add(stack);
            }
            return List.copyOf(colors);
        }
        var result = new ArrayList<ItemStack>();
        for (var entry : LegacyScanIdentity.knownItems().entrySet()) {
            if (!entry.getValue().itemId().equals(id) || !BuiltInRegistries.ITEM.containsKey(entry.getKey())) continue;
            Item item = BuiltInRegistries.ITEM.get(entry.getKey());
            if (item == Items.AIR) continue;
            ItemStack stack = new ItemStack(item, token.count());
            if (entry.getValue().metadata() != token.damage() && token.damage() != 32767 && !stack.isDamageableItem()) continue;
            if (stack.isDamageableItem()) stack.set(DataComponents.DAMAGE, token.damage());
            if (token.damage() == 32767 && item instanceof thaumcraft.api.items.ItemGenericEssentiaContainer container
                && token.legacySnbt() == null) {
                for (ItemStack variant : container.getTabVariants()) { variant.setCount(token.count()); result.add(variant); }
            } else if (applyLegacyTag(stack, token, registries)) result.add(stack);
        }
        boolean family = LegacyScanIdentity.knownItems().values().stream().anyMatch(value -> value.itemId().equals(id));
        // Unchanged ordinary vanilla identity is proved by explicit MC1.12 registration.
        // A flattened family MUST NOT fall back to an unrelated damage-zero modern base item.
        if (result.isEmpty() && !family && LegacyCraftTranslationKeys.VANILLA.containsKey(id.toString())
            && BuiltInRegistries.ITEM.containsKey(id)) {
            Item item = BuiltInRegistries.ITEM.get(id);
            ItemStack stack = item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item, token.count());
            if (!stack.isEmpty() && (token.damage() == 0 || token.damage() == 32767 || stack.isDamageableItem())) {
                if (stack.isDamageableItem()) stack.set(DataComponents.DAMAGE, token.damage());
                if (applyLegacyTag(stack, token, registries)) result.add(stack);
            }
        }
        return List.copyOf(result);
    }
    private static boolean applyLegacyTag(ItemStack stack, ItemToken token, HolderLookup.Provider registries) {
        if (token.legacySnbt() == null) return true;
        try {
            var tag = TagParser.parseTag(token.legacySnbt());
            if (tag.contains("Aspects")) {
                if (TCDataComponents.ASPECTS == null) return false;
                var aspects = new AspectList(); aspects.readFromNBT(tag);
                stack.set(TCDataComponents.ASPECTS, aspects); tag.remove("Aspects");
            }
            if (tag.contains("tc.charge")) {
                if (TCDataComponents.CHARGE == null || tag.getInt("tc.charge") < 0) return false;
                stack.set(TCDataComponents.CHARGE, tag.getInt("tc.charge")); tag.remove("tc.charge");
            }
            int hideFlags = tag.getInt("HideFlags");
            if (tag.contains("Potion")) {
                var potion = ResourceLocation.tryParse(tag.getString("Potion"));
                if (potion == null) return false;
                if (potion.equals(ResourceLocation.withDefaultNamespace("empty"))) stack.set(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
                else {
                    var holder = BuiltInRegistries.POTION.getHolder(net.minecraft.resources.ResourceKey.create(Registries.POTION, potion));
                    if (holder.isEmpty()) return false;
                    stack.set(DataComponents.POTION_CONTENTS, new PotionContents(holder.get()));
                }
                tag.remove("Potion");
            }
            if (tag.contains("Unbreakable")) {
                if (tag.getBoolean("Unbreakable")) stack.set(DataComponents.UNBREAKABLE, new Unbreakable((hideFlags & 4) == 0));
                tag.remove("Unbreakable");
            }
            if (tag.contains("display")) {
                var display = tag.getCompound("display").copy();
                if (display.contains("Name")) { stack.set(DataComponents.CUSTOM_NAME, Component.literal(display.getString("Name"))); display.remove("Name"); }
                if (display.contains("Lore")) {
                    var values = display.getList("Lore", 8); var lore = new ArrayList<Component>();
                    for (int i = 0; i < values.size(); i++) lore.add(Component.literal(values.getString(i)));
                    stack.set(DataComponents.LORE, new ItemLore(lore)); display.remove("Lore");
                }
                if (display.contains("color")) { stack.set(DataComponents.DYED_COLOR, new DyedItemColor(display.getInt("color"), (hideFlags & 64) == 0)); display.remove("color"); }
                if (display.isEmpty()) tag.remove("display"); else tag.put("display", display);
            }
            // No ambient holder cache: source numeric IDs resolve against this world's registry.
            for (String key : List.of("ench", "StoredEnchantments")) if (tag.contains(key)) {
                if (registries == null) {
                    // A placeholder remains a symbolic descriptor; it is NOT a fake enchanted
                    // gameplay item. Its source numeric metadata is consumed by matches below.
                    if (!stack.is(thaumcraft.api.items.ItemsTC.enchantedPlaceholder)) return false;
                    continue;
                }
                var values = tag.getList(key, 10);
                var enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
                for (int i = 0; i < values.size(); i++) {
                    var row = values.getCompound(i);
                    var target = sourceEnchantment(row.getInt("id"));
                    if (target == null || row.getInt("lvl") < 0 || row.getInt("lvl") > 255) return false;
                    var holder = registries.lookupOrThrow(Registries.ENCHANTMENT).get(target);
                    if (holder.isEmpty()) return false;
                    enchantments.set(holder.get(), row.getInt("lvl"));
                }
                stack.set(key.equals("ench") ? DataComponents.ENCHANTMENTS : DataComponents.STORED_ENCHANTMENTS,
                    enchantments.toImmutable().withTooltip((hideFlags & 1) == 0));
                tag.remove(key);
            }
            if (!tag.isEmpty()) stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            return true;
        } catch (Exception malformed) { return false; }
    }
    public static boolean matches(ItemToken token, ItemStack actual) {
        return matches(token, actual, null);
    }
    public static boolean matches(ItemToken token, ItemStack actual, HolderLookup.Provider registries) {
        if (token == null || actual == null || actual.isEmpty()) return false;
        if (token.kind() == ItemKind.OREDICT) {
            var tag = oreTag(token.name()); return tag != null && actual.is(tag);
        }
        if (token.name().equals("thaumcraft:enchanted_placeholder")) return matchesEnchantedPlaceholder(token, actual);
        for (ItemStack required : alternatives(token, registries)) {
            if (token.damage() == 32767 && required.isDamageableItem())
                required.set(DataComponents.DAMAGE, LegacyScanIdentity.rawDamage(actual));
            if (TheorycraftInventory.matchesExact(actual, required)) return true;
        }
        return false;
    }
    private static net.minecraft.resources.ResourceKey<Enchantment> sourceEnchantment(int originalId) {
        for (var descriptor : LegacyEnchantmentScanNames.VANILLA) if (descriptor.originalId() == originalId) return descriptor.target();
        return null;
    }
    private static boolean matchesEnchantedPlaceholder(ItemToken token, ItemStack actual) {
        if (token.legacySnbt() == null) return false;
        try {
            var rows = TagParser.parseTag(token.legacySnbt()).getList("ench", 10);
            if (rows.isEmpty()) return false;
            var enchantments = EnchantmentHelper.getEnchantmentsForCrafting(actual);
            boolean matched = false;
            // Source checkEnchantedPlaceholder's result is the last visited enchantment;
            // an explicitly present BELOW-threshold enchantment returns false immediately.
            // All builtin source requirements contain one row, so HashMap heap order is immaterial.
            for (int i = 0; i < rows.size(); i++) {
                var row = rows.getCompound(i); var target = sourceEnchantment(row.getInt("id"));
                if (target == null) return false;
                matched = false;
                for (var holder : enchantments.keySet()) if (holder.is(target)) {
                    if (enchantments.getLevel(holder) < row.getInt("lvl")) return false;
                    matched = true;
                }
            }
            return matched;
        } catch (Exception malformed) { return false; }
    }
    public static boolean matchesNativeRequirement(ItemStack required, ItemStack actual) {
        if (required == null || actual == null || required.isEmpty() || actual.isEmpty()) return false;
        if (!required.is(thaumcraft.api.items.ItemsTC.enchantedPlaceholder)) return TheorycraftInventory.matchesExact(actual, required);
        var targets = EnchantmentHelper.getEnchantmentsForCrafting(required);
        var enchantments = EnchantmentHelper.getEnchantmentsForCrafting(actual);
        boolean matched = false;
        for (var target : targets.keySet()) {
            matched = false;
            for (var holder : enchantments.keySet()) if (holder.is(target.unwrapKey().orElseThrow())) {
                if (enchantments.getLevel(holder) < targets.getLevel(target)) return false;
                matched = true;
            }
        }
        return matched;
    }
    public static Integer craftReference(ItemToken token) {
        if (token == null || token.count() <= 0) return null;
        if (token.kind() == ItemKind.OREDICT) return oreTag(token.name()) == null ? null : ("oredict:" + token.name()).hashCode();
        ItemStack stack = resolve(token);
        return stack.isEmpty() ? null : craftReference(stack);
    }
    public static Integer craftReference(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        var identity = LegacyScanIdentity.identify(stack);
        if (!stack.isDamageableItem() && LegacyScanIdentity.rawDamage(stack) != 0) return null;
        String key = LegacyCraftTranslationKeys.VANILLA.get(identity.itemId().toString());
        if (identity.itemId().getNamespace().equals("thaumcraft") && identity.sourceProven())
            key = (stack.getItem() instanceof BlockItem ? "tile." : "item.") + identity.itemId().getPath();
        if (key == null) return null;
        int damage = stack.isDamageableItem() ? LegacyScanIdentity.rawDamage(stack) : identity.metadata();
        return ("1x" + key + "@" + damage).hashCode();
    }
    public static TagKey<Item> oreTag(String name) {
        try { return ScanOreDictionary.tagForLegacyName(name); }
        catch (IllegalArgumentException unsupported) { /* explicit source recipe identities below */ }
        return switch (name) {
            case "wool" -> ItemTags.WOOL;
            case "chest" -> TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "chests"));
            case "plankWood" -> ItemTags.PLANKS;
            case "logWood" -> ItemTags.LOGS;
            case "stickWood" -> TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "rods/wooden"));
            case "workbench" -> TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "workbenches"));
            case "bookshelf" -> TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "bookshelves"));
            default -> null;
        };
    }
}
