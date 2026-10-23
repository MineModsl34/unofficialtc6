package thaumcraft.common.golems.ai;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.PathType;
import thaumcraft.common.entities.construct.EntityOwnedConstruct;

public class AIFollowOwner extends Goal {
    private final EntityOwnedConstruct pet;
    private LivingEntity owner;
    private final double followSpeed;
    private final PathNavigation navigation;
    private int timer;
    private final float minDistance, maxDistance;
    private float oldWaterCost;
    public AIFollowOwner(EntityOwnedConstruct pet, double speed, float minDistance, float maxDistance) {
        this.pet = pet;
        followSpeed = speed;
        navigation = pet.getNavigation();
        this.minDistance = minDistance;
        this.maxDistance = maxDistance;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        if (!(navigation instanceof GroundPathNavigation) && !(navigation instanceof PathNavigateGolemAir))
            throw new IllegalArgumentException("Unsupported mob type for FollowOwnerGoal");
    }
    @Override public boolean requiresUpdateEveryTick() { return true; }
    @Override public boolean canUse() {
        LivingEntity found = pet.getOwnerEntity();
        if (found == null || pet.distanceToSqr(found) < minDistance * minDistance) return false;
        owner = found;
        return true;
    }
    @Override public boolean canContinueToUse() { return owner != null && !navigation.isDone() && pet.distanceToSqr(owner) > maxDistance * maxDistance; }
    @Override public void start() { timer = 0; oldWaterCost = pet.getPathfindingMalus(PathType.WATER); pet.setPathfindingMalus(PathType.WATER, 0); }
    @Override public void stop() { owner = null; navigation.stop(); pet.setPathfindingMalus(PathType.WATER, oldWaterCost); }
    @Override public void tick() {
        if (owner == null) return;
        pet.getLookControl().setLookAt(owner, 10, pet.getMaxHeadXRot());
        if (--timer > 0) return;
        timer = 10;
        if (navigation.moveTo(owner, followSpeed) || pet.isLeashed() || pet.distanceToSqr(owner) < 144) return;
        int x = owner.getBlockX() - 2, z = owner.getBlockZ() - 2, y = Mth.floor(owner.getBoundingBox().minY);
        for (int xx = 0; xx <= 4; xx++) for (int zz = 0; zz <= 4; zz++) {
            if (!(xx < 1 || zz < 1 || xx > 3 || zz > 3)) continue;
            BlockPos pos = new BlockPos(x + xx, y, z + zz);
            if (!pet.level().hasChunkAt(pos) || !pet.level().getBlockState(pos.below()).isCollisionShapeFullBlock(pet.level(), pos.below())
                    || pet.level().getBlockState(pos).isCollisionShapeFullBlock(pet.level(), pos)
                    || pet.level().getBlockState(pos.above()).isCollisionShapeFullBlock(pet.level(), pos.above())) continue;
            pet.moveTo(x + xx + 0.5, y, z + zz + 0.5, pet.getYRot(), pet.getXRot());
            navigation.stop();
            return;
        }
    }
}
