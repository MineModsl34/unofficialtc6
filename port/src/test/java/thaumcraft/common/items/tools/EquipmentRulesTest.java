package thaumcraft.common.items.tools;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EquipmentRulesTest {
    @Test void axeHasSourceComponentClampAndVerticalOffset(){assertEquals(-.25,EquipmentRules.vacuumComponent(0,1,false));assertEquals(-.2,EquipmentRules.vacuumComponent(0,1,true),1E-9);assertEquals(.25,EquipmentRules.vacuumComponent(.2,-1,false));}
    @Test void swordDampsNegativeVelocityAndResetsExcessLift(){assertEquals(-.3/1.2F+.08F,EquipmentRules.swordLift(-.3),1E-9);assertEquals(.2F,EquipmentRules.swordLift(.5));assertEquals(.08F,EquipmentRules.swordLift(0));}
    @Test void grappleSoftensInsideEightAndBoundsZeroBeforeTimesFive(){assertEquals(80,EquipmentRules.grappleDivisor(4));assertEquals(40,EquipmentRules.grappleDivisor(8));assertEquals(50,EquipmentRules.grappleDivisor(10));assertEquals(5E-9,EquipmentRules.grappleDivisor(0));}
    @Test void allThreeShovelPlanesKeepSourceFaceYawMapping(){
        assertArrayEquals(new int[]{1,0,-1},EquipmentRules.shovelOffset(0,1,0,1,-1));assertArrayEquals(new int[]{1,-1,0},EquipmentRules.shovelOffset(0,2,0,1,-1));assertArrayEquals(new int[]{0,-1,1},EquipmentRules.shovelOffset(0,4,0,1,-1));
        assertArrayEquals(new int[]{1,-1,0},EquipmentRules.shovelOffset(1,1,0,1,-1));assertArrayEquals(new int[]{0,-1,1},EquipmentRules.shovelOffset(1,1,1,1,-1));assertArrayEquals(new int[]{1,0,-1},EquipmentRules.shovelOffset(2,2,0,1,-1));
    }
    @Test void warpSegmentsScaleTogetherAboveOneHundred(){assertArrayEquals(new int[]{48,0,0},EquipmentRules.sanitySegments(0,0,0));assertArrayEquals(new int[]{0,24,12},EquipmentRules.sanitySegments(50,50,100));assertArrayEquals(new int[]{24,9,9},EquipmentRules.sanitySegments(10,20,20));}
    @Test void crusherRetainsSourceDeviceMaterialsWithoutInventingFutureFamilies(){
        for(String path:new String[]{"arcane_workbench_charger","centrifuge","alembic","loot_crate_rare","table_wood","banner_crimson_cult","slab_greatwood"})assertTrue(EquipmentRules.crusherRejectsSourceWood("thaumcraft",path),path);
        for(String path:new String[]{"table_stone","loot_urn_rare","wand_workbench","taint_log","banner_future_addon"})assertFalse(EquipmentRules.crusherRejectsSourceWood("thaumcraft",path),path);
        assertFalse(EquipmentRules.crusherRejectsSourceWood("addon","centrifuge"));
    }
}
