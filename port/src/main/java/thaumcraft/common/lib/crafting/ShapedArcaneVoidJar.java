package thaumcraft.common.lib.crafting;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.crafting.ShapedArcaneRecipe;

/** Source JarVoid conversion copies the first normal jar's user NBT, not just its
 * essentia count. Component PATCH copying is the native analogue: preserves custom
 * name/filter/aspects/addon data but retains the new void jar's own default item data. */
public final class ShapedArcaneVoidJar extends ShapedArcaneRecipe {
    public ShapedArcaneVoidJar(ResourceLocation group, String research, int vis, AspectList crystals, ItemStack result, Object... recipe) {
        super(group, research, vis, crystals, result, recipe);
    }
    private ShapedArcaneVoidJar(RecipeArcaneWorkbench recipe) {
        super(recipe.getGroup(), recipe.pattern(), recipe.result(), recipe.research(), recipe.vis(), recipe.crystals());
        setMirrored(recipe.mirrored());
    }
    @Override public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack result = super.assemble(input, registries);
        for (ItemStack stack : input.items()) if (stack.is(BlocksTC.jarNormal.asItem())) {
            result.applyComponents(stack.getComponentsPatch()); break;
        }
        return result;
    }
    @Override public RecipeSerializer<?> getSerializer() { return ArcaneWorkbenchRecipes.VOID_JAR_SERIALIZER; }
    public static final class Serializer implements RecipeSerializer<ShapedArcaneVoidJar> {
        private static final MapCodec<ShapedArcaneVoidJar> CODEC = RecipeArcaneWorkbench.Serializer.CODEC.xmap(ShapedArcaneVoidJar::new, recipe -> recipe);
        private static final StreamCodec<RegistryFriendlyByteBuf, ShapedArcaneVoidJar> STREAM = new RecipeArcaneWorkbench.Serializer().streamCodec().map(ShapedArcaneVoidJar::new, recipe -> recipe);
        @Override public MapCodec<ShapedArcaneVoidJar> codec() { return CODEC; }
        @Override public StreamCodec<RegistryFriendlyByteBuf, ShapedArcaneVoidJar> streamCodec() { return STREAM; }
    }
}
