package thaumcraft.common.config;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.world.entity.monster.*;
import thaumcraft.common.entities.monster.EntityEldritchCrab;
import thaumcraft.common.entities.monster.EntityInhabitedZombie;
import thaumcraft.common.entities.monster.tainted.EntityTaintacle;

/** Source champion whitelist. Public class-based extension keeps the original assignable matching. */
public final class ConfigEntities {
    public static final Map<Class<?>,Integer> championModWhitelist=new LinkedHashMap<>();
    static {
        championModWhitelist.put(Zombie.class,0);championModWhitelist.put(Spider.class,0);
        championModWhitelist.put(Blaze.class,0);championModWhitelist.put(EnderMan.class,0);
        championModWhitelist.put(Skeleton.class,0);championModWhitelist.put(Witch.class,1);
        championModWhitelist.put(EntityEldritchCrab.class,0);championModWhitelist.put(EntityTaintacle.class,2);
        championModWhitelist.put(EntityInhabitedZombie.class,3);
    }
    private ConfigEntities() {}
}
