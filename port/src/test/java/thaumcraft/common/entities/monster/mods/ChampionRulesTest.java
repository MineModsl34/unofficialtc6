package thaumcraft.common.entities.monster.mods;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChampionRulesTest {
    @Test void exactFourteenRoles(){assertArrayEquals(new int[]{-1,2,2,-1,1,0,1,0,1,1,1,1,2,0},ChampionRules.TYPES);}
    @Test void defaultNormalChanceIsOnePercent(){int successes=0;for(int roll=0;roll<100;roll++)if(ChampionRules.score(roll,false,false,true,false,false,0,false)<=0)successes++;assertEquals(1,successes);}
    @Test void hardAddsTwoPercentagePoints(){int count=0;for(int i=0;i<100;i++)if(ChampionRules.score(i,false,true,true,false,false,0,false)<=0)count++;assertEquals(3,count);}
    @Test void easyDisablesDefaultWhitelistRoll(){for(int i=0;i<100;i++)assertTrue(ChampionRules.score(i,true,false,true,false,false,0,false)>0);}
    @Test void disabledSettingStillAllowsSourceOuterSpookyEdge(){assertTrue(ChampionRules.score(0,false,true,false,false,false,9,false)>0);assertEquals(-2,ChampionRules.score(0,false,true,false,true,true,9,false));}
    @Test void sourceWhitelistValueOneAddsNoChance(){assertEquals(0,ChampionRules.score(0,false,false,true,false,false,1,false));assertEquals(-2,ChampionRules.score(0,false,false,true,false,false,3,false));}
    @Test void bossWhitelistBoostSurvivesDisabledFlag(){assertEquals(-6,ChampionRules.score(0,false,false,false,false,false,9,true));}
    @Test void sourceArmorOrderAndLifeStealMinimum(){assertEquals(19F/25F,ChampionRules.armor(1));assertEquals(76F,ChampionRules.armor(100));assertEquals(2,ChampionRules.vampire(1));assertEquals(5,ChampionRules.vampire(10));}
    @Test void shieldsUseIntegerBaseHealthNotChampionBonus(){assertEquals(25,ChampionRules.shield(50));assertEquals(25,ChampionRules.shield(51.9));}
    @Test void sourceLootBagAndCritterFormulas(){assertEquals(0,ChampionRules.lootTier(4,0));assertEquals(1,ChampionRules.lootTier(5,0));assertEquals(2,ChampionRules.lootTier(8,9));assertEquals(2,ChampionRules.critterDamage(.1F,.1F));assertEquals(5,ChampionRules.critterDamage(1.5F,1));}
}
