package thaumcraft.common.lib.crafting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.level.Level;
import thaumcraft.api.crafting.IArcaneRecipe;

/** Genuine separate recipe type: an arcane recipe can NEVER be assembled on a vanilla table.
 * Shape matching is the native trimmed/mirrored 3x3 matcher. The server menu additionally checks
 * the six source crystal slots, research, live aura and the exact owner/block entity context.
 * Crystal order is AIR, FIRE, WATER, EARTH, ORDER, ENTROPY (source ShardType metadata).
 */
public class RecipeArcaneWorkbench implements IArcaneRecipe {
    private final String group;
    private final ShapedRecipePattern pattern;
    private final ItemStack result;
    private final String research;
    private final int vis;
    private final List<Integer> crystals;
    private boolean mirrored = true;

    public RecipeArcaneWorkbench(ShapedRecipePattern pattern, ItemStack result, String research, int vis, List<Integer> crystals) {
        this("", pattern, result, research, vis, crystals);
    }
    public RecipeArcaneWorkbench(String group, ShapedRecipePattern pattern, ItemStack result, String research, int vis, List<Integer> crystals) {
        ArcaneWorkbenchCosts.validateRecipeCosts(group, research, vis, crystals);
        if (pattern.width() > 3 || pattern.height() > 3 || result.isEmpty() || result.getCount() > result.getMaxStackSize())
            throw new IllegalArgumentException("Invalid arcane recipe");
        this.group = group; this.pattern = pattern; this.result = result.copy(); this.research = research; this.vis = vis; this.crystals = List.copyOf(crystals);
    }
    @Override public String getGroup() { return group; }
    public RecipeArcaneWorkbench setMirrored(boolean value) { mirrored = value; return this; }
    public boolean mirrored() { return mirrored; }
    public ShapedRecipePattern pattern() { return pattern; }
    public ItemStack result() { return result.copy(); }
    public String research() { return research; }
    public int vis() { return vis; }
    public List<Integer> crystals() { return crystals; }
    @Override public String getResearch() { return research; }
    @Override public int getVis() { return vis; }
    @Override public thaumcraft.api.aspects.AspectList getCrystals() { return IArcaneRecipe.aspects(crystals); }
    @Override public boolean matches(CraftingInput input, Level level) {
        if (mirrored) return pattern.matches(input);
        if (input.width() != pattern.width() || input.height() != pattern.height()) return false;
        for (int row = 0; row < input.height(); row++) for (int col = 0; col < input.width(); col++)
            if (!pattern.ingredients().get(col + row * pattern.width()).test(input.getItem(col, row))) return false;
        return true;
    }
    @Override public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) { return result.copy(); }
    @Override public boolean canCraftInDimensions(int width, int height) { return width >= pattern.width() && height >= pattern.height(); }
    @Override public ItemStack getResultItem(HolderLookup.Provider registries) { return result.copy(); }
    @Override public NonNullList<Ingredient> getIngredients() { return pattern.ingredients(); }
    // Deliberately not a CraftingRecipe: vanilla recipe-book/table paths cannot assemble this type.
    @Override public RecipeSerializer<?> getSerializer() { return ArcaneWorkbenchRecipes.SERIALIZER; }
    @Override public RecipeType<?> getType() { return ArcaneWorkbenchRecipes.TYPE; }

    public static final class Serializer implements RecipeSerializer<RecipeArcaneWorkbench> {
        public static final MapCodec<RecipeArcaneWorkbench> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.optionalFieldOf("group", "").forGetter(RecipeArcaneWorkbench::getGroup),
            ShapedRecipePattern.MAP_CODEC.forGetter(RecipeArcaneWorkbench::pattern),
            ItemStack.STRICT_CODEC.fieldOf("result").forGetter(recipe -> recipe.result),
            Codec.STRING.fieldOf("research").forGetter(RecipeArcaneWorkbench::research),
            Codec.intRange(0, 32767).fieldOf("vis").forGetter(RecipeArcaneWorkbench::vis),
            Codec.intRange(0, 64).listOf(6, 6).fieldOf("crystals").forGetter(RecipeArcaneWorkbench::crystals),
            Codec.BOOL.optionalFieldOf("mirrored", true).forGetter(RecipeArcaneWorkbench::mirrored)
        ).apply(instance, (group, pattern, result, research, vis, crystals, mirrored) ->
            new RecipeArcaneWorkbench(group, pattern, result, research, vis, crystals).setMirrored(mirrored)));
        private static final StreamCodec<RegistryFriendlyByteBuf, RecipeArcaneWorkbench> STREAM = StreamCodec.of(
            (buffer, recipe) -> {
                buffer.writeUtf(recipe.group, 256);
                ShapedRecipePattern.STREAM_CODEC.encode(buffer, recipe.pattern);
                ItemStack.STREAM_CODEC.encode(buffer, recipe.result);
                buffer.writeUtf(recipe.research, 256); buffer.writeVarInt(recipe.vis);
                for (int count : recipe.crystals) buffer.writeVarInt(count);
                buffer.writeBoolean(recipe.mirrored);
            }, buffer -> {
                String group = buffer.readUtf(256);
                ShapedRecipePattern pattern = ShapedRecipePattern.STREAM_CODEC.decode(buffer);
                ItemStack result = ItemStack.STREAM_CODEC.decode(buffer);
                String research = buffer.readUtf(256); int vis = buffer.readVarInt();
                List<Integer> crystals = List.of(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt());
                return new RecipeArcaneWorkbench(group, pattern, result, research, vis, crystals).setMirrored(buffer.readBoolean());
            });
        @Override public MapCodec<RecipeArcaneWorkbench> codec() { return CODEC; }
        @Override public StreamCodec<RegistryFriendlyByteBuf, RecipeArcaneWorkbench> streamCodec() { return STREAM; }
    }
}
