package thaumcraft.common.tiles.devices;

import java.util.HashSet;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DeviceRulesTest {
    @Test void urnScanCoversExactlyTheOriginalFiveByThreeByFiveNeighborhood() { var visited = new HashSet<String>(); for (int index = 0; index < 75; index++) { int[] p = DeviceRules.urnOffset(index); assertTrue(Math.abs(p[0]) <= 2); assertTrue(Math.abs(p[1]) <= 1); assertTrue(Math.abs(p[2]) <= 2); visited.add(Arrays.toString(p)); assertArrayEquals(p,DeviceRules.urnOffset(index + 75)); } assertEquals(75,visited.size()); }
    @Test void condenserRateCapAndCostAreSourceArithmetic() { assertEquals(0,DeviceRules.condenserInterval(0)); assertEquals(585,DeviceRules.condenserInterval(1)); assertEquals(5,DeviceRules.condenserInterval(40)); assertEquals(5,DeviceRules.condenserInterval(90)); assertEquals(5,DeviceRules.condenserCost(1)); assertEquals(7,DeviceRules.condenserCost(9)); }
    @Test void crafterUsesAllTenExactSourcePatterns() { assertArrayEquals(new int[]{9,1,2,2,4,3,3,6,6,8},Arrays.stream(DeviceRules.PATTERNS).mapToInt(pattern -> pattern.length).toArray()); assertArrayEquals(new int[]{0,3},DeviceRules.PATTERNS[3]); assertArrayEquals(new int[]{0,1,3,4,6,7},DeviceRules.PATTERNS[8]); assertFalse(Arrays.stream(DeviceRules.PATTERNS[9]).anyMatch(index -> index == 4)); }
    @Test void sourceRefillCannotSpendMoreThanPointOneVisPerPass() { assertEquals(.1F,DeviceRules.urnVisRequest(0)); assertEquals(.1F,DeviceRules.urnVisRequest(500)); assertEquals(.001F,DeviceRules.urnVisRequest(999)); assertEquals(0,DeviceRules.urnVisRequest(1000)); }
    @Test void sourceComparatorHasOneForNonemptyAndFifteenAtCapacity() { assertEquals(0,DeviceRules.compare(0)); assertEquals(1,DeviceRules.compare(.001F)); assertEquals(8,DeviceRules.compare(.5F)); assertEquals(15,DeviceRules.compare(1)); }
    @Test void sourceOreBridgeDoesNotEquateAllModernMinerals() { assertFalse(DeviceRules.baseOreTag("c","ingots")); assertFalse(DeviceRules.baseOreTag("c","ores")); assertFalse(DeviceRules.baseOreTag("c","gems")); assertFalse(DeviceRules.baseOreTag("c","ores/in_ground/stone")); assertFalse(DeviceRules.baseOreTag("c","foods")); assertTrue(DeviceRules.baseOreTag("c","ingots/iron")); assertTrue(DeviceRules.baseOreTag("c","ores/copper")); assertTrue(DeviceRules.baseOreTag("minecraft","planks")); assertTrue(DeviceRules.baseOreTag("minecraft","logs")); assertTrue(DeviceRules.baseOreTag("minecraft","wool")); assertFalse(DeviceRules.baseOreTag("addon","ingots/iron")); }
}
