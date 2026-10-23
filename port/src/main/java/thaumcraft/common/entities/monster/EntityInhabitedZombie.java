package thaumcraft.common.entities.monster;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.*;
import net.minecraft.world.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.goal.target.*;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import thaumcraft.api.entities.IEldritchMob;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.entities.Mob8NaturalRegistries;
import thaumcraft.common.entities.monster.cult.EntityCultist;
import thaumcraft.common.lib.SoundsTC;

public final class EntityInhabitedZombie extends Zombie implements IEldritchMob {
    public EntityInhabitedZombie(EntityType<? extends EntityInhabitedZombie> type,Level level){super(type,level);}
    public static AttributeSupplier.Builder attributes(){return Zombie.createAttributes().add(Attributes.MAX_HEALTH,30).add(Attributes.ATTACK_DAMAGE,5).add(Attributes.SPAWN_REINFORCEMENTS_CHANCE,0);}
    @Override protected void registerGoals(){thaumcraft.common.entities.SourceZombieGoals.install(this,goalSelector,targetSelector);targetSelector.addGoal(1,new HurtByTargetGoal(this).setAlertOthers());targetSelector.addGoal(3,new NearestAttackableTargetGoal<>(this,EntityCultist.class,true));}
    @Override protected boolean convertsInWater(){return false;}
    @Override public boolean killedEntity(ServerLevel level,LivingEntity victim){return true;}
    @Override public SpawnGroupData finalizeSpawn(ServerLevelAccessor world,DifficultyInstance difficulty,MobSpawnType reason,SpawnGroupData group){float chance=world.getDifficulty()==Difficulty.HARD?.9F:.6F;setItemSlot(EquipmentSlot.HEAD,new ItemStack(ItemsTC.crimsonPlateHelm));if(random.nextFloat()<=chance)setItemSlot(EquipmentSlot.CHEST,new ItemStack(ItemsTC.crimsonPlateChest));if(random.nextFloat()<=chance)setItemSlot(EquipmentSlot.LEGS,new ItemStack(ItemsTC.crimsonPlateLegs));return super.finalizeSpawn(world,difficulty,reason,group);}
    // Original deliberately skips the ordinary zombie death/loot path entirely.
    @Override public void die(DamageSource source){}
    @Override protected void tickDeath(){if(level() instanceof ServerLevel server){var crab=Mob8NaturalRegistries.ELDRITCH_CRAB.create(server);if(crab!=null){crab.moveTo(getX(),getY()+getEyeHeight(),getZ(),getYRot(),getXRot());crab.setHelm(true);server.addFreshEntity(crab);}dropExperience(getLastDamageSource()==null?null:getLastDamageSource().getEntity());server.sendParticles(ParticleTypes.POOF,getX(),getY()+getBbHeight()/2,getZ(),20,getBbWidth(),getBbHeight()/2,getBbWidth(),.02);}discard();}
    @Override protected SoundEvent getAmbientSound(){return SoundsTC.crabtalk;}@Override protected SoundEvent getHurtSound(DamageSource source){return SoundEvents.GENERIC_HURT;}
    @Override public boolean checkSpawnObstruction(LevelReader world){return level().getEntitiesOfClass(EntityInhabitedZombie.class,getBoundingBox().inflate(32,16,32)).stream().noneMatch(e->e!=this)&&super.checkSpawnObstruction(world);}
}
