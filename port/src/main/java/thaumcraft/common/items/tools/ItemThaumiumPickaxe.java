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


public final class ItemThaumiumPickaxe extends PickaxeItem {
    public ItemThaumiumPickaxe(){super(TCGearMaterials.TOOLMAT_THAUMIUM,new Properties().attributes(DiggerItem.createAttributes(TCGearMaterials.TOOLMAT_THAUMIUM,1F,-2.8F)));}


}
