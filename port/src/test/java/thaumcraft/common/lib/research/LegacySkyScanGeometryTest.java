package thaumcraft.common.lib.research;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class LegacySkyScanGeometryTest {
    @Test
    void vanillaAngleRetainsFloatProviderStepsAndPeriodicNegativeJavaRemainder() {
        assertEquals(0.7845178246498108F, LegacySkyScanGeometry.celestialAngle(0));
        assertEquals(0.0F, LegacySkyScanGeometry.celestialAngle(6000));
        assertEquals(0.2154821902513504F, LegacySkyScanGeometry.celestialAngle(12000));
        assertEquals(0.5F, LegacySkyScanGeometry.celestialAngle(18000));
        assertEquals(LegacySkyScanGeometry.celestialAngle(23999), LegacySkyScanGeometry.celestialAngle(-1));
        for (long tick = 0; tick < 24000; tick++) {
            assertEquals(LegacySkyScanGeometry.celestialAngle(tick), LegacySkyScanGeometry.celestialAngle(tick + 24000));
        }
    }

    @Test
    void yawCastComesBeforeSignedRemainderAndIsNotWrappedToNearestEquivalentHeading() {
        assertEquals(0, LegacySkyScanGeometry.aim(-90.9F, -12, 0.0F).yaw());
        assertEquals(0, LegacySkyScanGeometry.aim(-89.1F, -12, 0.0F).yaw());
        assertEquals(-359, LegacySkyScanGeometry.aim(-449.0F, -12, 0.0F).yaw());
        assertFalse(LegacySkyScanGeometry.aim(-449.0F, -90, 0.0F).inRangeYaw());
        assertTrue(LegacySkyScanGeometry.aim(-89.0F, -90, 0.0F).inRangeYaw());
    }

    @Test
    void yawNineMatchesTenDoesNotOnEitherSunArcBranch() {
        assertTrue(LegacySkyScanGeometry.aim(-81, -90, 0).aligned());
        assertFalse(LegacySkyScanGeometry.aim(-80, -90, 0).aligned());
        assertTrue(LegacySkyScanGeometry.aim(81, -45, .125F).aligned());
        assertFalse(LegacySkyScanGeometry.aim(80, -45, .125F).aligned());
        assertTrue(LegacySkyScanGeometry.aim(-261, -45, .125F).aligned());
        assertFalse(LegacySkyScanGeometry.aim(-260, -45, .125F).aligned());
    }

    @Test
    void truncatedPitchSixMatchesSevenDoesNotAndNinetyArcUsesEastBranch() {
        assertTrue(LegacySkyScanGeometry.aim(-90, -84.999F, 0).aligned());
        assertFalse(LegacySkyScanGeometry.aim(-90, -83, 0).aligned());
        assertTrue(LegacySkyScanGeometry.aim(-90, -96.999F, 0).aligned());
        assertFalse(LegacySkyScanGeometry.aim(-90, -97, 0).aligned());
        assertEquals(90, LegacySkyScanGeometry.aim(-90, -90, 0).arc());
        assertFalse(LegacySkyScanGeometry.aim(90, -90, 0).aligned());
    }

    @Test
    void nightStartsStrictlyAfterOneEightyAndOffTargetOnlyWorksAtNight() {
        var sunset = LegacySkyScanGeometry.aim(-90, 0, .25F);
        assertFalse(sunset.night());
        assertFalse(sunset.scannable());
        assertNull(LegacySkyScanGeometry.note(sunset, 0, -90));
        var justNight = LegacySkyScanGeometry.aim(0, 0, .253F);
        assertTrue(justNight.night() && justNight.scannable() && !justNight.aligned());
        assertEquals(new LegacySkyScanGeometry.Note("Star1", 2), LegacySkyScanGeometry.note(justNight, 0, 0));
    }

    @Test
    void sourceHorizontalFacingIndicesAndFloatFloorBoundariesAreNotArrayOrderGuesses() {
        assertEquals(0, LegacySkyScanGeometry.starNumber(180));
        assertEquals(1, LegacySkyScanGeometry.starNumber(0));
        assertEquals(2, LegacySkyScanGeometry.starNumber(90));
        assertEquals(3, LegacySkyScanGeometry.starNumber(-90));
        assertEquals(1, LegacySkyScanGeometry.starNumber(44.999F));
        assertEquals(2, LegacySkyScanGeometry.starNumber(45));
        assertEquals(1, LegacySkyScanGeometry.starNumber(-45));
        assertEquals(3, LegacySkyScanGeometry.starNumber(-45.001F));
        assertEquals(LegacySkyScanGeometry.starNumber(90), LegacySkyScanGeometry.starNumber(450));
    }

    @Test
    void keysUseGameTimeTruncatingSignedDayThenIntOverflowWhileMoonUsesDayTime() {
        assertEquals("CEL_0_", LegacySkyScanGeometry.dayPrefix(-23999));
        assertEquals("CEL_-1_", LegacySkyScanGeometry.dayPrefix(-24000));
        assertEquals(Integer.MIN_VALUE, LegacySkyScanGeometry.worldDay(((long) Integer.MAX_VALUE + 1) * 24000L));
        assertEquals("CEL_9_", LegacySkyScanGeometry.dayPrefix(9L * 24000 + 6000));
        assertEquals(7, LegacySkyScanGeometry.moonPhase(-24000));
        assertEquals(0, LegacySkyScanGeometry.moonPhase(-1));
        for (int phase = 0; phase < 8; phase++) {
            assertEquals(new LegacySkyScanGeometry.Note("Moon" + phase, 5 + phase),
                LegacySkyScanGeometry.note(LegacySkyScanGeometry.aim(-90, -90, .5F), phase * 24000L + 18000, -90));
        }
        assertEquals(new LegacySkyScanGeometry.Note("Sun", 0),
            LegacySkyScanGeometry.note(LegacySkyScanGeometry.aim(-90, -90, 0), 7L * 24000, -90));
    }

    @Test
    void unboundedInputRetainsJavaCastsRatherThanInventingFiniteClamp() {
        var result = LegacySkyScanGeometry.aim(Float.NaN, Float.NaN, Float.NaN);
        assertEquals(0, result.yaw());
        assertEquals(0, result.pitch());
        assertEquals(0, result.arc());
        assertTrue(result.aligned());
    }
}
