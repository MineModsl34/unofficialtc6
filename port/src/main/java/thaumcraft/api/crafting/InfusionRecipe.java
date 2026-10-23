package thaumcraft.api.crafting;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.util.RecipeMatcher;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.common.lib.crafting.CrucibleAspectCosts;
import thaumcraft.common.lib.crafting.InfusionRecipes;

/** Source infusion matching: owner KNOWS research (not strict completion), exact unordered
 * one-per-pedestal ingredients, and a central item. Dedicated native type cannot craft in a table.
 */
public class InfusionRecipe implements Recipe<InfusionInput>, IInfusionRecipe {
    public static final int MAX_COMPONENTS = 256;
    public static final Codec<String> STRING = Codec.STRING.validate(value -> value.length() <= 256
        ? DataResult.success(value) : DataResult.error(() -> "Infusion string too long"));
    public static final Codec<Tag> TAG = Codec.STRING.comapFlatMap(snbt -> {
        if (snbt.length() > 65536) return DataResult.error(() -> "Infusion output tag too large");
        try { Tag tag = TagParser.parseTag("{value:" + snbt + "}").get("value"); return tag == null ? DataResult.error(() -> "Missing output tag") : DataResult.success(tag); }
        catch (Exception exception) { return DataResult.error(() -> "Invalid infusion output tag"); }
    }, Tag::toString);
    public record TagOutput(String label, Tag value) {
        public TagOutput { if (label == null || label.length() > 256 || value == null) throw new IllegalArgumentException("Invalid infusion output tag"); value = value.copy(); }
        public static final Codec<TagOutput> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            STRING.fieldOf("tag").forGetter(TagOutput::label), TAG.fieldOf("value").forGetter(TagOutput::value)
        ).apply(instance, TagOutput::new));
    }
    public static final Codec<Object> OUTPUT = Codec.either(ItemStack.STRICT_CODEC, TagOutput.CODEC).xmap(
        either -> either.map(stack -> (Object)stack, tag -> new Object[]{tag.label(), tag.value()}),
        output -> output instanceof ItemStack stack ? Either.left(stack) : Either.right(new TagOutput((String)((Object[])output)[0], (Tag)((Object[])output)[1])));
    public static final MapCodec<InfusionRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
        STRING.fieldOf("research").forGetter(InfusionRecipe::getResearch),
        OUTPUT.fieldOf("result").forGetter(InfusionRecipe::getRecipeOutput),
        Codec.intRange(0, 512).fieldOf("instability").forGetter(recipe -> recipe.instability),
        CrucibleAspectCosts.CODEC.fieldOf("aspects").forGetter(InfusionRecipe::getAspects),
        Ingredient.CODEC.fieldOf("central").forGetter(InfusionRecipe::getRecipeInput),
        Ingredient.CODEC_NONEMPTY.listOf(0, MAX_COMPONENTS).fieldOf("components").forGetter(recipe -> List.copyOf(recipe.components)),
        STRING.optionalFieldOf("group", "").forGetter(InfusionRecipe::getGroup)
    ).apply(instance, InfusionRecipe::new));

    public AspectList aspects;
    public String research;
    protected NonNullList<Ingredient> components = NonNullList.create();
    public Ingredient sourceInput;
    public Object recipeOutput;
    public int instability;
    private String group;

    public InfusionRecipe(String research, Object output, int instability, AspectList costs, Object central, Object... components) {
        this(research, output, instability, costs, central instanceof Ingredient ingredient ? ingredient : CrucibleRecipe.ingredient(central),
            java.util.Arrays.stream(components).map(value -> value instanceof Ingredient ingredient ? ingredient : CrucibleRecipe.ingredient(value)).toList(), "");
    }
    public InfusionRecipe(String research, Object output, int instability, AspectList costs, Ingredient central, List<Ingredient> components, String group) {
        if (research == null || research.length() > 256 || instability < 0 || instability > 512 || group == null || group.length() > 256
            || central == null || components.size() > MAX_COMPONENTS || components.stream().anyMatch(ingredient -> ingredient == null || ingredient.isEmpty())
            || (costs != null && !CrucibleAspectCosts.valid(costs))) throw new IllegalArgumentException("Invalid infusion recipe");
        if (output != null && !(output instanceof ItemStack) && !(output instanceof Object[] array && array.length == 2 && array[0] instanceof String && array[1] instanceof Tag))
            throw new IllegalArgumentException("Unsupported infusion output");
        this.research = research; this.recipeOutput = copyOutput(output); this.instability = instability;
        this.aspects = costs == null ? new AspectList() : costs.copy(); this.sourceInput = central; this.components.addAll(components); this.group = group;
    }
    public static Object copyOutput(Object output) {
        if (output instanceof ItemStack stack) return stack.copy();
        if (output instanceof Object[] array) return new Object[]{array[0], ((Tag)array[1]).copy()};
        return null;
    }
    @Override public boolean matches(List<ItemStack> items, ItemStack central, Level level, Player owner) {
        return owner != null && ThaumcraftCapabilities.getKnowledge(owner).isResearchKnown(research)
            && centralMatches(central) && componentsMatch(items, getComponents());
    }
    protected boolean centralMatches(ItemStack stack) { return sourceInput == Ingredient.EMPTY || (!sourceInput.hasNoItems() && sourceInput.test(stack)); }
    public static boolean componentsMatch(List<ItemStack> items, List<Ingredient> expected) {
        return items.size() == expected.size() && expected.stream().noneMatch(Ingredient::hasNoItems) && RecipeMatcher.findMatches(items, expected) != null;
    }
    @Override public String getResearch() { return research; }
    @Override public Ingredient getRecipeInput() { return sourceInput; }
    @Override public NonNullList<Ingredient> getComponents() { NonNullList<Ingredient> result = NonNullList.create(); result.addAll(components); return result; }
    @Override public Object getRecipeOutput() { return copyOutput(recipeOutput); }
    @Override public AspectList getAspects() { return aspects.copy(); }
    @Override public Object getRecipeOutput(Player owner, ItemStack input, List<ItemStack> components) { return getRecipeOutput(); }
    @Override public AspectList getAspects(Player owner, ItemStack input, List<ItemStack> components) { return getAspects(); }
    @Override public int getInstability(Player owner, ItemStack input, List<ItemStack> components) { return instability; }
    @Override public String getGroup() { return group; }
    public InfusionRecipe setGroup(ResourceLocation group) { if (group.toString().length() > 256) throw new IllegalArgumentException("Infusion group too long"); this.group = group.toString(); return this; }
    /** Restore validated detached JEI metadata after a dynamic serializer reconstructed its fixed defaults.
     * The executable catalog is never mutated by the client; this preserves source add-on constraints.
     */
    public final void restoreJeiSnapshotBase(String research, int instability, AspectList costs, Ingredient central,
            List<Ingredient> components, String group) {
        InfusionRecipe checked = new InfusionRecipe(research, recipeOutput, instability, costs, central, components, group);
        this.research = checked.research;
        this.instability = checked.instability;
        this.aspects = checked.aspects;
        this.sourceInput = checked.sourceInput;
        this.components = checked.components;
        this.group = checked.group;
    }
    @Override public boolean matches(InfusionInput input, Level level) { return matches(input.components(), input.central(), level, input.owner()); }
    @Override public ItemStack assemble(InfusionInput input, HolderLookup.Provider lookup) {
        if (input.owner() == null || !matches(input, input.owner().level())) return ItemStack.EMPTY;
        Object output = getRecipeOutput(input.owner(), input.central(), input.components());
        if (output instanceof ItemStack stack) return stack.copy();
        if (output instanceof Object[] tag) { ItemStack result = input.central().copy(); return InfusionRecipes.applyOutputTag(result, (String)tag[0], (Tag)tag[1], lookup) ? result : ItemStack.EMPTY; }
        return ItemStack.EMPTY;
    }
    @Override public boolean canCraftInDimensions(int width, int height) { return false; }
    @Override public ItemStack getResultItem(HolderLookup.Provider lookup) { return recipeOutput instanceof ItemStack stack ? stack.copy() : ItemStack.EMPTY; }
    @Override public boolean isSpecial() { return true; }
    @Override public RecipeSerializer<?> getSerializer() { return InfusionRecipes.SERIALIZER; }
    @Override public RecipeType<?> getType() { return InfusionRecipes.TYPE; }
}
