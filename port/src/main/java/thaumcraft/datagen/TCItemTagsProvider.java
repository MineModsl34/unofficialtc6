package thaumcraft.datagen;

import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.ItemTagsProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import thaumcraft.Thaumcraft;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.lib.TCTags;

/** TC6's OreDictionaryEntries -> c: tags. */
public class TCItemTagsProvider extends ItemTagsProvider {
    public TCItemTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup,
                              CompletableFuture<TagLookup<Block>> blockTags, ExistingFileHelper files) {
        super(output, lookup, blockTags, Thaumcraft.MODID, files);
    }

    private static TagKey<Item> c(String path) {
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", path));
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        tag(TCTags.Items.SCAN_PAPER).add(Items.PAPER);
        tag(ItemTags.WOLF_FOOD).add(ItemsTC.brain,ItemsTC.tripleMeatTreat);
        // Forge 1.12 OreDictionary dyeBlack default was Items.DYE metadata 0 (ink sac).
        // Addons may opt into this explicit bridge; do not silently accept BLACK_DYE.
        tag(TCTags.Items.SCRIBING_INK).add(Items.INK_SAC);
        // Source DustTriggerOre inspects the block's ItemStack, not a modern block tag.
        // Addon bookshelves may opt in; chiseled bookshelves are not silently included.
        tag(c("bookshelves")).add(Items.BOOKSHELF);
        tag(c("workbenches")).add(Items.CRAFTING_TABLE);
        tag(c("nitor")).add(thaumcraft.api.blocks.BlocksTC.nitor.values().stream().map(Block::asItem).toArray(Item[]::new));
        // Remaining OreDictionaryEntries BETA26 categories, including every old meat meta.
        tag(c("quicksilver")).add(ItemsTC.quicksilver);
        tag(c("nuggets/meat")).add(thaumcraft.common.items.consumables.ItemChunksEdible.VARIANTS.stream()
            .map(name -> net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID,"chunk_" + name))).toArray(Item[]::new));
        String[] clusterMetals = {"iron","gold","copper","tin","silver","lead","cinnabar","quartz"};
        Item[] clusters = {ItemsTC.clusterIron,ItemsTC.clusterGold,ItemsTC.clusterCopper,ItemsTC.clusterTin,ItemsTC.clusterSilver,ItemsTC.clusterLead,ItemsTC.clusterCinnabar,ItemsTC.clusterQuartz};
        for (int index = 0; index < clusterMetals.length; index++) tag(c("clusters/" + clusterMetals[index])).add(clusters[index]);
        String[] crystalNames = {"air","earth","water","fire","order","entropy","taint"};
        Block[] crystals = {thaumcraft.api.blocks.BlocksTC.crystalAir,thaumcraft.api.blocks.BlocksTC.crystalEarth,
            thaumcraft.api.blocks.BlocksTC.crystalWater,thaumcraft.api.blocks.BlocksTC.crystalFire,
            thaumcraft.api.blocks.BlocksTC.crystalOrder,thaumcraft.api.blocks.BlocksTC.crystalEntropy,thaumcraft.api.blocks.BlocksTC.crystalTaint};
        for (int index = 0; index < crystalNames.length; index++) tag(c("ores/crystal_" + crystalNames[index])).add(crystals[index].asItem());
        copy(TCTags.Blocks.ORES_AMBER, TCTags.Items.ORES_AMBER);
        copy(TCTags.Blocks.ORES_CINNABAR, TCTags.Items.ORES_CINNABAR);
        copy(Tags.Blocks.ORES_QUARTZ, Tags.Items.ORES_QUARTZ);
        copy(Tags.Blocks.ORES, Tags.Items.ORES);
        copy(Tags.Blocks.ORES_IN_GROUND_STONE, Tags.Items.ORES_IN_GROUND_STONE);
        copy(TCTags.Blocks.GREATWOOD_LOGS, TCTags.Items.GREATWOOD_LOGS);
        copy(TCTags.Blocks.SILVERWOOD_LOGS, TCTags.Items.SILVERWOOD_LOGS);
        copy(BlockTags.LOGS, ItemTags.LOGS);
        copy(BlockTags.PLANKS, ItemTags.PLANKS);
        copy(BlockTags.LEAVES, ItemTags.LEAVES);
        copy(BlockTags.SAPLINGS, ItemTags.SAPLINGS);
        copy(BlockTags.SMALL_FLOWERS, ItemTags.SMALL_FLOWERS);
        copy(BlockTags.STAIRS, ItemTags.STAIRS);
        copy(BlockTags.WOODEN_STAIRS, ItemTags.WOODEN_STAIRS);
        copy(BlockTags.SLABS, ItemTags.SLABS);
        copy(BlockTags.WOODEN_SLABS, ItemTags.WOODEN_SLABS);
        copy(BlockTags.DIRT, ItemTags.DIRT);
        copy(TCTags.Blocks.CANDLES, TCTags.Items.CANDLES);
        copy(TCTags.Blocks.STORAGE_THAUMIUM, TCTags.Items.STORAGE_THAUMIUM);
        copy(TCTags.Blocks.STORAGE_VOID, TCTags.Items.STORAGE_VOID);
        copy(TCTags.Blocks.STORAGE_BRASS, TCTags.Items.STORAGE_BRASS);
        copy(Tags.Blocks.STORAGE_BLOCKS, Tags.Items.STORAGE_BLOCKS);

        tag(TCTags.Items.GEMS_AMBER).add(ItemsTC.amber);
        tag(Tags.Items.GEMS).addTag(TCTags.Items.GEMS_AMBER);
        tag(c("ingots/thaumium")).add(ItemsTC.ingotThaumium);
        tag(c("ingots/void")).add(ItemsTC.ingotVoid);
        tag(c("ingots/brass")).add(ItemsTC.ingotBrass);
        tag(Tags.Items.INGOTS).addTag(c("ingots/thaumium")).addTag(c("ingots/void")).addTag(c("ingots/brass"));
        String[] nuggets = {"iron", "copper", "tin", "silver", "lead", "quicksilver", "thaumium", "void", "brass", "quartz"};
        Item[] nuggetItems = {ItemsTC.nuggetIron, ItemsTC.nuggetCopper, ItemsTC.nuggetTin, ItemsTC.nuggetSilver, ItemsTC.nuggetLead,
            ItemsTC.nuggetQuicksilver, ItemsTC.nuggetThaumium, ItemsTC.nuggetVoid, ItemsTC.nuggetBrass, ItemsTC.nuggetQuartz};
        var allNuggets = tag(Tags.Items.NUGGETS);
        for (int i = 0; i < nuggets.length; i++) {
            TagKey<Item> t = c("nuggets/" + nuggets[i]);
            tag(t).add(nuggetItems[i]);
            allNuggets.addTag(t);
        }
        tag(c("plates/iron")).add(ItemsTC.plateIron);
        tag(c("plates/brass")).add(ItemsTC.plateBrass);
        tag(c("plates/thaumium")).add(ItemsTC.plateThaumium);
        tag(c("plates/void")).add(ItemsTC.plateVoid);
        tag(TagKey.create(Registries.ITEM, ResourceLocation.parse("curios:necklace")))
            .add(ItemsTC.amuletMundane, ItemsTC.amuletFancy, ItemsTC.amuletVis,ItemsTC.amuletVisCrafted);
        tag(TagKey.create(Registries.ITEM, ResourceLocation.parse("curios:ring")))
            .add(ItemsTC.ringMundane, ItemsTC.ringFancy, ItemsTC.ringApprentice,ItemsTC.ringCloud);
        tag(TagKey.create(Registries.ITEM, ResourceLocation.parse("curios:belt")))
            .add(ItemsTC.girdleMundane, ItemsTC.girdleFancy,ItemsTC.focusPouch);
        tag(ItemTags.SWORDS).add(ItemsTC.thaumiumSword, ItemsTC.voidSword);
        tag(ItemTags.HEAD_ARMOR).add(ItemsTC.thaumiumHelm, ItemsTC.voidHelm);
        tag(ItemTags.CHEST_ARMOR).add(ItemsTC.thaumiumChest, ItemsTC.voidChest);
        tag(ItemTags.LEG_ARMOR).add(ItemsTC.thaumiumLegs, ItemsTC.voidLegs);
        tag(ItemTags.FOOT_ARMOR).add(ItemsTC.thaumiumBoots, ItemsTC.voidBoots);
        tag(ItemTags.HEAD_ARMOR_ENCHANTABLE).add(ItemsTC.thaumiumHelm, ItemsTC.voidHelm);
        tag(ItemTags.CHEST_ARMOR_ENCHANTABLE).add(ItemsTC.thaumiumChest, ItemsTC.voidChest);
        tag(ItemTags.LEG_ARMOR_ENCHANTABLE).add(ItemsTC.thaumiumLegs, ItemsTC.voidLegs);
        tag(ItemTags.FOOT_ARMOR_ENCHANTABLE).add(ItemsTC.thaumiumBoots, ItemsTC.voidBoots);
        tag(ItemTags.DURABILITY_ENCHANTABLE).add(ItemsTC.thaumiumSword, ItemsTC.voidSword,
            ItemsTC.thaumiumHelm, ItemsTC.thaumiumChest, ItemsTC.thaumiumLegs, ItemsTC.thaumiumBoots,
            ItemsTC.voidHelm, ItemsTC.voidChest, ItemsTC.voidLegs, ItemsTC.voidBoots);
        thaumcraft.common.items.tools.EquipmentRegistries.itemTags().forEach((key,items) -> tag(key).add(items));
        for (Item item : net.minecraft.core.registries.BuiltInRegistries.ITEM) if (net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(Thaumcraft.MODID) && item instanceof net.minecraft.world.item.ArmorItem armor) {
            TagKey<Item> slot = switch (armor.getType()) {
                case HELMET -> ItemTags.HEAD_ARMOR; case CHESTPLATE -> ItemTags.CHEST_ARMOR; case LEGGINGS -> ItemTags.LEG_ARMOR; case BOOTS -> ItemTags.FOOT_ARMOR;
                default -> null;
            };
            TagKey<Item> enchanting = switch (armor.getType()) {
                case HELMET -> ItemTags.HEAD_ARMOR_ENCHANTABLE; case CHESTPLATE -> ItemTags.CHEST_ARMOR_ENCHANTABLE; case LEGGINGS -> ItemTags.LEG_ARMOR_ENCHANTABLE; case BOOTS -> ItemTags.FOOT_ARMOR_ENCHANTABLE;
                default -> null;
            };
            if (slot != null) tag(slot).add(item); if (enchanting != null) tag(enchanting).add(item); tag(ItemTags.DURABILITY_ENCHANTABLE).add(item);
        }
    }
}
