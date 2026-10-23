package thaumcraft.api.crafting;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.common.lib.crafting.CrucibleRecipes;
import thaumcraft.common.lib.crafting.CrucibleAspectCosts;

/**
 * Source CrucibleRecipe catalyst/aspect rules on a separate native recipe type. The research
 * gate is deliberately evaluated by CrucibleRecipes against the real owner, not by matches.
 * Mutable source setters remain available to add-ons, with defensive storage/snapshot getters.
 */
public final class CrucibleRecipe implements Recipe<CrucibleInput>, IThaumcraftRecipe {
    public static final int MAX_ASPECTS = CrucibleAspectCosts.MAX_ASPECTS;
    public static final int MAX_ASPECT_AMOUNT = CrucibleAspectCosts.MAX_AMOUNT;
    public static final int MAX_CATALYST_JSON = 16384;
    private ItemStack recipeOutput;
    private Ingredient catalyst;
    private AspectList aspects;
    private final String research;
    private String group;

    /**
     * Source API compatibility only: a MODERN process-local ingredient/component hash.
     * This is NOT the old ItemStack.toString/NBT hash and must not load a 1.12 saved hash.
     * The RecipeHolder ResourceLocation is the authoritative persistent modern identity.
     */
    public final int hash;

    public CrucibleRecipe(String researchKey, ItemStack result, Object catalyst, AspectList tags) {
        this(researchKey, result, ingredient(catalyst), tags, "");
    }

    public CrucibleRecipe(String researchKey, ItemStack result, Ingredient catalyst, AspectList tags, String group) {
        if (researchKey == null || researchKey.length() > 256 || group == null || group.length() > 256
            || result == null || result.isEmpty() || result.getCount() > result.getMaxStackSize())
            throw new IllegalArgumentException("Invalid crucible recipe properties");
        this.research = researchKey;
        this.recipeOutput = result.copy();
        this.group = group;
        setCatalyst(catalyst);
        setAspects(tags);
        this.hash = Objects.hash(research, ItemStack.hashItemAndComponents(recipeOutput), this.catalyst);
    }

    /** Modern Ingredient or component-aware ItemStack; finite legacy OreDictionary names only. */
    public static Ingredient ingredient(Object value) {
        if (value instanceof Ingredient ingredient) return ingredient;
        if (value instanceof ItemStack stack && !stack.isEmpty()) {
            if (thaumcraft.common.lib.research.LegacyScanIdentity.rawDamage(stack) == 32767
                && stack.getComponentsPatch().entrySet().stream().allMatch(entry -> entry.getKey() == net.minecraft.core.component.DataComponents.DAMAGE))
                return thaumcraft.api.ThaumcraftApiHelper.wildcardIngredient(stack);
            return stack.getComponentsPatch().isEmpty() && !stack.isDamageableItem()
                ? Ingredient.of(stack) : new IngredientNBTTC(stack).toVanilla();
        }
        if (value instanceof ItemLike item) return Ingredient.of(item);
        if (value instanceof TagKey<?> tag && tag.isFor(Registries.ITEM)) {
            @SuppressWarnings("unchecked") TagKey<net.minecraft.world.item.Item> itemTag = (TagKey<net.minecraft.world.item.Item>) tag;
            return Ingredient.of(itemTag);
        }
        if (value instanceof String name) {
            String path = switch (name) {
                case "nuggetQuartz" -> "nuggets/quartz";
                case "dustGlowstone" -> "dusts/glowstone";
                case "ingotIron" -> "ingots/iron";
                case "oreIron" -> "ores/iron";
                case "oreGold" -> "ores/gold";
                case "oreCopper" -> "ores/copper";
                case "oreTin" -> "ores/tin";
                case "oreSilver" -> "ores/silver";
                case "oreLead" -> "ores/lead";
                case "oreCinnabar" -> "ores/cinnabar";
                case "gemAmber" -> "gems/amber";
                case "nitor" -> "nitor";
                default -> null;
            };
            ResourceLocation id = path == null ? (name.contains(":") ? ResourceLocation.tryParse(name) : null)
                : ResourceLocation.fromNamespaceAndPath("c", path);
            if (id != null) return Ingredient.of(TagKey.create(Registries.ITEM, id));
        }
        throw new IllegalArgumentException("Invalid crucible catalyst: " + value);
    }

    public boolean matches(AspectList available, ItemStack stack) {
        if (stack == null || stack.isEmpty() || catalyst.hasNoItems() || !catalyst.test(stack) || available == null) return false;
        return CrucibleAspectCosts.matches(aspects, available);
    }

    public boolean catalystMatches(ItemStack stack) { return stack != null && !stack.isEmpty() && !catalyst.hasNoItems() && catalyst.test(stack); }

    /** Source subtraction returns a new list and keeps every unrelated aspect unchanged. */
    public AspectList removeMatching(AspectList available) {
        return CrucibleAspectCosts.subtract(aspects, available);
    }

    public ItemStack getRecipeOutput() { return recipeOutput.copy(); }
    @Override public String getResearch() { return research; }
    @Override public String getGroup() { return group; }
    public CrucibleRecipe setGroup(ResourceLocation group) {
        String value = Objects.requireNonNull(group).toString();
        if (value.length() > 256) throw new IllegalArgumentException("Crucible group is too long");
        this.group = value;
        return this;
    }
    public Ingredient getCatalyst() { return catalyst; }
    public void setCatalyst(Ingredient catalyst) {
        if (catalyst == null || catalyst.isEmpty()) throw new IllegalArgumentException("Empty crucible catalyst");
        this.catalyst = catalyst;
    }
    public AspectList getAspects() { return aspects.copy(); }
    public void setAspects(AspectList aspects) {
        if (!validAspects(aspects)) throw new IllegalArgumentException("Invalid crucible aspect costs");
        this.aspects = aspects.copy();
    }
    public static boolean validAspects(AspectList aspects) {
        return CrucibleAspectCosts.valid(aspects);
    }

    @Override public boolean matches(CrucibleInput input, Level level) { return matches(input.aspects(), input.catalyst()); }
    @Override public ItemStack assemble(CrucibleInput input, HolderLookup.Provider registries) { return recipeOutput.copy(); }
    @Override public boolean canCraftInDimensions(int width, int height) { return false; }
    @Override public ItemStack getResultItem(HolderLookup.Provider registries) { return recipeOutput.copy(); }
    @Override public NonNullList<Ingredient> getIngredients() { return NonNullList.of(Ingredient.EMPTY, catalyst); }
    @Override public boolean isSpecial() { return true; }
    @Override public RecipeSerializer<?> getSerializer() { return CrucibleRecipes.SERIALIZER; }
    @Override public RecipeType<?> getType() { return CrucibleRecipes.TYPE; }

    public static final class Serializer implements RecipeSerializer<CrucibleRecipe> {
        private static final Codec<String> BOUNDED_STRING = Codec.STRING.validate(value -> value.length() <= 256
            ? DataResult.success(value) : DataResult.error(() -> "Crucible string is too long"));
        public static final Codec<AspectList> ASPECTS_CODEC = CrucibleAspectCosts.CODEC;
        public static final MapCodec<CrucibleRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BOUNDED_STRING.fieldOf("research").forGetter(CrucibleRecipe::getResearch),
            ItemStack.STRICT_CODEC.fieldOf("result").forGetter(CrucibleRecipe::getRecipeOutput),
            Ingredient.CODEC_NONEMPTY.fieldOf("catalyst").forGetter(CrucibleRecipe::getCatalyst),
            ASPECTS_CODEC.fieldOf("aspects").forGetter(CrucibleRecipe::getAspects),
            BOUNDED_STRING.optionalFieldOf("group", "").forGetter(CrucibleRecipe::getGroup)
        ).apply(instance, CrucibleRecipe::new));
        private static final StreamCodec<RegistryFriendlyByteBuf, CrucibleRecipe> STREAM = StreamCodec.of(
            (buffer, recipe) -> {
                buffer.writeUtf(recipe.research, 256);
                ItemStack.STREAM_CODEC.encode(buffer, recipe.recipeOutput);
                var ops = buffer.registryAccess().createSerializationContext(JsonOps.INSTANCE);
                String ingredientJson = Ingredient.CODEC_NONEMPTY.encodeStart(ops, recipe.catalyst).getOrThrow().toString();
                buffer.writeUtf(ingredientJson, MAX_CATALYST_JSON);
                buffer.writeVarInt(recipe.aspects.size());
                for (var entry : recipe.aspects.aspects.entrySet()) {
                    buffer.writeUtf(entry.getKey().getTag(), 256);
                    buffer.writeVarInt(entry.getValue());
                }
                buffer.writeUtf(recipe.group, 256);
            }, buffer -> {
                String research = buffer.readUtf(256);
                ItemStack result = ItemStack.STREAM_CODEC.decode(buffer);
                var ops = buffer.registryAccess().createSerializationContext(JsonOps.INSTANCE);
                Ingredient catalyst = Ingredient.CODEC_NONEMPTY.parse(ops, JsonParser.parseString(buffer.readUtf(MAX_CATALYST_JSON))).getOrThrow();
                int size = buffer.readVarInt();
                if (size < 0 || size > MAX_ASPECTS) throw new IllegalArgumentException("Invalid crucible aspect count");
                AspectList aspects = new AspectList();
                for (int index = 0; index < size; index++) {
                    Aspect aspect = Aspect.getAspect(buffer.readUtf(256));
                    int amount = buffer.readVarInt();
                    if (aspect == null || aspects.aspects.containsKey(aspect) || amount <= 0 || amount > MAX_ASPECT_AMOUNT)
                        throw new IllegalArgumentException("Invalid crucible aspect cost");
                    aspects.add(aspect, amount);
                }
                return new CrucibleRecipe(research, result, catalyst, aspects, buffer.readUtf(256));
            });
        @Override public MapCodec<CrucibleRecipe> codec() { return CODEC; }
        @Override public StreamCodec<RegistryFriendlyByteBuf, CrucibleRecipe> streamCodec() { return STREAM; }
    }
}
