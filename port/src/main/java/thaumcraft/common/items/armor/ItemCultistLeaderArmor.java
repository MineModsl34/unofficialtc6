package thaumcraft.common.items.armor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
public final class ItemCultistLeaderArmor extends ArmorItem {
    public ItemCultistLeaderArmor(Type type) { super(WearableRegistries.CULTIST_LEADER,type,new Properties().durability(type.getDurability(30)).rarity(Rarity.RARE)); }
    @Override public boolean isValidRepairItem(ItemStack armor,ItemStack repair) { return repair.is(Items.IRON_INGOT) || super.isValidRepairItem(armor,repair); }
    @Override public ResourceLocation getArmorTexture(ItemStack stack,Entity wearer,EquipmentSlot slot,ArmorMaterial.Layer layer,boolean inner) { return ResourceLocation.parse("thaumcraft:textures/entity/armor/cultist_leader_armor.png"); }
}
