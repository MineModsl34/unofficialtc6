package thaumcraft.common.items.tools;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import thaumcraft.common.lib.SoundsTC;

public final class ItemElementalSword extends SwordItem {
    public ItemElementalSword(){super(EquipmentMaterials.ELEMENTAL,new Properties().rarity(Rarity.RARE).attributes(SwordItem.createAttributes(EquipmentMaterials.ELEMENTAL,3,-2.4F)));}
    @Override public UseAnim getUseAnimation(ItemStack stack){return UseAnim.NONE;}
    @Override public int getUseDuration(ItemStack stack,LivingEntity entity){return 72000;}
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){player.startUsingItem(hand);return InteractionResultHolder.success(player.getItemInHand(hand));}
    @Override public void onUseTick(Level level,LivingEntity owner,ItemStack stack,int remaining){
        int ticks=72000-remaining;Vec3 motion=owner.getDeltaMovement();
        if(motion.y<0)owner.fallDistance/=1.2F;
        owner.setDeltaMovement(motion.x,EquipmentRules.swordLift(motion.y),motion.z);
        if(owner instanceof ServerPlayer player)player.connection.aboveGroundTickCount=0;
        for(var entity:level.getEntities(owner,owner.getBoundingBox().inflate(2.5)))if(entity instanceof LivingEntity && !(entity instanceof Player) && entity.isAlive() && owner.getVehicle()!=entity){
            Vec3 delta=entity.position().subtract(owner.position());double distance=delta.length()+.1;
            entity.setDeltaMovement(entity.getDeltaMovement().add(delta.scale(1/2.5/distance)));
        }
        if(level.isClientSide)thaumcraft.common.lib.fx.SourceFX.local(level,"elemental_sword",owner.position(),net.minecraft.world.phys.Vec3.ZERO,owner.getId(),0);
        else if(ticks==0 || ticks%20==0)owner.playSound(SoundsTC.wind,.5F,.9F+level.random.nextFloat()*.2F);
        if(ticks%20==0)stack.hurtAndBreak(1,owner,owner.getUsedItemHand()==InteractionHand.MAIN_HAND?EquipmentSlot.MAINHAND:EquipmentSlot.OFFHAND);
    }
}
