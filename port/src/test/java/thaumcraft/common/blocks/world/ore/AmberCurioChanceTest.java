package thaumcraft.common.blocks.world.ore;

import java.util.Random;
import org.junit.jupiter.api.Test;
import thaumcraft.common.lib.research.theorycraft.AmberCurioRules;
import static org.junit.jupiter.api.Assertions.*;

final class AmberCurioChanceTest {
    @Test void exactSourceDoubleBoundaryAndSeededChance() {
        assertTrue(AmberCurioRules.isCurioRoll(0));
        assertFalse(AmberCurioRules.isCurioRoll(1));
        float lower = (float)0.066D;
        assertEquals(lower < 0.066D, AmberCurioRules.isCurioRoll(lower));
        assertTrue(AmberCurioRules.isCurioRoll(Math.nextDown(lower)));
        assertFalse(AmberCurioRules.isCurioRoll(Math.nextUp(lower)));
        Random random = new Random(51475391L);
        int successes = 0;
        for (int index = 0; index < 100_000; index++) {
            float roll = random.nextFloat();
            assertEquals(roll < 0.066D, AmberCurioRules.isCurioRoll(roll));
            if (AmberCurioRules.isCurioRoll(roll)) successes++;
        }
        assertTrue(successes > 6000 && successes < 7200);
    }
}
