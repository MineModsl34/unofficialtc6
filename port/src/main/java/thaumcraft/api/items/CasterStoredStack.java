package thaumcraft.api.items;

import com.mojang.serialization.Codec;
import java.util.Objects;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/** Immutable caster focus/block value. Keeps the original optional ItemStack wire and
 * save shape, with native content equality and defensive copies as vanilla containers. */
public final class CasterStoredStack {
    public static final Codec<CasterStoredStack> CODEC = ItemStack.OPTIONAL_CODEC
        .xmap(CasterStoredStack::new, CasterStoredStack::copy);
    public static final StreamCodec<RegistryFriendlyByteBuf, CasterStoredStack> STREAM_CODEC = ItemStack.OPTIONAL_STREAM_CODEC
        .map(CasterStoredStack::new, CasterStoredStack::copy);

    private final ItemStack stack;

    public CasterStoredStack(ItemStack stack) {
        this.stack = defensiveCopy(Objects.requireNonNull(stack));
    }

    public ItemStack copy() { return defensiveCopy(stack); }

    private static ItemStack defensiveCopy(ItemStack source) {
        ItemStack result = source.copy();
        if (result.isEmpty()) return result;
        // Native stack copies share component values. Mod-owned AspectList is mutable;
        // picked jars/crystals must not expose that value through either copy boundary.
        if (TCDataComponents.ASPECTS != null) {
            var aspects = source.get(TCDataComponents.ASPECTS);
            if (aspects != null) result.set(TCDataComponents.ASPECTS, aspects.copy());
        }
        return result;
    }

    @Override public boolean equals(Object other) {
        return this == other || other instanceof CasterStoredStack stored && ItemStack.matches(stack, stored.stack);
    }

    @Override public int hashCode() { return 31 * ItemStack.hashItemAndComponents(stack) + stack.getCount(); }
}
