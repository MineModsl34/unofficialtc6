package thaumcraft.gametest;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumcraft.Thaumcraft;
import thaumcraft.api.capabilities.IPlayerKnowledge.EnumResearchFlag;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.common.config.ConfigResearch;
import thaumcraft.common.lib.capabilities.PlayerDataSync;
import thaumcraft.common.lib.network.playerdata.PacketSyncKnowledge;

/** Actual production event/biome/stat routes with unspawned isolated FakePlayer owners.
 * Stored statistics and stage are explicit fixtures, not survival exploration or client HUD proof. */
@GameTestHolder(Thaumcraft.MODID)
@PrefixGameTestTemplate(false)
public final class PeriodicResearchGameTests {
    private static final List<String> MOVEMENT = List.of("m_walker", "m_runner", "m_jumper", "m_swimmer");

    @GameTest(template = "empty")
    public static void realPostTickUsesStrictStatsEvery200AfterQueuedOwnerFlushAndOnlyOnce(GameTestHelper helper) throws Exception {
        TestPlayer owner = player(helper.getLevel(), "TC_Periodic_Stats");
        TestPlayer other = player(helper.getLevel(), "TC_Periodic_Peer");
        var data = ThaumcraftCapabilities.getKnowledge(owner);
        data.addResearch("!PERIODIC_KEEP"); data.setResearchStage("!PERIODIC_KEEP", 3);
        data.setResearchFlag("!PERIODIC_KEEP", EnumResearchFlag.PAGE);
        CompoundTag peerBefore = ThaumcraftCapabilities.getKnowledge(other).serializeNBT().copy();
        CompoundTag warpBefore = ThaumcraftCapabilities.getWarp(owner).serializeNBT().copy();
        int xp = owner.totalExperience;
        List<CompoundTag> snapshots = new ArrayList<>();
        try (var capture = PlayerDataSync.captureGameTestPackets((recipient, packet) -> {
            helper.assertTrue(recipient == owner && packet instanceof PacketSyncKnowledge,
                "periodic direct markers synchronize only the actual owner knowledge");
            snapshots.add(((PacketSyncKnowledge) packet).data().copy());
        })) {
            stats(owner, 160000, 80000, 500, 8000);
            setStat(owner, Stats.SWIM_ONE_CM, 900000); // New 3D sprint-swimming is NOT source horizontal swimming.
            postTick(owner, 200);
            helper.assertTrue(snapshots.isEmpty() && MOVEMENT.stream().noneMatch(data::isResearchKnown),
                "all four strict greater-than boundaries deny, including renamed water stat");
            stats(owner, 160001, 80001, 501, 8001);
            PlayerDataSync.markKnowledgeDirty(owner);
            postTick(owner, 399);
            helper.assertTrue(snapshots.isEmpty() && PlayerDataSync.isKnowledgeDirty(owner), "off-cadence event does not grant or flush");
            postTick(owner, 400);
            helper.assertTrue(snapshots.size() == 5 && !PlayerDataSync.isKnowledgeDirty(owner),
                "queued twenty-tick sync occurs first, then one immediate owner snapshot for each new marker");
            helper.assertTrue(MOVEMENT.stream().noneMatch(key -> hasKey(snapshots.getFirst(), key)),
                "first queued snapshot genuinely precedes periodic writes");
            for (int i = 0; i < MOVEMENT.size(); i++) {
                String key = MOVEMENT.get(i);
                helper.assertTrue(data.getResearchStage(key) == 0 && hasKey(snapshots.get(i + 1), key),
                    "source direct raw-known-zero marker and exact incremental order: " + key);
                for (int next = i + 1; next < MOVEMENT.size(); next++)
                    helper.assertFalse(hasKey(snapshots.get(i + 1), MOVEMENT.get(next)), "later marker not sent early");
                for (EnumResearchFlag flag : EnumResearchFlag.values())
                    helper.assertFalse(data.hasResearchFlag(key, flag), "periodic marker is not a completed research popup");
            }
            postTick(owner, 600);
            helper.assertTrue(snapshots.size() == 5 && owner.messages.isEmpty(), "repeat has no regrant/resync, movement has no actionbar");
            helper.assertTrue(owner.totalExperience == xp && ThaumcraftCapabilities.getWarp(owner).serializeNBT().equals(warpBefore)
                && data.getResearchStage("!PERIODIC_KEEP") == 3 && data.hasResearchFlag("!PERIODIC_KEEP", EnumResearchFlag.PAGE)
                && ThaumcraftCapabilities.getKnowledge(other).serializeNBT().equals(peerBefore),
                "source does not award XP, mutate warp, clear existing flags or touch the peer");
        } finally { PlayerDataSync.forget(owner); PlayerDataSync.forget(other); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void exactQualifiedHeightGateStrictAbsoluteYAndLogicalHeightPreserveRawMarkers(GameTestHelper helper) throws Exception {
        TestPlayer owner = player(helper.getLevel(), "TC_Periodic_Height");
        var data = ThaumcraftCapabilities.getKnowledge(owner);
        List<CompoundTag> snapshots = new ArrayList<>();
        try (var capture = PlayerDataSync.captureGameTestPackets((recipient, packet) -> {
            helper.assertTrue(recipient == owner && packet instanceof PacketSyncKnowledge, "height feedback remains owner-only");
            snapshots.add(((PacketSyncKnowledge) packet).data().copy());
        })) {
            double high = owner.level().dimensionType().logicalHeight() * 0.4D;
            owner.setPos(0.5D, 9.0D, 0.5D);
            ConfigResearch.checkPeriodicStuff(owner);
            data.addResearch("UNLOCKAUROMANCY");
            ConfigResearch.checkPeriodicStuff(owner);
            helper.assertTrue(snapshots.isEmpty(), "unknown and raw-known-zero auromancy do not enable height markers");
            data.setResearchStage("UNLOCKAUROMANCY", 2);
            ConfigResearch.checkPeriodicStuff(owner);
            owner.setPos(0.5D, high + 1.0D, 0.5D);
            ConfigResearch.checkPeriodicStuff(owner);
            helper.assertTrue(snapshots.isEmpty(), "stage two already passed the source-only height window");
            data.setResearchStage("UNLOCKAUROMANCY", 1); // Explicit isolated stage fixture, not gameplay rollback.
            owner.setPos(0.5D, 10.0D, 0.5D);
            ConfigResearch.checkPeriodicStuff(owner);
            owner.setPos(0.5D, high, 0.5D);
            ConfigResearch.checkPeriodicStuff(owner);
            helper.assertTrue(snapshots.isEmpty(), "exact y=10 and y=logicalHeight*.4 fail strict inequalities");
            owner.setPos(0.5D, Math.nextDown(10.0D), 0.5D);
            ConfigResearch.checkPeriodicStuff(owner);
            helper.assertTrue(snapshots.size() == 1 && hasKey(snapshots.getFirst(), "m_deepdown")
                && !hasKey(snapshots.getFirst(), "m_uphigh"), "low threshold records only source deep marker");
            owner.setPos(0.5D, Math.nextUp(high), 0.5D);
            ConfigResearch.checkPeriodicStuff(owner);
            helper.assertTrue(snapshots.size() == 2 && data.getResearchStage("m_deepdown") == 0
                && data.getResearchStage("m_uphigh") == 0 && data.getResearchStage("UNLOCKAUROMANCY") == 1,
                "strict high threshold records only new raw-known-zero marker without advancing gate");
            helper.assertTrue(owner.messages.size() == 2 && translation(owner.messages.get(0)).equals("got.deepdown")
                && translation(owner.messages.get(1)).equals("got.uphigh"), "exact source actionbar translation order");
            ConfigResearch.checkPeriodicStuff(owner);
            helper.assertTrue(snapshots.size() == 2 && owner.messages.size() == 2, "no repeated height notifications");
        } finally { PlayerDataSync.forget(owner); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void actualNetherEndBiomeTagsNeedNoStageAndNotifyOnlyTheirActualOwners(GameTestHelper helper) throws Exception {
        ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        ServerLevel end = helper.getLevel().getServer().getLevel(Level.END);
        helper.assertTrue(nether != null && end != null, "real isolated server has vanilla Nether and End levels");
        TestPlayer hell = player(nether, "TC_Periodic_Hell"), edge = player(end, "TC_Periodic_End");
        TestPlayer peer = player(helper.getLevel(), "TC_Periodic_BiomePeer");
        helper.assertTrue(hell.level().getBiome(hell.blockPosition()).is(Tags.Biomes.IS_NETHER)
            && edge.level().getBiome(edge.blockPosition()).is(Tags.Biomes.IS_END), "actual biome holders carry source-compatible modern biome-type tags");
        CompoundTag peerBefore = ThaumcraftCapabilities.getKnowledge(peer).serializeNBT().copy();
        List<ServerPlayer> recipients = new ArrayList<>();
        List<CompoundTag> snapshots = new ArrayList<>();
        try (var capture = PlayerDataSync.captureGameTestPackets((recipient, packet) -> {
            helper.assertTrue((recipient == hell || recipient == edge) && packet instanceof PacketSyncKnowledge,
                "biome grants use only actual owner synchronization");
            recipients.add(recipient); snapshots.add(((PacketSyncKnowledge) packet).data().copy());
        })) {
            ConfigResearch.checkPeriodicStuff(hell); ConfigResearch.checkPeriodicStuff(edge);
            helper.assertTrue(recipients.equals(List.of(hell, edge)) && hasKey(snapshots.get(0), "m_hellandback")
                && !hasKey(snapshots.get(0), "m_endoftheworld") && hasKey(snapshots.get(1), "m_endoftheworld")
                && !hasKey(snapshots.get(1), "m_hellandback"), "source each-biome marker is independent and not stage-gated");
            helper.assertTrue(ThaumcraftCapabilities.getKnowledge(hell).getResearchStage("m_hellandback") == 0
                && ThaumcraftCapabilities.getKnowledge(edge).getResearchStage("m_endoftheworld") == 0
                && hell.messages.size() == 1 && translation(hell.messages.getFirst()).equals("got.hellandback")
                && edge.messages.size() == 1 && translation(edge.messages.getFirst()).equals("got.endoftheworld"),
                "raw known0 with source translated purple actionbar");
            ConfigResearch.checkPeriodicStuff(hell); ConfigResearch.checkPeriodicStuff(edge);
            helper.assertTrue(snapshots.size() == 2 && hell.messages.size() == 1 && edge.messages.size() == 1
                && ThaumcraftCapabilities.getKnowledge(peer).serializeNBT().equals(peerBefore), "repeat/bystander remain unchanged");
        } finally { PlayerDataSync.forget(hell); PlayerDataSync.forget(edge); PlayerDataSync.forget(peer); }
        helper.succeed();
    }

    private static TestPlayer player(ServerLevel level, String name) {
        TestPlayer player = new TestPlayer(level, new GameProfile(UUID.randomUUID(), name));
        player.setPos(0.5D, 64.0D, 0.5D);
        return player;
    }

    private static void postTick(ServerPlayer player, int tick) {
        player.tickCount = tick;
        NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player));
    }

    private static void stats(ServerPlayer owner, int walk, int run, int jump, int water) {
        setStat(owner, Stats.WALK_ONE_CM, walk); setStat(owner, Stats.SPRINT_ONE_CM, run);
        setStat(owner, Stats.JUMP, jump); setStat(owner, Stats.WALK_ON_WATER_ONE_CM, water);
    }

    private static void setStat(ServerPlayer owner, ResourceLocation stat, int value) {
        owner.getStats().setValue(owner, Stats.CUSTOM.get(stat), value);
    }

    private static boolean hasKey(CompoundTag data, String key) {
        for (var row : data.getList("research", net.minecraft.nbt.Tag.TAG_COMPOUND))
            if (((CompoundTag) row).getString("key").equals(key)) return true;
        return false;
    }

    private static String translation(Component component) {
        if (!(component.getContents() instanceof TranslatableContents value)) throw new AssertionError("Expected actual translated component");
        if (!component.getStyle().getColor().equals(net.minecraft.network.chat.TextColor.fromLegacyFormat(ChatFormatting.DARK_PURPLE)))
            throw new AssertionError("Expected source dark-purple actionbar");
        return value.getKey();
    }

    private static final class TestPlayer extends FakePlayer {
        final List<Component> messages = new ArrayList<>();
        TestPlayer(ServerLevel level, GameProfile profile) { super(level, profile); }
        @Override public void displayClientMessage(Component component, boolean actionBar) {
            if (!actionBar) throw new AssertionError("Source exploration notification must use actionbar");
            messages.add(component.copy());
        }
    }
}
