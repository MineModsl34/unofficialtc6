package thaumcraft.api.research;

import java.util.Objects;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.items.TCDataComponents;
import thaumcraft.common.lib.research.LegacyScanIdentity;

public class ScanItem implements IScanThing {
    public static final int WILDCARD_DAMAGE = 32767;
    String research;
    ItemStack stack;

    public ScanItem(String research, ItemStack stack) {
        this.research = research;
        // Source keeps the caller's stack reference; no defensive copy.
        this.stack = stack;
    }

    /** Descriptor-only wildcard: unlike setDamageValue, setting the raw component is not clamped. */
    public static ItemStack wildcard(ItemStack stack) {
        ItemStack descriptor = stack.copy();
        descriptor.set(DataComponents.DAMAGE, WILDCARD_DAMAGE);
        return descriptor;
    }

    @Override
    public boolean checkThing(Player player, Object object) {
        if (object == null) return false;
        ItemStack actual = null;
        if (object instanceof ItemStack item) actual = item;
        if (object instanceof ItemEntity entity && entity.getItem() != null) actual = entity.getItem();
        return actual != null && !actual.isEmpty() && matchesForCrafting(actual, stack);
    }

    /**
     * Narrow replacement for TC6 ThaumcraftInvHelper.areItemStacksEqualForCrafting.
     * Count is ignored; old flattened metadata/wildcards are compared before item NBT.
     * CUSTOM_DATA keeps the original top-level SNBT subset comparison. Migrated Aspects
     * and tc.charge retain their old NBT representations; other explicit modern component
     * requirements use value equality (an explicit 1.21 adaptation, not a universal NBT migration).
     */
    public static boolean matchesForCrafting(ItemStack actual, ItemStack recipe) {
        if (actual == null || recipe == null) return actual == recipe;
        if (actual.isEmpty() != recipe.isEmpty()) return false;
        var input = LegacyScanIdentity.identify(actual);
        var target = LegacyScanIdentity.identify(recipe);
        if (!input.itemId().equals(target.itemId())) return false;
        if (LegacyScanIdentity.rawDamage(recipe) != WILDCARD_DAMAGE && input.metadata() != target.metadata()) return false;
        // Source quirk: an actual stack WITHOUT NBT ignores even the recipe's NBT requirement.
        if (!hasSourceNbt(actual)) return true;
        CompoundTag actualNbt = legacyNbt(actual);
        CompoundTag recipeNbt = legacyNbt(recipe);
        for (String key : recipeNbt.getAllKeys()) {
            if (!actualNbt.contains(key) || !actualNbt.get(key).toString().equals(recipeNbt.get(key).toString())) return false;
        }
        for (var entry : recipe.getComponentsPatch().entrySet()) {
            DataComponentType<?> type = entry.getKey();
            if (mappedNbt(type) || type == DataComponents.DAMAGE) continue;
            if (entry.getValue().isPresent()) {
                if (!Objects.equals(actual.get(type), entry.getValue().get())) return false;
            } else if (actual.has(type)) return false;
        }
        return true;
    }

    private static boolean hasSourceNbt(ItemStack stack) {
        return stack.getComponentsPatch().entrySet().stream().anyMatch(entry -> entry.getKey() != DataComponents.DAMAGE);
    }

    private static boolean mappedNbt(DataComponentType<?> type) {
        return type == DataComponents.CUSTOM_DATA || type == TCDataComponents.ASPECTS || type == TCDataComponents.CHARGE;
    }

    private static CompoundTag legacyNbt(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        AspectList aspects = TCDataComponents.ASPECTS == null ? null : stack.get(TCDataComponents.ASPECTS);
        if (aspects != null) aspects.writeToNBT(tag);
        Integer charge = TCDataComponents.CHARGE == null ? null : stack.get(TCDataComponents.CHARGE);
        if (charge != null) tag.putInt("tc.charge", charge);
        return tag;
    }

    @Override
    public String getResearchKey(Player player, Object object) { return research; }
}
