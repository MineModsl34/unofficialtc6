package thaumcraft.api.crafting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import java.util.stream.Stream;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IEssentiaContainerItem;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.lib.crafting.InfusionRecipes;

/** TC6 labels had blank/filled metadata. One modern item must still distinguish those variants. */
public record LabelVariantIngredient(boolean filled) implements ICustomIngredient {
    public static final MapCodec<LabelVariantIngredient> CODEC = Codec.BOOL.fieldOf("filled")
        .xmap(LabelVariantIngredient::new, LabelVariantIngredient::filled);

    @Override public boolean test(ItemStack input) {
        if (input == null || input.isEmpty() || ItemsTC.label == null || !input.is(ItemsTC.label)) return false;
        AspectList contents = ((IEssentiaContainerItem) input.getItem()).getAspects(input);
        return filled == (contents != null && contents.size() > 0);
    }

    @Override public Stream<ItemStack> getItems() {
        if (ItemsTC.label == null) return Stream.empty();
        if (!filled) return Stream.of(new ItemStack(ItemsTC.label));
        return Aspect.aspects.values().stream().map(aspect -> {
            ItemStack label = new ItemStack(ItemsTC.label);
            ((IEssentiaContainerItem) label.getItem()).setAspects(label, new AspectList().add(aspect, 1));
            return label;
        });
    }

    @Override public boolean isSimple() { return false; }
    @Override public IngredientType<?> getType() { return InfusionRecipes.LABEL_INGREDIENT_TYPE; }
}
