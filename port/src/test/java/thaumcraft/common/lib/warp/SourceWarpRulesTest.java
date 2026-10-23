package thaumcraft.common.lib.warp;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SourceWarpRulesTest {
    @Test void storageClampAndSourceUnderflowSignQuirkAreLiteral() {
        assertEquals(4, SourceWarpRules.adjustedAmount(4, -5));
        assertEquals(8, SourceWarpRules.clampedValue(4, -5));
        assertEquals(0, SourceWarpRules.adjustedAmount(0, -1));
        assertEquals(0, SourceWarpRules.clampedValue(0, -1));
        assertEquals(-4, SourceWarpRules.adjustedAmount(4, -4));
        assertEquals(0, SourceWarpRules.clampedValue(4, -4));
        assertEquals(500, SourceWarpRules.clampedValue(499, 10));
    }
    @Test void sourceEvery2000TicksAndBothConfigurationGates() {
        assertFalse(SourceWarpRules.shouldCheck(0, false, false));
        assertFalse(SourceWarpRules.shouldCheck(1999, false, false));
        assertTrue(SourceWarpRules.shouldCheck(2000, false, false));
        assertTrue(SourceWarpRules.shouldCheck(4000, false, false));
        assertFalse(SourceWarpRules.shouldCheck(2000, true, false));
        assertFalse(SourceWarpRules.shouldCheck(2000, false, true));
    }
    @Test void failedRollStillConsumesSourceHundredBoundRng() {
        List<Integer> bounds = new ArrayList<>();
        var roll = SourceWarpRules.roll(0, 0, 0, 0, 0, false, bound -> { bounds.add(bound); return 0; });
        assertFalse(roll.occurred());
        assertEquals(List.of(100), bounds);
        assertFalse(SourceWarpRules.roll(10, 0, 0, 0, 25, false, bound -> 6).occurred());
        assertTrue(SourceWarpRules.roll(10, 0, 0, 0, 25, false, bound -> 5).occurred());
    }
    @Test void eventRollRetainsCounterTruncationWeightAndMaskRngOrder() {
        List<Integer> bounds = new ArrayList<>();
        int[] supplied = {0, 30, 3};
        int[] cursor = {0};
        var roll = SourceWarpRules.roll(20, 30, 50, 4, 101, true, bound -> {
            bounds.add(bound); return supplied[cursor[0]++];
        });
        assertEquals(List.of(100, 100, 4), bounds);
        assertTrue(roll.occurred());
        assertEquals(100, roll.effectiveWarp());
        assertEquals((int) (101 - (Math.sqrt(101) * 2 - 8)), roll.nextCounter());
        assertEquals(29, roll.effect());
        assertEquals(SourceWarpRules.Event.MIST_ONE, roll.event());
    }
    @Test void negativeCounterIsRetainedRatherThanClampedAfterEvent() {
        var roll = SourceWarpRules.roll(1, 0, 0, 0, 1, false, bound -> 0);
        assertEquals(-4, roll.nextCounter());
        assertEquals(SourceWarpRules.Event.NONE, roll.event());
    }
    @Test void allEffectBoundariesPreserveSeventySixOnlyClarityAndHighGearOverflow() {
        var events = SourceWarpRules.Event.values();
        assertEquals(SourceWarpRules.Event.NONE, SourceWarpRules.eventFor(-20));
        assertEquals(SourceWarpRules.Event.CREEPER_SOUND, SourceWarpRules.eventFor(4));
        assertEquals(SourceWarpRules.Event.EXPLOSION_SOUND, SourceWarpRules.eventFor(5));
        assertEquals(SourceWarpRules.Event.BLINDNESS, SourceWarpRules.eventFor(72));
        for (int value : new int[] {73, 74, 75, 77, 78, 79, 80}) assertEquals(SourceWarpRules.Event.STRONG_HUNGER, SourceWarpRules.eventFor(value));
        assertEquals(SourceWarpRules.Event.CLARITY, SourceWarpRules.eventFor(76));
        assertEquals(SourceWarpRules.Event.PORTAL, SourceWarpRules.eventFor(88));
        assertEquals(SourceWarpRules.Event.REAL_SPIDERS, SourceWarpRules.eventFor(92));
        assertEquals(SourceWarpRules.Event.MIST_MANY, SourceWarpRules.eventFor(300));
        for (int ordinal = 1; ordinal <= 18; ordinal++) assertEquals(events[ordinal], SourceWarpRules.eventFor(ordinal * 4));
    }
    @Test void sourceGuardianAndPotionCapsUseWeightedWarpNotStorageTotal() {
        assertEquals(1, SourceWarpRules.guardians(SourceWarpRules.Event.MIST_ONE, 100));
        assertEquals(3, SourceWarpRules.guardians(SourceWarpRules.Event.MIST_FEW, 100));
        assertEquals(6, SourceWarpRules.guardians(SourceWarpRules.Event.MIST_MANY, 100));
        assertEquals(8, SourceWarpRules.guardians(SourceWarpRules.Event.MIST_MANY, 500));
        assertEquals(0, SourceWarpRules.guardians(SourceWarpRules.Event.PORTAL, 100));
        assertEquals(0, SourceWarpRules.amplifier(14));
        assertEquals(1, SourceWarpRules.amplifier(15));
        assertEquals(3, SourceWarpRules.amplifier(100));
    }
}
