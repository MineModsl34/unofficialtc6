package thaumcraft.common.items.tools;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.Level;
import thaumcraft.common.items.TCGearMaterials;


public final class ItemThaumiumHoe extends HoeItem {
    public ItemThaumiumHoe(){super(TCGearMaterials.TOOLMAT_THAUMIUM,new Properties().attributes(DiggerItem.createAttributes(TCGearMaterials.TOOLMAT_THAUMIUM,-TCGearMaterials.TOOLMAT_THAUMIUM.getAttackDamageBonus(),TCGearMaterials.TOOLMAT_THAUMIUM.getAttackDamageBonus()-3)));}
    @Override public int getEnchantmentValue(){return 0;} // Source ItemHoe inherits Item's zero, not ItemTool's material enchantability.
    @Override public void postHurtEnemy(ItemStack stack,LivingEntity target,LivingEntity attacker){stack.hurtAndBreak(1,attacker,EquipmentSlot.MAINHAND);}

}
