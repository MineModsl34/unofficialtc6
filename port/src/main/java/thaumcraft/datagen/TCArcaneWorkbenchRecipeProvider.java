package thaumcraft.datagen;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.neoforged.neoforge.common.Tags;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.crafting.ShapelessArcaneRecipe;
import thaumcraft.common.lib.crafting.RecipeArcaneWorkbench;

/** Executable source ConfigRecipes arcane recipes whose outputs/ingredients already
 * exist in this port. Future devices/gear are NOT replaced with placeholder items.
 * Original mixed-case registry paths are flattened to lowercase modern legal IDs.
 * This is separate from the read-only full original recipe-display catalog.
 */
public final class TCArcaneWorkbenchRecipeProvider extends RecipeProvider {
    public TCArcaneWorkbenchRecipeProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) { super(output, lookup); }
    @Override protected void buildRecipes(RecipeOutput output) { generate(output); }
    public static void generate(RecipeOutput output) {
        output.accept(ResourceLocation.fromNamespaceAndPath("thaumcraft", "thaumometer"),
            new RecipeArcaneWorkbench(ShapedRecipePattern.of(Map.of('I', Ingredient.of(Tags.Items.INGOTS_GOLD), 'G', Ingredient.of(Items.GLASS_PANE)), " I ", "IGI", " I "),
                new ItemStack(ItemsTC.thaumometer), "FIRSTSTEPS@2", 20, List.of(1, 1, 1, 1, 1, 1)), null);
        shaped(output, "mechanism_simple", new ItemStack(ItemsTC.mechanismSimple), "BASEARTIFICE", 10, List.of(0, 1, 1, 0, 0, 0),
            Map.of('B', common("plates/brass"), 'I', common("plates/iron"), 'S', Ingredient.of(Tags.Items.RODS_WOODEN)), " B ", "ISI", " B ");
        shaped(output, "mechanism_complex", new ItemStack(ItemsTC.mechanismComplex), "BASEARTIFICE", 50, List.of(0, 1, 1, 0, 0, 0),
            Map.of('T', common("plates/thaumium"), 'Q', Ingredient.of(Items.PISTON), 'M', Ingredient.of(ItemsTC.mechanismSimple)), " M ", "TQT", " M ");
        shapeless(output, "vis_resonator", new ItemStack(ItemsTC.visResonator), "UNLOCKAUROMANCY@2", 50, List.of(1, 0, 1, 0, 0, 0),
            common("plates/iron"), common("gems/quartz"));
        shaped(output, "workbenchcharger", new ItemStack(BlocksTC.arcaneWorkbenchCharger), "WORKBENCHCHARGER", 200, List.of(2, 0, 0, 0, 2, 0),
            Map.of('I', Ingredient.of(Tags.Items.INGOTS_IRON), 'R', Ingredient.of(ItemsTC.visResonator), 'W', Ingredient.of(BlocksTC.plankGreatwood)), " R ", "W W", "I I");
        shaped(output, "caster_basic", new ItemStack(ItemsTC.casterBasic), "UNLOCKAUROMANCY@2", 100, List.of(1, 1, 1, 1, 1, 1),
            Map.of('I', Ingredient.of(Tags.Items.INGOTS_IRON), 'L', common("leathers"), 'R', Ingredient.of(ItemsTC.visResonator), 'T', Ingredient.of(ItemsTC.thaumometer)), "III", "LRL", "LTL");
        shaped(output, "enchantedfabric", new ItemStack(ItemsTC.fabric), "UNLOCKINFUSION", 5, List.of(0, 0, 0, 0, 0, 0),
            Map.of('S', Ingredient.of(Tags.Items.STRINGS), 'C', Ingredient.of(ItemTags.WOOL)), " S ", "SCS", " S ");
        shapeless(output, "mirrorglass", new ItemStack(ItemsTC.mirroredGlass), "BASEARTIFICE", 50, List.of(0, 0, 1, 0, 1, 0),
            Ingredient.of(ItemsTC.quicksilver), common("glass_panes"));
        shaped(output, "modvision", new ItemStack(ItemsTC.moduleVision), "GOLEMVISION", 50, List.of(0, 0, 1, 0, 0, 0),
            Map.of('B', Ingredient.of(Items.GLASS_BOTTLE), 'E', Ingredient.of(Items.FERMENTED_SPIDER_EYE), 'P', common("plates/brass"), 'G', Ingredient.of(ItemsTC.mechanismSimple)), "B B", "E E", "PGP");
        shaped(output, "modaggression", new ItemStack(ItemsTC.moduleAggression), "SEALGUARD", 50, List.of(0, 1, 0, 0, 0, 0),
            Map.of('R', common("glass_panes"), 'T', Ingredient.of(Items.BLAZE_POWDER), 'P', common("plates/brass"), 'G', Ingredient.of(ItemsTC.mechanismSimple)), " R ", "RTR", "PGP");
        shaped(output, "pavebarrier", new ItemStack(BlocksTC.pavingStoneBarrier, 4), "PAVINGSTONES", 50, List.of(0, 1, 0, 0, 1, 0),
            Map.of('S', Ingredient.of(BlocksTC.stoneArcaneBrick)), "SS", "SS");
        shaped(output, "pavetravel", new ItemStack(BlocksTC.pavingStoneTravel, 4), "PAVINGSTONES", 50, List.of(1, 0, 0, 1, 0, 0),
            Map.of('S', Ingredient.of(BlocksTC.stoneArcaneBrick)), "SS", "SS");
        shaped(output, "filter", new ItemStack(ItemsTC.filter, 2), "BASEALCHEMY", 15, List.of(0, 0, 1, 0, 0, 0),
            Map.of('G', Ingredient.of(Items.GOLD_INGOT), 'W', Ingredient.of(BlocksTC.plankSilverwood)), "GWG");
        shaped(output, "morphicresonator", new ItemStack(ItemsTC.morphicResonator), "BASEALCHEMY", 50, List.of(1, 1, 0, 0, 0, 0),
            Map.of('G', common("glass_panes"), 'B', common("plates/brass"), 'S', Ingredient.of(ItemsTC.nuggetRareEarth)), " G ", "BSB", " G ");
        shaped(output, "wardedjar", new ItemStack(BlocksTC.jarNormal), "WARDEDJARS", 5, List.of(0, 0, 0, 0, 0, 0),
            Map.of('W', Ingredient.of(ItemTags.WOODEN_SLABS), 'G', common("glass_panes")), "GWG", "G G", "GGG");
        output.accept(ResourceLocation.parse("thaumcraft:jarvoid"), new thaumcraft.common.lib.crafting.ShapedArcaneVoidJar(null,
            "WARDEDJARS", 50, new thaumcraft.api.aspects.AspectList().add(thaumcraft.api.aspects.Aspect.ENTROPY, 1),
            new ItemStack(BlocksTC.jarVoid), "J", 'J', BlocksTC.jarNormal), null);
        shaped(output, "infusionmatrix", new ItemStack(BlocksTC.infusionMatrix), "INFUSION@2", 150, List.of(1, 1, 1, 1, 1, 1),
            Map.of('S', Ingredient.of(BlocksTC.stoneArcaneBrick), 'N', common("nitor")), "S S", " N ", "S S");
        shaped(output, "arcanepedestal", new ItemStack(BlocksTC.pedestalArcane), "INFUSION", 10, List.of(0, 0, 0, 0, 0, 0),
            Map.of('S', Ingredient.of(BlocksTC.slabArcaneStone), 'B', Ingredient.of(BlocksTC.stoneArcane)), "SSS", " B ", "SSS");
        shaped(output, "ancientpedestal", new ItemStack(BlocksTC.pedestalAncient), "INFUSIONANCIENT", 150, List.of(0, 0, 0, 0, 0, 0),
            Map.of('S', Ingredient.of(BlocksTC.slabAncient), 'B', Ingredient.of(BlocksTC.stoneAncient)), "SSS", " B ", "SSS");
        shaped(output, "eldritchpedestal", new ItemStack(BlocksTC.pedestalEldritch), "INFUSIONELDRITCH", 150, List.of(0, 0, 0, 0, 0, 0),
            Map.of('S', Ingredient.of(BlocksTC.slabEldritch), 'B', Ingredient.of(BlocksTC.stoneEldritchTile)), "SSS", " B ", "SSS");
        shaped(output, "matrixmotion", new ItemStack(BlocksTC.matrixSpeed), "INFUSIONBOOST", 500, List.of(1, 0, 0, 0, 1, 0),
            Map.of('S', Ingredient.of(BlocksTC.stoneArcane), 'N', common("nitor"), 'G', Ingredient.of(Items.DIAMOND_BLOCK)), "SNS", "NGN", "SNS");
        shaped(output, "matrixcost", new ItemStack(BlocksTC.matrixCost), "INFUSIONBOOST", 500, List.of(1, 0, 1, 0, 0, 1),
            Map.of('S', Ingredient.of(BlocksTC.stoneArcane), 'A', Ingredient.of(ItemsTC.alumentum), 'G', Ingredient.of(Items.DIAMOND_BLOCK)), "SAS", "AGA", "SAS");
        shapeless(output, "redstoneinlay", new ItemStack(BlocksTC.inlay, 2), "INFUSIONSTABLE", 25, List.of(0, 0, 1, 0, 0, 0),
            Ingredient.of(Tags.Items.DUSTS_REDSTONE), Ingredient.of(Tags.Items.INGOTS_GOLD));
        shaped(output, "stabilizer", new ItemStack(BlocksTC.stabilizer), "INFUSIONSTABLE", 250, List.of(0, 0, 1, 1, 0, 1),
            Map.of('R', Ingredient.of(Items.REDSTONE_BLOCK), 'S', Ingredient.of(BlocksTC.slabArcaneStone), 'B', Ingredient.of(BlocksTC.stoneArcane),
                'M', Ingredient.of(ItemsTC.mechanismComplex), 'V', Ingredient.of(ItemsTC.visResonator), 'I', Ingredient.of(BlocksTC.inlay)), "SRS", "BVB", "IMI");
        shaped(output, "alembic", new ItemStack(BlocksTC.alembic), "ESSENTIASMELTER", 50, List.of(0, 0, 1, 0, 0, 0),
            Map.of('W', Ingredient.of(BlocksTC.plankGreatwood), 'B', Ingredient.of(Items.BUCKET), 'F', Ingredient.of(ItemsTC.filter), 'S', common("plates/brass")), "WFW", "SBS", "WFW");
        shaped(output, "essentiasmelter", new ItemStack(BlocksTC.smelterBasic), "ESSENTIASMELTER@2", 50, List.of(0, 1, 0, 0, 0, 0),
            Map.of('C', Ingredient.of(BlocksTC.crucible), 'F', Ingredient.of(Items.FURNACE), 'S', common("cobblestones"), 'B', common("plates/brass")), "BCB", "SFS", "SSS");
        shaped(output, "essentiasmelterthaumium", new ItemStack(BlocksTC.smelterThaumium), "ESSENTIASMELTERTHAUMIUM", 250, List.of(0, 2, 0, 0, 0, 0),
            Map.of('F', Ingredient.of(BlocksTC.smelterBasic), 'G', Ingredient.of(BlocksTC.metalAlchemical), 'I', common("plates/thaumium"), 'B', common("plates/brass")), "BFB", "IGI", "III");
        shaped(output, "essentiasmeltervoid", new ItemStack(BlocksTC.smelterVoid), "ESSENTIASMELTERVOID", 750, List.of(0, 3, 0, 0, 0, 0),
            Map.of('F', Ingredient.of(BlocksTC.smelterBasic), 'G', Ingredient.of(BlocksTC.metalAlchemicalAdvanced), 'I', common("plates/void"), 'B', common("plates/brass")), "BFB", "IGI", "III");
        shaped(output, "advalchemyconstruct", new ItemStack(BlocksTC.metalAlchemicalAdvanced), "ESSENTIASMELTERVOID@1", 200, List.of(0, 1, 0, 1, 0, 0),
            Map.of('A', Ingredient.of(BlocksTC.metalAlchemical), 'V', common("plates/void"), 'P', Ingredient.of(ItemsTC.primordialPearl)), " A ", "VPV", " A ");
        shaped(output, "mindclockwork", new ItemStack(ItemsTC.mindClockwork), "MINDCLOCKWORK@2", 25, List.of(0, 1, 0, 0, 1, 0),
            Map.of('G', Ingredient.of(ItemsTC.mechanismSimple), 'B', common("plates/brass"), 'P', common("glass_panes"), 'C', Ingredient.of(Items.COMPARATOR)), " P ", "PGP", "BCB");
        shaped(output, "grappleguntip", new ItemStack(ItemsTC.grappleGunTip), "GRAPPLEGUN", 25, List.of(0, 0, 0, 1, 0, 0),
            Map.of('B', common("plates/brass"), 'R', Ingredient.of(ItemsTC.nuggetRareEarth), 'H', Ingredient.of(Items.TRIPWIRE_HOOK)), "BRB", "RHR", "BRB");
        shaped(output, "grapplegunspool", new ItemStack(ItemsTC.grappleGunSpool), "GRAPPLEGUN", 25, List.of(0, 0, 1, 0, 0, 0),
            Map.of('G', Ingredient.of(ItemsTC.mechanismSimple), 'S', Ingredient.of(Tags.Items.STRINGS), 'H', Ingredient.of(Items.TRIPWIRE_HOOK)), "SHS", "SGS", "SSS");
    }
    private static Ingredient common(String path) { return Ingredient.of(TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", path))); }
    private static void shaped(RecipeOutput output, String id, ItemStack result, String research, int vis, List<Integer> crystals, Map<Character, Ingredient> key, String... pattern) {
        output.accept(ResourceLocation.fromNamespaceAndPath("thaumcraft", id), new RecipeArcaneWorkbench(ShapedRecipePattern.of(key, pattern), result, research, vis, crystals), null);
    }
    private static void shapeless(RecipeOutput output, String id, ItemStack result, String research, int vis, List<Integer> crystals, Ingredient... ingredients) {
        output.accept(ResourceLocation.fromNamespaceAndPath("thaumcraft", id), new ShapelessArcaneRecipe(List.of(ingredients), result, research, vis, crystals), null);
    }
}
