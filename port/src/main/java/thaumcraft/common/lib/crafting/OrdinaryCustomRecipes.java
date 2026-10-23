package thaumcraft.common.lib.crafting;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;
import thaumcraft.Thaumcraft;

/** Real native serializers remain available even while later gear/food recipes are conditional. */
@EventBusSubscriber(modid = Thaumcraft.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class OrdinaryCustomRecipes {
    public static final SimpleCraftingRecipeSerializer<RecipesRobeArmorDyes> ROBE_DYE = new SimpleCraftingRecipeSerializer<>(RecipesRobeArmorDyes::new);
    public static final SimpleCraftingRecipeSerializer<RecipesVoidRobeArmorDyes> VOID_ROBE_DYE = new SimpleCraftingRecipeSerializer<>(RecipesVoidRobeArmorDyes::new);
    public static final SimpleCraftingRecipeSerializer<RecipeTripleMeatTreat> TRIPLE_MEAT = new SimpleCraftingRecipeSerializer<>(RecipeTripleMeatTreat::new);
    private OrdinaryCustomRecipes() {}
    @SubscribeEvent public static void register(RegisterEvent event) {
        event.register(Registries.RECIPE_SERIALIZER, helper -> {
            helper.register(ResourceLocation.parse("thaumcraft:robedye"), ROBE_DYE);
            helper.register(ResourceLocation.parse("thaumcraft:voidarmordye"), VOID_ROBE_DYE);
            helper.register(ResourceLocation.parse("thaumcraft:triplemeattreat"), TRIPLE_MEAT);
        });
    }
}
