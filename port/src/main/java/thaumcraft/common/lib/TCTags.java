package thaumcraft.common.lib;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import thaumcraft.Thaumcraft;

/** The mod's tags. They replace the 1.12 Material and OreDictionary checks. */
public final class TCTags {
    private TCTags() {
    }

    public static final class Blocks {
        /** Analogue of Material.ROCK: what vis crystals attach to and grow on. */
        public static final TagKey<Block> CRYSTAL_SUPPORT = tc("crystal_support");
        /** 1.12 BlockStone and its three meta variants, plus deepslate and tuff: TC ores start at the 1.21 world bottom. */
        public static final TagKey<Block> TC_ORE_REPLACEABLES = tc("tc_ore_replaceables");
        public static final TagKey<Block> GREATWOOD_LOGS = tc("greatwood_logs");
        public static final TagKey<Block> SILVERWOOD_LOGS = tc("silverwood_logs");
        public static final TagKey<Block> CINDERPEARL_SOIL = tc("cinderpearl_soil");
        public static final TagKey<Block> ORES_AMBER = c("ores/amber");
        public static final TagKey<Block> ORES_CINNABAR = c("ores/cinnabar");
        public static final TagKey<Block> CANDLES = tc("candles");
        public static final TagKey<Block> STORAGE_THAUMIUM = c("storage_blocks/thaumium");
        public static final TagKey<Block> STORAGE_VOID = c("storage_blocks/void");
        public static final TagKey<Block> STORAGE_BRASS = c("storage_blocks/brass");

        private static TagKey<Block> tc(String path) {
            return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, path));
        }

        private static TagKey<Block> c(String path) {
            return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("c", path));
        }
    }

    public static final class Items {
        /** Explicit datapack bridge for source Forge OreDictionary's paper scan supplies. */
        public static final TagKey<Item> SCAN_PAPER = tc("legacy_scan_paper");
        /** Old dyeBlack crafting ingredient: vanilla ink sac, not modern black dye. */
        public static final TagKey<Item> SCRIBING_INK = tc("legacy_scribing_ink");
        public static final TagKey<Item> GREATWOOD_LOGS = tc("greatwood_logs");
        public static final TagKey<Item> SILVERWOOD_LOGS = tc("silverwood_logs");
        public static final TagKey<Item> ORES_AMBER = c("ores/amber");
        public static final TagKey<Item> ORES_CINNABAR = c("ores/cinnabar");
        public static final TagKey<Item> GEMS_AMBER = c("gems/amber");
        public static final TagKey<Item> CANDLES = tc("candles");
        public static final TagKey<Item> STORAGE_THAUMIUM = c("storage_blocks/thaumium");
        public static final TagKey<Item> STORAGE_VOID = c("storage_blocks/void");
        public static final TagKey<Item> STORAGE_BRASS = c("storage_blocks/brass");
        public static final TagKey<Item> INGOTS_THAUMIUM = c("ingots/thaumium");
        public static final TagKey<Item> INGOTS_VOID = c("ingots/void");
        public static final TagKey<Item> INGOTS_BRASS = c("ingots/brass");
        public static final TagKey<Item> NUGGETS_THAUMIUM = c("nuggets/thaumium");
        public static final TagKey<Item> NUGGETS_VOID = c("nuggets/void");
        public static final TagKey<Item> NUGGETS_BRASS = c("nuggets/brass");
        public static final TagKey<Item> NUGGETS_QUICKSILVER = c("nuggets/quicksilver");

        private static TagKey<Item> tc(String path) {
            return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, path));
        }

        private static TagKey<Item> c(String path) {
            return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", path));
        }
    }
}
