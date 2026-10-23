package thaumcraft.common.entities.monster;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import thaumcraft.api.ThaumcraftApiHelper;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.entities.ITaintedMob;
import thaumcraft.common.entities.WarpEntities;
import thaumcraft.common.lib.SoundsTC;
import thaumcraft.common.lib.warp.WarpTaintPresentationHooks;

/** Real TC slime: goo feeding, size-one projectiles, size-N => N single children, and flux-crystal loot. */
public final class EntityThaumicSlime extends Slime implements ITaintedMob {
    private int launched=10, spitCounter=100;
    public EntityThaumicSlime(EntityType<? extends Slime> type, Level world) { super(type,world); setSize(1 << (1+random.nextInt(3)),true); }
    @Override public void setSize(int size, boolean resetHealth) { super.setSize(size,resetHealth); xpReward=size+2; }
    public void launchAt(LivingEntity shooter, LivingEntity target) {
        setSize(1,true); double y=(shooter.getBoundingBox().minY+shooter.getBoundingBox().maxY)/2;
        double x=target.getX()-shooter.getX(), dy=target.getBoundingBox().minY+target.getBbHeight()/3F-y, z=target.getZ()-shooter.getZ(), horizontal=(float)Math.sqrt(x*x+z*z);
        if(horizontal>=1E-7) { moveTo(shooter.getX()+x/horizontal,y,shooter.getZ()+z/horizontal,(float)(Math.atan2(z,x)*180/Math.PI)-90,(float)(-Math.atan2(dy,horizontal)*180/Math.PI)); shoot(x,dy+(float)horizontal*.2F,z,1.5F,1); }
    }
    public void shoot(double x,double y,double z,float velocity,float inaccuracy) {
        float length=(float)Math.sqrt(x*x+y*y+z*z); x=(x/length+random.nextGaussian()*.0075F*inaccuracy)*velocity;
        y=(y/length+random.nextGaussian()*.0075F*inaccuracy)*velocity; z=(z/length+random.nextGaussian()*.0075F*inaccuracy)*velocity;
        setDeltaMovement(x,y,z); yRotO=(float)(Math.atan2(x,z)*180/Math.PI); xRotO=(float)(Math.atan2(y,(float)Math.sqrt(x*x+z*z))*180/Math.PI); setYRot(yRotO); setXRot(xRotO);
    }
    @Override public SpawnGroupData finalizeSpawn(ServerLevelAccessor world, DifficultyInstance difficulty, MobSpawnType reason, SpawnGroupData data) {
        int i=random.nextInt(3); if(i<2&&random.nextFloat()<.5F*difficulty.getSpecialMultiplier()) i++;
        setSize(1<<i,true); return super.finalizeSpawn(world,difficulty,reason,data); // Source intentionally calls vanilla's second size roll.
    }
    @Override public void tick() {
        int size=getSize();
        if(onGround()&&!wasOnGround) {
            wasOnGround=true; float before=targetSquish; super.tick(); targetSquish=before;
            if(level().isClientSide) WarpTaintPresentationHooks.slimeJump(this,size,size*2);
            if(doPlayJumpSound()) playSound(getJumpSound(),getSoundVolume(),((random.nextFloat()-random.nextFloat())*.2F+1)*.8F);
            targetSquish=-.5F; wasOnGround=onGround(); decreaseSquish();
        } else super.tick();
        if(level().isClientSide) {
            if(launched>0) { launched--; WarpTaintPresentationHooks.slimeJump(this,size,size*(launched+1)); }
        } else if(!isRemoved()) {
            Player player=level().getNearestPlayer(this,16);
            if(player!=null) { if(spitCounter>0) spitCounter--; lookAt(player,10,20);
                if(distanceTo(player)>4 && spitCounter<=0 && getSize()>2) {
                    spitCounter=101; EntityThaumicSlime shot=WarpEntities.THAUMIC_SLIME.create(level()); shot.launchAt(this,player); level().addFreshEntity(shot);
                    playSound(SoundsTC.gore,1,((random.nextFloat()-random.nextFloat())*.2F+1)*.8F); setSize(getSize()-1,true);
                }
            }
        }
    }
    @Override protected boolean spawnCustomParticles() { return true; }
    @Override public boolean checkSpawnRules(LevelAccessor world,MobSpawnType reason) { return false; }
    @Override protected boolean isDealsDamage() { return true; }
    @Override protected float getAttackDamage() { return getSize()+1; }
    @Override public void dealDamage(LivingEntity target) {
        int radiusSize=getSize()+(launched>0?2:0); // Source launched counter decreases ONLY clientside; keep the server's enlarged hit radius.
        DamageSource source=damageSources().mobAttack(this);
        if(isAlive()&&hasLineOfSight(target)&&distanceToSqr(target)<.6*radiusSize*.6*radiusSize&&target.hurt(source,getAttackDamage())) {
            playSound(SoundEvents.SLIME_ATTACK,1,(random.nextFloat()-random.nextFloat())*.2F+1);
            if(level() instanceof ServerLevel server) EnchantmentHelper.doPostAttackEffects(server,target,source);
        }
    }
    @Override public void remove(Entity.RemovalReason reason) {
        if(isRemoved()) return;
        int size=getSize();
        if(!level().isClientSide&&size>1&&getHealth()<=0) for(int k=0;k<size;k++) {
            EntityThaumicSlime child=WarpEntities.THAUMIC_SLIME.create(level()); child.setSize(1,true);
            child.moveTo(getX()+(k%2-.5F)*size/4F,getY()+.5,getZ()+(k/2-.5F)*size/4F,random.nextFloat()*360,0); level().addFreshEntity(child);
        }
        // Skip Slime.remove (it adds vanilla half-size children), retain LivingEntity's cleanup.
        if(reason==RemovalReason.KILLED||reason==RemovalReason.DISCARDED) triggerOnDeathMobEffects(reason);
        setRemoved(reason); getBrain().clearMemories();
    }
    @Override protected void dropCustomDeathLoot(ServerLevel world, DamageSource source, boolean recentlyHit) { if(getSize()>1) spawnAtLocation(ThaumcraftApiHelper.makeCrystal(Aspect.FLUX),getBbHeight()/2); }
    public int sourceLaunchedTicks() { return launched; }
    public int sourceSpitCounter() { return spitCounter; }
}
