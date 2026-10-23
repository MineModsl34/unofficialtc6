package thaumcraft.common.golems.seals;
/** Pure bounds/timing shared with the native logistics menu. */
public final class SealRules {
 private SealRules(){}
 public static boolean refreshDue(long now,long previous){return previous==Long.MIN_VALUE||now<previous||now-previous>=20;}
 public static boolean requestThrottled(long now,long previous){return previous!=Long.MIN_VALUE&&now>=previous&&now-previous<10;}
 public static int countDelta(int current,int delta){return (int)Math.max(0,Math.min(1_000_000,(long)current+delta));}
 public static boolean metadataMatches(int left,int right,boolean ignoreDamage){return ignoreDamage||left==32767||right==32767||left==right;}
}
