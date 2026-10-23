package thaumcraft.common.golems.ai;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import thaumcraft.api.golems.EnumGolemTrait;
import thaumcraft.api.golems.GolemHelper;
import thaumcraft.api.golems.seals.ISealEntity;
import thaumcraft.api.golems.tasks.Task;
import thaumcraft.common.config.ModConfig;
import thaumcraft.common.golems.EntityThaumcraftGolem;
import thaumcraft.common.golems.seals.SealHandler;
import thaumcraft.common.golems.tasks.TaskHandler;

public abstract class AIGoto extends Goal {
    protected final EntityThaumcraftGolem golem;
    protected int taskCounter = -1;
    protected final byte type;
    protected int cooldown;
    protected double minDist = 4;
    private BlockPos prevRamble;
    protected BlockPos targetBlock;
    private int pause;

    public AIGoto(EntityThaumcraftGolem golem, byte type) {
        this.golem = golem;
        this.type = type;
        setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
    }
    @Override public boolean requiresUpdateEveryTick() { return true; }
    @Override public boolean canUse() {
        if (cooldown > 0) { cooldown--; return false; }
        cooldown = 5;
        if (golem.getTask() != null && !golem.getTask().isSuspended()) return false;
        targetBlock = null;
        boolean start = findDestination();
        if (start && golem.getTask() != null && golem.getTask().getSealPos() != null) {
            ISealEntity seal = GolemHelper.getSealEntity(golem.level().dimension(), golem.getTask().getSealPos());
            if (seal != null) seal.getSeal().onTaskStarted(golem.level(), golem, golem.getTask());
        }
        return start;
    }
    @Override public void start() { moveTo(); taskCounter = 0; }
    protected abstract void moveTo();
    @Override public boolean canContinueToUse() {
        return taskCounter >= 0 && taskCounter <= 1000 && golem.getTask() != null && !golem.getTask().isSuspended()
                && isValidDestination(golem.level(), golem.getTask().getPos());
    }
    @Override public void tick() {
        if (golem.getTask() == null || pause-- > 0) return;
        double distance = golem.getTask().getType() == 0
                ? (targetBlock == null ? golem.getTask().getPos() : targetBlock).distToCenterSqr(golem.position())
                : golem.distanceToSqr(golem.getTask().getEntity());
        if (distance > minDist) {
            golem.getTask().setCompletion(false);
            taskCounter++;
            if (taskCounter % 5 == 0) {
                if (prevRamble != null && prevRamble.equals(golem.blockPosition())) {
                    Vec3 target = DefaultRandomPos.getPosTowards(golem, 6, 4, Vec3.atLowerCornerOf(golem.getTask().getPos()), Math.PI / 2);
                    if (target != null) golem.getNavigation().moveTo(target.x + 0.5, target.y + 0.5, target.z + 0.5, golem.getGolemMoveSpeed());
                } else moveTo();
                prevRamble = golem.blockPosition();
            }
        } else {
            TaskHandler.completeTask(golem.getTask(), golem);
            if (golem.getTask() != null && golem.getTask().isCompleted()) {
                if (taskCounter >= 0) taskCounter = 0;
                pause = 0;
            } else { pause = 10; taskCounter++; }
            taskCounter--;
        }
    }
    @Override public void stop() {
        if (golem.getTask() == null) return;
        if (!golem.getTask().isCompleted() && golem.getTask().isReserved() && ModConfig.CONFIG_GRAPHICS.showGolemEmotes)
            golem.level().broadcastEntityEvent(golem, (byte) 6);
        if (golem.getTask().isCompleted() && !golem.getTask().isSuspended()) golem.getTask().setSuspended(true);
        golem.getTask().setReserved(false);
    }
    protected abstract boolean findDestination();
    protected boolean isValidDestination(Level world, BlockPos pos) {
        // The source extension hook accepted every position. A stale seal task must
        // not force-load its destination after chunk unload in the native lifecycle.
        return world.hasChunkAt(pos);
    }
    protected boolean areGolemTagsValidForTask(Task ticket) {
        ISealEntity seal = SealHandler.getSealEntity(golem.level().dimension(), ticket.getSealPos());
        if (seal == null || seal.getSeal() == null) return true;
        // Source compared UUID to String, rejecting even the owner. Native bridge compares their values.
        if (seal.isLocked() && !Objects.equals(golem.getOwnerId() == null ? null : golem.getOwnerId().toString(), seal.getOwner())) return false;
        if (seal.getSeal().getRequiredTags() != null && !golem.getProperties().getTraits().containsAll(Arrays.asList(seal.getSeal().getRequiredTags()))) return false;
        if (seal.getSeal().getForbiddenTags() != null) for (EnumGolemTrait trait : seal.getSeal().getForbiddenTags())
            if (golem.getProperties().hasTrait(trait)) return false;
        return true;
    }
}
