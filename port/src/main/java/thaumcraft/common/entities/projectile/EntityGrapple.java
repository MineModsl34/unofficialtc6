package thaumcraft.common.entities.projectile;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import thaumcraft.common.items.tools.EquipmentRegistries;
import thaumcraft.common.items.tools.EquipmentRules;

/** Source hook physics, single-hook ownership and 30-tick airborne expiry.
 * The source global numeric-id table is world-scoped to avoid integrated-side/dimension collisions.
 */
public final class EntityGrapple extends ThrowableProjectile {
    private static final EntityDataAccessor<Boolean> PULLING=SynchedEntityData.defineId(EntityGrapple.class,EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> MAIN_HAND=SynchedEntityData.defineId(EntityGrapple.class,EntityDataSerializers.BOOLEAN);
    private static final Map<Level,Map<Integer,Integer>> ACTIVE=new WeakHashMap<>();
    private boolean boost,added;
    private int prevDist,count,missingOwnerTicks;
    public float ampl;
    public EntityGrapple(EntityType<? extends EntityGrapple> type,Level level){super(type,level);}
    public EntityGrapple(Level level,LivingEntity owner,InteractionHand hand){super(EquipmentRegistries.GRAPPLE,owner,level);entityData.set(MAIN_HAND,hand==InteractionHand.MAIN_HAND);}
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder){builder.define(PULLING,false);builder.define(MAIN_HAND,true);}
    public InteractionHand hand(){return entityData.get(MAIN_HAND)?InteractionHand.MAIN_HAND:InteractionHand.OFF_HAND;}
    public LivingEntity getThrower(){return getOwner() instanceof LivingEntity living?living:null;}
    public boolean getPulling(){return entityData.get(PULLING);}
    public void setPulling(){entityData.set(PULLING,true);}
    public static boolean active(Level level,LivingEntity owner){Integer id=ACTIVE.getOrDefault(level,Map.of()).get(owner.getId());return id!=null && level.getEntity(id) instanceof EntityGrapple hook && hook.isAlive();}
    @Override protected double getDefaultGravity(){return getPulling()?0:.03F;}
    @Override public boolean shouldRenderAtSqrDistance(double distance){return distance<4096;}
    @Override public Vec3 getMovementToShoot(double x,double y,double z,float velocity,float inaccuracy){
        float length=(float)Math.sqrt(x*x+y*y+z*z);
        // Source forces spread0 but still consumes its three Gaussian draws.
        return new Vec3(x/length+random.nextGaussian()*.007499999832361937*0,y/length+random.nextGaussian()*.007499999832361937*0,z/length+random.nextGaussian()*.007499999832361937*0).scale(velocity);
    }
    @Override public void tick(){
        super.tick();LivingEntity owner=getThrower();
        // Native persisted anchors must not pull a player in another level or live forever
        // after logout. A short loaded-owner grace lets both entities finish chunk loading.
        if(!level().isClientSide && owner!=null && (owner.level()!=level() || !owner.isAlive())){discard();return;}
        if(!getPulling() && isAlive() && (tickCount>30 || owner==null)){discard();return;}
        if(owner==null){if(!level().isClientSide && ++missingOwnerTicks>30)discard();return;}missingOwnerTicks=0;
        Map<Integer,Integer> active=ACTIVE.computeIfAbsent(level(),key->new HashMap<>());
        if(!level().isClientSide && isAlive() && !added){
            Integer old=active.put(owner.getId(),getId());if(old!=null && old!=getId()){Entity previous=level().getEntity(old);if(previous!=null)previous.discard();}
            added=true;
        } else if(level().isClientSide && !added){active.put(owner.getId(),getId());added=true;}
        Integer current=active.get(owner.getId());if(current!=null && current!=getId()){discard();return;}
        double distance=owner.distanceTo(this);
        if(getPulling() && isAlive()){
            if(owner.isShiftKeyDown()){discard();return;}
            if(!level().isClientSide && owner instanceof ServerPlayer player)player.connection.aboveGroundTickCount=0;
            owner.fallDistance=0;Vec3 pull=position().subtract(owner.position()).scale(1/EquipmentRules.grappleDivisor(distance));
            if(pull.length()>.25)pull=pull.normalize().scale(.25);
            owner.setDeltaMovement(owner.getDeltaMovement().add(pull.x,pull.y+.033,pull.z));
            if(!boost){owner.setDeltaMovement(owner.getDeltaMovement().add(0,.4F,0));boost=true;}
            int d=(int)(distance/2);count=d==prevDist?count+1:0;prevDist=d;
        }
        if(level().isClientSide)ampl=getPulling()?ampl*.66F:ampl+.02F;
    }
    @Override public void onHit(HitResult hit){if(!level().isClientSide && !getPulling()){setPulling();setDeltaMovement(Vec3.ZERO);setPos(hit.getLocation());level().broadcastEntityEvent(this,(byte)6);}}
    @Override public void handleEntityEvent(byte event){if(event==6){setPulling();setDeltaMovement(Vec3.ZERO);}else super.handleEntityEvent(event);}
    @Override public void remove(RemovalReason reason){
        LivingEntity owner=getThrower();if(owner!=null){Map<Integer,Integer> active=ACTIVE.get(level());if(active!=null && java.util.Objects.equals(active.get(owner.getId()),getId()))active.remove(owner.getId());}
        super.remove(reason);
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag){super.addAdditionalSaveData(tag);tag.putBoolean("Pulling",getPulling());tag.putBoolean("MainHand",hand()==InteractionHand.MAIN_HAND);tag.putBoolean("Boost",boost);}
    @Override protected void readAdditionalSaveData(CompoundTag tag){super.readAdditionalSaveData(tag);entityData.set(PULLING,tag.getBoolean("Pulling"));entityData.set(MAIN_HAND,!tag.contains("MainHand") || tag.getBoolean("MainHand"));boost=tag.getBoolean("Boost");added=false;}
}
