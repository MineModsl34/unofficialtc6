package thaumcraft.common.lib.crafting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/** Source optional-metal recipes resolve the first current tag member, never a cached foreign item. */
@EventBusSubscriber(modid = "thaumcraft", bus = EventBusSubscriber.Bus.MOD)
public final class TaggedMetalRecipes {
    private TaggedMetalRecipes() {}

    public static ItemStack output(ResourceLocation tag, int count, HolderLookup.Provider lookup) {
        TagKey<Item> key = TagKey.create(Registries.ITEM, tag);
        // JEI 19.69+ asks for result items with a lookup that has no item registry; items are static, so fall back
        return lookup.lookup(Registries.ITEM).flatMap(items -> items.get(key)).or(() -> BuiltInRegistries.ITEM.getTag(key))
            .flatMap(items -> items.stream().findFirst()).map(item -> new ItemStack(item, count)).orElse(ItemStack.EMPTY);
    }

    public static final RecipeSerializer<Cooking> COOKING = new RecipeSerializer<>() {
        private final MapCodec<Cooking> codec = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.optionalFieldOf("group", "").forGetter((Cooking recipe) -> recipe.getGroup()),
            Ingredient.CODEC_NONEMPTY.fieldOf("ingredient").forGetter((Cooking recipe) -> recipe.getIngredients().getFirst()),
            ResourceLocation.CODEC.fieldOf("output_tag").forGetter((Cooking recipe) -> recipe.outputTag),
            Codec.intRange(1, 64).optionalFieldOf("count", 2).forGetter((Cooking recipe) -> recipe.outputCount)
        ).apply(instance, Cooking::new));
        private final StreamCodec<RegistryFriendlyByteBuf, Cooking> stream = StreamCodec.of((buffer, recipe) -> {
            buffer.writeUtf(recipe.getGroup());
            Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, recipe.getIngredients().getFirst());
            buffer.writeResourceLocation(recipe.outputTag); buffer.writeVarInt(recipe.outputCount);
        }, buffer -> new Cooking(buffer.readUtf(), Ingredient.CONTENTS_STREAM_CODEC.decode(buffer),
            buffer.readResourceLocation(), buffer.readVarInt()));
        @Override public MapCodec<Cooking> codec() { return codec; }
        @Override public StreamCodec<RegistryFriendlyByteBuf, Cooking> streamCodec() { return stream; }
    };

    public static final RecipeSerializer<Shaped> SHAPED = new RecipeSerializer<>() {
        private final MapCodec<Shaped> codec = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.optionalFieldOf("group", "").forGetter((Shaped recipe) -> recipe.getGroup()),
            ShapedRecipePattern.MAP_CODEC.forGetter((Shaped recipe) -> recipe.pattern),
            ResourceLocation.CODEC.fieldOf("output_tag").forGetter((Shaped recipe) -> recipe.outputTag),
            Codec.intRange(1, 64).optionalFieldOf("count", 1).forGetter((Shaped recipe) -> recipe.outputCount)
        ).apply(instance, Shaped::new));
        private final StreamCodec<RegistryFriendlyByteBuf, Shaped> stream = StreamCodec.of((buffer, recipe) -> {
            buffer.writeUtf(recipe.getGroup()); ShapedRecipePattern.STREAM_CODEC.encode(buffer, recipe.pattern);
            buffer.writeResourceLocation(recipe.outputTag); buffer.writeVarInt(recipe.outputCount);
        }, buffer -> new Shaped(buffer.readUtf(), ShapedRecipePattern.STREAM_CODEC.decode(buffer),
            buffer.readResourceLocation(), buffer.readVarInt()));
        @Override public MapCodec<Shaped> codec() { return codec; }
        @Override public StreamCodec<RegistryFriendlyByteBuf, Shaped> streamCodec() { return stream; }
    };

    public static class Cooking extends SmeltingRecipe {
        public final ResourceLocation outputTag;
        public final int outputCount;
        public Cooking(String group, Ingredient ingredient, ResourceLocation tag, int count) {
            super(group, CookingBookCategory.MISC, ingredient, ItemStack.EMPTY, 1.0F, 200);
            if (count < 1 || count > 64) throw new IllegalArgumentException("Invalid metal output count");
            outputTag = tag; outputCount = count;
        }
        @Override public ItemStack getResultItem(HolderLookup.Provider lookup) { return output(outputTag, outputCount, lookup); }
        @Override public ItemStack assemble(SingleRecipeInput input, HolderLookup.Provider lookup) { return getResultItem(lookup); }
        @Override public boolean matches(SingleRecipeInput input, Level level) {
            return super.matches(input, level) && !getResultItem(level.registryAccess()).isEmpty();
        }
        @Override public RecipeSerializer<?> getSerializer() { return COOKING; }
    }

    public static class Shaped extends ShapedRecipe {
        public final ResourceLocation outputTag;
        public final int outputCount;
        public Shaped(String group, ShapedRecipePattern pattern, ResourceLocation tag, int count) {
            super(group, CraftingBookCategory.MISC, pattern, ItemStack.EMPTY);
            if (count < 1 || count > 64) throw new IllegalArgumentException("Invalid metal output count");
            outputTag = tag; outputCount = count;
        }
        @Override public ItemStack getResultItem(HolderLookup.Provider lookup) { return output(outputTag, outputCount, lookup); }
        @Override public boolean matches(CraftingInput input, Level level) {
            return super.matches(input, level) && !getResultItem(level.registryAccess()).isEmpty();
        }
        @Override public RecipeSerializer<?> getSerializer() { return SHAPED; }
    }

    public record MaterialAvailable(ResourceLocation tag) implements LootItemCondition {
        public static final MapCodec<MaterialAvailable> CODEC = ResourceLocation.CODEC.fieldOf("tag").xmap(MaterialAvailable::new, MaterialAvailable::tag);
        @Override public boolean test(LootContext context) { return !output(tag, 1, context.getLevel().registryAccess()).isEmpty(); }
        @Override public LootItemConditionType getType() { return MATERIAL_AVAILABLE; }
    }
    public static final LootItemConditionType MATERIAL_AVAILABLE = new LootItemConditionType(MaterialAvailable.CODEC);

    @SubscribeEvent public static void register(RegisterEvent event) {
        event.register(Registries.RECIPE_SERIALIZER, helper -> {
            helper.register(ResourceLocation.fromNamespaceAndPath("thaumcraft", "tagged_smelting"), COOKING);
            helper.register(ResourceLocation.fromNamespaceAndPath("thaumcraft", "tagged_shaped"), SHAPED);
        });
        event.register(Registries.LOOT_CONDITION_TYPE, helper -> helper.register(
            ResourceLocation.fromNamespaceAndPath("thaumcraft", "material_available"), MATERIAL_AVAILABLE));
    }
}
