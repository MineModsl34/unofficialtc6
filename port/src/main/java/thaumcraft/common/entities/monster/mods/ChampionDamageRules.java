package thaumcraft.common.entities.monster.mods;

/** Pure native-armor arithmetic; no second enchantment-effect/RNG or durability hook. */
public final class ChampionDamageRules {
    private ChampionDamageRules() {}

    /** The deterministic portion of 1.21 CombatRules.getDamageAfterAbsorb, without enchantments. */
    public static float armorReduction(float damage,float armor,float toughness) {
        float divisor=2.0F+toughness/4.0F;
        float protection=Math.min(20.0F,Math.max(armor*.2F,armor-damage/divisor))/25.0F;
        return damage-damage*(1.0F-protection);
    }

    /**
     * Re-evaluate native toughness at source-modified admitted damage, retaining the
     * already-evaluated enchantment/addon effect as a protection-fraction delta.
     * This deliberately does not invoke CombatRules' RNG-capable enchantment hook twice.
     * Later ARMOR callbacks receive both this reduction and the modified admitted damage.
     */
    public static float bridgeArmorReduction(float admitted,float modified,float armor,float toughness,float previousReduction) {
        if(admitted<=0.0F||admitted==modified)return previousReduction;
        float previousEffect=previousReduction-armorReduction(admitted,armor,toughness);
        return armorReduction(modified,armor,toughness)+previousEffect*(modified/admitted);
    }
}
