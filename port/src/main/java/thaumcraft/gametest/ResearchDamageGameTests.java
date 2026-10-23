package thaumcraft.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.DragonFireball;
import net.minecraft.world.entity.projectile.Fireball;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.LlamaSpit;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.entity.projectile.windcharge.AbstractWindCharge;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import thaumcraft.Thaumcraft;
import thaumcraft.api.capabilities.IPlayerKnowledge;
import thaumcraft.api.capabilities.IPlayerKnowledge.EnumResearchFlag;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.common.lib.capabilities.PlayerDataSync;
import thaumcraft.common.lib.network.playerdata.PacketSyncKnowledge;

/** Actual ServerPlayer.hurt -> native accepted-damage bus -> registered production hook.
 * Explicit research-stage fixtures isolate discovery conditions, not survival progression.
 * Outgoing packets use real compatible embedded logins and unmodified server send/checkPacket;
 * these tests do not claim projectile flight, GUI acceptance or remote TCP negotiation. */
@GameTestHolder(Thaumcraft.MODID)
@PrefixGameTestTemplate(false)
public final class ResearchDamageGameTests {
    private ResearchDamageGameTests() {}

    @GameTest(template = "empty")
    public static void fireDiscoveryRequiresExactStageAndPrecedesAbsorptionWithOwnerOnlyFirstSync(GameTestHelper h) {
        try (var players = new Players(h)) {
            var owner = players.owner;
            var data = ThaumcraftCapabilities.getKnowledge(owner);
            DamageSource fire = owner.damageSources().onFire();
            h.assertTrue(fire.is(DamageTypeTags.IS_FIRE), "real vanilla burning damage is tagged fire");
            CompoundTag unknown = data.serializeNBT();
            hurt(h, owner, fire, 1);
            h.assertTrue(unknown.equals(data.serializeNBT()), "unknown auromancy grants nothing");
            noDiscovery(h, owner); players.assertPeerUntouched(h);

            data.addResearch("BASEAUROMANCY"); data.setResearchStage("BASEAUROMANCY", 1);
            CompoundTag stageOne = data.serializeNBT();
            hurt(h, owner, fire, 1);
            h.assertTrue(stageOne.equals(data.serializeNBT()), "strict @2 rejects stage one");
            noDiscovery(h, owner); players.assertPeerUntouched(h);

            data.setResearchStage("BASEAUROMANCY", 2);
            prepareDamage(owner);
            owner.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 0));
            h.assertTrue(owner.getAbsorptionAmount() == 4, "real absorption effect raised the modern max-absorption attribute and hearts");
            float health = owner.getHealth();
            CompoundTag before = data.serializeNBT();
            h.assertTrue(owner.hurt(fire, 1), "real incoming fire damage was processed");
            h.assertTrue(owner.getHealth() == health && owner.getAbsorptionAmount() < 4,
                "absorption prevented HP loss, but not the original pre-absorption discovery");
            assertOnlyMarker(h, data, before, "f_onfire");
            assertDiscovery(h, owner, "f_onfire", "got.onfire");
            players.assertPeerUntouched(h);
            CompoundTag discovered = data.serializeNBT();
            hurt(h, owner, fire, 1);
            h.assertTrue(discovered.equals(data.serializeNBT()), "repeated burning is marker-idempotent");
            noDiscovery(h, owner); players.assertPeerUntouched(h);
            h.assertFalse(PlayerDataSync.isKnowledgeDirty(owner), "immediate source sync did not queue a duplicate periodic sync");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void allSevenLegacyProjectileDescendantsUseNativeDirectSourceStageAndFirstOnlySync(GameTestHelper h) {
        try (var players = new Players(h)) {
            var owner = players.owner;
            var data = ThaumcraftCapabilities.getKnowledge(owner);
            Cow shooter = Objects.requireNonNull(EntityType.COW.create(h.getLevel()));
            Entity arrow = Objects.requireNonNull(EntityType.ARROW.create(h.getLevel()));
            CompoundTag unknown = data.serializeNBT();
            hurt(h, owner, damage(owner, arrow, shooter), 1);
            h.assertTrue(unknown.equals(data.serializeNBT()), "unknown projectile research grants nothing");
            noDiscovery(h, owner);
            data.addResearch("FOCUSPROJECTILE"); data.setResearchStage("FOCUSPROJECTILE", 1);
            CompoundTag stageOne = data.serializeNBT();
            hurt(h, owner, damage(owner, arrow, shooter), 1);
            h.assertTrue(stageOne.equals(data.serializeNBT()), "strict projectile @2 rejects stage one");
            noDiscovery(h, owner);
            data.setResearchStage("FOCUSPROJECTILE", 2);

            List<Map.Entry<EntityType<? extends Entity>, String>> cases = List.of(
                Map.entry(EntityType.ARROW, "f_arrow"), Map.entry(EntityType.SPECTRAL_ARROW, "f_arrow"),
                Map.entry(EntityType.SMALL_FIREBALL, "f_fireball"), Map.entry(EntityType.FIREBALL, "f_fireball"),
                Map.entry(EntityType.DRAGON_FIREBALL, "f_fireball"), Map.entry(EntityType.WITHER_SKULL, "f_fireball"),
                Map.entry(EntityType.LLAMA_SPIT, "f_spit"));
            for (var entry : cases) {
                for (String marker : List.of("f_arrow", "f_fireball", "f_spit")) data.removeResearch(marker);
                Entity projectile = Objects.requireNonNull(entry.getKey().create(h.getLevel()));
                DamageSource source = damage(owner, projectile, shooter);
                h.assertTrue(source.getDirectEntity() == projectile && source.getEntity() == shooter,
                    "native damage source keeps projectile separate from its true attacker: " + entry.getKey());
                CompoundTag before = data.serializeNBT();
                hurt(h, owner, source, 1);
                assertOnlyMarker(h, data, before, entry.getValue());
                assertDiscovery(h, owner, entry.getValue(), "got.projectile");
                players.assertPeerUntouched(h);
                CompoundTag once = data.serializeNBT();
                hurt(h, owner, source, 1);
                h.assertTrue(once.equals(data.serializeNBT()), "second hit cannot reward the same marker twice");
                noDiscovery(h, owner); players.assertPeerUntouched(h);
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void modernSiblingsTrueAttackerAndCanceledOrFireResistantDamageCannotGrantLegacyMarkers(GameTestHelper h) {
        try (var players = new Players(h)) {
            var owner = players.owner;
            var data = ThaumcraftCapabilities.getKnowledge(owner);
            data.addResearch("FOCUSPROJECTILE"); data.setResearchStage("FOCUSPROJECTILE", 1);
            data.addResearch("BASEAUROMANCY"); data.setResearchStage("BASEAUROMANCY", 1);
            Cow shooter = Objects.requireNonNull(EntityType.COW.create(h.getLevel()));
            Entity firstFireball = Objects.requireNonNull(EntityType.SMALL_FIREBALL.create(h.getLevel()));
            DamageSource repeatedHit = damage(owner, firstFireball, shooter);
            h.assertFalse(repeatedHit.is(DamageTypeTags.BYPASSES_COOLDOWN), "native fireball obeys damage cooldown");
            CompoundTag stageOne = data.serializeNBT();
            hurt(h, owner, repeatedHit, 1);
            h.assertTrue(owner.invulnerableTime > 10 && stageOne.equals(data.serializeNBT()),
                "native accepted stage-one hit starts the cooldown but opens neither discovery");
            noDiscovery(h, owner); players.assertPeerUntouched(h);
            data.setResearchStage("FOCUSPROJECTILE", 2); data.setResearchStage("BASEAUROMANCY", 2);
            CompoundTag before = data.serializeNBT();
            // Do not call prepareDamage here: this must be a genuine same-strength hit
            // rejected by native cooldown after the research gates became eligible.
            h.assertFalse(owner.hurt(repeatedHit, 1), "native equal-strength hit is rejected during the existing cooldown");
            h.assertTrue(before.equals(data.serializeNBT()), "cooldown rejection grants neither newly eligible fire nor projectile marker");
            noDiscovery(h, owner); players.assertPeerUntouched(h);
            for (EntityType<? extends Entity> type : List.of(EntityType.TRIDENT, EntityType.WIND_CHARGE,
                EntityType.BREEZE_WIND_CHARGE, EntityType.FIREWORK_ROCKET, EntityType.SNOWBALL)) {
                Entity projectile = Objects.requireNonNull(type.create(h.getLevel()));
                hurt(h, owner, damage(owner, projectile, shooter), 1);
                h.assertTrue(before.equals(data.serializeNBT()), "modern/non-legacy projectile excluded: " + type);
                noDiscovery(h, owner); players.assertPeerUntouched(h);
            }
            Entity arrow = Objects.requireNonNull(EntityType.ARROW.create(h.getLevel()));
            DamageSource wrongDirect = new DamageSource(owner.damageSources().arrow((AbstractArrow)arrow, shooter).typeHolder(), shooter, arrow);
            h.assertTrue(wrongDirect.getDirectEntity() == shooter && wrongDirect.getEntity() == arrow, "fixture separates immediate and true source");
            hurt(h, owner, wrongDirect, 1);
            h.assertTrue(before.equals(data.serializeNBT()), "arrow TRUE attacker is not an immediate arrow hit");
            noDiscovery(h, owner); players.assertPeerUntouched(h);

            prepareDamage(owner);
            owner.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 100, 0));
            h.assertFalse(owner.hurt(owner.damageSources().onFire(), 1), "native fire resistance rejects before incoming event");
            h.assertTrue(before.equals(data.serializeNBT()), "invulnerable fire creates no research");
            noDiscovery(h, owner); players.assertPeerUntouched(h);
            owner.removeEffect(MobEffects.FIRE_RESISTANCE);

            try (var cancellation = new DamageCancellation(owner)) {
                prepareDamage(owner);
                h.assertFalse(owner.hurt(damage(owner, arrow, shooter), 1), "native incoming-damage cancellation rejects hurt");
                h.assertTrue(cancellation.calls == 1, "actual incoming damage event reached the high-priority listener once");
                h.assertTrue(before.equals(data.serializeNBT()), "canceled incoming damage never reaches the accepted-damage discovery hook");
                noDiscovery(h, owner); players.assertPeerUntouched(h);
            }
            h.assertTrue(shooter.hurt(shooter.damageSources().onFire(), 1), "native non-player victim also receives damage");
            h.assertTrue(before.equals(data.serializeNBT()), "non-player damage does not mutate any player knowledge");
            noDiscovery(h, owner); players.assertPeerUntouched(h);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void realFireballWithBothGatesPreservesSourceTwoImmediateSnapshotsAndActionbarOrder(GameTestHelper h) {
        try (var players = new Players(h)) {
            var owner = players.owner;
            var data = ThaumcraftCapabilities.getKnowledge(owner);
            data.addResearch("FOCUSPROJECTILE"); data.setResearchStage("FOCUSPROJECTILE", 2);
            data.addResearch("BASEAUROMANCY"); data.setResearchStage("BASEAUROMANCY", 2);
            Cow shooter = Objects.requireNonNull(EntityType.COW.create(h.getLevel()));
            Entity fireball = Objects.requireNonNull(EntityType.SMALL_FIREBALL.create(h.getLevel()));
            DamageSource source = damage(owner, fireball, shooter);
            h.assertTrue(source.is(DamageTypeTags.IS_FIRE), "vanilla small-fireball source also carries the fire tag");
            hurt(h, owner, source, 1);
            List<Object> sent = discoveries(owner);
            h.assertTrue(sent.size() == 4, "original separate fire and projectile branches each sync then notify");
            h.assertTrue(sent.get(0) instanceof PacketSyncKnowledge first && hasMarker(first.data(), "f_onfire")
                && !hasMarker(first.data(), "f_fireball"), "first owner snapshot contains only the earlier fire discovery");
            assertMessage(h, sent.get(1), "got.onfire");
            h.assertTrue(sent.get(2) instanceof PacketSyncKnowledge second && hasMarker(second.data(), "f_onfire")
                && hasMarker(second.data(), "f_fireball"), "second snapshot includes both discoveries in source order");
            assertMessage(h, sent.get(3), "got.projectile");
            players.assertPeerUntouched(h);
            CompoundTag once = data.serializeNBT();
            hurt(h, owner, source, 1);
            h.assertTrue(once.equals(data.serializeNBT()), "repeated dual-purpose hit does not regrant either marker");
            noDiscovery(h, owner); players.assertPeerUntouched(h);
        }
        h.succeed();
    }

    private static void prepareDamage(ServerPlayer owner) {
        owner.invulnerableTime = 0;
        owner.setHealth(owner.getMaxHealth());
        owner.removeEffect(MobEffects.ABSORPTION);
        owner.setAbsorptionAmount(0);
        owner.clearFire();
    }

    private static void hurt(GameTestHelper h, ServerPlayer owner, DamageSource source, float amount) {
        prepareDamage(owner);
        h.assertTrue(owner.hurt(source, amount), "actual ServerPlayer.hurt must reach the incoming damage pipeline");
    }

    private static DamageSource damage(ServerPlayer owner, Entity projectile, Cow shooter) {
        var sources = owner.damageSources();
        if (projectile instanceof ThrownTrident) return sources.trident(projectile, shooter);
        if (projectile instanceof AbstractArrow arrow) return sources.arrow(arrow, shooter);
        if (projectile instanceof Fireball fireball) return sources.fireball(fireball, shooter);
        if (projectile instanceof WitherSkull skull) return sources.witherSkull(skull, shooter);
        if (projectile instanceof DragonFireball) return sources.indirectMagic(projectile, shooter);
        if (projectile instanceof LlamaSpit) return sources.spit(projectile, shooter);
        if (projectile instanceof AbstractWindCharge) return sources.windCharge(projectile, shooter);
        if (projectile instanceof FireworkRocketEntity rocket) return sources.fireworks(rocket, shooter);
        return sources.thrown(projectile, shooter);
    }

    private static boolean hasMarker(CompoundTag snapshot, String key) {
        var research = snapshot.getList("research", Tag.TAG_COMPOUND);
        for (int i = 0; i < research.size(); i++) if (research.getCompound(i).getString("key").equals(key)) return true;
        return false;
    }

    private static void assertOnlyMarker(GameTestHelper h, IPlayerKnowledge data, CompoundTag before, String marker) {
        CompoundTag after = data.serializeNBT();
        var remaining = after.getList("research", Tag.TAG_COMPOUND);
        boolean removed = false;
        for (int i = 0; i < remaining.size(); i++) if (remaining.getCompound(i).getString("key").equals(marker)) {
            h.assertTrue(remaining.getCompound(i).getAllKeys().equals(java.util.Set.of("key")), "source marker has no fabricated stage or flags");
            remaining.remove(i); removed = true; break;
        }
        h.assertTrue(removed && before.equals(after), "only the source marker changes, with no observation/theory pool or unrelated research mutation");
        h.assertTrue(data.getResearchStage(marker) == 0, "marker stays at source default stage zero");
        for (EnumResearchFlag flag : EnumResearchFlag.values()) h.assertFalse(data.hasResearchFlag(marker, flag), "damage discovery does not add " + flag);
    }

    private static List<Object> discoveries(ServerPlayer player) {
        var channel = (EmbeddedChannel)player.connection.getConnection().channel();
        channel.runPendingTasks();
        channel.flushOutbound(); // preserve real writes if vanilla batched flushing on this tick
        var found = new ArrayList<Object>();
        Object packet;
        while ((packet = channel.readOutbound()) != null) {
            if (packet instanceof ClientboundCustomPayloadPacket custom && custom.payload() instanceof PacketSyncKnowledge knowledge) found.add(knowledge);
            if (packet instanceof ClientboundSystemChatPacket chat && chat.overlay()) found.add(chat);
            ReferenceCountUtil.release(packet);
        }
        return found;
    }

    private static void assertDiscovery(GameTestHelper h, ServerPlayer owner, String marker, String key) {
        List<Object> sent = discoveries(owner);
        h.assertTrue(sent.size() == 2, "first marker sends exactly one owner snapshot followed by one actionbar");
        h.assertTrue(sent.get(0) instanceof PacketSyncKnowledge snapshot && snapshot.data().equals(ThaumcraftCapabilities.getKnowledge(owner).serializeNBT())
            && hasMarker(snapshot.data(), marker), "unmodified send route carries the actual owner's new complete snapshot first");
        assertMessage(h, sent.get(1), key);
    }

    private static void assertMessage(GameTestHelper h, Object packet, String key) {
        h.assertTrue(packet instanceof ClientboundSystemChatPacket chat && chat.overlay()
            && chat.content().getContents() instanceof TranslatableContents translation && translation.getKey().equals(key)
            && Objects.equals(chat.content().getStyle().getColor(), TextColor.fromLegacyFormat(ChatFormatting.DARK_PURPLE)),
            "source translated dark-purple actionbar: " + key);
    }

    private static void noDiscovery(GameTestHelper h, ServerPlayer owner) {
        h.assertTrue(discoveries(owner).isEmpty(), "rejected or repeated damage sends no knowledge snapshot or discovery actionbar");
    }

    public static final class DamageCancellation implements AutoCloseable {
        private final ServerPlayer owner;
        int calls;
        DamageCancellation(ServerPlayer owner) { this.owner = owner; NeoForge.EVENT_BUS.register(this); }
        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public void incoming(LivingIncomingDamageEvent event) {
            if (event.getEntity() == owner) { calls++; event.setCanceled(true); }
        }
        @Override public void close() { NeoForge.EVENT_BUS.unregister(this); }
    }

    private static final class Players implements AutoCloseable {
        private final GameTestHelper helper;
        final ServerPlayer owner;
        final ServerPlayer peer;
        private final CompoundTag peerBefore;
        Players(GameTestHelper helper) {
            this.helper = helper;
            owner = login(helper, "TC_Hurt_Owner", 0);
            try { peer = login(helper, "TC_Hurt_Peer", 2); }
            catch (RuntimeException | Error failure) { remove(owner); throw failure; }
            ThaumcraftCapabilities.getKnowledge(owner).clear();
            ThaumcraftCapabilities.getKnowledge(peer).clear();
            PlayerDataSync.forget(owner); PlayerDataSync.forget(peer);
            discoveries(owner); discoveries(peer); // discard actual login/fixture setup traffic only
            peerBefore = ThaumcraftCapabilities.getKnowledge(peer).serializeNBT();
        }
        private static ServerPlayer login(GameTestHelper h, String name, int offset) {
            if (!GameTestHooks.isGametestServer()) throw new IllegalStateException("Damage fixture requires GameTest server");
            var profile = new GameProfile(UUID.randomUUID(), name);
            var information = ClientInformation.createDefault();
            var player = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), profile, information);
            var connection = new Connection(PacketFlow.SERVERBOUND);
            new EmbeddedChannel(connection);
            NetworkRegistry.configureMockConnection(connection);
            h.assertTrue(NetworkRegistry.hasChannel(connection, ConnectionProtocol.PLAY, ResourceLocation.parse("thaumcraft:sync_knowledge")),
                "official compatible-client fixture has actual owner knowledge channel before real login callbacks");
            h.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player,
                new CommonListenerCookie(profile, 0, information, false, ConnectionType.NEOFORGE));
            player.setGameMode(GameType.SURVIVAL);
            player.setPos(Vec3.atLowerCornerOf(h.absolutePos(new BlockPos(1 + offset, 100, 1))));
            // Vanilla ServerPlayer starts with 60 spawn-invulnerable ticks. Exercise its
            // native tick rather than changing private state or bypassing fire/projectile types.
            for (int i = 0; i < 60; i++) player.tick();
            return player;
        }
        void assertPeerUntouched(GameTestHelper h) {
            h.assertTrue(peerBefore.equals(ThaumcraftCapabilities.getKnowledge(peer).serializeNBT()), "peer knowledge is private and unchanged");
            noDiscovery(h, peer);
        }
        private void remove(ServerPlayer player) {
            PlayerDataSync.forget(player);
            try { helper.getLevel().getServer().getPlayerList().remove(player); }
            finally { ((EmbeddedChannel)player.connection.getConnection().channel()).finishAndReleaseAll(); }
        }
        @Override public void close() { try { remove(peer); } finally { remove(owner); } }
    }
}
