package thaumcraft.api.aspects;

import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.Event;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.common.lib.crafting.ThaumcraftCraftingManager;

/**
 * Not in TC6. Posted on NeoForge.EVENT_BUS in the second pass of the aspect table, after the tag rules: addons
 * can give aspects to items that stayed empty (made in their own machines, unique ones). Values of already
 * known items do not change. Other items' aspects are available through {@link #get}.
 */
public class AspectGapEvent extends Event {
    /** The item's aspects: registered or derived from recipes; null or empty if there are none. */
    public AspectList get(ItemStack stack) {
        return ThaumcraftCraftingManager.getObjectTags(stack);
    }

    /** Whether the item has no aspects (then {@link #fill} sets them); an empty entry, a failed derivation, also counts as empty. */
    public boolean isEmpty(ItemStack stack) {
        AspectList aspects = ThaumcraftApi.exists(stack) ? this.get(stack) : null;
        return aspects == null || aspects.size() == 0;
    }

    /** Sets the item's aspects if it has none; returns whether it did. */
    public boolean fill(ItemStack stack, AspectList aspects) {
        if (aspects == null || aspects.size() == 0 || !this.isEmpty(stack)) return false;
        ThaumcraftApi.registerObjectTag(stack.copyWithCount(1), aspects.copy());
        return true;
    }
}
