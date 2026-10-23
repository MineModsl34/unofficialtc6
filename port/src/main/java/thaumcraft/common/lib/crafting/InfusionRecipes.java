package thaumcraft.common.lib.crafting;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import java.util.LinkedHashMap;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.common.crafting.IngredientType;
import thaumcraft.Thaumcraft;
import thaumcraft.api.crafting.InfusionRecipe;
import thaumcraft.api.crafting.LabelVariantIngredient;
import thaumcraft.api.internal.CommonInternals;
import thaumcraft.api.items.TCDataComponents;
import thaumcraft.common.lib.enchantment.EnumInfusionEnchantment;
import thaumcraft.common.lib.research.LegacyScanIdentity;

/** Reload-aware real infusion catalog. Read-only research-display recipes are never executable. */
@EventBusSubscriber(modid = Thaumcraft.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class InfusionRecipes {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "infusion");
    public static final RecipeType<InfusionRecipe> TYPE = new RecipeType<>() { @Override public String toString() { return ID.toString(); } };
    public static final RecipeSerializer<InfusionRecipe> SERIALIZER = serializer(InfusionRecipe.CODEC);
    public static final RecipeSerializer<InfusionEnchantmentRecipe> ENCHANTMENT_SERIALIZER = serializer(InfusionEnchantmentRecipe.CODEC);
    public static final RecipeSerializer<InfusionRunicAugmentRecipe> RUNIC_SERIALIZER = serializer(InfusionRunicAugmentRecipe.CODEC);
    public static final IngredientType<LabelVariantIngredient> LABEL_INGREDIENT_TYPE = new IngredientType<>(LabelVariantIngredient.CODEC);
    private static MinecraftServer server;
    private InfusionRecipes() {}
    @SubscribeEvent public static void register(RegisterEvent event) {
        event.register(Registries.RECIPE_TYPE, helper -> helper.register(ID, TYPE));
        event.register(Registries.RECIPE_SERIALIZER, helper -> {
            helper.register(ID, SERIALIZER);
            helper.register(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "infusion_enchantment"), ENCHANTMENT_SERIALIZER);
            helper.register(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "infusion_runic"), RUNIC_SERIALIZER);
        });
        event.register(NeoForgeRegistries.Keys.INGREDIENT_TYPES, helper -> helper.register(
            ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "label_variant"), LABEL_INGREDIENT_TYPE));
    }
    public static void setServer(@Nullable MinecraftServer current) { server = current; }
    public static void register(ResourceLocation id, InfusionRecipe recipe) {
        if (server != null && !server.isSameThread()) throw new IllegalStateException("Infusion registration requires the server owner thread");
        CommonInternals.craftingRecipeCatalog.put(java.util.Objects.requireNonNull(id), java.util.Objects.requireNonNull(recipe));
    }
    public static List<RecipeHolder<InfusionRecipe>> all(@Nullable Level level) {
        LinkedHashMap<ResourceLocation, RecipeHolder<InfusionRecipe>> recipes = new LinkedHashMap<>();
        if (level != null) for (var recipe : level.getRecipeManager().getAllRecipesFor(TYPE)) recipes.put(recipe.id(), recipe);
        CommonInternals.craftingRecipeCatalog.forEach((id, recipe) -> {
            if (recipe instanceof InfusionRecipe infusion) recipes.put(id, new RecipeHolder<>(id, infusion));
            else recipes.remove(id); // A source add-on may replace a crucible id with infusion or vice versa.
        });
        return List.copyOf(recipes.values());
    }
    @Nullable public static InfusionRecipe findMatching(List<ItemStack> components, ItemStack central, @Nullable Player owner) {
        if (owner == null || owner.level().isClientSide || central == null || central.isEmpty() || components.size() > InfusionRecipe.MAX_COMPONENTS) return null;
        for (var holder : all(owner.level())) if (holder.value().matches(components, central, owner.level(), owner)) return holder.value();
        return null;
    }
    @Nullable public static InfusionRecipe findByOutput(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        Level level = server != null && server.isSameThread() ? server.overworld() : null;
        for (var holder : all(level)) if (holder.value().getRecipeOutput() instanceof ItemStack output
            && output.is(stack.getItem()) && output.getDamageValue() == stack.getDamageValue()) return holder.value();
        return null;
    }
    /** Matrix's original areItemStacksEqualForCrafting, not theory table's opposite subset rule.
     * Source actual-without-NBT bypasses recipe NBT; expected damage32767 is a real wildcard.
     */
    public static boolean itemsMatch(ItemStack actual, ItemStack required) {
        if (actual == null || required == null) return actual == required;
        if (actual.isEmpty() || required.isEmpty()) return actual.isEmpty() && required.isEmpty();
        var found = LegacyScanIdentity.identify(actual); var expected = LegacyScanIdentity.identify(required);
        boolean wildcard = LegacyScanIdentity.rawDamage(required) == 32767;
        if (!found.itemId().equals(expected.itemId()) || (!wildcard && found.metadata() != expected.metadata())) return false;
        boolean actualHasTags = actual.getComponentsPatch().entrySet().stream().anyMatch(entry -> entry.getKey() != DataComponents.DAMAGE);
        if (!actualHasTags) return true;
        for (var entry : required.getComponentsPatch().entrySet()) {
            if (entry.getKey() == DataComponents.DAMAGE) continue;
            if (entry.getKey() == DataComponents.CUSTOM_DATA) {
                CustomData expectedData = required.get(DataComponents.CUSTOM_DATA), actualData = actual.get(DataComponents.CUSTOM_DATA);
                if (expectedData != null && !SourceIngredientTags.contains(expectedData.copyTag(), actualData == null ? null : actualData.copyTag())) return false;
            } else if (!java.util.Objects.equals(entry.getValue().orElse(null), actual.get(entry.getKey()))) return false;
        }
        return true;
    }
    /** Source Object[]{label,Tag}: preserve the central stack and put one bounded source tag.
     * infench becomes the native component; all other original/add-on NBT labels stay CUSTOM_DATA.
     */
    public static boolean applyOutputTag(ItemStack stack, String label, Tag value, HolderLookup.Provider lookup) {
        if (stack == null || stack.isEmpty() || label == null || label.isEmpty() || label.length() > 256 || value == null || value.toString().length() > 65536) return false;
        if (label.equals("infench")) {
            if (!(value instanceof ListTag list) || list.size() > 256 || TCDataComponents.INFUSION_ENCHANTMENTS == null) return false;
            stack.set(TCDataComponents.INFUSION_ENCHANTMENTS, EnumInfusionEnchantment.fromLegacy(list)); return true;
        }
        CustomData previous = stack.get(DataComponents.CUSTOM_DATA); CompoundTag data = previous == null ? new CompoundTag() : previous.copyTag();
        data.put(label, value.copy());
        if (data.toString().length() > 131072) return false;
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data)); return true;
    }
    private static <T extends InfusionRecipe> RecipeSerializer<T> serializer(MapCodec<T> codec) {
        return new RecipeSerializer<>() {
            private final StreamCodec<RegistryFriendlyByteBuf, T> stream = StreamCodec.of((buffer, value) -> {
                var ops = buffer.registryAccess().createSerializationContext(JsonOps.INSTANCE);
                buffer.writeUtf(codec.codec().encodeStart(ops, value).getOrThrow().toString(), 262144);
            }, buffer -> {
                var ops = buffer.registryAccess().createSerializationContext(JsonOps.INSTANCE);
                return codec.codec().parse(ops, JsonParser.parseString(buffer.readUtf(262144))).getOrThrow();
            });
            @Override public MapCodec<T> codec() { return codec; }
            @Override public StreamCodec<RegistryFriendlyByteBuf, T> streamCodec() { return stream; }
        };
    }
}
