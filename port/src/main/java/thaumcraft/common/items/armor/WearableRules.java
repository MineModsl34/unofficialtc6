package thaumcraft.common.items.armor;
/** Pure source arithmetic; useful without engine bootstrap. */
public final class WearableRules {
    private WearableRules(){}
    public static float fallDamage(float incoming,boolean boots,boolean ring){float value=incoming;if(boots){value=Math.max(0,value/2-1);if(value<1)value=0;}if(ring){float reduced=Math.max(0,value/3-2);if(reduced<1)value=0;else if(reduced<value)value=reduced;if(value<1)value=0;}return value;}
    public static int curiosityAward(int orb,float random){int half=orb/2;return random<.05*half?2:random<.2*half?1:0;}
    public static int energy(int previous,boolean consumed){return previous>0?previous-1:consumed?60:previous;}
    public static float cloudJump(int boostAmplifier){return .75F+(boostAmplifier>=0?(boostAmplifier+1)*.1F:0);}
    public static int armorDisplay(int base,int fortressPieces,int masks,int voidRobeDefense){return base+(fortressPieces>0?fortressPieces*(1+masks):0)+voidRobeDefense;}
    public static float afterArmor(float damage,float armor,float toughness){float factor=2+toughness/4;float effective=Math.max(armor*.2F,Math.min(20,armor-damage/factor));return damage*(1-effective/25);}
}
