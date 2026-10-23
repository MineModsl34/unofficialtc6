package thaumcraft.datagen;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import thaumcraft.api.ThaumcraftApiHelper;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.crafting.InfusionRecipe;
import thaumcraft.api.crafting.IngredientNBTTC;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.lib.crafting.InfusionEnchantmentRecipe;
import thaumcraft.common.lib.crafting.InfusionRunicAugmentRecipe;
import thaumcraft.common.lib.enchantment.EnumInfusionEnchantment;

/** Source initializeInfusionRecipes current real content, never a captured-display fake output.
 * Future equipment, foci, constructs, mirrors, lamps and seals require their genuine later stages.
 */
public final class TCInfusionRecipeProvider extends RecipeProvider {
    public TCInfusionRecipeProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) { super(output, lookup); }
    @Override protected void buildRecipes(RecipeOutput output) { generate(output); }
    public static void generate(RecipeOutput output) {
        Aspect[] aspects = {Aspect.AIR, Aspect.FIRE, Aspect.WATER, Aspect.EARTH, Aspect.ORDER, Aspect.ENTROPY, Aspect.FLUX};
        var blocks = List.of(BlocksTC.crystalAir, BlocksTC.crystalFire, BlocksTC.crystalWater, BlocksTC.crystalEarth, BlocksTC.crystalOrder, BlocksTC.crystalEntropy, BlocksTC.crystalTaint);
        String[] names = {"air", "fire", "water", "earth", "order", "entropy", "flux"};
        for (int index = 0; index < aspects.length; index++) {
            Aspect aspect = aspects[index];
            save(output, "crystalcluster" + names[index], new InfusionRecipe("CRYSTALFARMER", new ItemStack(blocks.get(index)), index == 6 ? 4 : 0,
                new AspectList().add(aspect, 10).add(Aspect.CRYSTAL, 10).add(Aspect.TRAP, 5), ThaumcraftApiHelper.makeCrystal(aspect), new ItemStack(Items.WHEAT_SEEDS), ItemsTC.salisMundus));
        }
        save(output, "mindbiothaumic", new InfusionRecipe("MINDBIOTHAUMIC", new ItemStack(ItemsTC.mindBiothaumic), 4,
            new AspectList().add(Aspect.MIND, 50).add(Aspect.MECHANISM, 25), new ItemStack(ItemsTC.mindClockwork), new ItemStack(ItemsTC.brain), new ItemStack(ItemsTC.mechanismComplex)));
        save(output, "runicarmor", new InfusionRunicAugmentRecipe());
        enchant(output, EnumInfusionEnchantment.BURROWING, new AspectList().add(Aspect.SENSES, 80).add(Aspect.EARTH, 150), new ItemStack(Items.RABBIT_FOOT));
        enchant(output, EnumInfusionEnchantment.COLLECTOR, new AspectList().add(Aspect.DESIRE, 80).add(Aspect.WATER, 100), new ItemStack(Items.LEAD));
        enchant(output, EnumInfusionEnchantment.DESTRUCTIVE, new AspectList().add(Aspect.AVERSION, 200).add(Aspect.ENTROPY, 250), new ItemStack(Blocks.TNT));
        enchant(output, EnumInfusionEnchantment.REFINING, new AspectList().add(Aspect.ORDER, 80).add(Aspect.EXCHANGE, 60), new ItemStack(ItemsTC.salisMundus));
        enchant(output, EnumInfusionEnchantment.SOUNDING, new AspectList().add(Aspect.SENSES, 40).add(Aspect.FIRE, 60), new ItemStack(Items.MAP));
        enchant(output, EnumInfusionEnchantment.ARCING, new AspectList().add(Aspect.ENERGY, 40).add(Aspect.AIR, 60), new ItemStack(Blocks.REDSTONE_BLOCK));
        enchant(output, EnumInfusionEnchantment.ESSENCE, new AspectList().add(Aspect.BEAST, 40).add(Aspect.FLUX, 60), new ItemStack(ItemsTC.crystalEssence));
        enchant(output, EnumInfusionEnchantment.LAMPLIGHT, new AspectList().add(Aspect.LIGHT, 80).add(Aspect.AIR, 20), "nitor");
    }
    private static void enchant(RecipeOutput output, EnumInfusionEnchantment enchantment, AspectList costs, Object material) {
        save(output, "ie" + enchantment.name().toLowerCase(java.util.Locale.ROOT), new InfusionEnchantmentRecipe(enchantment, costs,
            new IngredientNBTTC(new ItemStack(Items.ENCHANTED_BOOK)).toVanilla(), material));
    }
    private static void save(RecipeOutput output, String name, InfusionRecipe recipe) { output.accept(ResourceLocation.fromNamespaceAndPath("thaumcraft", name), recipe, null); }
}
