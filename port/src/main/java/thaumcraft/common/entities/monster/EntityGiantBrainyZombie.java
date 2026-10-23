package thaumcraft.common.entities.monster;
import net.minecraft.network.syncher.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.level.Level;
import thaumcraft.common.entities.Mob8NaturalRules;

/** Source anger starts at zero, is not saved, and only resizes while greater than one. */
public final class EntityGiantBrainyZombie extends EntityBrainyZombie {
    private static final EntityDataAccessor<Float> ANGER=SynchedEntityData.defineId(EntityGiantBrainyZombie.class,EntityDataSerializers.FLOAT);
    private float sourceSize=1;
    private boolean sourceSizeChanged;
    public EntityGiantBrainyZombie(EntityType<? extends EntityGiantBrainyZombie> type,Level level){super(type,level);xpReward=15;}
    public static AttributeSupplier.Builder attributes(){return EntityBrainyZombie.attributes().add(Attributes.MAX_HEALTH,60).add(Attributes.ATTACK_DAMAGE,7);}
    @Override protected void registerGoals(){super.registerGoals();goalSelector.addGoal(2,new LeapAtTargetGoal(this,.4F));}
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder){super.defineSynchedData(builder);builder.define(ANGER,0F);}
    public float getAnger(){return entityData.get(ANGER);}public void setAnger(float value){entityData.set(ANGER,Float.isFinite(value)?Math.max(0,Math.min(2,value)):0);refreshDimensions();}
    @Override public boolean hurt(DamageSource source,float amount){setAnger(Mob8NaturalRules.giantAnger(getAnger()));return super.hurt(source,amount);}
    @Override public void aiStep(){super.aiStep();if(getAnger()>1){setAnger(getAnger()-.002F);sourceSize=1+getAnger();sourceSizeChanged=true;refreshDimensions();}getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(Mob8NaturalRules.giantAttack(getAnger()));}
    @Override public EntityDimensions getDefaultDimensions(Pose pose){return (sourceSizeChanged?getType().getDimensions().scale(sourceSize):super.getDefaultDimensions(pose)).withEyeHeight((1.74F+getAnger()*1.74F)-(isBaby()?.81F:0));}
}
