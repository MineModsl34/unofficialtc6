package thaumcraft.common.items.armor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import thaumcraft.api.items.*;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;
public final class ItemGoggles extends ArmorItem implements IVisDiscountGear,IGoggles,IRevealer,ICurioItem {
    public ItemGoggles() { super(WearableRegistries.SPECIAL,Type.HELMET,new Properties().durability(350).rarity(Rarity.RARE)); }
    @Override public int getVisDiscount(ItemStack stack,Player wearer) { return 5; }
    @Override public boolean showNodes(ItemStack stack,LivingEntity wearer) { return true; }
    @Override public boolean showIngamePopups(ItemStack stack,LivingEntity wearer) { return true; }
    @Override public boolean canEquip(SlotContext context,ItemStack stack) { return context.identifier().equals("head"); }
    @Override public boolean isValidRepairItem(ItemStack armor,ItemStack repair) { return repair.is(ItemsTC.ingotBrass) || super.isValidRepairItem(armor,repair); }
    @Override public ResourceLocation getArmorTexture(ItemStack stack,Entity wearer,EquipmentSlot slot,ArmorMaterial.Layer layer,boolean inner) { return ResourceLocation.parse("thaumcraft:textures/entity/armor/goggles.png"); }
}
