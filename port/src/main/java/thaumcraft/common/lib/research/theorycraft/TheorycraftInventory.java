package thaumcraft.common.lib.research.theorycraft;

import java.util.List;
import java.util.Objects;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import thaumcraft.api.items.TCDataComponents;
import thaumcraft.common.lib.research.LegacyScanIdentity;

/** Source main-inventory matching and bounded whole-payment planning; no missing-item exemption. */
public final class TheorycraftInventory {
    private TheorycraftInventory() {}
    public static boolean matches(ItemStack actual, ItemStack required) {
        if (actual == null || required == null || actual.isEmpty() || required.isEmpty()) return false;
        return TheorycraftOreEquivalence.matches(actual, required) || matchesExact(actual, required);
    }
    /** Ordinary research obtains use ore=false; keep source relaxed-NBT direction without
     * applying cards' deliberately broader OreDictionary payment equivalence. */
    public static boolean matchesExact(ItemStack actual, ItemStack required) {
        if (actual == null || required == null || actual.isEmpty() || required.isEmpty()) return false;
        var identity = LegacyScanIdentity.identify(actual); var target = LegacyScanIdentity.identify(required);
        if (!identity.itemId().equals(target.itemId()) || identity.metadata() != target.metadata()) return false;
        if (LegacyScanIdentity.rawDamage(actual) != LegacyScanIdentity.rawDamage(required)) return false;
        CompoundTag recipe = sourceNbt(required), inventory = sourceNbt(actual);
        // Original InventoryUtils passes ACTUAL as prime. With relaxed NBT, actual keys must
        // exist in the requirement, while an actual stack with no NBT is accepted.
        if (!recipe.isEmpty()) for (String key : inventory.getAllKeys()) if (!Objects.equals(inventory.get(key), recipe.get(key))) return false;
        if (!required.getComponentsPatch().entrySet().isEmpty()) for (var entry : actual.getComponentsPatch().entrySet()) {
            var type = entry.getKey();
            if (type == DataComponents.DAMAGE || type == DataComponents.CUSTOM_DATA || type == TCDataComponents.ASPECTS || type == TCDataComponents.CHARGE) continue;
            if (entry.getValue().isPresent() && !Objects.equals(actual.get(type), required.get(type))) return false;
        }
        return true;
    }
    private static CompoundTag sourceNbt(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (TCDataComponents.ASPECTS != null && stack.has(TCDataComponents.ASPECTS)) stack.get(TCDataComponents.ASPECTS).writeToNBT(tag);
        if (TCDataComponents.CHARGE != null && stack.has(TCDataComponents.CHARGE)) tag.putInt("tc.charge", stack.get(TCDataComponents.CHARGE));
        return tag;
    }
    public static int[] payment(List<ItemStack> inventory, ItemStack[] required, boolean[] consumed) {
        int[] result = new int[inventory.size()];
        if (required == null) return result;
        if (required.length > 64 || consumed == null || consumed.length != required.length) return null;
        for (int index = 0; index < required.length; index++) {
            ItemStack requirement = required[index]; if (requirement == null || requirement.isEmpty()) return null;
            int remaining = requirement.getCount();
            for (int slot = 0; slot < inventory.size() && remaining > 0; slot++) if (matches(inventory.get(slot), requirement)) {
                int available = inventory.get(slot).getCount() - (consumed[index] ? result[slot] : 0);
                int take = Math.min(available, remaining); if (consumed[index]) result[slot] += take; remaining -= take;
            }
            if (remaining > 0) return null;
        }
        return result;
    }
}
