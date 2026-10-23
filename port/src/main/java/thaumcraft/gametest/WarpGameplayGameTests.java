package thaumcraft.gametest;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumcraft.Thaumcraft;
import thaumcraft.api.capabilities.IPlayerWarp.EnumWarpType;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.entities.WarpEntities;
import thaumcraft.common.lib.capabilities.PlayerDataSync;
import thaumcraft.common.lib.network.playerdata.PacketSyncWarp;
import thaumcraft.common.lib.network.playerdata.PacketWarpMessage;
import thaumcraft.common.lib.potions.WarpEffects;
import thaumcraft.common.lib.warp.WarpGameplay;

/** Actual registered effects/entities, storage and owner payload path. Not TCP/render acceptance. */
@GameTestHolder(Thaumcraft.MODID)
@PrefixGameTestTemplate(false)
public final class WarpGameplayGameTests {
    private WarpGameplayGameTests() {}
    private static ServerPlayer player(GameTestHelper helper, String name) {
        return FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
    }

    @GameTest(template = "empty")
    public static void warpMutationRetainsUnderflowSignCounterAndOwnerPayloadOrder(GameTestHelper helper) throws Exception {
        var owner = player(helper, "TC_Warp_Mutate");
        var data = ThaumcraftCapabilities.getWarp(owner);
        data.set(EnumWarpType.PERMANENT, 3);
        data.set(EnumWarpType.NORMAL, 4);
        data.set(EnumWarpType.TEMPORARY, 2);
        data.setCounter(700);
        List<CustomPacketPayload> packets = new ArrayList<>();
        try (var capture = PlayerDataSync.captureGameTestPackets((recipient, packet) -> {
            helper.assertTrue(recipient == owner, "warp payload is owner-only"); packets.add(packet);
        })) {
            WarpGameplay.addWarpToPlayer(owner, -5, EnumWarpType.NORMAL);
            helper.assertTrue(data.get(EnumWarpType.NORMAL) == 8, "source underflow intentionally changes -5 to +current4");
            helper.assertTrue(data.getCounter() == 13, "positive adjusted amount resets counter to all three totals");
            helper.assertTrue(packets.size() == 2 && packets.get(0).equals(new PacketWarpMessage(4, (byte) 1)), "source feedback precedes owner storage snapshot");
            helper.assertTrue(packets.get(1) instanceof PacketSyncWarp snapshot && snapshot.data().getInt("counter") == 13, "snapshot is post-mutation");
            packets.clear();
            WarpGameplay.addWarpToPlayer(owner, -1, EnumWarpType.TEMPORARY);
            helper.assertTrue(data.get(EnumWarpType.TEMPORARY) == 1 && data.getCounter() == 13, "ordinary decrease does not reset counter");
            helper.assertTrue(packets.get(0).equals(new PacketWarpMessage(-1, (byte) 2)), "negative temporary feedback still transmitted though source client remains silent");
            packets.clear();
            WarpGameplay.addWarpToPlayer(owner, 0, EnumWarpType.PERMANENT);
            helper.assertTrue(packets.isEmpty(), "zero API input does nothing");
        } finally { PlayerDataSync.forget(owner); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nonTemporaryWarpUnlocksSourceWarpResearchOnlyAfterFirstSteps(GameTestHelper helper) throws Exception {
        var owner = player(helper, "TC_Warp_Unlock");
        var knowledge = ThaumcraftCapabilities.getKnowledge(owner);
        try (var capture = PlayerDataSync.captureGameTestPackets((recipient, packet) -> {})) {
            WarpGameplay.addWarpToPlayer(owner, 1, EnumWarpType.NORMAL);
            helper.assertFalse(knowledge.isResearchKnown("WARP"), "no warp research before strict FIRSTSTEPS");
            knowledge.addResearch("FIRSTSTEPS");
            knowledge.setResearchStage("FIRSTSTEPS", 100); // Explicit isolated completed-player fixture only.
            WarpGameplay.addWarpToPlayer(owner, 1, EnumWarpType.TEMPORARY);
            helper.assertFalse(knowledge.isResearchKnown("WARP"), "temporary warp alone does not unlock source WARP");
            int xp = owner.totalExperience;
            WarpGameplay.addWarpToPlayer(owner, 1, EnumWarpType.NORMAL);
            helper.assertTrue(knowledge.isResearchComplete("WARP"), "source one-stage WARP completed by real trusted progression");
            helper.assertTrue(owner.totalExperience == xp + 5, "source stage skip completes one-stage WARP for five XP");
            WarpGameplay.addWarpToPlayer(owner, 1, EnumWarpType.PERMANENT);
            helper.assertTrue(owner.totalExperience == xp + 5, "already-completed WARP not awarded again");
        } finally { PlayerDataSync.forget(owner); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void registeredWarpEffectsRetainCadenceAndUnnaturalHungerRottenFleshCure(GameTestHelper helper) {
        var owner = player(helper, "TC_Warp_Hunger");
        helper.assertTrue(WarpEffects.UNNATURAL_HUNGER.value().shouldApplyEffectTickThisTick(17, 2), "source hunger runs every tick");
        helper.assertTrue(WarpEffects.INFECTIOUS_VIS_EXHAUST.value().shouldApplyEffectTickThisTick(80, 2), "infectious source every40 duration");
        helper.assertFalse(WarpEffects.INFECTIOUS_VIS_EXHAUST.value().shouldApplyEffectTickThisTick(79, 2), "not every arbitrary tick");
        helper.assertTrue(WarpEffects.THAUMARHIA.value().shouldApplyEffectTickThisTick(20, 0), "thaumarhia source every20 duration");
        WarpGameplay.addEffect(owner, WarpEffects.UNNATURAL_HUNGER, 5000, 2, true, true, true);
        helper.assertTrue(owner.getEffect(WarpEffects.UNNATURAL_HUNGER).getCures().isEmpty(), "source event hunger cannot be cured by milk");
        WarpGameplay.finishedUsingItem(owner, new ItemStack(Items.ROTTEN_FLESH));
        MobEffectInstance reduced = owner.getEffect(WarpEffects.UNNATURAL_HUNGER);
        helper.assertTrue(reduced.getAmplifier() == 1 && reduced.getDuration() == 4400 && !reduced.isVisible(), "source cure removes one level and600ticks then hides particles");
        helper.assertTrue(reduced.getCures().isEmpty(), "cure does not accidentally restore milk cure");
        WarpGameplay.finishedUsingItem(owner, new ItemStack(Items.ROTTEN_FLESH));
        WarpGameplay.finishedUsingItem(owner, new ItemStack(Items.ROTTEN_FLESH));
        helper.assertFalse(owner.hasEffect(WarpEffects.UNNATURAL_HUNGER), "final level cured entirely");
        owner.removeAllEffects();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void actualMindSpiderRetainsSourceAttributesViewerPersistenceAndHarmlessAttack(GameTestHelper helper) {
        var spider = WarpEntities.MIND_SPIDER.create(helper.getLevel());
        helper.assertTrue(spider != null && spider.getMaxHealth() == 1.0F, "actual registered TC mind spider has source one HP");
        helper.assertTrue(spider.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) == 1.0, "source one damage");
        spider.setViewer("TC_Warp_Viewer");
        spider.setHarmless(true);
        var nbt = new net.minecraft.nbt.CompoundTag();
        spider.addAdditionalSaveData(nbt);
        var restored = WarpEntities.MIND_SPIDER.create(helper.getLevel());
        restored.readAdditionalSaveData(nbt);
        helper.assertTrue(restored.isHarmless() && restored.getViewer().equals("TC_Warp_Viewer"), "source harmless/viewer NBT retained");
        helper.assertFalse(restored.doHurtTarget(player(helper, "TC_Warp_SpiderTarget")), "hallucination cannot damage its target");
        restored.moveTo(helper.absolutePos(new net.minecraft.core.BlockPos(1,1,1)).getCenter());
        restored.setNoAi(true); restored.setNoGravity(true);
        // In 1.21 ServerLevel's outer ticker increments age, not Entity.tick itself.
        restored.tickCount = 1199;
        helper.getLevel().tickNonPassenger(restored);
        helper.assertTrue(restored.tickCount == 1200 && !restored.isRemoved(), "source harmless spider remains alive at exact tick1200");
        helper.getLevel().tickNonPassenger(restored);
        helper.assertTrue(restored.isRemoved(), "source harmless spider expires after tick1200");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sourceBrainIsEdibleRegisteredWarpCureWithoutInadvertentHungerEffect(GameTestHelper helper) {
        helper.assertTrue(ItemsTC.brain != null, "real source brain dependency registered");
        var stack = new ItemStack(ItemsTC.brain);
        var food = stack.getFoodProperties(player(helper, "TC_Warp_BrainFood"));
        helper.assertTrue(food != null && food.nutrition() == 4 && Math.abs(food.saturation() - 1.6F) < 0.0001, "source four food/.2 saturation modifier");
        helper.assertTrue(food.effects().isEmpty(), "source onFoodEaten override suppresses constructor-only Hunger effect");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void guardianRetainsSourceAttributesMagicResistanceHomeAndEldritchAllegiance(GameTestHelper helper) {
        var guardian = WarpEntities.ELDRITCH_GUARDIAN.create(helper.getLevel());
        helper.assertTrue(guardian.getMaxHealth() == 50 && guardian.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) == 7,
            "actual source guardian HP50/damage7, not vanilla substitute");
        helper.assertTrue(guardian.getArmorValue() == 4 && Math.abs(guardian.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) - 0.28) < 0.00001,
            "source armor4/speed.28");
        guardian.hurt(helper.getLevel().damageSources().magic(), 10);
        helper.assertTrue(guardian.getHealth() == 45, "source magic damage halves before vanilla handling");
        var home = helper.absolutePos(new net.minecraft.core.BlockPos(1, 1, 1));
        guardian.restrictTo(home, 16);
        var tag = new net.minecraft.nbt.CompoundTag(); guardian.addAdditionalSaveData(tag);
        var restored = WarpEntities.ELDRITCH_GUARDIAN.create(helper.getLevel()); restored.readAdditionalSaveData(tag);
        helper.assertTrue(restored.hasRestriction() && restored.getRestrictCenter().equals(home) && restored.getRestrictRadius() == 16,
            "source HomeD/X/Y/Z survives actual entity NBT");
        helper.assertFalse(restored.removeWhenFarAway(100000), "home guardian does not despawn");
        helper.assertTrue(restored.isAlliedTo(WarpEntities.MIND_SPIDER.create(helper.getLevel())), "source IEldritchMob allied marker");
        restored.handleEntityEvent((byte)17);
        helper.assertTrue(restored.armLiftL == 0.9F && restored.armLiftR == 0.9F, "source both-arm event17");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void actualEldritchOrbRetainsMagicAreaDamageWeaknessUndeadExclusionAndLifetime(GameTestHelper helper) {
        var shooter = helper.spawn(WarpEntities.ELDRITCH_GUARDIAN, 2, 2, 2); shooter.setNoAi(true);
        var living = helper.spawn(net.minecraft.world.entity.EntityType.VILLAGER, 3, 2, 2); living.setNoAi(true);
        var undead = helper.spawn(net.minecraft.world.entity.EntityType.ZOMBIE, 3, 2, 3); undead.setNoAi(true);
        var position = helper.absoluteVec(new net.minecraft.world.phys.Vec3(3, 2, 2));
        var orb = new thaumcraft.common.entities.projectile.EntityEldritchOrb(helper.getLevel(), shooter);
        orb.setPos(position); orb.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        float shooterHealth = shooter.getHealth(), undeadHealth = undead.getHealth(), livingHealth = living.getHealth();
        orb.onHit(new net.minecraft.world.phys.EntityHitResult(living));
        helper.assertTrue(orb.isRemoved(), "source owned orb removed after impact");
        helper.assertTrue(Math.abs(livingHealth - living.getHealth() - 7 * 0.666F) < 0.0001,
            "source AoE is 0.666 * thrower attack attribute, actual indirect magic path");
        var weakness = living.getEffect(net.minecraft.world.effect.MobEffects.WEAKNESS);
        helper.assertTrue(weakness != null && weakness.getDuration() == 160 && weakness.getAmplifier() == 0, "source weakness160");
        helper.assertTrue(shooter.getHealth() == shooterHealth && undead.getHealth() == undeadHealth && !undead.hasEffect(net.minecraft.world.effect.MobEffects.WEAKNESS),
            "source shooter and undead both excluded from area damage/effects");
        var expired = WarpEntities.ELDRITCH_ORB.create(helper.getLevel());
        expired.setPos(helper.absoluteVec(new net.minecraft.world.phys.Vec3(2, 6, 2))); expired.tickCount = 99;
        helper.getLevel().tickNonPassenger(expired);
        helper.assertTrue(expired.tickCount == 100 && !expired.isRemoved(), "source orb remains alive at exact100ticks");
        helper.getLevel().tickNonPassenger(expired);
        helper.assertTrue(expired.isRemoved(), "source orb expires only above100ticks");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void infectiousExhaustUsesActualFourBlockSpreadAndDecreasingAmplifier(GameTestHelper helper) {
        var source = helper.spawn(net.minecraft.world.entity.EntityType.VILLAGER, 2, 2, 2); source.setNoAi(true);
        var target = helper.spawn(net.minecraft.world.entity.EntityType.VILLAGER, 3, 2, 2); target.setNoAi(true);
        WarpGameplay.addEffect(source, WarpEffects.INFECTIOUS_VIS_EXHAUST, 6000, 2, false, true, true);
        WarpEffects.INFECTIOUS_VIS_EXHAUST.value().applyEffectTick(source, 2);
        var spread = target.getEffect(WarpEffects.INFECTIOUS_VIS_EXHAUST);
        helper.assertTrue(spread != null && spread.getDuration() == 6000 && spread.getAmplifier() == 1, "source amplification decreases with each actual infection");
        helper.assertTrue(!spread.getCures().isEmpty(), "newly spread exhaust retains source default milk cure unlike event owner");
        target.removeEffect(WarpEffects.INFECTIOUS_VIS_EXHAUST);
        WarpEffects.INFECTIOUS_VIS_EXHAUST.value().applyEffectTick(source, 0);
        helper.assertTrue(target.hasEffect(WarpEffects.VIS_EXHAUST) && !target.hasEffect(WarpEffects.INFECTIOUS_VIS_EXHAUST), "source final infection is plain exhaust");
        helper.succeed();
    }
}
