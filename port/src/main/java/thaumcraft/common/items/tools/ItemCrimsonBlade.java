package thaumcraft.common.items.tools;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import thaumcraft.api.items.IWarpingGear;

public final class ItemCrimsonBlade extends SwordItem implements IWarpingGear {
    public ItemCrimsonBlade(){super(EquipmentMaterials.CRIMSON,new Properties().rarity(Rarity.EPIC).attributes(SwordItem.createAttributes(EquipmentMaterials.CRIMSON,3,-2.4F)));}
    @Override public void inventoryTick(ItemStack stack,Level level,Entity entity,int slot,boolean selected){super.inventoryTick(stack,level,entity,slot,selected);EquipmentToolActions.repair(stack,entity);}
    @Override public boolean hurtEnemy(ItemStack stack,LivingEntity target,LivingEntity attacker){
        if(!target.level().isClientSide && (!(target instanceof Player) || !(attacker instanceof Player) || target.getServer()!=null && target.getServer().isPvpAllowed())){
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,60));target.addEffect(new MobEffectInstance(MobEffects.HUNGER,120));
        }
        return super.hurtEnemy(stack,target,attacker);
    }
    @Override public int getWarp(ItemStack stack,Player player){return 2;}
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flags){lines.add(Component.translatable("enchantment.special.sapgreat").withStyle(ChatFormatting.GOLD));super.appendHoverText(stack,context,lines,flags);}
}
