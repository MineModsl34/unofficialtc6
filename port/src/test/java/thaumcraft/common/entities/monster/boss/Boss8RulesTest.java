package thaumcraft.common.entities.monster.boss;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Boss8RulesTest {
    @Test void sourceHitCapAndEnrageAmplifiers(){assertEquals(35,Boss8Rules.cappedHit(80));assertEquals(12,Boss8Rules.cappedHit(12));assertEquals(5,Boss8Rules.regenerationAmplifier(80));assertEquals(8,Boss8Rules.strengthAmplifier(80));assertEquals(2,Boss8Rules.hasteAmplifier(80));}
    @Test void shieldUsesBaseHealthNotMultiplayerHealth(){assertEquals(264,Boss8Rules.shield(400));assertEquals(99,Boss8Rules.shield(150));}
    @Test void earlyPortalStagesUseRapidSourceDelay(){assertEquals(15,Boss8Rules.portalDelay(0,7,0));assertEquals(16,Boss8Rules.portalDelay(4,7,5));}
    @Test void latePortalStagesUseNearbyCultistCount(){assertEquals(60,Boss8Rules.portalDelay(5,3,0));assertEquals(130,Boss8Rules.portalDelay(12,2,0));assertEquals(62,Boss8Rules.portalDelay(13,3,2));}
    @Test void frenzyRingStartsAfterFirstThirtyTicks(){assertFalse(Boss8Rules.frenzyPulse(150));assertFalse(Boss8Rules.frenzyPulse(130));assertTrue(Boss8Rules.frenzyPulse(120));assertFalse(Boss8Rules.frenzyPulse(119));assertEquals(3.75,Boss8Rules.frenzyRadius(120));assertEquals(17.5,Boss8Rules.frenzyRadius(10));}
    @Test void aggroUsesBothStrictThresholdsAndSourceLastValue(){assertFalse(Boss8Rules.sourceAggroCandidate(125,100,0));assertTrue(Boss8Rules.sourceAggroCandidate(126,100,0));assertFalse(Boss8Rules.sourceAggroCandidate(126,100,126));assertFalse(Boss8Rules.sourceAggroCandidate(1070,1000,0));assertTrue(Boss8Rules.sourceAggroCandidate(1101,1000,0));}
}
