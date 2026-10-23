package thaumcraft.common.items.casters;
import java.util.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
/** Source EntityUtils friendliness, cone, LOS and inflated ray target contracts. */
public final class FocusTargeting {
    private FocusTargeting(){}
    public static boolean friendly(Entity source,Entity target){if(source==null||target==null)return false;if(source==target||source.isPassengerOfSameVehicle(target)||source.isAlliedTo(target))return true;if(target instanceof OwnableEntity pet&&source.getUUID().equals(pet.getOwnerUUID()))return true;return target instanceof Player&&target.level().getServer()!=null&&!target.level().getServer().isPvpAllowed();}
    public static HitResult block(Level level,Entity caster,Vec3 from,Vec3 to){var hit=level.clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,caster));return hit.getType()==HitResult.Type.MISS?null:hit;}
    public static boolean visible(Entity from,Entity to){return block(from.level(),from,from.position().add(0,from.getBbHeight()/2,0),to.position().add(0,to.getBbHeight()/2,0))==null;}
    public static boolean cone(Entity source,Entity target,float aperture,float range){Vec3 diff=target.position().add(0,target.getBbHeight()/2,0).subtract(source.position().add(0,source.getEyeHeight(),0));Vec3 axis=source.getLookAngle().scale(range);double cosine=diff.dot(axis)/diff.length()/axis.length();return cosine>Math.cos(aperture/2)&&diff.dot(axis)/axis.length()<axis.length();}
    public static EntityHitResult pointed(Level level,Entity ignored,Vec3 start,Vec3 direction,double minimum,double range,float padding){Vec3 end=start.add(direction.scale(range));AABB bounds=(ignored==null?new AABB(start,start).inflate(.5):ignored.getBoundingBox()).expandTowards(direction.scale(range)).inflate(padding);double nearest=0;EntityHitResult result=null;for(var entity:level.getEntities(ignored,bounds)){if(!entity.isPickable()||start.distanceTo(entity.position())<minimum||block(level,ignored,start,entity.position().add(0,entity.getEyeHeight(),0))!=null)continue;var box=entity.getBoundingBox().inflate(Math.max(.8,entity.getPickRadius()));var point=box.clip(start,end);if(box.contains(start)){result=new EntityHitResult(entity,point.orElse(start));nearest=0;}else if(point.isPresent()){double distance=start.distanceTo(point.get());if(distance<nearest||nearest==0){result=new EntityHitResult(entity,point.get());nearest=distance;}}}return result;}
    public static List<LivingEntity> nearby(Level level,Entity excluded,double radius){var entities=level.getEntitiesOfClass(LivingEntity.class,new AABB(excluded.position(),excluded.position()).inflate(radius),e->e!=excluded&&e.isAlive());entities.sort(Comparator.comparingDouble(excluded::distanceToSqr));return entities;}
}
