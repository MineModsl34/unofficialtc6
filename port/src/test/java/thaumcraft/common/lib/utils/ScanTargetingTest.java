package thaumcraft.common.lib.utils;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import thaumcraft.common.lib.utils.ScanTargeting.Candidate;

class ScanTargetingTest {
    private static final Vec3 EYE = Vec3.ZERO;
    private static final Vec3 LOOK = new Vec3(1, 0, 0);

    private static Candidate<String> candidate(String name, double x) {
        return new Candidate<>(name, new Vec3(x, -1.6, 0), new AABB(x - 0.1, -0.1, -0.1, x + 0.1, 0.1, 0.1),
            0.0F, true, true);
    }

    private static String pick(Candidate<String>... candidates) {
        return ScanTargeting.pickFromCandidates(EYE, LOOK, 1.0D, 9.0D, true, List.of(candidates));
    }

    @Test
    void nearestWinsInEitherIterationOrder() {
        assertEquals("near", pick(candidate("far", 7), candidate("near", 3)));
        assertEquals("near", pick(candidate("near", 3), candidate("far", 7)));
    }

    @Test
    void equalPositiveIntersectionsKeepFirst() {
        assertEquals("first", pick(candidate("first", 3), candidate("second", 3)));
    }

    @Test
    void insideBoxZeroSentinelCanBeReplacedByLaterOutsideHit() {
        assertEquals("inside", pick(candidate("inside", 0)));
        assertEquals("later", pick(candidate("inside", 0), candidate("later", 5)));
        assertEquals("inside", pick(candidate("earlier", 5), candidate("inside", 0)));
        assertEquals("second-inside", pick(candidate("first-inside", 0), candidate("second-inside", 0)));
    }

    @Test
    void minimumDistanceUsesFeetNotEyeOrIntersection() {
        var belowEye = candidate("below", 0); // feet distance1.6 even though eye lies in inflated box
        assertEquals("below", pick(belowEye));
        var tooNear = new Candidate<>("too-near", new Vec3(0.999, 0, 0), candidate("", 3).bounds(), 0F, true, true);
        var boundary = new Candidate<>("boundary", new Vec3(1, 0, 0), candidate("", 3).bounds(), 0F, true, true);
        assertNull(pick(tooNear));
        assertEquals("boundary", pick(boundary));
    }

    @Test
    void nonCollidableTargetsIncludedOnlyWhenRequestedAndBlockedEyesExcluded() {
        var item = new Candidate<>("item", new Vec3(4, 0, 0), candidate("", 4).bounds(), 0F, false, true);
        assertEquals("item", pick(item));
        assertNull(ScanTargeting.pickFromCandidates(EYE, LOOK, 1, 9, false, List.of(item)));
        var blocked = new Candidate<>("behind-wall", item.feet(), item.bounds(), 0F, true, false);
        assertNull(pick(blocked));
    }

    @Test
    void collisionBorderHasFloatPointEightFloorButLargerAddonBorderRetained() {
        AABB offAxis = new AABB(3, 0.79, -0.1, 4, 0.9, 0.1);
        assertEquals("floor", pick(new Candidate<>("floor", new Vec3(3, 1, 0), offAxis, 0.1F, true, true)));
        AABB fartherOffAxis = new AABB(3, 1.0, -0.1, 4, 1.1, 0.1);
        assertNull(pick(new Candidate<>("too-far", new Vec3(3, 1, 0), fartherOffAxis, 0F, true, true)));
        assertEquals("addon", pick(new Candidate<>("addon", new Vec3(3, 1, 0), fartherOffAxis, 1.25F, true, true)));
    }

    @Test
    void insideRequiresAllSixStrictInequalities() {
        AABB box = new AABB(0, 0, 0, 1, 1, 1);
        assertTrue(ScanTargeting.strictlyInside(box, new Vec3(0.5, 0.5, 0.5)));
        for (Vec3 point : List.of(new Vec3(0, .5, .5), new Vec3(1, .5, .5), new Vec3(.5, 0, .5),
            new Vec3(.5, 1, .5), new Vec3(.5, .5, 0), new Vec3(.5, .5, 1))) {
            assertFalse(ScanTargeting.strictlyInside(box, point));
        }
    }

    @Test
    void legacyPlaneInterceptionRetainsEndpointsAndRejectsAlmostParallelAxis() {
        AABB box = new AABB(0, 0, 0, 1, 1, 1);
        assertEquals(new Vec3(0, .5, .5), ScanTargeting.legacyIntercept(box, new Vec3(-1, .5, .5), new Vec3(0, .5, .5)));
        assertEquals(new Vec3(0, .5, .5), ScanTargeting.legacyIntercept(box, new Vec3(0, .5, .5), new Vec3(-1, .5, .5)));
        assertNull(ScanTargeting.legacyIntercept(box, new Vec3(-.00001, .5, .5), new Vec3(.00001, .5, .5)));
    }

    @Test
    void exactRangeEndCanHitInflatedBoxButBeyondEndCannot() {
        double border = (double) 0.8F;
        var end = new Candidate<>("end", new Vec3(10, 0, 0), new AABB(9 + border, -.1, -.1, 10, .1, .1), 0F, true, true);
        assertEquals("end", pick(end));
        var outside = new Candidate<>("outside", new Vec3(10, 0, 0), new AABB(9 + border + .001, -.1, -.1, 10, .1, .1), 0F, true, true);
        assertNull(pick(outside));
    }
}
