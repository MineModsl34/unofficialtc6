package thaumcraft.common.items.armor;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import thaumcraft.api.items.*;
public final class ItemFortressArmor extends ArmorItem implements IGoggles,IRevealer {
    public ItemFortressArmor(Type type) { super(WearableRegistries.FORTRESS,type,new Properties().durability(type.getDurability(40)).rarity(Rarity.RARE)); }
    @Override public boolean showNodes(ItemStack stack,LivingEntity wearer) { return WearableTags.has(stack,"goggles"); }
    @Override public boolean showIngamePopups(ItemStack stack,LivingEntity wearer) { return WearableTags.has(stack,"goggles"); }
    @Override public boolean isValidRepairItem(ItemStack armor,ItemStack repair) { return repair.is(ItemsTC.ingotThaumium) || super.isValidRepairItem(armor,repair); }
    @Override public ResourceLocation getArmorTexture(ItemStack stack,Entity wearer,EquipmentSlot slot,ArmorMaterial.Layer layer,boolean inner) { return ResourceLocation.parse("thaumcraft:textures/entity/armor/fortress_armor.png"); }
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> tooltip,TooltipFlag flag) {
        if (WearableTags.has(stack,"goggles")) tooltip.add(Component.translatable("item.thaumcraft.goggles").withStyle(ChatFormatting.DARK_PURPLE));
        if (WearableTags.has(stack,"mask")) tooltip.add(Component.translatable("item.fortress_helm.mask." + WearableTags.integer(stack,"mask")).withStyle(ChatFormatting.GOLD));
        super.appendHoverText(stack,context,tooltip,flag);
    }
}
