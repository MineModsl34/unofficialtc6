package thaumcraft.common.entities.projectile;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import thaumcraft.api.casters.*;
import thaumcraft.common.items.casters.*;
import thaumcraft.common.lib.fx.SourceFX;
public class EntityFocusCloud extends Entity {
    private static final EntityDataAccessor<Float> RADIUS=SynchedEntityData.defineId(EntityFocusCloud.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<CompoundTag> PACK=SynchedEntityData.defineId(EntityFocusCloud.class,EntityDataSerializers.COMPOUND_TAG);
    private static final Map<Long,Long> cooldownMap=new HashMap<>();private FocusPackage focusPackage;private int duration;private LivingEntity owner;private UUID ownerUniqueId;private FocusEffect[] effects;
    public EntityFocusCloud(EntityType<? extends EntityFocusCloud> type,Level level){super(type,level);}
    public EntityFocusCloud(FocusPackage pack,Trajectory t,float radius,int duration){this(CasterRegistries.CLOUD,pack.world);focusPackage=pack;setPos(t.source);setOwner(pack.getCaster());setRadius(radius);setDuration(duration);entityData.set(PACK,pack.serialize());}
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder){builder.define(RADIUS,.5F);builder.define(PACK,new CompoundTag());}
    public int getDuration(){return duration;}public void setDuration(int value){duration=value;}public float getRadius(){return entityData.get(RADIUS);}public void setRadius(float value){entityData.set(RADIUS,value);refreshDimensions();}
    public void setOwner(LivingEntity owner){this.owner=owner;ownerUniqueId=owner==null?null:owner.getUUID();}public LivingEntity getOwner(){if(owner==null&&ownerUniqueId!=null&&level() instanceof net.minecraft.server.level.ServerLevel server&&server.getEntity(ownerUniqueId) instanceof LivingEntity living)owner=living;return owner;}
    public FocusPackage getFocusPackage(){if(focusPackage==null){focusPackage=new FocusPackage();focusPackage.deserialize(entityData.get(PACK));focusPackage.world=level();}return focusPackage;}
    @Override public EntityDimensions getDimensions(Pose pose){return EntityDimensions.scalable(getRadius()*2,.5F);}
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key){super.onSyncedDataUpdated(key);if(RADIUS.equals(key))refreshDimensions();}
    @Override public void tick(){super.tick();float radius=getRadius();if(!level().isClientSide&&(tickCount>duration*20||getOwner()==null)){discard();return;}if(level().isClientSide){renderSourceCloud(radius);return;}if(tickCount%5!=0)return;long now=System.currentTimeMillis();var trajectories=new ArrayList<Trajectory>();var targets=new ArrayList<HitResult>();for(var entity:level().getEntities(this,new AABB(position(),position()).inflate(radius))){if(!entity.isAlive())continue;if(entity instanceof EntityFocusCloud)entity.move(MoverType.SELF,entity.position().subtract(position()).scale(1/50.0));if(entity instanceof LivingEntity&&(!cooldownMap.containsKey(entity.getId())||cooldownMap.getOrDefault((long)entity.getId(),0L)<=now)){cooldownMap.put((long)entity.getId(),now+2000);var center=entity.position().add(0,entity.getBbHeight()/2,0);targets.add(new EntityHitResult(entity,center));trajectories.add(new Trajectory(position(),center.subtract(position())));}}for(int i=0;i<radius;i++){var direction=new Vec3(random.nextGaussian(),random.nextGaussian(),random.nextGaussian()).normalize();var hit=FocusTargeting.block(level(),this,position(),position().add(direction.scale(radius)));if(hit instanceof BlockHitResult block){long key=block.getBlockPos().asLong();if(cooldownMap.getOrDefault(key,0L)>now)continue;cooldownMap.put(key,now+2000);targets.add(block);trajectories.add(new Trajectory(position(),direction));}}if(!targets.isEmpty()){var owner=getOwner();FocusBlockActions.schedule(level(),()->FocusEngine.runFocusPackage(getFocusPackage().copy(owner),trajectories.toArray(Trajectory[]::new),targets.toArray(HitResult[]::new)),0);}}
    private void renderSourceCloud(float radius){if(effects==null)effects=getFocusPackage().getFocusEffects();if(effects.length==0)return;var worldRandom=level().random;
        for(int i=0;i<radius;i++){FocusEffect effect=effects[random.nextInt(effects.length)];
            SourceFX.local(level(),"focus_cloud_base",new Vec3(getX()+worldRandom.nextGaussian()*radius/2.0*.85,getY()+worldRandom.nextGaussian()*radius/2.0*.85,getZ()+worldRandom.nextGaussian()*radius/2.0*.85),new Vec3(worldRandom.nextGaussian()*.01,worldRandom.nextGaussian()*.01,worldRandom.nextGaussian()*.01),FocusEngine.getElementColor(effect.getKey()),0);
            effect.renderParticleFX(level(),getX()+worldRandom.nextGaussian()*radius/2.0,getY()+worldRandom.nextGaussian()*radius/2.0,getZ()+worldRandom.nextGaussian()*radius/2.0,worldRandom.nextGaussian()*.01F,worldRandom.nextGaussian()*.01F,worldRandom.nextGaussian()*.01F);
        }
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag){tag.putInt("Age",tickCount);tag.putInt("Duration",duration);tag.putFloat("Radius",getRadius());if(ownerUniqueId!=null)tag.putUUID("OwnerUUID",ownerUniqueId);tag.put("pack",getFocusPackage().serialize());}
    @Override protected void readAdditionalSaveData(CompoundTag tag){tickCount=tag.getInt("Age");duration=tag.getInt("Duration");setRadius(tag.getFloat("Radius"));ownerUniqueId=tag.hasUUID("OwnerUUID")?tag.getUUID("OwnerUUID"):null;focusPackage=new FocusPackage();focusPackage.deserialize(tag.getCompound("pack"));focusPackage.world=level();entityData.set(PACK,focusPackage.serialize());}
}
