package thaumcraft.common.lib.research.theorycraft;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import thaumcraft.common.lib.research.LegacyScanIdentity;

/** The source OreDictionary early-success path for the finite built-in card materials.
 * Explicit old identities supply TC6 baseline membership; live leaf tags allow the same
 * material from addons. Generic modern groups (gems/ingots) are NEVER equivalence groups.
 * New materials/tags outside this list are not claimed to be a universal OreDictionary port. */
public final class TheorycraftOreEquivalence {
    private static final java.util.Set<String> NITOR = java.util.Set.of("white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
        "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black");
    private TheorycraftOreEquivalence() {}
    public static boolean matches(ItemStack actual, ItemStack required) {
        var target = LegacyScanIdentity.identify(required);
        String group = sourceGroup(target);
        if (group == null) return false;
        if (LegacyScanIdentity.rawDamage(actual) != 0 && !actual.isDamageableItem()) return false;
        // Source wildcard ore membership ignores damage and all item NBT/components.
        if (group.equals(sourceGroup(LegacyScanIdentity.identify(actual)))) return true;
        TagKey<Item> tag = group.equals("wool") ? ItemTags.WOOL : TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(group.equals("nitor") ? "thaumcraft" : "c", group));
        return actual.is(tag);
    }
    static String sourceGroup(LegacyScanIdentity.Identity identity) {
        String id = identity.itemId().toString(); int meta = identity.metadata();
        if (id.equals("minecraft:wool") && meta >= 0 && meta < 16) return "wool";
        if (id.equals("minecraft:chest") || id.equals("minecraft:ender_chest") || id.equals("minecraft:trapped_chest")) return "chests";
        if (id.startsWith("thaumcraft:nitor_") && NITOR.contains(id.substring("thaumcraft:nitor_".length())) && meta == 0) return "nitor";
        if (id.equals("thaumcraft:ingot")) return switch (meta) { case 0 -> "ingots/thaumium"; case 1 -> "ingots/void"; case 2 -> "ingots/brass"; default -> null; };
        if (meta != 0) return null;
        return switch (id) {
            case "thaumcraft:amber" -> "gems/amber";
            case "thaumcraft:quicksilver" -> "quicksilver";
            case "minecraft:gold_ingot" -> "ingots/gold";
            case "minecraft:iron_ingot" -> "ingots/iron";
            case "minecraft:brick" -> "bricks/normal";
            case "minecraft:diamond" -> "gems/diamond";
            case "minecraft:emerald" -> "gems/emerald";
            case "minecraft:quartz" -> "gems/quartz";
            case "minecraft:glowstone_dust" -> "dusts/glowstone";
            case "minecraft:redstone" -> "dusts/redstone";
            case "minecraft:gunpowder" -> "gunpowders";
            case "minecraft:leather" -> "leathers";
            case "minecraft:feather" -> "feathers";
            case "minecraft:egg" -> "eggs";
            default -> null;
        };
    }
}
