package thaumcraft.common.lib.crafting;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;
import thaumcraft.Thaumcraft;

/** MOD-bus registration only; JSON/datagen registers the separate source recipe salismundus. */
@EventBusSubscriber(modid = Thaumcraft.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class ResearchAcquisitionRecipes {
    public static final ResourceLocation MAGIC_DUST_ID =
        ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "magic_dust");
    public static final SimpleCraftingRecipeSerializer<RecipeMagicDust> MAGIC_DUST =
        new SimpleCraftingRecipeSerializer<>(RecipeMagicDust::new);

    private ResearchAcquisitionRecipes() {}

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.RECIPE_SERIALIZER, helper -> helper.register(MAGIC_DUST_ID, MAGIC_DUST));
    }
}
