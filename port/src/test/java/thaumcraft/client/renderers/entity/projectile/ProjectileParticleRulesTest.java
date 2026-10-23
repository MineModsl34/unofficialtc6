package thaumcraft.client.renderers.entity.projectile;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ProjectileParticleRulesTest {
    @Test void noFixedThreeCoefficientsAreInvented(){assertFalse(ProjectileParticleRules.admit(0,0));assertFalse(ProjectileParticleRules.admit(.1F,0));assertTrue(ProjectileParticleRules.admit(.3F,0));assertFalse(ProjectileParticleRules.admit(.3F,.3F));}
    @Test void sourceWrapIsRelativeToLastRenderedFrame(){assertTrue(ProjectileParticleRules.admit(.1F,.8F));assertFalse(ProjectileParticleRules.admit(.1F,.95F));assertTrue(ProjectileParticleRules.admit(.9F,.1F));}
    @Test void originalDoublePointTwoComparisonIsNotChangedToFloatThreshold(){assertTrue(ProjectileParticleRules.admit(.2F,0));assertFalse(ProjectileParticleRules.admit(Math.nextDown(.2F),0));}
}
