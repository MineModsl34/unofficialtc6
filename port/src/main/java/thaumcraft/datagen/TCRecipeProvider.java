package thaumcraft.datagen;

import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.data.recipes.SimpleCookingRecipeBuilder;
import net.minecraft.data.recipes.SpecialRecipeBuilder;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.common.Tags;
import thaumcraft.Thaumcraft;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.lib.TCTags;
import thaumcraft.common.lib.crafting.RecipeMagicDust;

/**
 * TC6's ConfigRecipes.initializeNormalRecipes / initializeSmelting, as blocks and items are ported.
 * OreDictionary ingredients are replaced by c: tags (ingotIron -> c:ingots/iron, stone -> c:stones and so on).
 * Recipe names are as in TC6, lower-cased.
 */
public class TCRecipeProvider extends RecipeProvider {
    public TCRecipeProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
        super(output, lookup);
    }

    private static String id(String name) {
        return Thaumcraft.MODID + ":" + name;
    }

    @Override
    protected void buildRecipes(RecipeOutput output) {
        TCArcaneWorkbenchRecipeProvider.generate(output);
        TCCrucibleRecipeProvider.generate(output);
        TCInfusionRecipeProvider.generate(output);
        TCAlchemyNormalRecipeProvider.generate(output);
        TCOrdinaryCustomRecipeProvider.generate(output);
        TCOptionalMetalRecipeProvider.generate(output);
        // ConfigRecipes:2760: distinct crystal components and reusable bowl/flint require
        // the source custom recipe, not an item-only shapeless substitute or research gate.
        SpecialRecipeBuilder.special(RecipeMagicDust::new).save(output, id("salismundus"));
        // --- nuggets and ingots ---
        oneToMany(output, ItemsTC.nuggetIron, Items.IRON_NUGGET, 1, "ironnuggetconvert");
        oneToMany(output, ItemsTC.ingotThaumium, ItemsTC.nuggetThaumium, 9, "thaumiumtonuggets");
        oneToMany(output, ItemsTC.ingotVoid, ItemsTC.nuggetVoid, 9, "voidtonuggets");
        oneToMany(output, ItemsTC.ingotBrass, ItemsTC.nuggetBrass, 9, "brasstonuggets");
        oneToMany(output, Items.QUARTZ, ItemsTC.nuggetQuartz, 9, "quartztonuggets");
        oneToMany(output, ItemsTC.quicksilver, ItemsTC.nuggetQuicksilver, 9, "quicksilvertonuggets");
        nineToOne(output, TCTags.Items.NUGGETS_THAUMIUM, ItemsTC.nuggetThaumium, ItemsTC.ingotThaumium, "nuggetstothaumium");
        nineToOne(output, TCTags.Items.NUGGETS_VOID, ItemsTC.nuggetVoid, ItemsTC.ingotVoid, "nuggetstovoid");
        nineToOne(output, TCTags.Items.NUGGETS_BRASS, ItemsTC.nuggetBrass, ItemsTC.ingotBrass, "nuggetstobrass");
        nineToOne(output, TCTags.Items.NUGGETS_QUICKSILVER, ItemsTC.nuggetQuicksilver, ItemsTC.quicksilver, "nuggetstoquicksilver");

        // --- metal blocks, flesh, amber ---
        nineToOne(output, ItemsTC.ingotThaumium, BlocksTC.metalBlockThaumium, "thaumiumingotstoblock");
        oneToMany(output, BlocksTC.metalBlockThaumium, ItemsTC.ingotThaumium, 9, "thaumiumblocktoingots");
        nineToOne(output, ItemsTC.ingotVoid, BlocksTC.metalBlockVoid, "voidingotstoblock");
        oneToMany(output, BlocksTC.metalBlockVoid, ItemsTC.ingotVoid, 9, "voidblocktoingots");
        nineToOne(output, ItemsTC.ingotBrass, BlocksTC.metalBlockBrass, "brassingotstoblock");
        oneToMany(output, BlocksTC.metalBlockBrass, ItemsTC.ingotBrass, 9, "brassblocktoingots");
        nineToOne(output, Items.ROTTEN_FLESH, BlocksTC.fleshBlock, "fleshtoblock");
        oneToMany(output, BlocksTC.fleshBlock, Items.ROTTEN_FLESH, 9, "blocktoflesh");
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, BlocksTC.amberBlock)
            .pattern("##").pattern("##").define('#', TCTags.Items.GEMS_AMBER)
            .unlockedBy("has_amber", has(TCTags.Items.GEMS_AMBER)).save(output, id("ambertoblock"));
        square(output, BlocksTC.amberBlock, BlocksTC.amberBrick, "amberblocktobrick");
        square(output, BlocksTC.amberBrick, BlocksTC.amberBlock, "amberbricktoblock");
        oneToMany(output, BlocksTC.amberBlock, ItemsTC.amber, 4, "amberblocktoamber");

        // --- plates ---
        plate(output, Tags.Items.INGOTS_IRON, Items.IRON_INGOT, ItemsTC.plateIron, "ironplate");
        plate(output, TCTags.Items.INGOTS_BRASS, ItemsTC.ingotBrass, ItemsTC.plateBrass, "brassplate");
        plate(output, TCTags.Items.INGOTS_THAUMIUM, ItemsTC.ingotThaumium, ItemsTC.plateThaumium, "thaumiumplate");
        plate(output, TCTags.Items.INGOTS_VOID, ItemsTC.ingotVoid, ItemsTC.plateVoid, "voidplate");

        // --- plants ---
        oneToMany(output, BlocksTC.shimmerleaf, ItemsTC.quicksilver, 1, "shimmerleaftoquicksilver");
        oneToMany(output, BlocksTC.cinderpearl, Items.BLAZE_POWDER, 1, "cinderpearltoblazepowder");

        // --- wood and stone ---
        oneToMany(output, BlocksTC.logGreatwood, BlocksTC.plankGreatwood, 4, "plankgreatwood");
        oneToMany(output, BlocksTC.logSilverwood, BlocksTC.plankSilverwood, 4, "planksilverwood");
        stairs(output, BlocksTC.plankGreatwood, BlocksTC.stairsGreatwood, "stairsgreatwood");
        stairs(output, BlocksTC.plankSilverwood, BlocksTC.stairsSilverwood, "stairssilverwood");
        stairs(output, BlocksTC.stoneArcane, BlocksTC.stairsArcane, "stairsarcane");
        stairs(output, BlocksTC.stoneArcaneBrick, BlocksTC.stairsArcaneBrick, "stairsarcanebrick");
        stairs(output, BlocksTC.stoneAncient, BlocksTC.stairsAncient, "stairsancient");
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, BlocksTC.stoneArcane, 9)
            .pattern("KKK").pattern("KCK").pattern("KKK")
            .define('K', Tags.Items.STONES).define('C', ItemsTC.crystalEssence)
            .unlockedBy("has_crystal", has(ItemsTC.crystalEssence)).save(output, id("stonearcane"));
        square(output, BlocksTC.stoneArcane, BlocksTC.stoneArcaneBrick, "brickarcane");
        slab(output, BlocksTC.plankGreatwood, BlocksTC.slabGreatwood, "slabgreatwood");
        slab(output, BlocksTC.plankSilverwood, BlocksTC.slabSilverwood, "slabsilverwood");
        slab(output, BlocksTC.stoneArcane, BlocksTC.slabArcaneStone, "slabarcanestone");
        slab(output, BlocksTC.stoneArcaneBrick, BlocksTC.slabArcaneBrick, "slabarcanebrick");
        slab(output, BlocksTC.stoneAncient, BlocksTC.slabAncient, "slabancient");
        slab(output, BlocksTC.stoneEldritchTile, BlocksTC.slabEldritch, "slabeldritch");
        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, BlocksTC.tableWood)
            .pattern("SSS").pattern("W W").define('S', ItemTags.WOODEN_SLABS).define('W', ItemTags.PLANKS)
            .unlockedBy("has_planks", has(ItemTags.PLANKS)).save(output, id("tablewood"));
        // Blocks.STONE_SLAB 1.12 (meta 0) is the 1.21 smooth stone slab
        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, BlocksTC.tableStone)
            .pattern("SSS").pattern("W W").define('S', Items.SMOOTH_STONE_SLAB).define('W', Tags.Items.STONES)
            .unlockedBy("has_stone", has(Tags.Items.STONES)).save(output, id("tablestone"));

        // ConfigRecipes:2913-2917: ordinary shapeless recipes, not research/arcane gates.
        // The real empty-phial path (scribingtoolscraft1) is emitted by TCAlchemyNormalRecipeProvider.
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ItemsTC.scribingTools)
            .requires(Items.GLASS_BOTTLE).requires(Items.FEATHER).requires(TCTags.Items.SCRIBING_INK)
            .group(id("inkwell")).unlockedBy("has_glass_bottle", has(Items.GLASS_BOTTLE))
            .save(output, id("scribingtoolscraft2"));
        // Item-only ingredient matches the original wildcard damage 32767 and ignores NBT.
        // A plain new result stack intentionally removes wear/custom data/name on refill.
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ItemsTC.scribingTools)
            .requires(ItemsTC.scribingTools).requires(TCTags.Items.SCRIBING_INK)
            .group(id("inkwell")).unlockedBy("has_scribing_tools", has(ItemsTC.scribingTools))
            .save(output, id("scribingtoolsrefill"));

        // --- candles ---
        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, BlocksTC.candles.get(DyeColor.WHITE), 3)
            .pattern(" S ").pattern(" T ").pattern(" T ").define('S', Tags.Items.STRINGS).define('T', ItemsTC.tallow)
            .group(id("tallowcandles"))
            .unlockedBy("has_tallow", has(ItemsTC.tallow)).save(output, id("tallowcandle"));
        for (DyeColor dye : DyeColor.values()) {
            ShapelessRecipeBuilder.shapeless(RecipeCategory.DECORATIONS, BlocksTC.candles.get(dye))
                .requires(dye.getTag()).requires(TCTags.Items.CANDLES)
                .group(id("tallowcandles"))
                .unlockedBy("has_candle", has(TCTags.Items.CANDLES)).save(output, id("tallowcandle" + dye.getName()));
        }

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ItemsTC.jarBrace, 2)
            .pattern("NSN").pattern("S S").pattern("NSN").define('N', TCTags.Items.NUGGETS_BRASS).define('S', Tags.Items.RODS_WOODEN)
            .unlockedBy("has_brass", has(TCTags.Items.NUGGETS_BRASS)).save(output, id("brassbrace"));

        // ConfigRecipes.initializeNormalRecipes: ordinary crafting, not research-gated arcane recipes.
        gear(output, TCTags.Items.INGOTS_THAUMIUM, ItemsTC.ingotThaumium,
            ItemsTC.thaumiumHelm, ItemsTC.thaumiumChest, ItemsTC.thaumiumLegs, ItemsTC.thaumiumBoots,
            ItemsTC.thaumiumSword, "thaumium");
        gear(output, TCTags.Items.INGOTS_VOID, ItemsTC.ingotVoid,
            ItemsTC.voidHelm, ItemsTC.voidChest, ItemsTC.voidLegs, ItemsTC.voidBoots,
            ItemsTC.voidSword, "void");
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ItemsTC.amuletMundane)
            .pattern(" S ").pattern("S S").pattern(" I ")
            .define('S', Tags.Items.STRINGS).define('I', TCTags.Items.INGOTS_BRASS)
            .group(id("baubles_stuff")).unlockedBy("has_brass", has(TCTags.Items.INGOTS_BRASS))
            .save(output, id("babuleamulet"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ItemsTC.ringMundane)
            .pattern("NNN").pattern("N N").pattern("NNN").define('N', TCTags.Items.NUGGETS_BRASS)
            .group(id("baubles_stuff")).unlockedBy("has_brass", has(TCTags.Items.NUGGETS_BRASS))
            .save(output, id("babulering"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ItemsTC.girdleMundane)
            .pattern(" L ").pattern("L L").pattern(" I ")
            .define('L', Tags.Items.LEATHERS).define('I', TCTags.Items.INGOTS_BRASS)
            .group(id("baubles_stuff")).unlockedBy("has_brass", has(TCTags.Items.INGOTS_BRASS))
            .save(output, id("babulegirdle"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ItemsTC.amuletFancy)
            .pattern(" S ").pattern("SGS").pattern(" I ")
            .define('S', Tags.Items.STRINGS).define('G', Tags.Items.GEMS_DIAMOND).define('I', Tags.Items.INGOTS_GOLD)
            .group(id("baubles_stuff")).unlockedBy("has_diamond", has(Tags.Items.GEMS_DIAMOND))
            .save(output, id("babuleamuletfancy"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ItemsTC.ringFancy)
            .pattern("NGN").pattern("N N").pattern("NNN")
            .define('N', Tags.Items.NUGGETS_GOLD).define('G', Tags.Items.GEMS_DIAMOND)
            .group(id("baubles_stuff")).unlockedBy("has_diamond", has(Tags.Items.GEMS_DIAMOND))
            .save(output, id("babuleringfancy"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ItemsTC.girdleFancy)
            .pattern(" L ").pattern("LGL").pattern(" I ")
            .define('L', Tags.Items.LEATHERS).define('G', Tags.Items.GEMS_DIAMOND).define('I', Tags.Items.INGOTS_GOLD)
            .group(id("baubles_stuff")).unlockedBy("has_diamond", has(Tags.Items.GEMS_DIAMOND))
            .save(output, id("babulegirdlefancy"));
        // Found vis amulet, apprentice ring and pearl are loot-only in this slice: no invented recipes.

        // --- GameRegistry.addSmelting (ConfigRecipes.initializeSmelting) ---
        smelt(output, BlocksTC.oreCinnabar, ItemsTC.quicksilver, 1, 1.0F, "quicksilver_from_ore");
        smelt(output, BlocksTC.oreAmber, ItemsTC.amber, 1, 1.0F, "amber_from_ore");
        smelt(output, BlocksTC.oreQuartz, Items.QUARTZ, 1, 1.0F, "quartz_from_ore");
        smelt(output, BlocksTC.logGreatwood, Items.CHARCOAL, 1, 0.5F, "charcoal_from_greatwood");
        smelt(output, BlocksTC.logSilverwood, Items.CHARCOAL, 1, 0.5F, "charcoal_from_silverwood");
        smelt(output, ItemsTC.clusterIron, Items.IRON_INGOT, 2, 1.0F, "iron_from_cluster");
        smelt(output, ItemsTC.clusterGold, Items.GOLD_INGOT, 2, 1.0F, "gold_from_cluster");
        smelt(output, ItemsTC.clusterCinnabar, ItemsTC.quicksilver, 2, 1.0F, "quicksilver_from_cluster");
        smelt(output, ItemsTC.clusterQuartz, Items.QUARTZ, 2, 1.0F, "quartz_from_cluster");
        // Optional metals use TCOptionalMetalRecipeProvider; smelting bonuses use their source catalogue.
    }

    private void gear(RecipeOutput output, net.minecraft.tags.TagKey<net.minecraft.world.item.Item> ingot,
                      ItemLike unlock, ItemLike helm, ItemLike chest, ItemLike legs, ItemLike boots,
                      ItemLike sword, String material) {
        gearArmor(output, ingot, unlock, helm, material + "helm", material, "III", "I I");
        gearArmor(output, ingot, unlock, chest, material + "chest", material, "I I", "III", "III");
        gearArmor(output, ingot, unlock, legs, material + "legs", material, "III", "I I", "I I");
        gearArmor(output, ingot, unlock, boots, material + "boots", material, "I I", "I I");
        ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, sword)
            .pattern("I").pattern("I").pattern("S").define('I', ingot).define('S', Tags.Items.RODS_WOODEN)
            .group(id(material + "_stuff")).unlockedBy(getHasName(unlock), has(ingot))
            .save(output, id(material + "sword"));
    }

    private void gearArmor(RecipeOutput output, net.minecraft.tags.TagKey<net.minecraft.world.item.Item> ingot,
                           ItemLike unlock, ItemLike result, String name, String material, String... pattern) {
        ShapedRecipeBuilder builder = ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, result);
        for (String row : pattern) builder.pattern(row);
        builder.define('I', ingot).group(id(material + "_stuff"))
            .unlockedBy(getHasName(unlock), has(ingot)).save(output, id(name));
    }

    private void oneToMany(RecipeOutput output, ItemLike in, ItemLike out, int count, String name) {
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, out, count).requires(in)
            .unlockedBy(getHasName(in), has(in)).save(output, id(name));
    }

    private void nineToOne(RecipeOutput output, ItemLike in, ItemLike out, String name) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, out).pattern("###").pattern("###").pattern("###").define('#', in)
            .unlockedBy(getHasName(in), has(in)).save(output, id(name));
    }

    private void nineToOne(RecipeOutput output, net.minecraft.tags.TagKey<net.minecraft.world.item.Item> in, ItemLike unlock, ItemLike out, String name) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, out).pattern("###").pattern("###").pattern("###").define('#', in)
            .unlockedBy(getHasName(unlock), has(in)).save(output, id(name));
    }

    private void square(RecipeOutput output, ItemLike in, ItemLike out, String name) {
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, out, 4).pattern("##").pattern("##").define('#', in)
            .unlockedBy(getHasName(in), has(in)).save(output, id(name));
    }

    private void plate(RecipeOutput output, net.minecraft.tags.TagKey<net.minecraft.world.item.Item> in, ItemLike unlock, ItemLike out, String name) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, out, 3).pattern("BBB").define('B', in)
            .unlockedBy(getHasName(unlock), has(in)).save(output, id(name));
    }

    private void stairs(RecipeOutput output, ItemLike in, ItemLike out, String name) {
        // In TC6 the pattern was "K  ", "KK ", "KKK"
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, out, 4).pattern("K  ").pattern("KK ").pattern("KKK").define('K', in)
            .unlockedBy(getHasName(in), has(in)).save(output, id(name));
    }

    private void slab(RecipeOutput output, ItemLike in, ItemLike out, String name) {
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, out, 6).pattern("KKK").define('K', in)
            .unlockedBy(getHasName(in), has(in)).save(output, id(name));
    }

    private void smelt(RecipeOutput output, ItemLike in, ItemLike out, int count, float xp, String name) {
        SimpleCookingRecipeBuilder.smelting(Ingredient.of(in), RecipeCategory.MISC, new ItemStack(out, count), xp, 200)
            .unlockedBy(getHasName(in), has(in))
            .save(output, id(name));
    }
}
