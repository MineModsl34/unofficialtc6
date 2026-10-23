package thaumcraft.common.entities.monster.tainted;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.level.Level;
import thaumcraft.api.ThaumcraftApiHelper;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.common.entities.Mob8BossRegistries;
public final class EntityTaintSeedPrime extends EntityTaintSeed {
    public EntityTaintSeedPrime(EntityType<? extends EntityTaintSeedPrime> type,Level level){super(type,level);xpReward=12;}
    public EntityTaintSeedPrime(Level level){this(Mob8BossRegistries.TAINT_SEED_PRIME,level);}
    @Override protected int getArea(){return 2;}
    public static AttributeSupplier.Builder createAttributes(){return EntityTaintSeed.createAttributes().add(Attributes.MAX_HEALTH,150).add(Attributes.ATTACK_DAMAGE,7);}
    @Override protected void dropCustomDeathLoot(ServerLevel level,DamageSource source,boolean recentlyHit){super.dropCustomDeathLoot(level,source,recentlyHit);if(random.nextBoolean())spawnAtLocation(ThaumcraftApiHelper.makeCrystal(Aspect.FLUX),getBbHeight()/2);if(random.nextBoolean())spawnAtLocation(ThaumcraftApiHelper.makeCrystal(Aspect.FLUX),getBbHeight()/2);}
}
