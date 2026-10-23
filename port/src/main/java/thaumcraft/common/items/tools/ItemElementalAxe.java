package thaumcraft.common.items.tools;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class ItemElementalAxe extends AxeItem {
    public ItemElementalAxe(){super(EquipmentMaterials.ELEMENTAL,new Properties().rarity(Rarity.RARE).attributes(DiggerItem.createAttributes(EquipmentMaterials.ELEMENTAL,5,-3)));}
    @Override public UseAnim getUseAnimation(ItemStack stack){return UseAnim.BOW;}
    @Override public int getUseDuration(ItemStack stack,LivingEntity entity){return 72000;}
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){player.startUsingItem(hand);return InteractionResultHolder.success(player.getItemInHand(hand));}
    @Override public void onUseTick(Level level,LivingEntity owner,ItemStack stack,int remaining){
        var position=owner.position();
        for(ItemEntity item:level.getEntitiesOfClass(ItemEntity.class,new net.minecraft.world.phys.AABB(position,position).inflate(10),ItemEntity::isAlive)){
            Vec3 delta=new Vec3(item.getX()-owner.getX(),item.getY()-owner.getY()+owner.getBbHeight()/2F,item.getZ()-owner.getZ());
            double length=(float)Math.sqrt(delta.lengthSqr());if(length<=1E-9)continue;delta=delta.scale(1/length);Vec3 motion=item.getDeltaMovement();
            item.setDeltaMovement(EquipmentRules.vacuumComponent(motion.x,delta.x,false),EquipmentRules.vacuumComponent(motion.y,delta.y,true),EquipmentRules.vacuumComponent(motion.z,delta.z,false));
            if(level.isClientSide)thaumcraft.common.lib.fx.SourceFX.local(level,"elemental_axe",item.position(),net.minecraft.world.phys.Vec3.ZERO,item.getId(),0);
        }
    }
}
