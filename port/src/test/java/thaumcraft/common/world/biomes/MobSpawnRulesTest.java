package thaumcraft.common.world.biomes;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MobSpawnRulesTest {
    private static final MobSpawnRules.Flags ALL=new MobSpawnRules.Flags(true,true,true,true,true);
    @Test void forestPechConstructorEntryIsOverwrittenNotDoubled(){var rows=MobSpawnRules.spawns("magical_forest",true,true,false,true,false,ALL);assertEquals(3,rows.size());assertEquals(10,rows.stream().filter(r->r.entity().equals("pech")).findFirst().orElseThrow().weight());assertEquals(20,rows.stream().filter(r->r.entity().equals("wisp")).findFirst().orElseThrow().weight());}
    @Test void eerieWeightsAndMagicalPostInit(){var rows=MobSpawnRules.spawns("eerie",false,true,false,true,false,ALL);assertEquals(5,rows.size());assertEquals(32,rows.get(0).weight());assertEquals(8,rows.get(1).weight());assertEquals(3,rows.get(2).weight());assertEquals(1,rows.get(3).weight());assertEquals(10,rows.get(4).weight());}
    @Test void eldritchGuardianIsUnconditional(){var rows=MobSpawnRules.spawns("eldritch",false,true,false,false,false,new MobSpawnRules.Flags(false,false,false,false,false));assertEquals(2,rows.size());assertEquals("eldritch_guardian",rows.get(1).entity());}
    @Test void ordinaryPoolOnlyGetsBrainy(){var rows=MobSpawnRules.spawns("",true,false,false,true,false,ALL);assertEquals(1,rows.size());assertEquals(new MobSpawnRules.Spawn("brainy_zombie",10,1,1),rows.get(0));}
    @Test void halloweenOverworldAndNetherDiffer(){var nether=MobSpawnRules.spawns("",false,false,true,true,false,ALL);assertEquals(2,nether.size());assertEquals(10,nether.get(0).weight());assertEquals(5,nether.get(1).weight());var halloween=MobSpawnRules.spawns("",true,false,false,true,true,ALL);assertEquals(5,halloween.get(1).weight());}
    @Test void emptyMonsterBiomesExcludedExceptSourceNetherHalloween(){assertTrue(MobSpawnRules.spawns("",true,true,false,false,false,ALL).isEmpty());assertEquals(1,MobSpawnRules.spawns("",true,false,false,false,true,ALL).size());}
    @Test void eachRuntimeFlagIsHonored(){assertTrue(MobSpawnRules.spawns("eerie",false,true,false,true,true,new MobSpawnRules.Flags(false,false,false,false,false)).isEmpty());}
}
