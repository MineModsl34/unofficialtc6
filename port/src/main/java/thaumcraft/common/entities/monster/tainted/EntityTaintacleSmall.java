package thaumcraft.common.entities.monster.tainted;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.level.*;
import thaumcraft.common.entities.Mob8BossRegistries;
public final class EntityTaintacleSmall extends EntityTaintacle {
    private int lifetime=200;
    public EntityTaintacleSmall(EntityType<? extends EntityTaintacleSmall> type,Level level){super(type,level);xpReward=0;}
    public EntityTaintacleSmall(Level level){this(Mob8BossRegistries.TAINTACLE_SMALL,level);}
    public static AttributeSupplier.Builder createAttributes(){return EntityTaintacle.createAttributes().add(Attributes.MAX_HEALTH,5).add(Attributes.ATTACK_DAMAGE,2);}
    @Override public void tick(){super.tick();if(!level().isClientSide&&lifetime--<=0)actuallyHurt(damageSources().magic(),10);}
    @Override public boolean checkSpawnRules(LevelAccessor level,MobSpawnType reason){return false;}
    @Override protected void dropCustomDeathLoot(ServerLevel level,DamageSource source,boolean recentlyHit){}
}
