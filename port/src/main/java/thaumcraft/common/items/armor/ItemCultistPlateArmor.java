package thaumcraft.common.items.armor;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;

/** Source eighteen durability units, iron repair, no invented warp or vis-discount interfaces. */
public final class ItemCultistPlateArmor extends ArmorItem {
    public ItemCultistPlateArmor(Type type, Holder<ArmorMaterial> material) {
        super(material, type, new Properties().durability(type.getDurability(18)).rarity(Rarity.UNCOMMON));
    }
    @Override public boolean isValidRepairItem(ItemStack armor, ItemStack material) { return material.is(Items.IRON_INGOT) || super.isValidRepairItem(armor, material); }
    @Override public ResourceLocation getArmorTexture(ItemStack stack, Entity wearer, EquipmentSlot slot, ArmorMaterial.Layer layer, boolean inner) {
        // Source EntityInhabitedZombie dependency is not registered in this stage; actual source
        // registry identity, if an addon/later phase supplies it, selects the original zombie atlas.
        boolean zombie = wearer != null && net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(wearer.getType()).equals(ResourceLocation.fromNamespaceAndPath("thaumcraft", "inhabitedzombie"));
        return ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/entity/armor/" + (zombie ? "zombie_plate_armor.png" : "cultist_plate_armor.png"));
    }
}
