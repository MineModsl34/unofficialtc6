package thaumcraft.common.items.tools;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.Level;
import thaumcraft.common.items.TCGearMaterials;
import thaumcraft.api.items.IWarpingGear;

public final class ItemVoidPickaxe extends PickaxeItem implements IWarpingGear {
    public ItemVoidPickaxe(){super(TCGearMaterials.TOOLMAT_VOID,new Properties().attributes(DiggerItem.createAttributes(TCGearMaterials.TOOLMAT_VOID,1F,-2.8F)));}

    @Override public void inventoryTick(ItemStack stack,Level level,Entity entity,int slot,boolean selected){super.inventoryTick(stack,level,entity,slot,selected);EquipmentToolActions.repair(stack,entity);}
    @Override public boolean onLeftClickEntity(ItemStack stack,Player player,Entity target){EquipmentToolActions.voidLeftClick(player,target);return false;}
    @Override public int getWarp(ItemStack stack,Player player){return 1;}
}
