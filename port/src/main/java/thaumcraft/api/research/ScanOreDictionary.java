package thaumcraft.api.research;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import thaumcraft.common.lib.research.LegacyScanIdentity;

/**
 * TC6 material-marker matching, backed by current item tags instead of OreDictionary ids.
 * Original ConfigResearch and OreDictionaryEntries names are explicitly adapted; addons can pass
 * explicit item tags with {@link #forTags}. Unknown old names fail instead of becoming AIR
 * or an invented naming-convention alias. Research keys themselves remain opaque.
 *
 * Unlike TC6's permanent positive/negative item-hash cache, every check reads the current
 * tag membership. This intentional reload adaptation avoids stale matches after tag rebinding.
 */
public class ScanOreDictionary implements IScanThing {
    private static final Map<String, TagKey<Item>> LEGACY_TAGS = legacyTags();

    String research;
    String[] entries;
    private final List<TagKey<Item>> modernTags;

    public ScanOreDictionary(String research, String... entries) {
        this.research = research;
        // Original API retains the caller's array, including subsequent entry changes.
        this.entries = Objects.requireNonNull(entries, "entries");
        this.modernTags = null;
        tagKeys(); // Reject unsupported names at registration, before any gameplay scan.
    }

    private ScanOreDictionary(String research, List<TagKey<Item>> tags) {
        this.research = research;
        this.modernTags = List.copyOf(tags);
        for (TagKey<Item> tag : modernTags) {
            if (!tag.registry().equals(Registries.ITEM)) {
                throw new IllegalArgumentException("Scanning requires an item tag: " + tag);
            }
        }
    }

    /** Explicit addon bridge; no automatic conversion of arbitrary old OreDictionary names. */
    public static ScanOreDictionary forTags(String research, List<TagKey<Item>> tags) {
        return new ScanOreDictionary(research, tags);
    }

    public static TagKey<Item> tagForLegacyName(String name) {
        TagKey<Item> tag = LEGACY_TAGS.get(name);
        if (tag == null) throw new IllegalArgumentException("Unsupported legacy scan OreDictionary name: " + name);
        return tag;
    }

    @Override
    public boolean checkThing(Player player, Object object) {
        ItemStack stack = null;
        if (object instanceof BlockPos pos) {
            // Source used Block.getItem, never generic pick-block or liquid bucket fallback.
            // These current-content material blocks have direct metadata-zero BlockItems;
            // merged TC double slabs retain the original missing ItemBlock behavior.
            stack = LegacyScanIdentity.directBlockStack(player.level().getBlockState(pos));
        } else if (object instanceof ItemStack item) {
            stack = item;
        } else if (object instanceof ItemEntity entity && entity.getItem() != null) {
            stack = entity.getItem();
        }
        return stack != null && !stack.isEmpty() && matchesTags(stack::is);
    }

    List<TagKey<Item>> tagKeys() {
        return modernTags != null ? modernTags : java.util.Arrays.stream(entries)
            .map(ScanOreDictionary::tagForLegacyName).toList();
    }

    boolean matchesTags(Predicate<TagKey<Item>> membership) {
        for (TagKey<Item> tag : tagKeys()) {
            if (membership.test(tag)) return true;
        }
        return false;
    }

    @Override
    public String getResearchKey(Player player, Object object) { return research; }

    private static Map<String, TagKey<Item>> legacyTags() {
        Map<String, TagKey<Item>> map = new LinkedHashMap<>();
        // Original ConfigResearch:344–347, in the original entry/group order.
        put(map, "oreIron", "ores/iron");
        put(map, "ingotIron", "ingots/iron");
        put(map, "blockIron", "storage_blocks/iron");
        put(map, "plateIron", "plates/iron");
        put(map, "ingotBrass", "ingots/brass");
        put(map, "blockBrass", "storage_blocks/brass");
        put(map, "plateBrass", "plates/brass");
        put(map, "ingotThaumium", "ingots/thaumium");
        put(map, "blockThaumium", "storage_blocks/thaumium");
        put(map, "plateThaumium", "plates/thaumium");
        put(map, "ingotVoid", "ingots/void");
        put(map, "blockVoid", "storage_blocks/void");
        put(map, "plateVoid", "plates/void");
        put(map, "oreAmber", "ores/amber"); put(map, "oreCinnabar", "ores/cinnabar"); put(map, "oreQuartz", "ores/quartz");
        for (String aspect : new String[]{"Air","Earth","Water","Fire","Order","Entropy","Taint"})
            put(map,"oreCrystal" + aspect,"ores/crystal_" + aspect.toLowerCase(java.util.Locale.ROOT));
        for (String metal : new String[]{"Iron","Copper","Tin","Silver","Lead","Quicksilver","Thaumium","Void","Brass","Quartz","Meat"})
            put(map,"nugget" + metal,"nuggets/" + metal.toLowerCase(java.util.Locale.ROOT));
        for (String metal : new String[]{"Iron","Gold","Copper","Tin","Silver","Lead","Cinnabar","Quartz"})
            put(map,"cluster" + metal,"clusters/" + metal.toLowerCase(java.util.Locale.ROOT));
        put(map,"quicksilver","quicksilver"); put(map,"gemAmber","gems/amber"); put(map,"nitor","nitor");
        for (String[] alias : new String[][]{{"logWood","logs"},{"plankWood","planks"},{"slabWood","wooden_slabs"},
                {"treeSapling","saplings"},{"treeLeaves","leaves"},{"trapdoorWood","wooden_trapdoors"}})
            map.put(alias[0],TagKey.create(Registries.ITEM,ResourceLocation.withDefaultNamespace(alias[1])));
        return java.util.Collections.unmodifiableMap(map);
    }

    private static void put(Map<String, TagKey<Item>> map, String legacy, String path) {
        map.put(legacy, TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", path)));
    }
}
