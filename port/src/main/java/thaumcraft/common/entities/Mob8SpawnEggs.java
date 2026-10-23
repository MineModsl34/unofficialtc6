package thaumcraft.common.entities;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;

/** Actual source egg colors. Natural group's seven eggs are registered by its own item factory. */
public final class Mob8SpawnEggs {
    private Mob8SpawnEggs() {}
    public static Map<String,Item> createItems() {
        var result=new LinkedHashMap<String,Item>();
        egg(result,"cultist_portal_greater",6842578,32896);
        egg(result,"cultist_portal_lesser",9438728,6316242);
        egg(result,"eldritch_warden",6842578,8421504);
        egg(result,"eldritch_golem",6842578,8947848);
        egg(result,"cultist_leader",6842578,9438728);
        egg(result,"taintacle_giant",6842578,10618530);
        egg(result,"spellbat",-16129,-806354944);
        egg(result,"mind_spider",4996656,4473924);
        egg(result,"eldritch_guardian",8421504,0);
        egg(result,"cultist_knight",9438728,128);
        egg(result,"cultist_cleric",9438728,8388608);
        egg(result,"thaumic_slime",10618530,-32513);
        egg(result,"taint_crawler",10618530,3158064);
        egg(result,"taintacle",10618530,4469572);
        egg(result,"taint_swarm",10618530,16744576);
        egg(result,"taint_seed",10618530,4465237);
        egg(result,"taint_seed_prime",10618530,5583718);
        return result;
    }
    @SuppressWarnings("unchecked")
    private static void egg(Map<String,Item> result,String key,int primary,int secondary) {
        result.put(key+"_spawn_egg",new DeferredSpawnEggItem(()->{
            var id=ResourceLocation.fromNamespaceAndPath("thaumcraft",key);
            if(!BuiltInRegistries.ENTITY_TYPE.containsKey(id))throw new IllegalStateException("Missing genuine source egg type "+id);
            return (EntityType<? extends Mob>)BuiltInRegistries.ENTITY_TYPE.get(id);
        },primary,secondary,new Item.Properties()));
    }
}

