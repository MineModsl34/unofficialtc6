package thaumcraft.common.items.curios;

import net.minecraft.core.Holder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.enchantment.Enchantment;

public class ItemPrimordialPearl extends Item {
    public ItemPrimordialPearl() {
        super(new Properties().durability(8).rarity(Rarity.UNCOMMON).setNoRepair());
    }

    /** Client item predicate thaumcraft:type: pearl 0, nodule 1, mote 2. */
    public static float modelType(ItemStack stack) {
        return stack.getDamageValue() < 3 ? 0.0F : stack.getDamageValue() < 6 ? 1.0F : 2.0F;
    }

    @Override
    public String getDescriptionId(ItemStack stack) {
        return super.getDescriptionId(stack) + (stack.getDamageValue() < 3 ? "_pearl"
            : stack.getDamageValue() < 6 ? "_nodule" : "_mote");
    }

    @Override
    public boolean hasCraftingRemainingItem(ItemStack stack) {
        return stack.getDamageValue() < 7;
    }

    @Override
    public ItemStack getCraftingRemainingItem(ItemStack stack) {
        if (!hasCraftingRemainingItem(stack)) {
            return ItemStack.EMPTY;
        }
        ItemStack remainder = new ItemStack(this, stack.getCount());
        remainder.setDamageValue(stack.getDamageValue() + 1);
        return remainder;
    }

    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack repair) {
        return false;
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }

    @Override
    public boolean supportsEnchantment(ItemStack stack, Holder<Enchantment> enchantment) {
        return false;
    }
}
