/*
 * StandardizeList derived from Minecraft Forge, Copyright (c) 2016-2020.
 * This library is free software; you can redistribute it and/or modify it
 * under the GNU Lesser General Public License version 2.1.
 * Distributed without any warranty, including merchantability or fitness
 * for a particular purpose. See the GNU LGPL for full license terms.
 * https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html
 */
package thaumcraft.common.items.armor;

import java.util.*;
/**
 * Numerical Forge 1.12 ISpecialArmor adapter used by source fortress/void robes.
 * StandardizeList below retains Forge LGPL-2.1 source algorithm and priority/cap behavior.
 * Original: reference/mc-1.12.2/net/minecraftforge/common/ISpecialArmor.java.
 * Minecraft Forge Copyright (c) 2016-2020; LGPL version 2.1 or later.
 */
public final class SourceSpecialArmorRules {
    private static final boolean DEBUG=false;
    public static final class Piece implements Comparable<Piece> {
        public final int slot,priority,maximum;
        public double ratio;
        public Piece(int slot,int priority,double ratio,int maximum){this.slot=slot;this.priority=priority;this.ratio=ratio;this.maximum=maximum;}
        @Override public int compareTo(Piece other){if(other.priority!=priority)return other.priority-priority;double left=ratio==0?0:maximum*100D/ratio,right=other.ratio==0?0:other.maximum*100D/other.ratio;return (int)(left-right);}
    }
    public record Payment(int slot,int amount) {}
    public record Result(float damage,List<Integer> durability,List<Payment> payments) { public Result {durability=List.copyOf(durability);payments=List.copyOf(payments);} }
    private SourceSpecialArmorRules(){}
    /** Values are source armor attributes + source extra fortress Armor/Toughness, not doubled native gear. */
    public static Result reduce(float incoming,float armor,float toughness,List<Piece> input){
        if(!Float.isFinite(incoming)||incoming<=0)return new Result(0,List.of(0,0,0,0),List.of());
        Piece[] pieces=input.stream().map(p->new Piece(p.slot,p.priority,p.ratio,p.maximum)).toArray(Piece[]::new);
        if(pieces.length>4)throw new IllegalArgumentException("Four source armor slots");
        int[] durability=new int[4];var payments=new ArrayList<Payment>();double damage=incoming;
        if(pieces.length>0){
            StandardizeList(pieces,damage);int priority=pieces[0].priority;double ratio=0;
            for(Piece piece:pieces){
                if(piece.slot<0||piece.slot>3)throw new IllegalArgumentException("Source armor slot");
                if(priority!=piece.priority){damage-=damage*ratio;ratio=0;priority=piece.priority;}
                ratio+=piece.ratio;double absorb=damage*piece.ratio;if(absorb>0){int amount=(int)Math.max(1,absorb);durability[piece.slot]+=amount;payments.add(new Payment(piece.slot,amount));}
            }
            damage-=damage*ratio;
        }
        if(damage>0&&(armor>0||toughness>0)){
            int armorDamage=(int)Math.max(1,damage/4);
            // Source's second pass is inventory order, not the sorted absorption order.
            for(int slot=0;slot<4;slot++)for(Piece piece:pieces)if(piece.slot==slot){durability[slot]+=armorDamage;payments.add(new Payment(slot,armorDamage));break;}
            damage=WearableRules.afterArmor((float)damage,armor,toughness);
        }
        return new Result((float)damage,List.of(durability[0],durability[1],durability[2],durability[3]),payments);
    }
        private static void StandardizeList(Piece[] armor, double damage)
        {
            Arrays.sort(armor);

            int     start     = 0;
            double  total     = 0;
            int     priority  = armor[0].priority;
            int     pStart    = 0;
            boolean pChange   = false;
            boolean pFinished = false;

            if (DEBUG)
            {
                for (Piece prop : armor)
                {
                    System.out.println(prop);
                }
                System.out.println("========================");
            }

            for (int x = 0; x < armor.length; x++)
            {
                total += armor[x].ratio;
                if (x == armor.length - 1 || armor[x].priority != priority)
                {
                    if (armor[x].priority != priority)
                    {
                        total -= armor[x].ratio;
                        x--;
                        pChange = true;
                    }
                    if (total > 1)
                    {
                        for (int y = start; y <= x; y++)
                        {
                            double newRatio = armor[y].ratio / total;
                            if (newRatio * damage > armor[y].maximum)
                            {
                                armor[y].ratio = (double)armor[y].maximum / damage;
                                total = 0;
                                for (int z = pStart; z <= y; z++)
                                {
                                    total += armor[z].ratio;
                                }
                                start = y + 1;
                                x = y;
                                break;
                            }
                            else
                            {
                                armor[y].ratio = newRatio;
                                pFinished = true;
                            }
                        }
                        if (pChange && pFinished)
                        {
                            damage -= (damage * total);
                            total = 0;
                            start = x + 1;
                            priority = armor[start].priority;
                            pStart = start;
                            pChange = false;
                            pFinished = false;
                            if (damage <= 0)
                            {
                                for (int y = x + 1; y < armor.length; y++)
                                {
                                    armor[y].ratio = 0;
                                }
                                break;
                            }
                        }
                    }
                    else
                    {
                        for (int y = start; y <= x; y++)
                        {
                            total -= armor[y].ratio;
                            if (damage * armor[y].ratio > armor[y].maximum)
                            {
                                armor[y].ratio = (double)armor[y].maximum / damage;
                            }
                            total += armor[y].ratio;
                        }
                        damage -= (damage * total);
                        total = 0;
                        if (x != armor.length - 1)
                        {
                            start = x + 1;
                            priority = armor[start].priority;
                            pStart = start;
                            pChange = false;
                            if (damage <= 0)
                            {
                                for (int y = x + 1; y < armor.length; y++)
                                {
                                    armor[y].ratio = 0;
                                }
                                break;
                            }
                        }
                    }
                }
            }
            if (DEBUG)
            {
                for (Piece prop : armor)
                {
                    System.out.println(prop);
                }
            }
        }


}
