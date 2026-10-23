package thaumcraft.common.config;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.common.lib.crafting.ThaumcraftCraftingManager;

/**
 * Not in TC6 (there addons registered their own items, and the Ore Dictionary covered metals).
 * Other mods' items without aspects get them from common tags: a log like oak, a stone like stone,
 * an ingot by its material. The rules only fill gaps: explicit values of TC and addons are not touched,
 * and everything crafted from these items is then derived from recipes as usual. Order: from specific to general.
 */
public final class ConfigTagAspects {
    private ConfigTagAspects() {
    }

    public static void fill() {
        // natural blocks and plants, like the vanilla ones
        like("minecraft:logs", Items.OAK_LOG);
        like("minecraft:planks", Items.OAK_PLANKS);
        like("minecraft:leaves", Items.OAK_LEAVES);
        like("minecraft:saplings", Items.OAK_SAPLING);
        like("minecraft:small_flowers", Items.POPPY);
        like("minecraft:tall_flowers", Items.ROSE_BUSH);
        like("minecraft:flowers", Items.POPPY);
        like("c:mushrooms", Items.RED_MUSHROOM);
        like("c:seeds", Items.WHEAT_SEEDS);
        like("minecraft:dirt", Items.DIRT);
        like("c:cobblestones", Items.COBBLESTONE);
        like("c:stones", Items.STONE);
        like("minecraft:base_stone_overworld", Items.STONE);
        like("minecraft:base_stone_nether", Items.NETHERRACK);
        like("c:netherracks", Items.NETHERRACK);
        like("c:end_stones", Items.END_STONE);
        like("c:obsidians", Items.OBSIDIAN);
        like("c:sands", Items.SAND);
        like("c:gravels", Items.GRAVEL);
        like("c:glass_blocks", Items.GLASS);
        like("c:glass_panes", Items.GLASS_PANE);
        like("minecraft:wool", Items.WHITE_WOOL);
        like("c:rods/wooden", Items.STICK);
        like("c:strings", Items.STRING);
        like("c:leathers", Items.LEATHER);
        like("c:feathers", Items.FEATHER);
        like("c:bones", Items.BONE);
        like("c:slime_balls", Items.SLIME_BALL);
        like("c:ender_pearls", Items.ENDER_PEARL);
        like("c:gunpowders", Items.GUNPOWDER);
        like("c:foods/raw_meat", Items.BEEF);
        like("c:foods/cooked_meat", Items.COOKED_BEEF);
        like("c:foods/raw_fish", Items.COD);
        like("c:foods/cooked_fish", Items.COOKED_COD);
        like("c:foods/fruit", Items.APPLE);
        like("c:foods/berry", Items.SWEET_BERRIES);
        like("c:foods/vegetable", Items.CARROT);
        like("c:crops", Items.WHEAT);

        // metals without values in TC: ingot, dust, ore, raw; nuggets and blocks are derived from crafting
        metal("steel", al(Aspect.METAL, 15).add(Aspect.ORDER, 5));
        metal("bronze", al(Aspect.METAL, 10).add(Aspect.TOOL, 5));
        metal("aluminum", al(Aspect.METAL, 10).add(Aspect.AIR, 5));
        metal("aluminium", al(Aspect.METAL, 10).add(Aspect.AIR, 5));
        metal("zinc", al(Aspect.METAL, 10).add(Aspect.ENTROPY, 3));
        metal("nickel", al(Aspect.METAL, 10).add(Aspect.ORDER, 3));
        metal("uranium", al(Aspect.METAL, 10).add(Aspect.ENERGY, 10).add(Aspect.DEATH, 5));
        metal("titanium", al(Aspect.METAL, 15).add(Aspect.PROTECT, 5));
        metal("cobalt", al(Aspect.METAL, 15).add(Aspect.MOTION, 5));
        metal("meteoric_iron", al(Aspect.METAL, 15).add(Aspect.VOID, 5));
        metal("desh", al(Aspect.METAL, 15).add(Aspect.FIRE, 5));
        metal("abyssalnite", al(Aspect.METAL, 15).add(Aspect.DARKNESS, 5));
        metal("refined_coralium", al(Aspect.METAL, 15).add(Aspect.WATER, 5).add(Aspect.FLUX, 3));
        metal("dreadium", al(Aspect.METAL, 15).add(Aspect.DEATH, 5));
        metal("ethaxium", al(Aspect.METAL, 20).add(Aspect.ELDRITCH, 5));
        metal("knightmetal", al(Aspect.METAL, 15).add(Aspect.PROTECT, 5));
        gem("ruby", al(Aspect.CRYSTAL, 10).add(Aspect.DESIRE, 10).add(Aspect.FIRE, 3));
        gem("sapphire", al(Aspect.CRYSTAL, 10).add(Aspect.DESIRE, 10).add(Aspect.WATER, 3));
        gem("apatite", al(Aspect.CRYSTAL, 5).add(Aspect.LIFE, 3));
        gem("zanite", al(Aspect.CRYSTAL, 10).add(Aspect.AIR, 5));
        gem("ambrosium", al(Aspect.LIFE, 5).add(Aspect.FIRE, 3));
        gem("coralium", al(Aspect.CRYSTAL, 10).add(Aspect.WATER, 5).add(Aspect.FLUX, 3));
        gem("shadow", al(Aspect.CRYSTAL, 10).add(Aspect.DARKNESS, 10));
        gem("certus_quartz", al(Aspect.CRYSTAL, 10).add(Aspect.ENERGY, 3));
        gem("fluix", al(Aspect.CRYSTAL, 10).add(Aspect.ENERGY, 5));
        like("c:dusts/sulfur", Items.GUNPOWDER);
        tag("c:dusts/saltpeter", al(Aspect.EARTH, 3).add(Aspect.FIRE, 3));

        // plates, gears, rods, dusts of any material, by its ingot or gem (duplicates that Almost
        // Unified removes from recipes, and parts from other mods' machines)
        shapes("plates", 1);
        shapes("gears", 4);
        shapes("rods", 1);
        shapes("dusts", 1);

        // everything else by item kind
        tag("c:ores", al(Aspect.EARTH, 5).add(Aspect.METAL, 5));
        tag("c:raw_materials", al(Aspect.METAL, 10).add(Aspect.EARTH, 3));
        tag("c:ingots", al(Aspect.METAL, 10));
        tag("c:gems", al(Aspect.CRYSTAL, 10));
        tag("c:dusts", al(Aspect.ENTROPY, 3).add(Aspect.EARTH, 2));
        tag("c:nuggets", al(Aspect.METAL, 1));
    }

    private static void shapes(String shape, int ingots) {
        String prefix = "c:" + shape + "/";
        BuiltInRegistries.ITEM.getTags().map(pair -> pair.getFirst().location().toString()).filter(id -> id.startsWith(prefix)).toList()
            .forEach(id -> {
                String material = id.substring(prefix.length());
                AspectList base = first("c:ingots/" + material);
                if (base == null) base = first("c:gems/" + material);
                if (base == null) return;
                AspectList out = new AspectList();
                for (Aspect aspect : base.getAspects()) out.add(aspect, base.getAmount(aspect) * ingots);
                tag(id, out);
            });
    }

    /** Aspects of the first item in the tag that has them. */
    private static AspectList first(String tag) {
        for (var holder : BuiltInRegistries.ITEM.getTagOrEmpty(TagKey.create(Registries.ITEM, ResourceLocation.parse(tag)))) {
            AspectList aspects = ThaumcraftCraftingManager.getObjectTags(new ItemStack(holder));
            if (aspects != null && aspects.size() > 0) return aspects;
        }
        return null;
    }

    private static void metal(String material, AspectList ingot) {
        tag("c:ingots/" + material, ingot);
        tag("c:dusts/" + material, ingot.copy().add(Aspect.ENTROPY, 1));
        tag("c:ores/" + material, ingot.copy().add(Aspect.EARTH, 5));
        tag("c:raw_materials/" + material, ingot.copy().add(Aspect.EARTH, 3));
    }

    private static void gem(String material, AspectList gem) {
        tag("c:gems/" + material, gem);
        tag("c:ores/" + material, gem.copy().add(Aspect.EARTH, 5));
    }

    /** Items of the tag without aspects get the aspects of the vanilla sample. */
    private static void like(String tag, Item sample) {
        AspectList aspects = ThaumcraftCraftingManager.getObjectTags(new ItemStack(sample));
        if (aspects != null && aspects.size() > 0) tag(tag, aspects);
    }

    private static void tag(String tag, AspectList aspects) {
        TagKey<Item> key = TagKey.create(Registries.ITEM, ResourceLocation.parse(tag));
        for (var holder : BuiltInRegistries.ITEM.getTagOrEmpty(key)) {
            ItemStack stack = new ItemStack(holder);
            // an empty entry is a cached failed derivation and may be replaced
            AspectList known = ThaumcraftApi.exists(stack) ? ThaumcraftCraftingManager.getObjectTags(stack) : null;
            if (known == null || known.size() == 0) ThaumcraftApi.registerObjectTag(stack, aspects.copy());
        }
    }

    private static AspectList al(Aspect aspect, int amount) {
        return new AspectList().add(aspect, amount);
    }
}
