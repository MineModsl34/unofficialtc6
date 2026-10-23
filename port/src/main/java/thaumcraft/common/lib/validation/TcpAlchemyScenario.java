package thaumcraft.common.lib.validation;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;

/** Finite reviewed native action graph. Strings identify registered production objects only. */
public final class TcpAlchemyScenario {
    public enum Kind { RESEARCH, THEORY, CRAFT, USE_BLOCK, SMELTER_LOAD, WAIT_TRANSFER, PHIAL_EXTRACT,
        PHIAL_INSERT, PEDESTAL_PLACE, MATRIX_ACTIVATE, MATRIX_START, CHECKPOINT, SCAN_BLOCK, SCAN_ITEM, OUTPUT_PICKUP }
    public record Ingredient(int slot, String item, int count, String aspect) {}
    public record Action(String id, Kind kind, BlockPos position, String item, int amount,
                         String research, int stage, List<Ingredient> ingredients) {}
    public static final BlockPos CRAFT = new BlockPos(258, 65, 0), WORKBENCH = new BlockPos(258, 65, 4);
    public static final BlockPos CRUCIBLE = new BlockPos(258, 65, 8), THEORY = new BlockPos(258, 65, 12);
    public static final BlockPos STORAGE = THEORY.offset(2, 0, 0), STORAGE_PARTNER = STORAGE.east();
    public static final BlockPos MATERIAL_CHEST = STORAGE, MATERIAL_CHEST_PARTNER = STORAGE_PARTNER;
    public static final BlockPos SCAN_ITEM = new BlockPos(262, 66, 20);
    public static final BlockPos SMELTER = new BlockPos(264, 65, 0), ALEMBIC = SMELTER.above(), JAR = new BlockPos(266, 65, 0);
    public static final BlockPos SMELTER_CRYSTAL = new BlockPos(264, 65, 4), JAR_CRYSTAL = new BlockPos(266, 65, 4);
    public static final BlockPos SMELTER_TRAP = new BlockPos(264, 65, 8), JAR_TRAP = new BlockPos(266, 65, 8);
    public static final BlockPos MATRIX = new BlockPos(274, 67, 0), CENTER = MATRIX.below(2);
    public static final BlockPos LEFT = new BlockPos(271, 65, 0), RIGHT = new BlockPos(277, 65, 0);
    public static final List<String> PRIMALS = List.of("aer", "ignis", "aqua", "terra", "ordo", "perditio");
    public static final List<String> SECONDARIES = List.of("vacuos", "lux", "motus", "gelum", "vitreus", "metallum", "victus", "mortuus", "potentia", "permutatio");
    public static final List<BlockPos> CRYSTAL_SCANS = java.util.stream.IntStream.range(0, 6).mapToObj(i -> new BlockPos(262, 65, 24 + i * 2)).toList();
    public static final List<String> SCAN_ITEMS = List.of("cauldron", "brewing_stand", "nether_wart", "ghast_tear", "dragon_breath",
        "water_bucket", "milk_bucket", "slime_ball", "enchanting_table", "golden_apple", "enchanted_golden_apple", "gunpowder",
        "fermented_spider_eye", "glistering_melon_slice", "magma_cream", "blaze_powder", "rabbit_foot", "beacon", "spawner",
        "ender_eye", "nether_star", "glass", "feather", "cobweb", "flint", "torch", "coal", "soul_sand", "sugar_cane",
        "iron_ingot", "gold_ingot", "diamond", "emerald", "lapis_lazuli", "redstone", "clay_ball", "leather",
        "diamond_chestplate", "iron_axe", "shield", "book", "crafting_table", "oak_planks", "terracotta", "white_terracotta",
        "orange_terracotta", "magenta_terracotta", "light_blue_terracotta", "yellow_terracotta", "lime_terracotta", "pink_terracotta",
        "gray_terracotta", "light_gray_terracotta", "cyan_terracotta", "purple_terracotta", "blue_terracotta", "brown_terracotta",
        "green_terracotta", "red_terracotta", "black_terracotta", "white_concrete", "orange_concrete", "magenta_concrete",
        "light_blue_concrete", "yellow_concrete", "lime_concrete", "pink_concrete", "gray_concrete", "light_gray_concrete",
        "cyan_concrete", "purple_concrete", "blue_concrete", "brown_concrete", "green_concrete", "red_concrete", "black_concrete");
    private TcpAlchemyScenario() {}
    private static Ingredient item(int slot, String item, int count) { return new Ingredient(slot, item.contains(":") ? item : "minecraft:" + item, count, ""); }
    private static Ingredient crystal(int slot, String aspect, int count) { return new Ingredient(slot, "thaumcraft:crystal_essence", count, aspect); }
    private static Action action(String id, Kind kind, BlockPos pos, String item, int amount, String research, int before, Ingredient... ingredients) {
        return new Action(id, kind, pos, item, amount, research, before, List.of(ingredients));
    }
    private static Action research(String key, int before, Ingredient... ingredients) {
        return action("research-" + key.toLowerCase(java.util.Locale.ROOT) + "-" + before, Kind.RESEARCH, WORKBENCH, "", 0, key, before, ingredients);
    }
    public static int resultingStage(Action action) {
        if (action.stage() < 0) return action.research().startsWith("BASE") || action.research().equals("WARDEDJARS") ? 2 : 1;
        return switch (action.research()) {
            case "UNLOCKALCHEMY" -> action.stage() + 1;
            case "ALUMENTUM", "THEORYRESEARCH", "UNLOCKINFUSION", "CRYSTALFARMER" -> 3;
            case "ESSENTIASMELTER" -> action.stage() == 3 ? 5 : action.stage() + 1;
            case "INFUSION" -> action.stage() == 2 ? 4 : 2;
            default -> action.stage() + 1;
        };
    }
    public static List<Action> generateActions() {
        ArrayList<Action> actions = new ArrayList<>();
        for (String id : SCAN_ITEMS) actions.add(action("scan-item-" + id, Kind.SCAN_ITEM, SCAN_ITEM, "minecraft:" + id, 1, "", -1));
        for (int i = 0; i < 6; i++) actions.add(action("scan-crystal-block-" + PRIMALS.get(i), Kind.SCAN_BLOCK, CRYSTAL_SCANS.get(i),
            "thaumcraft:crystal_" + PRIMALS.get(i), 1, "", -1));
        actions.add(research("UNLOCKALCHEMY", -1)); actions.add(research("UNLOCKALCHEMY", 1));
        actions.add(action("dust-native-crucible", Kind.USE_BLOCK, CRUCIBLE, "thaumcraft:salis_mundus", 1, "", -1));
        actions.add(research("UNLOCKALCHEMY", 2));
        actions.add(action("crucible-water", Kind.USE_BLOCK, CRUCIBLE, "minecraft:water_bucket", 1, "", -1));
        actions.add(action("crucible-coal", Kind.USE_BLOCK, CRUCIBLE, "minecraft:coal", 1, "", -1));
        actions.add(action("crucible-torches", Kind.USE_BLOCK, CRUCIBLE, "minecraft:torch", 2, "", -1));
        actions.add(action("crucible-native-nitor", Kind.USE_BLOCK, CRUCIBLE, "minecraft:glowstone_dust", 1, "", -1));
        actions.add(action("pickup-native-nitor", Kind.OUTPUT_PICKUP, CRUCIBLE, "thaumcraft:nitor_yellow", 1, "", -1));
        actions.add(research("UNLOCKALCHEMY", 3)); actions.add(research("BASEALCHEMY", -1));
        actions.add(research("ALUMENTUM", -1)); actions.add(research("ALUMENTUM", 1));
        actions.add(action("alumentum-coal", Kind.USE_BLOCK, CRUCIBLE, "minecraft:coal", 1, "", -1));
        actions.add(action("alumentum-sand", Kind.USE_BLOCK, CRUCIBLE, "minecraft:sand", 1, "", -1));
        actions.add(action("crucible-native-alumentum", Kind.USE_BLOCK, CRUCIBLE, "minecraft:coal", 1, "", -1));
        actions.add(action("pickup-native-alumentum", Kind.OUTPUT_PICKUP, CRUCIBLE, "thaumcraft:alumentum", 1, "", -1));
        actions.add(research("METALLURGY", -1));
        actions.add(action("brass-flint", Kind.USE_BLOCK, CRUCIBLE, "minecraft:flint", 1, "", -1));
        actions.add(action("crucible-native-brass", Kind.USE_BLOCK, CRUCIBLE, "minecraft:iron_ingot", 1, "", -1));
        actions.add(action("pickup-native-brass", Kind.OUTPUT_PICKUP, CRUCIBLE, "thaumcraft:ingot_brass", 1, "", -1));
        actions.add(research("METALLURGY", 1)); actions.add(research("THEORYRESEARCH", -1));
        actions.add(action("craft-native-scribing", Kind.CRAFT, CRAFT, "thaumcraft:scribing_tools", 1, "", -1,
            item(0, "glass_bottle", 1), item(1, "feather", 1), item(2, "ink_sac", 1)));
        actions.add(action("native-research-table-conversion", Kind.USE_BLOCK, THEORY, "thaumcraft:scribing_tools", 1, "", -1));
        actions.add(research("THEORYRESEARCH", 1));
        actions.add(action("theory-native-alchemy", Kind.THEORY, THEORY, "", 32, "ALCHEMY", -1, item(-1, "paper", 64)));
        actions.add(research("ESSENTIASMELTER", -1));
        actions.add(research("ESSENTIASMELTER", 1, PRIMALS.stream().map(tag -> crystal(-1, tag, 1)).toArray(Ingredient[]::new)));
        actions.add(research("ESSENTIASMELTER", 2, SECONDARIES.stream().map(tag -> crystal(-1, tag, 1)).toArray(Ingredient[]::new)));
        actions.add(action("craft-native-smelter", Kind.CRAFT, WORKBENCH, "thaumcraft:smelter_basic", 1, "", -1,
            item(0, "thaumcraft:plate_brass", 1), item(1, "thaumcraft:crucible", 1), item(2, "thaumcraft:plate_brass", 1),
            item(3, "cobblestone", 1), item(4, "furnace", 1), item(5, "cobblestone", 1), item(6, "cobblestone", 1),
            item(7, "cobblestone", 1), item(8, "cobblestone", 1), crystal(10, "ignis", 1)));
        actions.add(research("ESSENTIASMELTER", 3)); actions.add(research("WARDEDJARS", -1));
        actions.add(action("place-native-smelter", Kind.USE_BLOCK, SMELTER.below(), "thaumcraft:smelter_basic", 1, "", -1));
        actions.add(action("smelter-load-air", Kind.SMELTER_LOAD, SMELTER, "", 64, "aer", -1, crystal(0, "aer", 64), item(1, "coal", 1)));
        actions.add(action("smelter-load-crystal", Kind.SMELTER_LOAD, SMELTER_CRYSTAL, "", 8, "vitreus", -1, item(0, "glass", 8), item(1, "coal", 1)));
        actions.add(action("smelter-load-trap", Kind.SMELTER_LOAD, SMELTER_TRAP, "", 32, "vinculum", -1, crystal(0, "vinculum", 32), item(1, "coal", 1)));
        actions.add(action("wait-native-air-pipeline", Kind.WAIT_TRANSFER, JAR, "", 20, "aer", -1));
        actions.add(action("wait-native-crystal-pipeline", Kind.WAIT_TRANSFER, JAR_CRYSTAL, "", 10, "vitreus", -1));
        actions.add(action("wait-native-trap-pipeline", Kind.WAIT_TRANSFER, JAR_TRAP, "", 5, "vinculum", -1));
        actions.add(action("craft-native-empty-phials", Kind.CRAFT, CRAFT, "thaumcraft:phial_empty", 8, "", -1,
            item(1, "clay_ball", 1), item(3, "glass", 1), item(5, "glass", 1), item(7, "glass", 1)));
        actions.add(action("extract-native-air-phial", Kind.PHIAL_EXTRACT, JAR, "thaumcraft:phial_empty", 10, "aer", -1));
        actions.add(action("insert-native-air-phial", Kind.PHIAL_INSERT, JAR, "thaumcraft:phial_filled", 1, "aer", -1));
        actions.add(action("extract-native-air-phial-for-research", Kind.PHIAL_EXTRACT, JAR, "thaumcraft:phial_empty", 10, "aer", -1));
        actions.add(research("UNLOCKINFUSION", -1)); actions.add(research("UNLOCKINFUSION", 1)); actions.add(research("BASEINFUSION", -1));
        actions.add(action("theory-native-infusion", Kind.THEORY, THEORY, "", 32, "INFUSION", -1, item(-1, "paper", 64)));
        actions.add(research("INFUSION", -1)); actions.add(research("INFUSION", 1, item(-1, "stone", 1), item(-1, "feather", 1)));
        actions.add(action("craft-native-matrix", Kind.CRAFT, WORKBENCH, "thaumcraft:infusion_matrix", 1, "", -1,
            item(0, "thaumcraft:stone_arcane_brick", 1), item(2, "thaumcraft:stone_arcane_brick", 1),
            item(4, "thaumcraft:nitor_yellow", 1), item(6, "thaumcraft:stone_arcane_brick", 1), item(8, "thaumcraft:stone_arcane_brick", 1),
            crystal(9, "aer", 1), crystal(10, "ignis", 1), crystal(11, "aqua", 1), crystal(12, "terra", 1), crystal(13, "ordo", 1), crystal(14, "perditio", 1)));
        actions.add(research("INFUSION", 2)); actions.add(research("CRYSTALFARMER", -1));
        actions.add(research("CRYSTALFARMER", 1, PRIMALS.stream().map(tag -> crystal(-1, tag, 1)).toArray(Ingredient[]::new)));
        actions.add(action("place-native-matrix", Kind.USE_BLOCK, MATRIX.below(), "thaumcraft:infusion_matrix", 1, "", -1));
        actions.add(action("pedestal-real-air-crystal", Kind.PEDESTAL_PLACE, CENTER, "thaumcraft:crystal_essence", 1, "aer", -1, crystal(-1, "aer", 1)));
        actions.add(action("pedestal-real-seeds", Kind.PEDESTAL_PLACE, LEFT, "minecraft:wheat_seeds", 1, "", -1));
        actions.add(action("pedestal-real-dust", Kind.PEDESTAL_PLACE, RIGHT, "thaumcraft:salis_mundus", 1, "", -1));
        actions.add(action("activate-native-matrix", Kind.MATRIX_ACTIVATE, MATRIX, "thaumcraft:caster_basic", 1, "", -1));
        actions.add(action("start-native-infusion", Kind.MATRIX_START, MATRIX, "thaumcraft:caster_basic", 1, "", -1));
        actions.add(action("checkpoint-paid-native-infusion", Kind.CHECKPOINT, MATRIX, "", 0, "", -1));
        return List.copyOf(actions);
    }
    public static List<BlockPos> machinePositions() {
        ArrayList<BlockPos> values = new ArrayList<>(List.of(WORKBENCH, WORKBENCH.above(), CRUCIBLE, THEORY, STORAGE, STORAGE_PARTNER, MATRIX, CENTER, LEFT, RIGHT));
        for (BlockPos smelter : List.of(SMELTER, SMELTER_CRYSTAL, SMELTER_TRAP)) {
            values.add(smelter); values.add(smelter.above()); values.add(smelter.above().east()); values.add(smelter.above().east(2)); values.add(smelter.east(2));
        }
        for (int x : List.of(-1, 1)) for (int z : List.of(-1, 1)) values.add(MATRIX.offset(x, -2, z));
        return List.copyOf(values);
    }
}
