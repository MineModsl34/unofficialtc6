package thaumcraft.common.items.armor;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import thaumcraft.api.items.IVisDiscountGear;
import thaumcraft.api.items.IWarpingGear;

public final class ItemCultistRobeArmor extends ArmorItem implements IVisDiscountGear, IWarpingGear {
    public ItemCultistRobeArmor(Type type, Holder<ArmorMaterial> material) {
        super(material, type, new Properties().durability(type.getDurability(17)).rarity(Rarity.UNCOMMON));
    }
    @Override public boolean isValidRepairItem(ItemStack armor, ItemStack material) { return material.is(Items.IRON_INGOT) || super.isValidRepairItem(armor, material); }
    @Override public int getVisDiscount(ItemStack stack, Player player) { return 1; }
    @Override public int getWarp(ItemStack stack, Player player) { return 1; }
    @Override public ResourceLocation getArmorTexture(ItemStack stack, Entity wearer, EquipmentSlot slot, ArmorMaterial.Layer layer, boolean inner) {
        return ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/entity/armor/cultist_robe_armor.png");
    }
}
