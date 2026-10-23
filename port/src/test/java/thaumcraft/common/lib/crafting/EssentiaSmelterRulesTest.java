package thaumcraft.common.lib.crafting;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EssentiaSmelterRulesTest {
    @Test void voidHasSourceFifteenTickOutputNotAnInventedSpeedUpgrade() { assertEquals(15,EssentiaSmelterRules.outputPeriod(0,false)); assertEquals(10,EssentiaSmelterRules.outputPeriod(1,false)); assertEquals(15,EssentiaSmelterRules.outputPeriod(2,false)); }
    @Test void alumentumOnlyChangesOutputPeriod() { assertEquals(12,EssentiaSmelterRules.outputPeriod(0,true)); assertEquals(8,EssentiaSmelterRules.outputPeriod(1,true)); assertEquals(12,EssentiaSmelterRules.outputPeriod(2,true)); assertEquals(66,EssentiaSmelterRules.cookTime(33,0)); }
    @Test void bellowsUsesOriginalFloatTruncationAndEfficiencies() { assertEquals(8,EssentiaSmelterRules.cookTime(5,1)); assertEquals(6,EssentiaSmelterRules.cookTime(5,3)); assertEquals(.8F,EssentiaSmelterRules.efficiency(0)); assertEquals(.9F,EssentiaSmelterRules.efficiency(1)); assertEquals(.95F,EssentiaSmelterRules.efficiency(2)); }
}
