package thaumcraft.common.lib.utils;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** TC6 EntityUtils' ordered, eye-visible entity selector, separate from the block fallback. */
public final class ScanTargeting {
    public static final double LEGACY_BLOCK_REACH = 5.0D;
    private static final double LEGACY_PLANE_EPSILON_SQUARED = 1.0000000116860974E-7D;

    private ScanTargeting() {}

    @Nullable
    public static Entity pointedEntity(Level level, Entity source, double minRange, double range,
                                      float padding, boolean nonCollide) {
        Vec3 eye = source.getEyePosition();
        Vec3 look = legacyLook(source);
        AABB area = source.getBoundingBox().expandTowards(look.scale(range)).inflate(padding);
        List<Candidate<Entity>> candidates = new ArrayList<>();
        // Vanilla's default predicate excludes spectators, and excludes source by identity.
        // Do not sort: the original selector has observable ordered ties and a zero sentinel.
        for (Entity entity : level.getEntities(source, area)) {
            // Source short-circuits before any world ray; avoid extra block/chunk reads for skipped entities.
            if (eye.distanceTo(entity.position()) < minRange || (!entity.canBeCollidedWith() && !nonCollide)) continue;
            boolean visible = level.clip(new ClipContext(eye, entity.getEyePosition(),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, source)).getType() == HitResult.Type.MISS;
            candidates.add(new Candidate<>(entity, entity.position(), entity.getBoundingBox(),
                entity.getPickRadius(), entity.canBeCollidedWith(), visible));
        }
        return pickFromCandidates(eye, look, minRange, range, nonCollide, candidates);
    }

    /** Source-only fluids and outline blocks, with the old vanilla reach of five blocks.
     * The legacy generic.reachDistance / modern addon reach-modifier bridge is deferred. */
    public static BlockHitResult legacyBlockHit(Player player) {
        Vec3 eye = player.getEyePosition();
        return player.level().clip(new ClipContext(eye, eye.add(legacyLook(player).scale(LEGACY_BLOCK_REACH)),
            ClipContext.Block.OUTLINE, ClipContext.Fluid.SOURCE_ONLY, player));
    }

    /** Keep the original trig phase/float operations; modern calculateViewVector is algebraically
     * equivalent but not bit-identical at boundary angles. */
    public static Vec3 legacyLook(Entity entity) {
        float degrees = (float) (Math.PI / 180.0D);
        float yawCos = Mth.cos(-entity.getYRot() * degrees - (float) Math.PI);
        float yawSin = Mth.sin(-entity.getYRot() * degrees - (float) Math.PI);
        float pitchCos = -Mth.cos(-entity.getXRot() * degrees);
        float pitchSin = Mth.sin(-entity.getXRot() * degrees);
        return new Vec3(yawSin * pitchCos, pitchSin, yawCos * pitchCos);
    }

    @Nullable
    public static Object scanTarget(Player player) {
        Entity entity = pointedEntity(player.level(), player, 1.0D, 9.0D, 0.0F, true);
        if (entity != null) return entity;
        BlockHitResult block = legacyBlockHit(player);
        return block.getType() == HitResult.Type.BLOCK ? block.getBlockPos() : null;
    }

    /** Immutable geometry inputs also make the original corner cases independently testable. */
    public record Candidate<T>(T value, Vec3 feet, AABB bounds, float collisionBorder,
                               boolean collidable, boolean eyeLineOfSight) {}

    @Nullable
    public static <T> T pickFromCandidates(Vec3 eye, Vec3 look, double minRange, double range,
                                          boolean nonCollide, List<Candidate<T>> candidates) {
        T pointed = null;
        double nearest = 0.0D;
        Vec3 end = eye.add(look.scale(range));
        for (Candidate<T> entity : candidates) {
            if (eye.distanceTo(entity.feet()) < minRange || (!entity.collidable() && !nonCollide)
                || !entity.eyeLineOfSight()) continue;
            // Preserve float widening: source Math.max used 0.8F, not the double 0.8D.
            AABB box = entity.bounds().inflate(Math.max(0.8F, entity.collisionBorder()));
            Vec3 intercept = legacyIntercept(box, eye, end);
            if (strictlyInside(box, eye)) {
                pointed = entity.value();
                nearest = 0.0D;
            } else if (intercept != null) {
                double distance = eye.distanceTo(intercept);
                // An inside-box zero can be replaced by a later positive hit: intentional TC6 quirk.
                if (distance < nearest || nearest == 0.0D) {
                    pointed = entity.value();
                    nearest = distance;
                }
            }
        }
        return pointed;
    }

    public static boolean strictlyInside(AABB box, Vec3 point) {
        return point.x > box.minX && point.x < box.maxX && point.y > box.minY && point.y < box.maxY
            && point.z > box.minZ && point.z < box.maxZ;
    }

    /** Six-plane 1.12 interception: inclusive endpoints, strict nearest ties, old near-parallel epsilon.
     * Modern AABB.clip excludes certain endpoint hits, so it cannot replace this calculation. */
    @Nullable
    public static Vec3 legacyIntercept(AABB box, Vec3 start, Vec3 end) {
        Vec3 nearest = null;
        double nearestDistance = Double.POSITIVE_INFINITY;
        double[] planes = {box.minX, box.maxX, box.minY, box.maxY, box.minZ, box.maxZ};
        for (int index = 0; index < planes.length; index++) {
            int axis = index / 2;
            double origin = axis == 0 ? start.x : axis == 1 ? start.y : start.z;
            double delta = axis == 0 ? end.x - start.x : axis == 1 ? end.y - start.y : end.z - start.z;
            if (delta * delta < LEGACY_PLANE_EPSILON_SQUARED) continue;
            double fraction = (planes[index] - origin) / delta;
            if (fraction < 0.0D || fraction > 1.0D) continue;
            Vec3 point = start.add(end.subtract(start).scale(fraction));
            boolean onFace = axis == 0 ? point.y >= box.minY && point.y <= box.maxY && point.z >= box.minZ && point.z <= box.maxZ
                : axis == 1 ? point.x >= box.minX && point.x <= box.maxX && point.z >= box.minZ && point.z <= box.maxZ
                : point.x >= box.minX && point.x <= box.maxX && point.y >= box.minY && point.y <= box.maxY;
            if (!onFace) continue;
            double distance = start.distanceToSqr(point);
            if (distance < nearestDistance) { nearest = point; nearestDistance = distance; }
        }
        return nearest;
    }
}
