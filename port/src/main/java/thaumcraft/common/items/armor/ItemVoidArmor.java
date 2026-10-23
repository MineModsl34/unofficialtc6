package thaumcraft.common.items.armor;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import thaumcraft.api.items.IWarpingGear;
import thaumcraft.common.items.TCGearMaterials;

public class ItemVoidArmor extends ArmorItem implements IWarpingGear {
    public ItemVoidArmor(Type type) {
        super(TCGearMaterials.VOID, type, new Properties().durability(type.getDurability(10)).rarity(Rarity.UNCOMMON));
    }

    @Override
    public ResourceLocation getArmorTexture(ItemStack stack, Entity wearer, EquipmentSlot slot,
                                            ArmorMaterial.Layer layer, boolean innerModel) {
        return ResourceLocation.parse("thaumcraft:textures/entity/armor/void_" + (innerModel ? 2 : 1) + ".png");
    }

    @Override
    public void inventoryTick(ItemStack stack, Level world, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, world, entity, slot, selected);
        if (!world.isClientSide && stack.isDamaged() && entity instanceof LivingEntity && entity.tickCount % 20 == 0) {
            stack.setDamageValue(stack.getDamageValue() - 1);
            // TC6's InventoryPlayer also called onArmorTick after all-inventory onUpdate: equipped armor
            // repaired twice. NeoForge 1.21 uses global armor indices 36..39, but removed that extra hook.
            if (stack.isDamaged() && entity instanceof Player player && slot >= 36 && slot < 40
                && player.getInventory().armor.get(slot - 36) == stack) {
                stack.setDamageValue(stack.getDamageValue() - 1);
            }
        }
    }

    @Override
    public int getWarp(ItemStack stack, Player player) {
        return 1;
    }
}
