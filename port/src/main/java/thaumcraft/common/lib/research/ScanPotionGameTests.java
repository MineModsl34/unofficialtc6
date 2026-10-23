package thaumcraft.common.lib.research;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumcraft.Thaumcraft;
import thaumcraft.api.capabilities.IPlayerKnowledge.EnumResearchFlag;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ResearchEvent;
import thaumcraft.api.research.ScanningManager;
import thaumcraft.common.lib.capabilities.PlayerDataSync;
import thaumcraft.common.lib.network.playerdata.PacketSyncKnowledge;

/** Actual registered effect handlers and production dispatch, without replacing scanner/catalog lists. */
@GameTestHolder(Thaumcraft.MODID)
@PrefixGameTestTemplate(false)
public final class ScanPotionGameTests {
    private static ServerPlayer player(GameTestHelper helper, String name) {
        return FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
    }

    private static ItemStack basePotion(net.minecraft.world.item.Item item) {
        return PotionContents.createItemStack(item, Potions.SWIFTNESS);
    }

    private static ItemStack mixedPotion(net.minecraft.world.item.Item item) {
        ItemStack stack = basePotion(item);
        stack.set(DataComponents.POTION_CONTENTS, stack.get(DataComponents.POTION_CONTENTS)
            .withEffectAdded(new MobEffectInstance(MobEffects.POISON, 0, 5, true, false)));
        return stack;
    }

    @GameTest(template = "empty")
    public static void activeLivingEffectsMatchByStableKeyWithoutDurationAmplifierOrParticleFiltering(GameTestHelper helper) {
        var scanner = new ScanPotion(MobEffects.MOVEMENT_SPEED);
        var pig = EntityType.PIG.create(helper.getLevel());
        helper.assertTrue(pig != null, "actual living fixture exists");
        helper.assertFalse(scanner.checkThing(null, pig), "no active speed initially");
        pig.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 120, 3, true, false));
        helper.assertTrue(scanner.checkThing(null, pig), "active amplified/hidden speed matches");
        helper.assertTrue(scanner.getResearchKey(null, pig).equals("!effect.moveSpeed") && scanner.sourceProvenName(),
            "source getName marker is not modern effect.minecraft.speed");
        helper.assertFalse(new ScanPotion(MobEffects.POISON).checkThing(null, pig), "unrelated active effect absent");
        pig.removeEffect(MobEffects.MOVEMENT_SPEED);
        helper.assertFalse(scanner.checkThing(null, pig), "removed active effect no longer matches");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void baseAndCustomContentsScanPotionSplashLingeringTippedArrowAndArbitraryItems(GameTestHelper helper) {
        var speed = new ScanPotion(MobEffects.MOVEMENT_SPEED);
        var poison = new ScanPotion(MobEffects.POISON);
        for (var item : List.of(Items.POTION, Items.SPLASH_POTION, Items.LINGERING_POTION, Items.TIPPED_ARROW)) {
            ItemStack base = basePotion(item);
            helper.assertTrue(speed.checkThing(null, base), "base potion effect in " + item);
            helper.assertFalse(poison.checkThing(null, base), "base does not invent poison");
            ItemStack mixed = mixedPotion(item);
            helper.assertTrue(speed.checkThing(null, mixed) && poison.checkThing(null, mixed),
                "base plus custom includes both, even custom duration0/hidden/amplifier5");
        }
        ItemStack arbitrary = new ItemStack(Items.IRON_AXE);
        arbitrary.set(DataComponents.POTION_CONTENTS, new PotionContents(Optional.empty(), Optional.of(0xFFFFFF),
            List.of(new MobEffectInstance(MobEffects.POISON, 40))));
        helper.assertTrue(poison.checkThing(null, arbitrary), "source PotionUtils read potion NBT on any nonempty item, not only PotionItem");
        helper.assertFalse(speed.checkThing(null, arbitrary), "custom-only item has no base effect");
        helper.assertFalse(speed.checkThing(null, PotionContents.createItemStack(Items.POTION, Potions.AWKWARD)), "awkward has no effect");
        helper.assertFalse(speed.checkThing(null, ItemStack.EMPTY), "empty item has no effect");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void itemEntitiesScanButLivingHeldItemsAndProjectileEntityContentsDoNotBecomeItemTargets(GameTestHelper helper) {
        var speed = new ScanPotion(MobEffects.MOVEMENT_SPEED);
        var owner = player(helper, "TC_Effect_Held");
        owner.setItemInHand(InteractionHand.MAIN_HAND, basePotion(Items.POTION));
        helper.assertFalse(speed.checkThing(owner, owner), "living branch is active effects only, never its held potion");
        var dropped = new ItemEntity(helper.getLevel(), 0, 0, 0, basePotion(Items.SPLASH_POTION));
        helper.assertTrue(speed.checkThing(owner, dropped), "original item-helper branch resolves dropped stack");
        var bottle = new ThrownPotion(helper.getLevel(), 0, 0, 0);
        bottle.setItem(basePotion(Items.SPLASH_POTION));
        helper.assertFalse(speed.checkThing(owner, bottle), "flying bottle is not an ItemEntity/item-helper target");
        var arrow = new Arrow(helper.getLevel(), 0, 0, 0, basePotion(Items.TIPPED_ARROW), null);
        helper.assertFalse(speed.checkThing(owner, arrow), "flying tipped arrow is not an ItemEntity/item-helper target");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void defaultEffectScannersRecordBothMarkersOnceThenFlushOnlyOwnerWithoutComplexResearchXpOrFlags(GameTestHelper helper) throws Exception {
        var owner = player(helper, "TC_Effect_Once");
        var other = player(helper, "TC_Effect_Other");
        var data = ThaumcraftCapabilities.getKnowledge(owner);
        var otherBefore = ThaumcraftCapabilities.getKnowledge(other).serializeNBT().copy();
        var warpBefore = ThaumcraftCapabilities.getWarp(owner).serializeNBT().copy();
        int xp = owner.totalExperience;
        ItemStack mixed = mixedPotion(Items.SPLASH_POTION);
        List<ServerPlayer> recipients = new ArrayList<>();
        List<Object> packets = new ArrayList<>();
        try (var capture = PlayerDataSync.captureGameTestPackets((recipient, payload) -> { recipients.add(recipient); packets.add(payload); });
             var probe = new Probe(owner)) {
            helper.assertTrue(ResearchCategories.getResearch("!effect.moveSpeed") == null
                && ResearchCategories.getResearch("!effect.poison") == null, "effect markers are opaque, not a complex-entry bypass");
            ScanningManager.scanTheThing(owner, mixed);
            for (String key : List.of("!effect.moveSpeed", "!effect.poison")) {
                helper.assertTrue(data.isResearchKnown(key) && data.isResearchComplete(key) && data.getResearchStage(key) == 0,
                    "actual startup effect scanner writes exact stage0 marker " + key);
                for (var flag : EnumResearchFlag.values()) helper.assertFalse(data.hasResearchFlag(key, flag), "opaque effect marker invents no popup/research/page flags");
            }
            helper.assertTrue(probe.speedCalls == 1 && probe.poisonCalls == 1, "exactly one actual registered handler per matching effect");
            var first = data.serializeNBT().copy();
            int sends = packets.size();
            ScanningManager.scanTheThing(owner, mixed);
            helper.assertTrue(data.serializeNBT().equals(first) && packets.size() == sends
                && probe.speedCalls == 1 && probe.poisonCalls == 1, "repeat scans do not award twice or redispatch known effect events");
            helper.assertTrue(owner.totalExperience == xp && ThaumcraftCapabilities.getWarp(owner).serializeNBT().equals(warpBefore),
                "effect markers grant neither catalog XP nor research warp");
            helper.assertTrue(ThaumcraftCapabilities.getKnowledge(other).serializeNBT().equals(otherBefore), "bystander data unchanged");
            owner.tickCount = 20;
            PlayerDataSync.flushKnowledgeIfDue(owner);
            helper.assertTrue(packets.getLast() instanceof PacketSyncKnowledge && recipients.stream().allMatch(recipient -> recipient == owner),
                "actual dirty flush and any independent generic gain feedback address only owner");
        } finally { PlayerDataSync.forget(owner); PlayerDataSync.forget(other); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void canceledDefaultEffectScanDoesNotWriteMarkersKnowledgeWarpPacketsOrDirty(GameTestHelper helper) throws Exception {
        var owner = player(helper, "TC_Effect_Cancel");
        var data = ThaumcraftCapabilities.getKnowledge(owner);
        var before = data.serializeNBT().copy();
        var warpBefore = ThaumcraftCapabilities.getWarp(owner).serializeNBT().copy();
        int xp = owner.totalExperience;
        List<Object> packets = new ArrayList<>();
        try (var capture = PlayerDataSync.captureGameTestPackets((recipient, payload) -> packets.add(payload));
             var probe = new Probe(owner)) {
            probe.cancel = true;
            ScanningManager.scanTheThing(owner, mixedPotion(Items.POTION));
            helper.assertTrue(probe.speedCalls == 1 && probe.poisonCalls == 1, "real default effect events dispatched and canceled");
            helper.assertTrue(data.serializeNBT().equals(before) && ThaumcraftCapabilities.getWarp(owner).serializeNBT().equals(warpBefore)
                && owner.totalExperience == xp, "canceled scan changes no attachments or XP");
            helper.assertTrue(packets.isEmpty() && !PlayerDataSync.isKnowledgeDirty(owner), "canceled scan sends and queues nothing");
        } finally { PlayerDataSync.forget(owner); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void modernOnlyEffectUsesExplicitUnprovenNameRatherThanOldVanillaAlias(GameTestHelper helper) {
        var scanner = new ScanPotion(MobEffects.SLOW_FALLING);
        helper.assertFalse(scanner.sourceProvenName(), "modern extra is not falsely source-proven");
        helper.assertTrue(scanner.getResearchKey(null, null).equals("!" + MobEffects.SLOW_FALLING.value().getDescriptionId()),
            "modern effect retains its current description-key fallback");
        helper.assertTrue(scanner.checkThing(null, PotionContents.createItemStack(Items.POTION, Potions.SLOW_FALLING)), "current effect still scans");
        helper.succeed();
    }

    public static final class Probe implements AutoCloseable {
        private final ServerPlayer owner;
        boolean cancel;
        int speedCalls;
        int poisonCalls;

        Probe(ServerPlayer owner) { this.owner = owner; NeoForge.EVENT_BUS.register(this); }

        @SubscribeEvent
        public void research(ResearchEvent.Research event) {
            if (event.getPlayer() != owner) return;
            if (event.getResearchKey().equals("!effect.moveSpeed")) speedCalls++;
            if (event.getResearchKey().equals("!effect.poison")) poisonCalls++;
            if (cancel) event.setCanceled(true);
        }

        @SubscribeEvent
        public void knowledge(ResearchEvent.Knowledge event) {
            if (cancel && event.getPlayer() == owner) event.setCanceled(true);
        }

        @Override
        public void close() { NeoForge.EVENT_BUS.unregister(this); }
    }
}
