package thaumcraft.client.lib;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** Pure default-off fixture gate only; actual Minecraft screen/menu preservation is checked
 * after vanilla setScreen in the TCP audit, not claimed by this bootstrap-free unit test. */
class ResearchStageFourClientScreenGuardTest {
    @Test
    void disabledAuditNeverDismissesEvenReadyMatchingMenu() {
        Object menu = new Object();
        assertFalse(ResearchStageFourNetworkClientAudit.shouldDismissTheoryScreen(false, menu, menu, true));
    }

    @Test
    void enabledAuditRequiresActualReadyMenuIdentityNotEqualityOrNull() {
        Object menu = new String("same"), equalButDifferent = new String("same");
        assertTrue(ResearchStageFourNetworkClientAudit.shouldDismissTheoryScreen(true, menu, menu, true));
        assertFalse(ResearchStageFourNetworkClientAudit.shouldDismissTheoryScreen(true, menu, equalButDifferent, true));
        assertFalse(ResearchStageFourNetworkClientAudit.shouldDismissTheoryScreen(true, menu, menu, false));
        assertFalse(ResearchStageFourNetworkClientAudit.shouldDismissTheoryScreen(true, null, null, true));
        assertFalse(ResearchStageFourNetworkClientAudit.shouldDismissTheoryScreen(true, menu, null, true));
    }
}
