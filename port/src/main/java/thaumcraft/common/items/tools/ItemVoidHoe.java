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
import thaumcraft.api.items.IWarpingGear;

public final class ItemVoidHoe extends HoeItem implements IWarpingGear {
    public ItemVoidHoe(){super(TCGearMaterials.TOOLMAT_VOID,new Properties().attributes(DiggerItem.createAttributes(TCGearMaterials.TOOLMAT_VOID,-TCGearMaterials.TOOLMAT_VOID.getAttackDamageBonus(),TCGearMaterials.TOOLMAT_VOID.getAttackDamageBonus()-3)));}
    @Override public int getEnchantmentValue(){return 0;}
    @Override public boolean isValidRepairItem(ItemStack stack,ItemStack ingredient){return false;} // Source ItemVoidHoe does not override Item's false repair-material predicate.
    @Override public void postHurtEnemy(ItemStack stack,LivingEntity target,LivingEntity attacker){stack.hurtAndBreak(1,attacker,EquipmentSlot.MAINHAND);}
    @Override public void inventoryTick(ItemStack stack,Level level,Entity entity,int slot,boolean selected){super.inventoryTick(stack,level,entity,slot,selected);EquipmentToolActions.repair(stack,entity);}
    @Override public boolean onLeftClickEntity(ItemStack stack,Player player,Entity target){EquipmentToolActions.voidLeftClick(player,target);return false;}
    @Override public int getWarp(ItemStack stack,Player player){return 1;}
}
