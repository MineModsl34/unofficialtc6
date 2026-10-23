package thaumcraft.common.items.armor;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import thaumcraft.common.items.TCGearMaterials;

public class ItemThaumiumArmor extends ArmorItem {
    public ItemThaumiumArmor(Type type) {
        super(TCGearMaterials.THAUMIUM, type, new Properties().durability(type.getDurability(25)).rarity(Rarity.UNCOMMON));
    }

    @Override
    public ResourceLocation getArmorTexture(ItemStack stack, Entity wearer, EquipmentSlot slot,
                                            ArmorMaterial.Layer layer, boolean innerModel) {
        return ResourceLocation.parse("thaumcraft:textures/entity/armor/thaumium_" + (innerModel ? 2 : 1) + ".png");
    }
}
