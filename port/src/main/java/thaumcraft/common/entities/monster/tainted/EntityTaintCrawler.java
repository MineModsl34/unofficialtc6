package thaumcraft.common.entities.monster.tainted;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.*;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.api.ThaumcraftApiHelper;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.entities.ITaintedMob;
import thaumcraft.common.blocks.world.taint.*;
import thaumcraft.common.entities.Mob8BossRegistries;
import thaumcraft.common.entities.ai.combat.SourceMeleeAttackGoal;
import thaumcraft.common.lib.potions.WarpEffects;
public final class EntityTaintCrawler extends Monster implements ITaintedMob {
    @Override protected net.minecraft.resources.ResourceKey<net.minecraft.world.level.storage.loot.LootTable> getDefaultLootTable(){return net.minecraft.world.level.storage.loot.BuiltInLootTables.EMPTY;}
    public EntityTaintCrawler(EntityType<? extends EntityTaintCrawler> type,Level level){super(type,level);xpReward=3;}
    public EntityTaintCrawler(Level level){this(Mob8BossRegistries.TAINT_CRAWLER,level);}
    public static AttributeSupplier.Builder createAttributes(){return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH,8).add(Attributes.MOVEMENT_SPEED,.275).add(Attributes.ATTACK_DAMAGE,2);}
    @Override protected void registerGoals(){goalSelector.addGoal(1,new FloatGoal(this));goalSelector.addGoal(2,new SourceMeleeAttackGoal(this,1,false));goalSelector.addGoal(3,new RandomStrollGoal(this,1));goalSelector.addGoal(7,new LookAtPlayerGoal(this,Player.class,8));goalSelector.addGoal(8,new RandomLookAroundGoal(this));targetSelector.addGoal(1,new HurtByTargetGoal(this).setAlertOthers());targetSelector.addGoal(2,new NearestAttackableTargetGoal<>(this,Player.class,true));}
    @Override public boolean canAttackType(EntityType<?> type){return !Mob8BossRegistries.isTainted(type)&&super.canAttackType(type);}@Override public boolean isAlliedTo(Entity entity){return entity instanceof ITaintedMob||super.isAlliedTo(entity);}
    @Override public void tick(){if(!level().isClientSide&&isAlive()&&tickCount%40==0){BlockPos pos=blockPosition();BlockState state=level().getBlockState(pos);if(!state.is(BlockTags.LEAVES)&&state.getFluidState().isEmpty()&&!TaintHelper.isTaint(state)&&TaintHelper.replaceablePlant(state)&&TaintHelper.adjacentSolid(level(),pos)&&!BlockTaintFibre.isOnlyAdjacentToTaint(level(),pos))level().setBlock(pos,BlocksTC.taintFibre.defaultBlockState(),3);}super.tick();}
    @Override public boolean doHurtTarget(Entity target){if(!super.doHurtTarget(target))return false;int severity=level().getDifficulty()==Difficulty.NORMAL?3:level().getDifficulty()==Difficulty.HARD?6:0;if(severity>0&&random.nextInt(severity+1)>2&&target instanceof LivingEntity living)living.addEffect(new MobEffectInstance(WarpEffects.FLUX_TAINT,severity*20));return true;}
    @Override protected void dropCustomDeathLoot(ServerLevel level,DamageSource source,boolean recentlyHit){if(random.nextInt(8)==0)spawnAtLocation(ThaumcraftApiHelper.makeCrystal(Aspect.FLUX),getBbHeight()/2);}
    @Override protected SoundEvent getAmbientSound(){return SoundEvents.SILVERFISH_AMBIENT;}@Override protected SoundEvent getHurtSound(DamageSource source){return SoundEvents.SILVERFISH_HURT;}@Override protected SoundEvent getDeathSound(){return SoundEvents.SILVERFISH_DEATH;}@Override public float getVoicePitch(){return .7F;}@Override protected void playStepSound(BlockPos pos,BlockState state){playSound(SoundEvents.SILVERFISH_STEP,.15F,1);}
}
