package thaumcraft.client.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import thaumcraft.Thaumcraft;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.crafting.CrucibleRecipe;
import thaumcraft.api.crafting.IArcaneRecipe;
import thaumcraft.api.crafting.InfusionRecipe;
import thaumcraft.common.lib.crafting.ArcaneWorkbenchRecipes;
import thaumcraft.common.lib.crafting.CrucibleRecipes;
import thaumcraft.common.lib.crafting.InfusionRecipes;
import thaumcraft.common.lib.crafting.RecipeArcaneWorkbench;
import thaumcraft.api.crafting.ShapelessArcaneRecipe;

/** Optional JEI discovery only. Common setup never loads this client/API-dependent class. */
@JeiPlugin
public final class ThaumcraftJeiPlugin implements IModPlugin {
    private final ThaumcraftJeiRuntime runtime = new ThaumcraftJeiRuntime();
    public static final RecipeType<RecipeHolder<IArcaneRecipe>> ARCANE = RecipeType.createRecipeHolderType(ArcaneWorkbenchRecipes.ID);
    public static final RecipeType<RecipeHolder<CrucibleRecipe>> CRUCIBLE = RecipeType.createRecipeHolderType(CrucibleRecipes.ID);
    public static final RecipeType<RecipeHolder<InfusionRecipe>> INFUSION = RecipeType.createRecipeHolderType(InfusionRecipes.ID);

    @Override public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "jei");
    }

    @Override public void registerCategories(IRecipeCategoryRegistration registration) {
        var gui = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(new ArcaneCategory(gui), new CrucibleCategory(gui), new InfusionCategory(gui),
            new AspectCombinationCategory(gui), new ItemAspectCategory(gui));
    }

    @Override public void registerRecipes(IRecipeRegistration registration) {
        runtime.register(registration);
        TripleMeatJeiRecipes.register(registration);
    }

    @Override public void registerIngredients(mezz.jei.api.registration.IModIngredientRegistration registration) {
        AspectJeiRecipes.registerIngredients(registration);
    }
    @Override public void registerItemSubtypes(mezz.jei.api.registration.ISubtypeRegistration registration) {
        AspectJeiRecipes.registerItemSubtypes(registration);
    }
    @Override public void registerRecipeTransferHandlers(mezz.jei.api.registration.IRecipeTransferRegistration registration) {
        registration.addRecipeTransferHandler(new ArcaneRecipeTransferHandler(registration.getTransferHelper()), ARCANE);
        if (Boolean.getBoolean("thaumcraft.jeiReview")) registration.addRecipeTransferListener(JeiTransferClientAudit.listener());
    }
    @Override public void onRuntimeAvailable(mezz.jei.api.runtime.IJeiRuntime jeiRuntime) { runtime.available(jeiRuntime); }
    @Override public void onRuntimeUnavailable() { runtime.unavailable(); }

    @Override public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalysts(ARCANE, BlocksTC.arcaneWorkbench);
        registration.addRecipeCatalysts(CRUCIBLE, BlocksTC.crucible, BlocksTC.thaumatorium);
        registration.addRecipeCatalysts(INFUSION, BlocksTC.infusionMatrix);
    }
}
