package thaumcraft.common.entities;

/** Pure BETA26 arithmetic. Quirks are retained instead of normalizing to vanilla mobs. */
public final class Mob8NaturalRules {
    private Mob8NaturalRules() {}
    public static float giantAttack(float anger) { return 7 + (anger - 1) * 5; }
    public static float giantAnger(float previous) { return Math.min(2,previous + .1F); }
    public static int tradeTier(int value,int roll) { if(value<=0||roll<0||roll>=value)throw new IllegalArgumentException("Trade roll");return Math.min(5,Math.max((value+1)/2,roll+1)); }
    public static int pechValue(int explicit,int desire) { return explicit != 0 ? explicit : Math.min(32,desire/2); }
    public static boolean wispStationary(double x,double y,double z) { return Math.abs(x)<=.1F&&Math.abs(y)<=.1F&&Math.abs(z)<=.1F; }
    public static int pechWeapon(int roll) { if(roll<0||roll>=20)throw new IllegalArgumentException("Equipment roll");return switch(roll){case 0,12->1;case 2,4,10,11,13->2;default->0;}; }
    /** Exactly one destination: source empty-first order and strict '< max', without the original merge-loop dupe. */
    public static int rewardSlot(int[] counts,int[] limits,boolean[] same,int rewardCount){if(counts.length!=4||limits.length!=4||same.length!=4||rewardCount<=0)return -1;for(int i=0;i<4;i++)if(counts[i]==0&&rewardCount<=limits[i]||same[i]&&counts[i]>=0&&(long)counts[i]+rewardCount<limits[i])return i;return -1;}
    public static double[] arrowHeading(double x,double y,double z,float speed,float inaccuracy,double gx,double gy,double gz){float length=(float)Math.sqrt(x*x+y*y+z*z);if(length==0||!Float.isFinite(length))return new double[]{0,0,0};double spread=.007499999832361937D*inaccuracy;return new double[]{(x/length+gx*spread)*speed,(y/length+gy*spread)*speed,(z/length+gz*spread)*speed};}
}
