package thaumcraft.common.golems;

import java.nio.ByteBuffer;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GolemPropertyRulesTest {
    @Test void matchesExactSourceByteBufferPacking() {
        long actual = 0; byte[] values = {5,4,3,2,1,10,0,0};
        for (int i = 0; i < 6; i++) actual = GolemPropertyRules.putByte(actual,i,values[i]);
        assertEquals(ByteBuffer.wrap(values).getLong(),actual);
        for (int i = 0; i < 8; i++) assertEquals(values[i],GolemPropertyRules.byteAt(actual,i));
    }
    @Test void reservedLow16AndOtherPartsArePreserved() {
        long source = 0x010203040509babeL;
        long edited = GolemPropertyRules.putByte(source,2,1);
        assertEquals(0x010201040509babeL,edited); assertFalse(GolemPropertyRules.newborn(edited));
        assertTrue(GolemPropertyRules.newborn(0x0102010405000000L));
    }
    @Test void malformedSignedBytesRanksAndCatalogIndicesFailClosed() {
        assertFalse(GolemPropertyRules.valid(0xff00000000000000L,6,5,5,4,4));
        assertFalse(GolemPropertyRules.valid(0x00000000000b0000L,6,5,5,4,4));
        assertTrue(GolemPropertyRules.valid(0x05040403030a1234L,6,5,5,4,4));
        assertThrows(IllegalArgumentException.class,() -> GolemPropertyRules.putByte(0,0,128));
        assertThrows(IllegalArgumentException.class,() -> GolemPropertyRules.byteAt(0,-1));
    }
    @Test void traitOppositesCancelRatherThanReplace() {
        var opposites = Map.of("LIGHT","HEAVY","HEAVY","LIGHT","FRAGILE","ARMORED","ARMORED","FRAGILE","CLUMSY","DEFT","DEFT","CLUMSY");
        Set<String> traits = new HashSet<>();
        for (String trait : List.of("LIGHT","SMART","FRAGILE","DEFT","FRAGILE","ARMORED","HEAVY")) GolemPropertyRules.addTrait(traits,trait,opposites::get);
        assertEquals(Set.of("SMART","DEFT"),traits);
        GolemPropertyRules.addTrait(traits,"CLUMSY",opposites::get); assertEquals(Set.of("SMART"),traits);
    }
    @Test void basicWoodSourcePressCostsEightMechanismAndBoundsAddonInputs() {
        assertEquals(8,GolemPropertyRules.workCost(1,new int[]{3,2,1}));
        assertEquals(12,GolemPropertyRules.workCost(2,new int[]{4,3,1}));
        assertThrows(IllegalArgumentException.class,() -> GolemPropertyRules.workCost(1,new int[]{0}));
        assertThrows(IllegalArgumentException.class,() -> GolemPropertyRules.workCost(1,new int[]{4097}));
    }
}
