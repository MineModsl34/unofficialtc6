package thaumcraft.common.items.tools;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;

public final class ItemElementalPickaxe extends PickaxeItem {
    public ItemElementalPickaxe(){super(EquipmentMaterials.ELEMENTAL,new Properties().rarity(Rarity.RARE).attributes(DiggerItem.createAttributes(EquipmentMaterials.ELEMENTAL,1,-2.8F)));}
    @Override public boolean onLeftClickEntity(ItemStack stack,Player player,Entity target){
        if(!player.level().isClientSide && (!(target instanceof Player) || player.getServer()!=null && player.getServer().isPvpAllowed()))target.igniteForSeconds(2);
        return false;
    }
}
