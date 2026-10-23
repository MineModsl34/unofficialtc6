package thaumcraft.common.items.armor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import thaumcraft.api.items.IRechargable;
public final class ItemBootsTraveller extends ArmorItem implements IRechargable {
    public ItemBootsTraveller() { super(WearableRegistries.SPECIAL,Type.BOOTS,new Properties().durability(350).rarity(Rarity.RARE)); }
    @Override public boolean isValidRepairItem(ItemStack armor,ItemStack repair) { return repair.is(Items.LEATHER); }
    @Override public ResourceLocation getArmorTexture(ItemStack stack,Entity wearer,EquipmentSlot slot,ArmorMaterial.Layer layer,boolean inner) { return ResourceLocation.parse("thaumcraft:textures/entity/armor/bootstraveler.png"); }
    @Override public int getMaxCharge(ItemStack stack,LivingEntity wearer) { return 240; }
    @Override public EnumChargeDisplay showInHud(ItemStack stack,LivingEntity wearer) { return EnumChargeDisplay.PERIODIC; }
}
