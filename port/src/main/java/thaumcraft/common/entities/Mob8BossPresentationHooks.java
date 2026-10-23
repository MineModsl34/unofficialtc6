package thaumcraft.common.entities;

import java.util.function.Consumer;
import thaumcraft.common.entities.monster.tainted.EntityTaintSwarm;
import thaumcraft.common.entities.monster.tainted.EntityTaintSeed;
import thaumcraft.common.entities.monster.boss.EntityEldritchWarden;

/** Sided hooks keep the dedicated server free of client particle classes. */
public final class Mob8BossPresentationHooks {
    public static Consumer<EntityTaintSwarm> swarmTick = entity -> {};
    public static Consumer<EntityTaintSeed> seedTick = entity -> {};
    public static Consumer<EntityEldritchWarden> wardenTick = entity -> {};
    private Mob8BossPresentationHooks() {}
}
