package thaumcraft.api;

import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import net.neoforged.neoforge.items.wrapper.SidedInvWrapper;
import thaumcraft.common.golems.seals.SealInventoryOps;
import thaumcraft.common.golems.seals.SealOreRules;
import thaumcraft.common.lib.research.LegacyScanIdentity;

/** TC6 public inventory helpers, using capabilities, native containers and component-backed NBT. */
public final class ThaumcraftInvHelper {
    private ThaumcraftInvHelper() {}

    public static IItemHandler getItemHandlerAt(Level world, BlockPos pos, Direction side) {
        if (!world.hasChunkAt(pos)) return null;
        IItemHandler handler = world.getCapability(Capabilities.ItemHandler.BLOCK, pos, side);
        if (handler != null) return handler;
        Container container = HopperBlockEntity.getContainerAt(world, pos);
        return container == null ? null : wrapInventory(container, side);
    }

    public static IItemHandler wrapInventory(Container inventory, Direction side) {
        return inventory instanceof WorldlyContainer sided && side != null
            ? new SidedInvWrapper(sided, side) : new InvWrapper(inventory);
    }

    public static boolean compareTagsRelaxed(CompoundTag prime, CompoundTag other) {
        for (String key : prime.getAllKeys()) {
            if (other == null || !other.contains(key) || !Objects.equals(prime.get(key), other.get(key))) return false;
        }
        return true;
    }

    public static boolean areItemStackTagsEqualRelaxed(ItemStack prime, ItemStack other) {
        if (prime.isEmpty() || other.isEmpty()) return prime.isEmpty() && other.isEmpty();
        return componentsContain(prime, other, false);
    }

    private static boolean componentsContain(ItemStack expected, ItemStack actual, boolean craftingStrings) {
        for (var entry : expected.getComponentsPatch().entrySet()) {
            var type = entry.getKey();
            // Native damage represents old metadata, which is compared independently of old item NBT.
            if (type == DataComponents.DAMAGE) continue;
            if (type == DataComponents.CUSTOM_DATA) {
                CustomData required = expected.get(DataComponents.CUSTOM_DATA);
                CustomData found = actual.get(DataComponents.CUSTOM_DATA);
                if (required == null) { if (found != null) return false; continue; }
                CompoundTag prime = required.copyTag(), other = found == null ? null : found.copyTag();
                for (String key : prime.getAllKeys()) {
                    if (other == null || !other.contains(key)) return false;
                    if (craftingStrings ? !prime.get(key).toString().equals(other.get(key).toString())
                        : !Objects.equals(prime.get(key), other.get(key))) return false;
                }
            } else if (!Objects.equals(entry.getValue().orElse(null), actual.get(type))) return false;
        }
        return true;
    }

    public static boolean areItemStackTagsEqualForCrafting(ItemStack slotItem, ItemStack recipeItem) {
        return slotItem != null && recipeItem != null && componentsContain(recipeItem, slotItem, true);
    }

    private static boolean itemMatches(ItemStack target, ItemStack input, boolean strict) {
        if (target == null || input == null) return target == input;
        var a = LegacyScanIdentity.identify(target);
        var b = LegacyScanIdentity.identify(input);
        return a.itemId().equals(b.itemId()) && (a.metadata() == b.metadata()
            || !strict && LegacyScanIdentity.rawDamage(target) == 32767);
    }

    private static boolean tagsEqual(ItemStack a, ItemStack b) {
        return a.getComponentsPatch().forget(type -> type == DataComponents.DAMAGE)
            .equals(b.getComponentsPatch().forget(type -> type == DataComponents.DAMAGE));
    }

    public static boolean containsMatch(boolean strict, ItemStack[] inputs, List<ItemStack> targets) {
        for (ItemStack input : inputs) for (ItemStack target : targets)
            if (itemMatches(target, input, strict) && tagsEqual(target, input)) return true;
        return false;
    }

    public static boolean areItemsEqual(ItemStack first, ItemStack second) {
        if (first.isDamageableItem() && second.isDamageableItem()) return first.getItem() == second.getItem();
        return itemMatches(first, second, true);
    }

    public static boolean areItemStacksEqualForCrafting(ItemStack stack, Object input) {
        if (stack == null || input == null) return stack == input;
        if (input instanceof Object[]) return true; // Retains the original source API behavior.
        if (input instanceof String name) return containsMatch(false, new ItemStack[]{stack}, ThaumcraftApiHelper.getOresWithWildCards(name));
        if (!(input instanceof ItemStack recipe)) return false;
        return (stack.getComponentsPatch().forget(type -> type == DataComponents.DAMAGE).isEmpty()
                || areItemStackTagsEqualForCrafting(stack, recipe)) && itemMatches(recipe, stack, false);
    }

    public static ItemStack insertStackAt(Level world, BlockPos pos, Direction side, ItemStack stack, boolean simulate) {
        IItemHandler handler = getItemHandlerAt(world, pos, side);
        return handler == null ? stack : ItemHandlerHelper.insertItemStacked(handler, stack, simulate);
    }

    public static ItemStack hasRoomFor(Level world, BlockPos pos, Direction side, ItemStack stack) {
        ItemStack remaining = insertStackAt(world, pos, side, stack.copy(), true);
        return remaining.isEmpty() ? stack.copy() : remaining.copyWithCount(stack.getCount() - remaining.getCount());
    }

    public static boolean hasRoomForSome(Level world, BlockPos pos, Direction side, ItemStack stack) {
        ItemStack remaining = insertStackAt(world, pos, side, stack.copy(), true);
        return stack.getCount() == 0 || remaining.getCount() != stack.getCount();
    }

    public static boolean hasRoomForAll(Level world, BlockPos pos, Direction side, ItemStack stack) {
        return insertStackAt(world, pos, side, stack.copy(), true).isEmpty();
    }

    public static boolean areItemStacksEqual(ItemStack prime, ItemStack other, InvFilter filter) {
        if (prime == null || other == null) return prime == other;
        if (prime.isEmpty() || other.isEmpty()) return prime.isEmpty() && other.isEmpty();
        if (filter.useMod) return BuiltInRegistries.ITEM.getKey(prime.getItem()).getNamespace()
            .equals(BuiltInRegistries.ITEM.getKey(other.getItem()).getNamespace());
        if (filter.useOre && other.getComponentsPatch().forget(type -> type == DataComponents.DAMAGE).isEmpty()) {
            if (SealOreRules.sameVanillaGroup(BuiltInRegistries.ITEM.getKey(prime.getItem()).toString(),
                    BuiltInRegistries.ITEM.getKey(other.getItem()).toString())
                || prime.getTags().filter(tag -> SealOreRules.isReviewedTag(tag.location().toString())).anyMatch(other::is)) return true;
        }
        // The source mutates the caller's filter when either operand carries wildcard metadata.
        if (LegacyScanIdentity.rawDamage(prime) == 32767 || LegacyScanIdentity.rawDamage(other) == 32767) filter.igDmg = true;
        boolean items = SealInventoryOps.areItemStacksEqual(prime, other,
            new SealInventoryOps.Filter(filter.igDmg, true, false, false));
        return items && (filter.igNBT || (filter.relaxedNBT ? areItemStackTagsEqualRelaxed(prime, other) : tagsEqual(prime, other)));
    }

    public static int countTotalItemsIn(IItemHandler inventory, ItemStack stack, InvFilter filter) {
        int count = 0;
        if (inventory != null) for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack current = inventory.getStackInSlot(slot);
            if (areItemStacksEqual(stack, current, filter)) count += current.getCount();
        }
        return count;
    }

    public static int countTotalItemsIn(Level world, BlockPos pos, Direction side, ItemStack stack, InvFilter filter) {
        return countTotalItemsIn(getItemHandlerAt(world, pos, side), stack, filter);
    }

    public static class InvFilter {
        public boolean igDmg, igNBT, useOre, useMod, relaxedNBT;
        public static final InvFilter STRICT = new InvFilter(false, false, false, false);
        public static final InvFilter BASEORE = new InvFilter(false, false, true, false);
        public InvFilter(boolean ignoreDamage, boolean ignoreNBT, boolean useOre, boolean useMod) {
            igDmg = ignoreDamage; igNBT = ignoreNBT; this.useOre = useOre; this.useMod = useMod;
        }
        public InvFilter setRelaxedNBT() { relaxedNBT = true; return this; }
    }
}
