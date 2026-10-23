package thaumcraft.common.blocks.world.taint;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TaintSpreadRulesTest {
    @Test void onlySolidNonTaintNeighborSupportsFibre() {
        for (boolean air : new boolean[]{false, true}) for (boolean material : new boolean[]{false, true}) for (boolean solid : new boolean[]{false, true})
            assertEquals(!air && !material && solid, TaintSpreadRules.isNonTaintSupport(air, material, solid));
    }
    @Test void taintRockLogAndGooCannotMasqueradeAsHealthyFibreSupport() {
        assertFalse(TaintSpreadRules.isNonTaintSupport(false, true, true));
        assertFalse(TaintSpreadRules.isNonTaintSupport(false, true, false));
        assertTrue(TaintSpreadRules.isNonTaintSupport(false, false, true));
    }
}
