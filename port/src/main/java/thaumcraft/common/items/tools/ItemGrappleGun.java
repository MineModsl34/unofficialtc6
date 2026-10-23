package thaumcraft.common.items.tools;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import thaumcraft.api.items.IRechargable;
import thaumcraft.api.items.RechargeHelper;
import thaumcraft.common.entities.projectile.EntityGrapple;
import thaumcraft.common.lib.SoundsTC;

public final class ItemGrappleGun extends Item implements IRechargable {
    public ItemGrappleGun(){super(new Properties().stacksTo(1).rarity(Rarity.UNCOMMON));}
    @Override public int getMaxCharge(ItemStack stack,LivingEntity wearer){return 100;}
    @Override public EnumChargeDisplay showInHud(ItemStack stack,LivingEntity wearer){return EnumChargeDisplay.NORMAL;}
    public static boolean loaded(ItemStack stack){return stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getByte("loaded")==1;}
    public static void loaded(ItemStack stack,boolean loaded){CustomData.update(DataComponents.CUSTOM_DATA,stack,tag->tag.putByte("loaded",(byte)(loaded?1:0)));}
    @Override public void inventoryTick(ItemStack stack,Level level,Entity entity,int slot,boolean selected){if(entity instanceof LivingEntity owner && !EntityGrapple.active(level,owner) && loaded(stack))loaded(stack,false);}
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        player.playSound(SoundsTC.ice,3,.8F+level.random.nextFloat()*.1F);ItemStack stack=player.getItemInHand(hand);
        if(!level.isClientSide && RechargeHelper.getCharge(stack)>0){
            var hook=new EntityGrapple(level,player,hand);hook.shootFromRotation(player,player.getXRot(),player.getYRot(),-5,1.5F,0);
            double angle=(player.getYRot()-.5F)/180F*3.141593F,sign=hand==InteractionHand.MAIN_HAND?1:-1;var look=player.getLookAngle();
            hook.setPos(hook.getX()-Math.cos(angle)*.2F*sign+look.x,hook.getY(),hook.getZ()-Math.sin(angle)*.3F*sign+look.z);
            if(level.addFreshEntity(hook)){RechargeHelper.consumeCharge(stack,player,1);loaded(stack,true);}
        }
        return InteractionResultHolder.success(stack);
    }
    @Override public boolean shouldCauseReequipAnimation(ItemStack oldStack,ItemStack newStack,boolean slotChanged){return oldStack.is(this) && newStack.is(this)?loaded(oldStack)!=loaded(newStack):oldStack.getItem()!=newStack.getItem();}
}
