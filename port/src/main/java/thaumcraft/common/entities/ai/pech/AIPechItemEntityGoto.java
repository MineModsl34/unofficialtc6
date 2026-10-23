package thaumcraft.common.entities.ai.pech;
import java.util.EnumSet;
import net.minecraft.sounds.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import thaumcraft.common.entities.monster.EntityPech;
public final class AIPechItemEntityGoto extends Goal {
    public static final String TRADE_DROP="thaumcraft:PechDrop";
    private final EntityPech pech;private ItemEntity target;private int count,penalty;
    public AIPechItemEntityGoto(EntityPech pech){this.pech=pech;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
    @Override public boolean canUse(){if(--count>0)return false;target=null;double range=Double.MAX_VALUE;for(ItemEntity item:pech.level().getEntitiesOfClass(ItemEntity.class,pech.getBoundingBox().inflate(16))){double distance=pech.distanceToSqr(item);if(pech.canPickup(item.getItem())&&!item.getPersistentData().getBoolean(TRADE_DROP)&&distance<=256&&distance<range){range=distance;target=item;}}return target!=null;}
    @Override public boolean canContinueToUse(){return target!=null&&target.isAlive()&&!pech.getNavigation().isDone()&&pech.distanceToSqr(target)<256;}
    @Override public void stop(){target=null;}@Override public void start(){pech.getNavigation().moveTo(pech.getNavigation().createPath(target,0),pech.getAttributeValue(Attributes.MOVEMENT_SPEED)*1.5);count=0;}
    @Override public boolean requiresUpdateEveryTick(){return true;}
    @Override public void tick(){if(target==null)return;pech.getLookControl().setLookAt(target,30,30);if(pech.getSensing().hasLineOfSight(target)&&--count<=0){count=penalty+4+pech.getRandom().nextInt(4);pech.getNavigation().moveTo(target,pech.getAttributeValue(Attributes.MOVEMENT_SPEED)*1.5);var path=pech.getNavigation().getPath();var end=path==null?null:path.getEndNode();if(end!=null&&target.distanceToSqr(end.x,end.y,end.z)<1)penalty=0;else penalty=Math.min(600,penalty+10);}
        if(pech.distanceToSqr(target.getX(),target.getBoundingBox().minY,target.getZ())<=1.5){count=0;int original=target.getItem().getCount();ItemStack remaining=pech.pickupItem(target.getItem());if(remaining.isEmpty())target.discard();else target.setItem(remaining);if(remaining.isEmpty()||remaining.getCount()!=original)pech.level().playSound(null,target.getX(),target.getY(),target.getZ(),SoundEvents.ITEM_PICKUP,SoundSource.NEUTRAL,.2F,((pech.getRandom().nextFloat()-pech.getRandom().nextFloat())*.7F+1)*2);}}
}
