package thaumcraft.common.lib.crafting;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class MirrorLinkRulesTest{
    @Test void sourceRetryIsFortyUpToSixHundredAndRecovers(){assertEquals(60,MirrorLinkRules.retryInterval(40,false));assertEquals(600,MirrorLinkRules.retryInterval(600,false));assertEquals(40,MirrorLinkRules.retryInterval(600,true));}
    @Test void pollutionUsesStrictOriginalThresholds(){assertFalse(MirrorLinkRules.pollutes(128,false));assertTrue(MirrorLinkRules.pollutes(129,false));assertFalse(MirrorLinkRules.pollutes(64,true));assertTrue(MirrorLinkRules.pollutes(65,true));}
    @Test void sourceEjectionBeginsAfterTwentyAndEssentiaIsSingleUnit(){assertFalse(MirrorLinkRules.canEject(20,1));assertTrue(MirrorLinkRules.canEject(21,1));assertFalse(MirrorLinkRules.canEject(21,0));assertTrue(MirrorLinkRules.acceptsEssentiaTransfer(1));assertFalse(MirrorLinkRules.acceptsEssentiaTransfer(2));assertFalse(MirrorLinkRules.acceptsEssentiaTransfer(0));}
}
