package thaumcraft.common.entities;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Source collector homing, twenty-step acceleration, half-block release and type10
 * trail. Native entity metadata replaces unframed optional additional spawn bytes. As
 * in source saves, reload retains type but does not resurrect a transient target. */
public final class EntityFollowingItem extends EntitySpecialItem {
    private static final EntityDataAccessor<CompoundTag> TARGET = SynchedEntityData.defineId(EntityFollowingItem.class, EntityDataSerializers.COMPOUND_TAG);
    private Entity target;
    private double targetX, targetY, targetZ;
    private int mode = 3, followAge = 20;
    public net.minecraft.util.RandomSource trailRandom() { return random; }
    public EntityFollowingItem(EntityType<? extends EntityFollowingItem> type, Level level) { super(type, level); }
    public EntityFollowingItem(Level level, double x, double y, double z, ItemStack stack, Entity target, int mode) {
        this(ToolRegistration.FOLLOWING_ITEM, level); setPos(x, y, z); setItem(stack.copy());
        this.target = target; this.mode = mode; noPhysics = true; updateTarget();
        CompoundTag data = new CompoundTag(); data.putInt("target", target == null ? -1 : target.getId());
        data.putDouble("x", targetX); data.putDouble("y", targetY); data.putDouble("z", targetZ); data.putInt("type", mode);
        entityData.set(TARGET, data);
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { super.defineSynchedData(builder); builder.define(TARGET, new CompoundTag()); }
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (accessor == TARGET && level().isClientSide) {
            CompoundTag data = entityData.get(TARGET); target = level().getEntity(data.getInt("target"));
            targetX = data.getDouble("x"); targetY = data.getDouble("y"); targetZ = data.getDouble("z"); mode = data.getInt("type");
        }
    }
    private void updateTarget() {
        if (target != null) { targetX = target.getX(); targetY = target.getBoundingBox().minY + target.getBbHeight() / 2F; targetZ = target.getZ(); }
    }
    @Override public void tick() {
        updateTarget();
        if (targetX == 0 && targetY == 0 && targetZ == 0) setDeltaMovement(getDeltaMovement().add(0, -.04F, 0));
        else {
            float dx = (float)(targetX - getX()), dy = (float)(targetY - getY()), dz = (float)(targetZ - getZ());
            if (followAge > 1) followAge--;
            double distance = (float)Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (distance > .5) { distance *= followAge; setDeltaMovement(dx / distance, dy / distance, dz / distance); }
            else {
                setDeltaMovement(getDeltaMovement().scale(.1F)); target = null; targetX = targetY = targetZ = 0; noPhysics = false;
                if (!level().isClientSide) { CompoundTag data = new CompoundTag(); data.putInt("target", -1); data.putInt("type", mode); entityData.set(TARGET, data); }
            }
            if (level().isClientSide) ToolPresentation.followingTrail(this, mode);
        }
        super.tick();
    }
    @Override public void addAdditionalSaveData(CompoundTag data) { super.addAdditionalSaveData(data); data.putShort("type", (short)mode); }
    @Override public void readAdditionalSaveData(CompoundTag data) { super.readAdditionalSaveData(data); mode = data.getShort("type"); }
}
