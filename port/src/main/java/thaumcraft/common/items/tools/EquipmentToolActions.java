package thaumcraft.common.items.tools;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

final class EquipmentToolActions {
    private EquipmentToolActions() {}
    static void repair(ItemStack stack,Entity owner){
        // All source void/crimson/primal tools repair on BOTH logical sides, unlike void armor.
        if(stack.isDamaged() && owner instanceof LivingEntity && owner.tickCount%20==0)stack.setDamageValue(stack.getDamageValue()-1);
    }
    static void voidLeftClick(Player player,Entity target){
        if(!player.level().isClientSide && target instanceof LivingEntity living
            && (!(target instanceof Player) || player.getServer()!=null && player.getServer().isPvpAllowed()))
            living.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,80));
    }
}
