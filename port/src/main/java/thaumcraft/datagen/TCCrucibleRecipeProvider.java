package thaumcraft.datagen;

import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.conditions.NotCondition;
import net.neoforged.neoforge.common.conditions.TagEmptyCondition;
import thaumcraft.api.ThaumcraftApiHelper;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.crafting.CrucibleRecipe;
import thaumcraft.api.items.ItemsTC;

/** ConfigRecipes.initializeAlchemyRecipes: only outputs backed by genuine current content.
 * The captured book recipe-display catalog is NEVER used as executable recipe data.
 * Future foci/fluids/seals/devices and conditional add-on metals remain unported.
 */
public final class TCCrucibleRecipeProvider extends RecipeProvider {
    public TCCrucibleRecipeProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) { super(output, lookup); }
    @Override protected void buildRecipes(RecipeOutput output) { generate(output); }

    public static void generate(RecipeOutput output) {
        for (Aspect aspect : Aspect.aspects.values()) {
            CrucibleRecipe recipe = new CrucibleRecipe("BASEALCHEMY", ThaumcraftApiHelper.makeCrystal(aspect),
                "nuggetQuartz", new AspectList().add(aspect, 2))
                .setGroup(ResourceLocation.fromNamespaceAndPath("thaumcraft", "viscrystalgroup"));
            save(output, "vis_crystal_" + aspect.getTag(), recipe);
        }
        save(output, "nitor", new CrucibleRecipe("UNLOCKALCHEMY@3", new ItemStack(BlocksTC.nitor.get(DyeColor.YELLOW)),
            "dustGlowstone", new AspectList().add(Aspect.ENERGY, 10).add(Aspect.FIRE, 10).add(Aspect.LIGHT, 10)));
        // TC6 coal metadata 32767 includes both coal and charcoal; these are separate 1.21 items.
        save(output, "alumentum", new CrucibleRecipe("ALUMENTUM", new ItemStack(ItemsTC.alumentum),
            Ingredient.of(Items.COAL, Items.CHARCOAL), new AspectList().add(Aspect.ENERGY, 10).add(Aspect.FIRE, 10).add(Aspect.ENTROPY, 5)));
        save(output, "brassingot", new CrucibleRecipe("METALLURGY@1", new ItemStack(ItemsTC.ingotBrass),
            "ingotIron", new AspectList().add(Aspect.TOOL, 5)));
        save(output, "thaumiumingot", new CrucibleRecipe("METALLURGY@2", new ItemStack(ItemsTC.ingotThaumium),
            "ingotIron", new AspectList().add(Aspect.MAGIC, 5).add(Aspect.EARTH, 5)));
        save(output, "voidingot", new CrucibleRecipe("BASEELDRITCH", new ItemStack(ItemsTC.ingotVoid),
            new ItemStack(ItemsTC.voidSeed), new AspectList().add(Aspect.METAL, 10).add(Aspect.FLUX, 5)));
        save(output, "hedge_tallow", new CrucibleRecipe("HEDGEALCHEMY@1", new ItemStack(ItemsTC.tallow),
            new ItemStack(Items.ROTTEN_FLESH), new AspectList().add(Aspect.FIRE, 1)));
        save(output, "hedge_leather", new CrucibleRecipe("HEDGEALCHEMY@1", new ItemStack(Items.LEATHER),
            new ItemStack(Items.ROTTEN_FLESH), new AspectList().add(Aspect.AIR, 3).add(Aspect.BEAST, 3)));
        save(output, "metal_purification_iron", new CrucibleRecipe("METALPURIFICATION", new ItemStack(ItemsTC.clusterIron),
            "oreIron", new AspectList().add(Aspect.METAL, 5).add(Aspect.ORDER, 5)));
        save(output, "metal_purification_gold", new CrucibleRecipe("METALPURIFICATION", new ItemStack(ItemsTC.clusterGold),
            "oreGold", new AspectList().add(Aspect.METAL, 5).add(Aspect.ORDER, 5)));
        save(output, "metal_purification_cinnabar", new CrucibleRecipe("METALPURIFICATION", new ItemStack(ItemsTC.clusterCinnabar),
            "oreCinnabar", new AspectList().add(Aspect.METAL, 5).add(Aspect.ORDER, 5)));
        // Original foundCopper/Tin/Silver/LeadOre gates become native loaded-tag conditions.
        // Copper now exists in vanilla 1.21; absent add-on ores do not register dead/free recipes.
        optionalMetal(output, "copper", ItemsTC.clusterCopper);
        optionalMetal(output, "tin", ItemsTC.clusterTin);
        optionalMetal(output, "silver", ItemsTC.clusterSilver);
        optionalMetal(output, "lead", ItemsTC.clusterLead);
    }

    private static void optionalMetal(RecipeOutput output, String metal, net.minecraft.world.item.Item result) {
        RecipeOutput conditional = output.withConditions(new NotCondition(new TagEmptyCondition("c", "ores/" + metal)));
        save(conditional, "metal_purification_" + metal, new CrucibleRecipe("METALPURIFICATION", new ItemStack(result),
            "c:ores/" + metal, new AspectList().add(Aspect.METAL, 5).add(Aspect.ORDER, 5)));
    }

    private static void save(RecipeOutput output, String id, CrucibleRecipe recipe) {
        output.accept(ResourceLocation.fromNamespaceAndPath("thaumcraft", id), recipe, null);
    }
}
