package thaumcraft.common.golems.seals;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SealRulesTest {
 @Test void firstRefreshDoesNotOverflowAtNormalWorldTime(){assertTrue(SealRules.refreshDue(0,Long.MIN_VALUE));assertTrue(SealRules.refreshDue(1000,Long.MIN_VALUE));assertTrue(SealRules.refreshDue(Long.MAX_VALUE,Long.MIN_VALUE));}
 @Test void firstRequestIsNotThrottledBySentinel(){assertFalse(SealRules.requestThrottled(0,Long.MIN_VALUE));assertFalse(SealRules.requestThrottled(1000,Long.MIN_VALUE));}
 @Test void refreshHasSourceTwentyTickInterval(){assertFalse(SealRules.refreshDue(119,100));assertTrue(SealRules.refreshDue(120,100));}
 @Test void requestHasTenTickBound(){assertTrue(SealRules.requestThrottled(109,100));assertFalse(SealRules.requestThrottled(110,100));}
 @Test void timeReversalDoesNotPermanentlyDisableMenu(){assertTrue(SealRules.refreshDue(50,100));assertFalse(SealRules.requestThrottled(50,100));}
 @Test void ghostCountCannotOverflow(){assertEquals(1_000_000,SealRules.countDelta(Integer.MAX_VALUE,Integer.MAX_VALUE));assertEquals(0,SealRules.countDelta(1,Integer.MIN_VALUE));}
 @Test void damageComparisonIsIndependentFromOtherComponents(){assertTrue(SealRules.metadataMatches(12,50,true));assertFalse(SealRules.metadataMatches(12,50,false));assertTrue(SealRules.metadataMatches(32767,50,false));assertTrue(SealRules.metadataMatches(12,32767,false));assertTrue(SealRules.metadataMatches(12,12,false));}
 @Test void nativeNbtProjectionUsesOnlyOverridesAndExcludesDamageWithoutMutatingStacks()throws Exception{String source=java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/thaumcraft/common/golems/seals/SealInventoryOps.java"));String projection=source.substring(source.indexOf("private static boolean componentsEqualExceptDamage"),source.indexOf("private static boolean oreMatch"));assertTrue(projection.contains("a.getComponentsPatch().forget(type->type==DataComponents.DAMAGE)"));assertTrue(projection.contains("b.getComponentsPatch().forget(type->type==DataComponents.DAMAGE)"));assertFalse(projection.contains("getComponents()"));assertFalse(projection.contains(".remove("));}
 @Test void sourceOreEarlyMatchRequiresBareSecondOperandRegardlessIgnoreNbtFlag()throws Exception{String source=java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/thaumcraft/common/golems/seals/SealInventoryOps.java"));String branch=source.substring(source.indexOf("if(f.useOre"),source.indexOf("var x=LegacyScanIdentity"));assertTrue(branch.contains("b.getComponentsPatch().forget(type->type==DataComponents.DAMAGE).isEmpty()"));assertTrue(branch.contains("oreMatch(a,b)"));assertFalse(branch.contains("f.igNBT"));assertFalse(branch.contains("a.getComponentsPatch()"));}
}
