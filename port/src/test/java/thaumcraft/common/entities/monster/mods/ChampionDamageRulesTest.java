package thaumcraft.common.entities.monster.mods;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChampionDamageRulesTest {
    @Test void nativeReductionCallbackIsDeferredAndReceivesCooldownDelta() {
        var container=new net.neoforged.neoforge.common.damagesource.DamageContainer(null,20);
        var calls=new java.util.concurrent.atomic.AtomicInteger();
        container.addModifier(net.neoforged.neoforge.common.damagesource.DamageContainer.Reduction.ARMOR,(state,reduction)->{
            calls.incrementAndGet();assertEquals(15,state.getNewDamage());
            float modified=ChampionRules.armor(state.getNewDamage());
            state.setNewDamage(modified);return ChampionDamageRules.bridgeArmorReduction(15,modified,0,0,reduction);
        });
        assertEquals(0,calls.get());
        container.setReduction(net.neoforged.neoforge.common.damagesource.DamageContainer.Reduction.INVULNERABILITY,5);
        assertEquals(0,calls.get());
        container.setReduction(net.neoforged.neoforge.common.damagesource.DamageContainer.Reduction.ARMOR,0);
        assertEquals(1,calls.get());assertEquals(11.4F,container.getNewDamage(),.00001F);
    }
    @Test void armorIsAppliedToSourceModifiedAdmittedDamage() {
        float admitted=20,modified=ChampionRules.armor(admitted);
        float previous=ChampionDamageRules.armorReduction(admitted,10,2);
        float result=ChampionDamageRules.bridgeArmorReduction(admitted,modified,10,2,previous);
        assertEquals(ChampionDamageRules.armorReduction(modified,10,2),result);
        assertNotEquals((admitted-previous)*19F/25F,modified-result);
    }
    @Test void retainsAlreadyEvaluatedPenetrationFraction() {
        float admitted=20,modified=15.2F;
        float nativeReduction=ChampionDamageRules.armorReduction(admitted,10,2);
        float result=ChampionDamageRules.bridgeArmorReduction(admitted,modified,10,2,nativeReduction-2);
        assertEquals(ChampionDamageRules.armorReduction(modified,10,2)-modified*.1F,result,.00001F);
    }
    @Test void preservesEarlierAddonFractionAndLaterCallbackInput() {
        float admitted=10,modified=ChampionRules.armor(admitted);
        float result=ChampionDamageRules.bridgeArmorReduction(admitted,modified,5,0,
            ChampionDamageRules.armorReduction(admitted,5,0)+1);
        assertEquals(ChampionDamageRules.armorReduction(modified,5,0)+modified/admitted,result,.00001F);
        assertEquals(7.6F,modified,.00001F); // LOW source-special-armor sees this, not the original ten.
    }
    @Test void admittedCooldownDeltaIsNotOriginalIncomingHit() {
        float delta=3,modified=ChampionRules.armor(delta);
        float result=ChampionDamageRules.bridgeArmorReduction(delta,modified,0,0,0);
        assertEquals(2.28F,modified-result,.00001F);
        assertEquals(2,ChampionRules.vampire(modified));
    }
    @Test void zeroAndUnmodifiedInputsDoNotInventArmorHooks() {
        assertEquals(2,ChampionDamageRules.bridgeArmorReduction(0,0,20,8,2));
        assertEquals(2,ChampionDamageRules.bridgeArmorReduction(5,5,20,8,2));
        assertEquals(0,ChampionDamageRules.armorReduction(10,0,0));
    }
    @Test void matchesNativeClampOperationOrderAtBothBounds() {
        assertEquals(16F,ChampionDamageRules.armorReduction(100,20,0),.00001F);
        assertEquals(8F,ChampionDamageRules.armorReduction(10,40,20),.00001F);
    }
}
