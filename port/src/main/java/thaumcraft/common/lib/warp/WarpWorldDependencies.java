package thaumcraft.common.lib.warp;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.common.entities.monster.EntityMindSpider;

/** Original 50-attempt warp placement. Actual TC registered entities only; no vanilla substitutes. */
public final class WarpWorldDependencies {
    private WarpWorldDependencies() {}

    private static Mob create(ServerPlayer player, String key) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("thaumcraft", key);
        if (!BuiltInRegistries.ENTITY_TYPE.containsKey(id)) throw new IllegalStateException("Missing source warp entity " + id);
        Entity entity = BuiltInRegistries.ENTITY_TYPE.get(id).create(player.level());
        if (!(entity instanceof Mob mob)) throw new IllegalStateException("Source warp entity is not a mob: " + id);
        return mob;
    }

    public static void spawnGuardian(ServerPlayer player) {
        Mob guardian = create(player, "eldritch_guardian");
        if (place(player, guardian, false)) {
            guardian.setTarget(player);
            player.level().addFreshEntity(guardian);
        }
    }

    public static boolean spawnPortal(ServerPlayer player) {
        Mob portal = create(player, "cultist_portal_lesser");
        if (!place(player, portal, true)) return false;
        portal.finalizeSpawn(player.serverLevel(), player.level().getCurrentDifficultyAt(portal.blockPosition()), MobSpawnType.EVENT, null);
        player.level().addFreshEntity(portal);
        return true;
    }

    public static void suddenlySpiders(ServerPlayer player, int warp, boolean real) {
        for (int i = 0; i < Math.min(50, warp); i++) {
            EntityMindSpider spider = (EntityMindSpider) create(player, "mind_spider");
            if (!place(player, spider, false)) continue;
            spider.setTarget(player);
            if (!real) {
                spider.setViewer(player.getName().getString());
                spider.setHarmless(true);
            }
            player.level().addFreshEntity(spider);
        }
    }

    private static boolean place(ServerPlayer player, Mob mob, boolean portal) {
        var world = player.level();
        var random = world.getRandom();
        int x = Mth.floor(player.getX()), y = Mth.floor(player.getY()), z = Mth.floor(player.getZ());
        for (int attempt = 0; attempt < 50; attempt++) {
            int xx = x + Mth.nextInt(random, 7, 24) * Mth.nextInt(random, -1, 1);
            int yy = y + Mth.nextInt(random, 7, 24) * Mth.nextInt(random, -1, 1);
            int zz = z + Mth.nextInt(random, 7, 24) * Mth.nextInt(random, -1, 1);
            BlockPos ground = new BlockPos(xx, yy - 1, zz);
            if (portal) mob.setPos(xx + 0.5, yy + 1.0, zz + 0.5);
            var state = world.getBlockState(ground);
            if (portal ? !state.isSolidRender(world, ground) : !state.isCollisionShapeFullBlock(world, ground)) continue;
            if (!portal) mob.setPos(xx, yy, zz);
            if (world.isUnobstructed(mob) && world.noCollision(mob, mob.getBoundingBox())
                && !world.containsAnyLiquid(mob.getBoundingBox())) return true;
        }
        return false;
    }

    public static void placeFluxGoo(LivingEntity target) {
        if (BlocksTC.fluxGoo == null) throw new IllegalStateException("Source warp flux_goo is not registered");
        target.level().setBlockAndUpdate(target.blockPosition(), BlocksTC.fluxGoo.defaultBlockState());
    }
}
