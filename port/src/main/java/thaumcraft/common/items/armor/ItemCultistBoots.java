package thaumcraft.common.items.armor;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import thaumcraft.api.items.IVisDiscountGear;
import thaumcraft.api.items.IWarpingGear;

public final class ItemCultistBoots extends ArmorItem implements IVisDiscountGear, IWarpingGear {
    public ItemCultistBoots() { super(ArmorMaterials.IRON, Type.BOOTS, new Properties().durability(Type.BOOTS.getDurability(15)).rarity(Rarity.UNCOMMON)); }
    @Override public boolean isValidRepairItem(ItemStack armor, ItemStack material) { return material.is(Items.IRON_INGOT) || super.isValidRepairItem(armor, material); }
    @Override public int getVisDiscount(ItemStack stack, Player player) { return 1; }
    @Override public int getWarp(ItemStack stack, Player player) { return 1; }
    @Override public ResourceLocation getArmorTexture(ItemStack stack, Entity wearer, EquipmentSlot slot, ArmorMaterial.Layer layer, boolean inner) {
        return ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/entity/armor/cultistboots.png");
    }
}
