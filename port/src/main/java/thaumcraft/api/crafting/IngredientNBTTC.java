package thaumcraft.api.crafting;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;
import java.util.stream.Stream;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;
import thaumcraft.common.lib.crafting.CrucibleRecipes;
import thaumcraft.common.lib.crafting.SourceIngredientTags;

/** Source IngredientNBTTC migrated to modern components and NeoForge's custom ingredient API.
 * Source NBT requires each expected TOP-LEVEL tag to be equal, but allows unrelated extra tags.
 * Modern CUSTOM_DATA must therefore be compared by keys, not as one whole equal component.
 * Flattened item ids replace metadata; durable damage is still an exact catalyst constraint.
 */
public final class IngredientNBTTC implements ICustomIngredient {
    public static final MapCodec<IngredientNBTTC> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
        ItemStack.STRICT_CODEC.fieldOf("stack").forGetter(IngredientNBTTC::stack)
    ).apply(instance, IngredientNBTTC::new));
    private final ItemStack stack;

    public IngredientNBTTC(ItemStack stack) {
        if (stack == null || stack.isEmpty()) throw new IllegalArgumentException("Empty source component ingredient");
        this.stack = stack.copyWithCount(1);
    }
    public ItemStack stack() { return stack.copy(); }

    @Override public boolean test(ItemStack input) {
        if (input == null || input.isEmpty() || !input.is(stack.getItem()) || input.getDamageValue() != stack.getDamageValue()) return false;
        for (var entry : stack.getComponentsPatch().entrySet()) {
            var type = entry.getKey();
            if (type == DataComponents.CUSTOM_DATA) {
                CustomData expected = stack.get(DataComponents.CUSTOM_DATA);
                CustomData actual = input.get(DataComponents.CUSTOM_DATA);
                if (expected == null) { if (actual != null) return false; }
                else if (!containsSourceTags(expected.copyTag(), actual == null ? null : actual.copyTag())) return false;
            } else if (!Objects.equals(entry.getValue().orElse(null), input.get(type))) return false;
        }
        return true;
    }

    /** Pure original compareTagsRelaxed; nested tags/lists themselves are exact, not recursive subsets. */
    public static boolean containsSourceTags(CompoundTag expected, CompoundTag actual) {
        return SourceIngredientTags.contains(expected, actual);
    }

    @Override public Stream<ItemStack> getItems() { return Stream.of(stack.copy()); }
    @Override public boolean isSimple() { return false; }
    @Override public IngredientType<?> getType() { return CrucibleRecipes.NBT_INGREDIENT_TYPE; }
    @Override public boolean equals(Object other) { return other instanceof IngredientNBTTC ingredient && ItemStack.isSameItemSameComponents(stack, ingredient.stack); }
    @Override public int hashCode() { return ItemStack.hashItemAndComponents(stack); }
}
