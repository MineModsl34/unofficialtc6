package thaumcraft.common.golems.ai;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.Objects;
import java.util.function.Predicate;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.player.Player;

/** Source selectable-friendly target goal, also retained as an addon API. */
public class AINearestValidTarget<T extends LivingEntity> extends TargetGoal {
    protected final Class<T> targetClass;
    private final int targetChance;
    protected final Comparator<T> theNearestAttackableTargetSorter;
    protected final Predicate<T> targetEntitySelector;
    protected T targetEntity;
    private int targetUnseenTicks;

    public AINearestValidTarget(PathfinderMob mob, Class<T> type, boolean sight) { this(mob, type, sight, false); }
    public AINearestValidTarget(PathfinderMob mob, Class<T> type, boolean sight, boolean nearby) { this(mob, type, 10, sight, nearby, null); }
    public AINearestValidTarget(PathfinderMob mob, Class<T> type, int chance, boolean sight, boolean nearby, Predicate<T> selector) {
        super(mob, sight, nearby);
        targetClass = type;
        targetChance = chance;
        theNearestAttackableTargetSorter = Comparator.comparingDouble(mob::distanceToSqr);
        setFlags(EnumSet.of(Flag.TARGET)); // Native target selector's equivalent of source mutex bit 1.
        targetEntitySelector = target -> {
            if (selector != null && !selector.test(target)) return false;
            if (target instanceof Player player) {
                double range = getFollowDistance();
                if (target.isShiftKeyDown()) range *= 0.8F;
                if (target.isInvisible()) range *= 0.7F * Math.max(0.1F, player.getArmorCoverPercentage());
                if (target.distanceTo(mob) > range) return false;
            }
            return isSuitableTarget(target, false);
        };
    }
    private boolean friendly() { return mob instanceof ITargets targets && targets.getTargetFriendly(); }
    @Override public boolean canContinueToUse() {
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) return false;
        if (mob.getTeam() != null && ((target.getTeam() == mob.getTeam()) != friendly())) return false;
        double range = getFollowDistance();
        if (mob.distanceToSqr(target) > range * range) return false;
        if (mustSee) {
            if (mob.getSensing().hasLineOfSight(target)) targetUnseenTicks = 0;
            else if (++targetUnseenTicks > 60) return false;
        }
        return true;
    }
    protected boolean isSuitableTarget(LivingEntity target, boolean includeInvincibles) {
        if (target == null || target == mob || !target.isAlive() || !mob.canAttackType(target.getType())) return false;
        boolean friendly = friendly();
        if (mob.getTeam() != null && ((target.getTeam() == mob.getTeam()) != friendly)) return false;
        if (mob instanceof OwnableEntity ownable && ownable.getOwnerUUID() != null) {
            if (target instanceof OwnableEntity other) {
                boolean sameOwner = Objects.equals(ownable.getOwnerUUID(), other.getOwnerUUID());
                if (sameOwner != friendly) return false;
            } else if (!(target instanceof Player) && friendly) return false;
            if (target == ownable.getOwner() && !friendly) return false;
        } else if (target instanceof Player player && !includeInvincibles && player.getAbilities().invulnerable && !friendly) return false;
        return (!mustSee || mob.getSensing().hasLineOfSight(target)) && mob.isWithinRestriction(target.blockPosition());
    }
    @Override public boolean canUse() {
        if (targetChance > 0 && mob.getRandom().nextInt(targetChance) != 0) return false;
        double range = getFollowDistance();
        var candidates = mob.level().getEntitiesOfClass(targetClass, mob.getBoundingBox().inflate(range, 4, range),
                target -> !target.isSpectator() && targetEntitySelector.test(target));
        candidates.sort(theNearestAttackableTargetSorter);
        if (candidates.isEmpty()) return false;
        targetEntity = candidates.getFirst();
        return true;
    }
    @Override public void start() { mob.setTarget(targetEntity); targetUnseenTicks = 0; super.start(); }

    /** Source public comparator helper retained for addons. */
    public class Sorter implements Comparator<Entity> {
        private final Entity entity;
        public Sorter(Entity entity) { this.entity = entity; }
        @Override public int compare(Entity first, Entity second) {
            return Double.compare(entity.distanceToSqr(first), entity.distanceToSqr(second));
        }
    }
}
