package thaumcraft.common.world.biomes;

import java.util.ArrayList;
import java.util.List;

/** Source biome constructors followed by Forge1.12 addSpawn (UPSERT, not additive duplicate weight). */
public final class MobSpawnRules {
    public record Spawn(String entity,int weight,int min,int max) {}
    public record Flags(boolean angry,boolean pech,boolean wisp,boolean elder,boolean firebat) {}
    private MobSpawnRules() {}
    public static List<Spawn> spawns(String tcBiome,boolean ordinaryPool,boolean magical,boolean nether,
                                     boolean inheritedMonsters,boolean halloween,Flags flags) {
        var list=new ArrayList<Spawn>();
        if(tcBiome.equals("magical_forest")){
            if(flags.pech)upsert(list,"pech",20,1,2);if(flags.wisp)upsert(list,"wisp",20,1,2);
        } else if(tcBiome.equals("eerie")) {
            if(flags.angry){upsert(list,"brainy_zombie",32,1,1);upsert(list,"giant_brainy_zombie",8,1,1);}
            if(flags.wisp)upsert(list,"wisp",3,1,1);if(flags.elder)upsert(list,"eldritch_guardian",1,1,1);
        } else if(tcBiome.equals("eldritch")) {
            upsert(list,"inhabited_zombie",1,1,1);upsert(list,"eldritch_guardian",1,1,1); // Source does NOT check elder flag here.
        }
        boolean hasMonsters=inheritedMonsters||!list.isEmpty();
        if(flags.angry&&ordinaryPool&&hasMonsters)upsert(list,"brainy_zombie",10,1,1);
        if(flags.pech&&magical&&hasMonsters)upsert(list,"pech",10,1,1);
        if(flags.firebat&&nether)upsert(list,"firebat",10,1,2);
        if(flags.firebat&&ordinaryPool&&halloween)upsert(list,"firebat",5,1,2);
        if(flags.wisp&&nether)upsert(list,"wisp",5,1,1);
        return List.copyOf(list);
    }
    private static void upsert(List<Spawn> list,String entity,int weight,int min,int max){
        for(int i=0;i<list.size();i++)if(list.get(i).entity.equals(entity)){list.set(i,new Spawn(entity,weight,min,max));return;}
        list.add(new Spawn(entity,weight,min,max));
    }
}
