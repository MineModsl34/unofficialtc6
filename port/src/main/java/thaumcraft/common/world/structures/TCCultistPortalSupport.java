package thaumcraft.common.world.structures;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Reviewed 1.12 Material.GROUND/ROCK/SAND/SNOW registry bridge; no modern generic solid/dirt tag. */
public final class TCCultistPortalSupport {
    public static final String TAG_ID = "thaumcraft:cultist_portal_support";
    private static final List<String> SOURCE_SUPPORT_IDS = build();

    private TCCultistPortalSupport() {}

    /** Actual vanilla entries are required; later-stage source TC blocks may be optional tag entries. */
    public static List<String> sourceSupportIds() { return SOURCE_SUPPORT_IDS; }

    private static List<String> build() {
        Set<String> ids = new TreeSet<>();
        // BlockDirt/Farmland/GrassPath use GROUND. Grass/Mycelium use GRASS, Clay uses CLAY: excluded.
        add(ids, "minecraft", "dirt", "coarse_dirt", "podzol", "farmland", "dirt_path");
        // Gravel inherits BlockFalling's SAND, and Snow is the layer, NOT crafted snow_block.
        add(ids, "minecraft", "sand", "red_sand", "gravel", "soul_sand", "snow");
        // Block.java registerBlocks + corresponding source constructors. Metadata splits map to IDs;
        // modern copper/deepslate/calcite/dripstone/new soil families are deliberately not admitted.
        add(ids, "minecraft", "stone", "granite", "polished_granite", "diorite", "polished_diorite", "andesite", "polished_andesite",
            "cobblestone", "mossy_cobblestone", "bedrock", "gold_ore", "iron_ore", "coal_ore", "lapis_ore", "diamond_ore",
            "redstone_ore", "emerald_ore", "nether_quartz_ore", "dispenser", "dropper", "sandstone", "chiseled_sandstone", "cut_sandstone",
            "red_sandstone", "chiseled_red_sandstone", "cut_red_sandstone", "bricks", "obsidian", "spawner", "furnace", "stone_pressure_plate",
            "netherrack", "stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks", "chiseled_stone_bricks", "nether_bricks",
            "nether_brick_fence", "enchanting_table", "end_portal_frame", "end_stone", "ender_chest", "cobblestone_wall", "mossy_cobblestone_wall",
            "quartz_block", "chiseled_quartz_block", "quartz_pillar", "prismarine", "prismarine_bricks", "dark_prismarine", "terracotta",
            "coal_block", "purpur_block", "purpur_pillar", "end_stone_bricks", "magma_block", "red_nether_bricks", "bone_block", "observer",
            "cobblestone_stairs", "brick_stairs", "stone_brick_stairs", "nether_brick_stairs", "sandstone_stairs", "quartz_stairs",
            "red_sandstone_stairs", "purpur_stairs", "stone_slab", "smooth_stone_slab", "sandstone_slab", "petrified_oak_slab",
            "cobblestone_slab", "brick_slab", "stone_brick_slab", "nether_brick_slab", "quartz_slab", "red_sandstone_slab", "purpur_slab",
            "smooth_stone", "smooth_sandstone", "smooth_red_sandstone", "smooth_quartz");
        // Source dye-metadata blocks: hardened/glazed terracotta, concrete (ROCK), powder (SAND), shulkers (ROCK).
        for (String color : List.of("white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black")) {
            for (String suffix : List.of("terracotta", "glazed_terracotta", "concrete", "concrete_powder", "shulker_box"))
                add(ids, "minecraft", color + "_" + suffix);
        }
        // Source TC constructors using ROCK. No custom TAINT material, glass crystals, wood crates,
        // grassAmbient or metal crucible/smelter/jar devices. Future source blocks are optional, not fabricated.
        add(ids, "thaumcraft", "stone_arcane", "stone_arcane_brick", "stone_ancient", "stone_ancient_tile", "stone_ancient_rock",
            "stone_ancient_glyphed", "stone_ancient_doorway", "stone_eldritch_tile", "stone_porous", "stairs_arcane", "stairs_arcane_brick",
            "stairs_ancient", "slab_arcane_stone", "slab_arcane_brick", "slab_ancient", "slab_eldritch", "loot_urn_common", "loot_urn_uncommon",
            "loot_urn_rare", "table_stone", "paving_stone_travel", "paving_stone_barrier", "pillar_arcane", "pillar_ancient", "pillar_eldritch",
            "matrix_speed", "matrix_cost", "ore_cinnabar", "ore_amber", "ore_quartz", "wand_workbench", "infusion_matrix", "golem_builder", "dioptra",
            "infernal_furnace", "pedestal_arcane", "pedestal_ancient", "pedestal_eldritch", "recharge_pedestal", "spa", "stabilizer", "vis_battery",
            "everfull_urn", "hole", "placeholder_brick", "placeholder_obsidian", "placeholder_bars", "placeholder_anvil", "placeholder_cauldron",
            "placeholder_table");
        return List.copyOf(ids);
    }

    private static void add(Set<String> ids, String namespace, String... paths) {
        for (String path : paths) if (!ids.add(namespace + ":" + path)) throw new IllegalArgumentException("Duplicate source portal support: " + path);
    }
}
