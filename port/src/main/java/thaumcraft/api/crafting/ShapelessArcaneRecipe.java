package thaumcraft.api.crafting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ItemLike;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import thaumcraft.common.lib.crafting.ArcaneShapelessMatching;
import thaumcraft.common.lib.crafting.ArcaneWorkbenchCosts;
import thaumcraft.common.lib.crafting.ArcaneWorkbenchRecipes;
import thaumcraft.api.aspects.AspectList;

/** Original ShapelessOreRecipe matching with modern tag/custom ingredients. The recipe
 * tests only the nine crafting inputs; the actual server menu enforces its six crystal
 * slots, owner research and live aura for BOTH arcane shapes. A stacked item occupies
 * one ingredient slot, never several, just like the original. No vanilla table type.
 */
public class ShapelessArcaneRecipe implements IArcaneRecipe {
    private final String group;
    private final NonNullList<Ingredient> ingredients;
    private final ItemStack result;
    private final String research;
    private final int vis;
    private final List<Integer> crystals;

    public ShapelessArcaneRecipe(ResourceLocation group, String research, int vis, AspectList crystals, ItemLike result, Object... recipe) {
        this(group, research, vis, crystals, new ItemStack(result), recipe);
    }
    public ShapelessArcaneRecipe(ResourceLocation group, String research, int vis, AspectList crystals, ItemStack result, Object... recipe) {
        this(group == null ? "" : group.toString(), ArcaneRecipeInputs.shapeless(recipe), result, research, vis, ArcaneRecipeInputs.crystals(crystals));
    }
    public ShapelessArcaneRecipe(ResourceLocation group, String research, int vis, AspectList crystals, NonNullList<Ingredient> inputs, ItemStack result) {
        this(group == null ? "" : group.toString(), inputs, result, research, vis, ArcaneRecipeInputs.crystals(crystals));
    }

    public ShapelessArcaneRecipe(List<Ingredient> ingredients, ItemStack result, String research, int vis, List<Integer> crystals) {
        this("", ingredients, result, research, vis, crystals);
    }
    public ShapelessArcaneRecipe(String group, List<Ingredient> ingredients, ItemStack result, String research, int vis, List<Integer> crystals) {
        ArcaneWorkbenchCosts.validateRecipeCosts(group, research, vis, crystals);
        if (ingredients == null || ingredients.isEmpty() || ingredients.size() > 9 || ingredients.stream().anyMatch(ingredient -> ingredient == null || ingredient.isEmpty())
            || result.isEmpty() || result.getCount() > result.getMaxStackSize()) throw new IllegalArgumentException("Invalid shapeless arcane recipe");
        this.group = group;
        this.ingredients = NonNullList.of(Ingredient.EMPTY, ingredients.toArray(Ingredient[]::new));
        this.result = result.copy(); this.research = research; this.vis = vis; this.crystals = List.copyOf(crystals);
    }
    @Override public String getGroup() { return group; }
    @Override public String research() { return research; }
    @Override public int vis() { return vis; }
    @Override public List<Integer> crystals() { return crystals; }
    @Override public String getResearch() { return research; }
    @Override public int getVis() { return vis; }
    @Override public AspectList getCrystals() { return IArcaneRecipe.aspects(crystals); }
    @Override public NonNullList<Ingredient> getIngredients() { return NonNullList.of(Ingredient.EMPTY, ingredients.toArray(Ingredient[]::new)); }
    @Override public boolean matches(CraftingInput input, Level level) {
        if (input.ingredientCount() != ingredients.size()) return false;
        List<ItemStack> occupied = new ArrayList<>(input.ingredientCount());
        for (ItemStack stack : input.items()) if (!stack.isEmpty()) occupied.add(stack);
        return ArcaneShapelessMatching.matches(occupied, ingredients);
    }
    @Override public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) { return result.copy(); }
    @Override public ItemStack getResultItem(HolderLookup.Provider registries) { return result.copy(); }
    @Override public boolean canCraftInDimensions(int width, int height) { return width > 0 && height > 0 && (long) width * height >= ingredients.size(); }
    @Override public RecipeSerializer<?> getSerializer() { return ArcaneWorkbenchRecipes.SHAPELESS_SERIALIZER; }
    @Override public RecipeType<?> getType() { return ArcaneWorkbenchRecipes.TYPE; }

    public static final class Serializer implements RecipeSerializer<ShapelessArcaneRecipe> {
        public static final MapCodec<ShapelessArcaneRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.optionalFieldOf("group", "").forGetter(ShapelessArcaneRecipe::getGroup),
            Ingredient.CODEC_NONEMPTY.listOf(1, 9).fieldOf("ingredients").forGetter(recipe -> recipe.ingredients),
            ItemStack.STRICT_CODEC.fieldOf("result").forGetter(recipe -> recipe.result),
            Codec.STRING.fieldOf("research").forGetter(ShapelessArcaneRecipe::research),
            Codec.intRange(0, 32767).fieldOf("vis").forGetter(ShapelessArcaneRecipe::vis),
            Codec.intRange(0, 64).listOf(6, 6).fieldOf("crystals").forGetter(ShapelessArcaneRecipe::crystals)
        ).apply(instance, ShapelessArcaneRecipe::new));
        private static final StreamCodec<RegistryFriendlyByteBuf, ShapelessArcaneRecipe> STREAM = StreamCodec.of(
            (buffer, recipe) -> {
                buffer.writeUtf(recipe.group, 256); buffer.writeVarInt(recipe.ingredients.size());
                for (Ingredient ingredient : recipe.ingredients) Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, ingredient);
                ItemStack.STREAM_CODEC.encode(buffer, recipe.result);
                buffer.writeUtf(recipe.research, 256); buffer.writeVarInt(recipe.vis);
                for (int count : recipe.crystals) buffer.writeVarInt(count);
            }, buffer -> {
                String group = buffer.readUtf(256); int count = buffer.readVarInt();
                if (count < 1 || count > 9) throw new IllegalArgumentException("Shapeless arcane ingredient count must be 1..9");
                List<Ingredient> ingredients = new ArrayList<>(count);
                for (int index = 0; index < count; index++) ingredients.add(Ingredient.CONTENTS_STREAM_CODEC.decode(buffer));
                ItemStack result = ItemStack.STREAM_CODEC.decode(buffer);
                String research = buffer.readUtf(256); int vis = buffer.readVarInt();
                return new ShapelessArcaneRecipe(group, ingredients, result, research, vis,
                    List.of(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt()));
            });
        @Override public MapCodec<ShapelessArcaneRecipe> codec() { return CODEC; }
        @Override public StreamCodec<RegistryFriendlyByteBuf, ShapelessArcaneRecipe> streamCodec() { return STREAM; }
    }
}
