package thaumcraft.common.lib.research;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/**
 * Valid vanilla item variants from ItemStackTheFlatteningFix, followed by the item
 * renames in DataFixers/EntityTheRenameningFix/DyeItemRenameFix (1.21.1 sources).
 * This is a deliberately finite ITEM correspondence, not a reverse world/NBT datafixer.
 * Newly added items, invalid old metadata and ambiguous dead_bush are not fabricated.
 */
public final class LegacyVanillaScanIdentities {
    private static final Map<ResourceLocation, LegacyScanIdentity.Identity> ITEMS = create();

    private LegacyVanillaScanIdentities() {}

    public static Map<ResourceLocation, LegacyScanIdentity.Identity> knownItems() { return ITEMS; }

    private static Map<ResourceLocation, LegacyScanIdentity.Identity> create() {
        Map<ResourceLocation, LegacyScanIdentity.Identity> map = new LinkedHashMap<>();
        family(map, "stone", "stone", "granite", "polished_granite", "diorite", "polished_diorite", "andesite", "polished_andesite");
        family(map, "dirt", "dirt", "coarse_dirt", "podzol");
        family(map, "leaves", "oak_leaves", "spruce_leaves", "birch_leaves", "jungle_leaves");
        family(map, "leaves2", "acacia_leaves", "dark_oak_leaves");
        family(map, "log", "oak_log", "spruce_log", "birch_log", "jungle_log");
        family(map, "log2", "acacia_log", "dark_oak_log");
        family(map, "sapling", "oak_sapling", "spruce_sapling", "birch_sapling", "jungle_sapling", "acacia_sapling", "dark_oak_sapling");
        family(map, "planks", "oak_planks", "spruce_planks", "birch_planks", "jungle_planks", "acacia_planks", "dark_oak_planks");
        family(map, "sand", "sand", "red_sand");
        family(map, "quartz_block", "quartz_block", "chiseled_quartz_block", "quartz_pillar");
        family(map, "anvil", "anvil", "chipped_anvil", "damaged_anvil");
        String[] colors = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"};
        for (String suffix : new String[] {"wool", "carpet", "stained_glass", "stained_glass_pane", "concrete", "concrete_powder", "bed"}) {
            for (int meta = 0; meta < colors.length; meta++) put(map, colors[meta] + "_" + suffix, suffix, meta);
        }
        for (int meta = 0; meta < colors.length; meta++) {
            put(map, colors[meta] + "_terracotta", "stained_hardened_clay", meta);
            // ItemBanner used dye damage, the REVERSE of wool/EnumDyeColor metadata.
            put(map, colors[meta] + "_banner", "banner", 15 - meta);
        }
        family(map, "hardened_clay", "terracotta");
        family(map, "prismarine", "prismarine", "prismarine_bricks", "dark_prismarine");
        family(map, "cobblestone_wall", "cobblestone_wall", "mossy_cobblestone_wall");
        family(map, "sandstone", "sandstone", "chiseled_sandstone", "cut_sandstone");
        family(map, "red_sandstone", "red_sandstone", "chiseled_red_sandstone", "cut_red_sandstone");
        family(map, "stonebrick", "stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks", "chiseled_stone_bricks");
        family(map, "monster_egg", "infested_stone", "infested_cobblestone", "infested_stone_bricks", "infested_mossy_stone_bricks", "infested_cracked_stone_bricks", "infested_chiseled_stone_bricks");
        family(map, "yellow_flower", "dandelion");
        family(map, "red_flower", "poppy", "blue_orchid", "allium", "azure_bluet", "red_tulip", "orange_tulip", "white_tulip", "pink_tulip", "oxeye_daisy");
        family(map, "double_plant", "sunflower", "lilac", "tall_grass", "large_fern", "rose_bush", "peony");
        // Both deadbush:0 and tallgrass:0 flatten to dead_bush; no unique inverse exists.
        put(map, "short_grass", "tallgrass", 1);
        put(map, "fern", "tallgrass", 2);
        family(map, "sponge", "sponge", "wet_sponge");
        family(map, "purpur_slab", "purpur_slab");
        family(map, "stone_slab", "smooth_stone_slab", "sandstone_slab", "petrified_oak_slab", "cobblestone_slab", "brick_slab", "stone_brick_slab", "nether_brick_slab", "quartz_slab");
        family(map, "stone_slab2", "red_sandstone_slab");
        family(map, "wooden_slab", "oak_slab", "spruce_slab", "birch_slab", "jungle_slab", "acacia_slab", "dark_oak_slab");
        family(map, "coal", "coal", "charcoal");
        family(map, "fish", "cod", "salmon", "tropical_fish", "pufferfish");
        family(map, "cooked_fish", "cooked_cod", "cooked_salmon");
        family(map, "skull", "skeleton_skull", "wither_skeleton_skull", "zombie_head", "player_head", "creeper_head", "dragon_head");
        family(map, "golden_apple", "golden_apple", "enchanted_golden_apple");
        // Black/blue/white dyes were NEW items; old dye:0/4/15 are ink/lapis/bone meal.
        family(map, "dye", "ink_sac", "red_dye", "green_dye", "cocoa_beans", "lapis_lazuli", "purple_dye", "cyan_dye", "light_gray_dye", "gray_dye", "pink_dye", "lime_dye", "yellow_dye", "light_blue_dye", "magenta_dye", "orange_dye", "bone_meal");
        String[][] renamed = {
            {"silver_glazed_terracotta", "light_gray_glazed_terracotta"}, {"silver_shulker_box", "light_gray_shulker_box"},
            {"fence", "oak_fence"}, {"fence_gate", "oak_fence_gate"}, {"wooden_door", "oak_door"}, {"boat", "oak_boat"},
            {"lit_pumpkin", "jack_o_lantern"}, {"pumpkin", "carved_pumpkin"}, {"trapdoor", "oak_trapdoor"},
            {"nether_brick", "nether_bricks"}, {"red_nether_brick", "red_nether_bricks"}, {"netherbrick", "nether_brick"},
            {"wooden_button", "oak_button"}, {"wooden_pressure_plate", "oak_pressure_plate"}, {"noteblock", "note_block"},
            {"grass", "grass_block"}, {"brick_block", "bricks"}, {"end_bricks", "end_stone_bricks"}, {"golden_rail", "powered_rail"},
            {"magma", "magma_block"}, {"quartz_ore", "nether_quartz_ore"}, {"reeds", "sugar_cane"}, {"slime", "slime_block"},
            {"stone_stairs", "cobblestone_stairs"}, {"waterlily", "lily_pad"}, {"web", "cobweb"}, {"snow", "snow_block"}, {"snow_layer", "snow"},
            {"fireworks", "firework_rocket"}, {"firework_charge", "firework_star"},
            // Subsequent item-only renames, not block identities (melon is a notable collision).
            {"melon_block", "melon"}, {"melon", "melon_slice"}, {"speckled_melon", "glistering_melon_slice"},
            {"chorus_fruit_popped", "popped_chorus_fruit"}, {"sign", "oak_sign"}, {"grass_path", "dirt_path"}
        };
        for (String[] pair : renamed) put(map, pair[1], pair[0], 0);
        for (String disc : new String[] {"11", "13", "blocks", "cat", "chirp", "far", "mall", "mellohi", "stal", "strad", "wait", "ward"}) {
            put(map, "music_disc_" + disc, "record_" + disc, 0);
        }
        return Collections.unmodifiableMap(map);
    }

    private static void family(Map<ResourceLocation, LegacyScanIdentity.Identity> map, String legacy, String... modern) {
        for (int meta = 0; meta < modern.length; meta++) put(map, modern[meta], legacy, meta);
    }

    private static void put(Map<ResourceLocation, LegacyScanIdentity.Identity> map, String modern, String legacy, int meta) {
        ResourceLocation id = ResourceLocation.withDefaultNamespace(modern);
        var previous = map.put(id, new LegacyScanIdentity.Identity(ResourceLocation.withDefaultNamespace(legacy), meta, true));
        if (previous != null) throw new IllegalStateException("Ambiguous vanilla item scan identity: " + id);
    }
}
