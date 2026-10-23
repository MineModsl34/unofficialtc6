package thaumcraft.common.entities.projectile;

import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import thaumcraft.common.entities.AlumentumRegistration;
import thaumcraft.common.entities.AlumentumPresentation;

/** Literal source throwable: fixed .75 launch speed and a terrain-breaking 1.1 explosion
 * without fire. Native modern collision/owner-ignore handling replaces old EntityThrowable. */
public final class EntityAlumentum extends ThrowableProjectile {
    public EntityAlumentum(EntityType<? extends EntityAlumentum> type, Level level) { super(type, level); }
    public EntityAlumentum(Level level, LivingEntity shooter) { super(AlumentumRegistration.TYPE, shooter, level); }
    public EntityAlumentum(Level level, double x, double y, double z) { super(AlumentumRegistration.TYPE, x, y, z, level); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {}
    public net.minecraft.util.RandomSource trailRandom() { return random; }
    @Override protected double getDefaultGravity() { return .03F; }
    @Override public Vec3 getMovementToShoot(double x, double y, double z, float velocity, float inaccuracy) {
        Vec3 normalized = new Vec3(x, y, z).normalize();
        double spread = .007499999832361937D * inaccuracy;
        return normalized.add(random.nextGaussian() * spread, random.nextGaussian() * spread, random.nextGaussian() * spread).scale(.75F);
    }
    @Override public void shoot(double x, double y, double z, float velocity, float inaccuracy) { super.shoot(x, y, z, .75F, inaccuracy); }
    @Override public void tick() {
        super.tick();
        if (level().isClientSide) AlumentumPresentation.trail(this);
    }
    @Override protected void onHit(HitResult hit) {
        if (!level().isClientSide) {
            // Old explosions did not identify EntityThrowable's owner as the explosive placer.
            level().explode(this, damageSources().explosion(this, null), null, getX(), getY(), getZ(), 1.1F, false, Level.ExplosionInteraction.BLOCK);
            discard();
        }
    }
}
