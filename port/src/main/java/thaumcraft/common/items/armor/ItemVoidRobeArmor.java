package thaumcraft.common.items.armor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import thaumcraft.api.items.*;
public final class ItemVoidRobeArmor extends ItemRobeArmor implements IGoggles,IRevealer,IWarpingGear {
    public ItemVoidRobeArmor(Type type) { super(type,WearableRegistries.VOID_ROBE,18,Rarity.EPIC); }
    @Override public boolean isValidRepairItem(ItemStack armor,ItemStack repair) { return repair.is(ItemsTC.ingotVoid); }
    @Override public int getVisDiscount(ItemStack stack,Player wearer) { return 5; }
    @Override public int getWarp(ItemStack stack,Player wearer) { return 3; }
    @Override public boolean showNodes(ItemStack stack,LivingEntity wearer) { return getType() == Type.HELMET; }
    @Override public boolean showIngamePopups(ItemStack stack,LivingEntity wearer) { return getType() == Type.HELMET; }
    @Override public ResourceLocation getArmorTexture(ItemStack stack,Entity wearer,EquipmentSlot slot,ArmorMaterial.Layer layer,boolean inner) { return ResourceLocation.parse("thaumcraft:textures/entity/armor/void_robe_armor" + (layer.dyeable() ? "_overlay" : "") + ".png"); }
    @Override public void inventoryTick(ItemStack stack,Level world,Entity entity,int slot,boolean selected) {
        super.inventoryTick(stack,world,entity,slot,selected);
        if (!world.isClientSide && stack.isDamaged() && entity instanceof LivingEntity && entity.tickCount % 20 == 0) {
            stack.setDamageValue(stack.getDamageValue() - 1);
            if (stack.isDamaged() && entity instanceof Player player && slot >= 36 && slot < 40 && player.getInventory().armor.get(slot-36) == stack) stack.setDamageValue(stack.getDamageValue() - 1);
        }
    }
}
