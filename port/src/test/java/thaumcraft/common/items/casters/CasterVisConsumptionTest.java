package thaumcraft.common.items.casters;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CasterVisConsumptionTest {
    @Test void casterDiscountIsFloatAndHasTenPercentFloor() {
        assertEquals(.75F, CasterVisConsumption.modifier(.25F));
        assertEquals(.1F, CasterVisConsumption.modifier(2));
        assertEquals(1.2F, CasterVisConsumption.modifier(-.2F));
    }
    @Test void simulateDoesNotDrainAndInsufficientPoolFailsBeforePayment() {
        class Pool implements CasterVisConsumption.Aura {
            int drains;
            public float available(int x, int z) { return 2; }
            public float drain(int x, int z, float amount) { drains++; return amount; }
        }
        Pool pool = new Pool();
        assertTrue(CasterVisConsumption.consume(9, 1, true, pool));
        assertFalse(CasterVisConsumption.consume(11, 1, false, pool));
        assertEquals(0, pool.drains);
    }
    @Test void sourceCrossOrderAndNoProgressProtection() {
        var visits = new java.util.ArrayList<String>();
        assertTrue(CasterVisConsumption.consume(5, 1, false, new CasterVisConsumption.Aura() {
            public float available(int x, int z) { return 10; }
            public float drain(int x, int z, float amount) { visits.add(x + ":" + z); return amount; }
        }));
        assertEquals(java.util.List.of("0:0", "0:1", "-1:0", "0:-1", "1:0"), visits);
        assertFalse(CasterVisConsumption.consume(1, 2, false, new CasterVisConsumption.Aura() {
            public float available(int x, int z) { return 10; }
            public float drain(int x, int z, float amount) { return 0; }
        }));
    }
}
