package thaumcraft.common.items.tools;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.Level;
import thaumcraft.common.items.TCGearMaterials;


public final class ItemThaumiumAxe extends AxeItem {
    public ItemThaumiumAxe(){super(TCGearMaterials.TOOLMAT_THAUMIUM,new Properties().attributes(DiggerItem.createAttributes(TCGearMaterials.TOOLMAT_THAUMIUM,8-TCGearMaterials.TOOLMAT_THAUMIUM.getAttackDamageBonus(),-3F)));}


}
