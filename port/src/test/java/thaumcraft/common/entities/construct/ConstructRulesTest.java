package thaumcraft.common.entities.construct;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConstructRulesTest {
    @Test void radiusUsesEnchantabilityAndDestructiveWithSourceMinimum(){assertEquals(2,ConstructRules.radius(0,0));assertEquals(2,ConstructRules.radius(5,0));assertEquals(5,ConstructRules.radius(15,0));assertEquals(9,ConstructRules.radius(15,2));}
    @Test void depthAndDelayPreserveSourceIntegerArithmetic(){assertEquals(40,ConstructRules.depth(5,0));assertEquals(72,ConstructRules.depth(5,2));assertEquals(6,ConstructRules.delay(3.9F,4));assertEquals(15,ConstructRules.delay(10.9F,3));assertEquals(1,ConstructRules.delay(.2F,30));}
    @Test void sourceChargeNarrowsOnlyWhenTheTailRemovalBranchActuallyRuns(){assertEquals(9,ConstructRules.paidCharge(10));assertEquals(0,ConstructRules.paidCharge(.25F));assertEquals(0,ConstructRules.paidCharge(.99F));assertEquals(19,ConstructRules.paidCharge(19.9F));}
    @Test void spiralIncrementTruncatesFloatAndSlowsAtOuterRadius(){assertEquals(23,ConstructRules.nextSpiral(0,0));assertEquals(19,ConstructRules.nextSpiral(0,2));assertEquals(9,ConstructRules.nextSpiral(0,7));assertEquals(3,ConstructRules.nextSpiral(0,12));assertEquals(360,ConstructRules.nextSpiral(357,10));}
    @Test void advancedMovementIsBothSourceOverridesMultiplied(){assertEquals(300,ConstructRules.movementDivisor(true,false));assertEquals(20,ConstructRules.movementDivisor(false,false));assertEquals(5,ConstructRules.movementDivisor(false,true));}
    @Test void flagsPreserveOtherBitsAndTeamFriendlySelectionIsAnEquality(){assertEquals(10,ConstructRules.flag(2,3,true));assertEquals(8,ConstructRules.flag(10,1,false));assertTrue(ConstructRules.teamAllowed(false,false,true));assertTrue(ConstructRules.teamAllowed(true,false,false));assertFalse(ConstructRules.teamAllowed(true,true,false));assertTrue(ConstructRules.teamAllowed(true,true,true));assertFalse(ConstructRules.teamAllowed(true,false,true));}
    @Test void arrowSpreadUsesSourceGaussianCoefficientAndFloatNorm(){double noise=.007499999832361937D*2;assertArrayEquals(new double[]{noise*2,(1-noise)*2,noise},ConstructRules.arrowHeading(0,1,0,2,2,1,-1,.5),1E-12);float length=(float)Math.sqrt(14);assertArrayEquals(new double[]{1D/length*2,2D/length*2,3D/length*2},ConstructRules.arrowHeading(1,2,3,2,0,99,99,99),1E-12);}
}
